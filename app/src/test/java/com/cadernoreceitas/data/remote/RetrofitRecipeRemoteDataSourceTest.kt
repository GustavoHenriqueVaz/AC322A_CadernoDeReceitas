package com.cadernoreceitas.data.remote

import com.cadernoreceitas.data.remote.dto.CategoriesResponseDto
import com.cadernoreceitas.data.remote.dto.MealSummariesResponseDto
import com.cadernoreceitas.data.remote.dto.MealsResponseDto
import com.cadernoreceitas.domain.model.RecipeException
import com.cadernoreceitas.testutil.TestFixtures
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * `RetrofitRecipeRemoteDataSource` + `MealApi` de verdade contra um `MockWebServer` (teste JVM puro).
 * O Retrofit e montado como no `DefaultAppContainer` (mesmo conversor kotlinx-serialization).
 */
class RetrofitRecipeRemoteDataSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var remote: RetrofitRecipeRemoteDataSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        remote = RetrofitRecipeRemoteDataSource(buildApi())
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun buildApi(client: OkHttpClient = OkHttpClient.Builder().build()): MealApi =
        Retrofit.Builder()
            .baseUrl(server.url("/api/json/v1/1/"))
            .client(client)
            .addConverterFactory(
                TestFixtures.json.asConverterFactory("application/json; charset=UTF-8".toMediaType()),
            )
            .build()
            .create(MealApi::class.java)

    private fun json(body: String, code: Int = 200) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun fixture(name: String) = TestFixtures.resourceText(name)

    private suspend fun expectFailure(kind: RecipeException.Kind, block: suspend () -> Unit): RecipeException {
        val error = runCatching { block() }.exceptionOrNull()
        assertThat(error).isInstanceOf(RecipeException::class.java)
        assertThat((error as RecipeException).kind).isEqualTo(kind)
        return error
    }

    // ------------------------------------------------------------------ sucesso

    @Test
    fun `search devolve as receitas e chama search-php com o parametro s`() = runBlocking<Unit> {
        server.enqueue(json(fixture("meals_search.json")))

        val meals = remote.search("chicken soup")

        assertThat(meals.map { it.strMeal }).containsExactly("Satee", "Kumpir", "Flan").inOrder()
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.requestUrl!!.encodedPath).isEqualTo("/api/json/v1/1/search.php")
        assertThat(request.requestUrl!!.queryParameter("s")).isEqualTo("chicken soup")
    }

    @Test
    fun `search com caracteres especiais e acentos chega ao servidor intacto`() = runBlocking<Unit> {
        val query = "pão & queijo=100% #1?"
        server.enqueue(json(fixture("meals_null.json")))

        remote.search(query)

        val request = server.takeRequest()
        assertThat(request.requestUrl!!.queryParameter("s")).isEqualTo(query)
        assertThat(request.requestUrl!!.querySize).isEqualTo(1) // nada "vazou" para outros parametros
    }

    @Test
    fun `search com texto vazio pede os destaques (s vazio)`() = runBlocking<Unit> {
        server.enqueue(json(fixture("meals_search.json")))

        val meals = remote.search("")

        assertThat(meals).hasSize(3)
        assertThat(server.takeRequest().requestUrl!!.queryParameter("s")).isEmpty()
    }

    @Test
    fun `lookup devolve a receita e chama lookup-php com o parametro i`() = runBlocking<Unit> {
        server.enqueue(json(fixture("meal_lookup.json")))

        val meal = remote.lookup("52772")

        assertThat(meal!!.idMeal).isEqualTo("52772")
        assertThat(meal.strInstructions).contains("° F") // UTF-8 chegou direito
        val request = server.takeRequest()
        assertThat(request.requestUrl!!.encodedPath).isEqualTo("/api/json/v1/1/lookup.php")
        assertThat(request.requestUrl!!.queryParameter("i")).isEqualTo("52772")
    }

    @Test
    fun `random devolve a primeira receita da resposta`() = runBlocking<Unit> {
        server.enqueue(json(fixture("random.json")))

        val meal = remote.random()

        assertThat(meal!!.strMeal).isEqualTo("Torta de fiambre")
        assertThat(server.takeRequest().requestUrl!!.encodedPath).isEqualTo("/api/json/v1/1/random.php")
    }

    @Test
    fun `categories devolve a lista de categorias`() = runBlocking<Unit> {
        server.enqueue(json(fixture("categories.json")))

        val categories = remote.categories()

        assertThat(categories.map { it.strCategory }).containsExactly("Beef", "Chicken", "Dessert").inOrder()
        assertThat(server.takeRequest().requestUrl!!.encodedPath).isEqualTo("/api/json/v1/1/categories.php")
    }

    @Test
    fun `byCategory devolve os resumos e chama filter-php com o parametro c`() = runBlocking<Unit> {
        server.enqueue(json(fixture("filter.json")))

        val summaries = remote.byCategory("Seafood")

        assertThat(summaries).hasSize(3)
        assertThat(summaries[0].strCountry).isEqualTo("Brazil")
        val request = server.takeRequest()
        assertThat(request.requestUrl!!.encodedPath).isEqualTo("/api/json/v1/1/filter.php")
        assertThat(request.requestUrl!!.queryParameter("c")).isEqualTo("Seafood")
    }

    @Test
    fun `resposta com campos novos e desconhecidos ainda e aceita`() = runBlocking<Unit> {
        server.enqueue(json("{\"meals\":[{\"idMeal\":\"1\",\"strMeal\":\"Bolo\",\"campoNovo\":{\"x\":1}}],\"v\":2}"))

        assertThat(remote.search("bolo").single().strMeal).isEqualTo("Bolo")
    }

    // ------------------------------------------------------------------ {"meals":null}

    @Test
    fun `meals null (nada encontrado) vira lista vazia ou null, nunca erro`() = runBlocking<Unit> {
        repeat(4) { server.enqueue(json(fixture("meals_null.json"))) }

        assertThat(remote.search("zzzz")).isEmpty()
        assertThat(remote.lookup("99999999")).isNull()
        assertThat(remote.random()).isNull()
        assertThat(remote.byCategory("Nope")).isEmpty()
        server.enqueue(json("{\"categories\":null}"))
        assertThat(remote.categories()).isEmpty()
    }

    // ------------------------------------------------------------------ HTTP de erro

    @Test
    fun `HTTP 500 vira RecipeException SERVER (e a causa e a HttpException)`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val error = expectFailure(RecipeException.Kind.SERVER) { remote.search("x") }

        assertThat(error.cause).isInstanceOf(HttpException::class.java)
        assertThat((error.cause as HttpException).code()).isEqualTo(500)
    }

    @Test
    fun `outros HTTP de erro (404, 429, 502, 503) tambem viram SERVER`() = runBlocking<Unit> {
        for (code in listOf(400, 404, 429, 502, 503)) {
            server.enqueue(MockResponse().setResponseCode(code).setBody("<html>erro $code</html>"))
            expectFailure(RecipeException.Kind.SERVER) { remote.lookup("1") }
        }
    }

    @Test
    fun `HTTP 500 em todos os endpoints`() = runBlocking<Unit> {
        repeat(5) { server.enqueue(MockResponse().setResponseCode(500)) }

        expectFailure(RecipeException.Kind.SERVER) { remote.search("x") }
        expectFailure(RecipeException.Kind.SERVER) { remote.lookup("1") }
        expectFailure(RecipeException.Kind.SERVER) { remote.random() }
        expectFailure(RecipeException.Kind.SERVER) { remote.categories() }
        expectFailure(RecipeException.Kind.SERVER) { remote.byCategory("Beef") }
    }

    // ------------------------------------------------------------------ corpo invalido

    @Test
    fun `corpo que nao e JSON (HTML de proxy) vira INVALID_DATA`() = runBlocking<Unit> {
        server.enqueue(json("<html><body>Bem-vindo ao Wi-Fi do hotel</body></html>"))

        val error = expectFailure(RecipeException.Kind.INVALID_DATA) { remote.search("x") }

        assertThat(error.cause).isInstanceOf(SerializationException::class.java)
    }

    @Test
    fun `JSON truncado vira INVALID_DATA`() = runBlocking<Unit> {
        server.enqueue(json("{\"meals\":[{\"idMeal\":\"1\",\"strMeal\":\"Bo"))

        expectFailure(RecipeException.Kind.INVALID_DATA) { remote.lookup("1") }
    }

    @Test
    fun `meals como texto (Invalid ID da API real) vira INVALID_DATA`() = runBlocking<Unit> {
        server.enqueue(json(fixture("meals_invalid_id.json")))

        expectFailure(RecipeException.Kind.INVALID_DATA) { remote.lookup("abc") }
    }

    @Test
    fun `corpo vazio com 200 vira INVALID_DATA`() = runBlocking<Unit> {
        server.enqueue(json(""))

        expectFailure(RecipeException.Kind.INVALID_DATA) { remote.categories() }
    }

    @Test
    fun `tipo errado em um campo (meals objeto) vira INVALID_DATA`() = runBlocking<Unit> {
        server.enqueue(json("{\"meals\":{\"idMeal\":\"1\"}}"))

        expectFailure(RecipeException.Kind.INVALID_DATA) { remote.search("x") }
    }

    // ------------------------------------------------------------------ rede

    @Test
    fun `servidor que derruba a conexao vira NETWORK`() = runBlocking<Unit> {
        // o OkHttp refaz a tentativa uma vez em falha de conexao: enfileira o bastante
        repeat(4) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)) }

        val error = expectFailure(RecipeException.Kind.NETWORK) { remote.search("x") }

        assertThat(error.cause).isInstanceOf(IOException::class.java)
    }

    @Test
    fun `conexao recusada (servidor fora do ar) vira NETWORK`() = runBlocking<Unit> {
        server.shutdown()

        expectFailure(RecipeException.Kind.NETWORK) { remote.lookup("1") }
    }

    @Test
    fun `timeout de leitura vira NETWORK`() = runBlocking<Unit> {
        val impatient = RetrofitRecipeRemoteDataSource(
            buildApi(
                OkHttpClient.Builder()
                    .readTimeout(300, TimeUnit.MILLISECONDS)
                    .retryOnConnectionFailure(false)
                    .build(),
            ),
        )
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val error = expectFailure(RecipeException.Kind.NETWORK) { impatient.search("x") }

        assertThat(error.cause).isInstanceOf(SocketTimeoutException::class.java)
    }

    @Test
    fun `corpo interrompido no meio vira NETWORK`() = runBlocking<Unit> {
        server.enqueue(
            json(fixture("meals_search.json")).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )

        expectFailure(RecipeException.Kind.NETWORK) { remote.search("x") }
    }

    // ------------------------------------------------------------------ cancelamento

    @Test
    fun `cancelar a coroutine cancela a chamada HTTP e nao vira RecipeException`() = runBlocking<Unit> {
        server.enqueue(json(fixture("meals_search.json")).setHeadersDelay(10, TimeUnit.SECONDS))
        val started = CompletableDeferred<Unit>()

        val job = async {
            started.complete(Unit)
            remote.search("x")
        }
        started.await()
        assertThat(server.takeRequest(5, TimeUnit.SECONDS)).isNotNull() // a requisicao chegou ao servidor
        job.cancel()

        val error = runCatching { job.await() }.exceptionOrNull()
        assertThat(error).isInstanceOf(CancellationException::class.java)
        assertThat(error).isNotInstanceOf(RecipeException::class.java)
        assertThat(job.isCancelled).isTrue()
    }

    @Test
    fun `withTimeout sobre uma chamada lenta lanca TimeoutCancellationException (nao NETWORK)`() = runBlocking<Unit> {
        server.enqueue(json(fixture("meals_search.json")).setHeadersDelay(10, TimeUnit.SECONDS))

        val error = runCatching { withTimeout(400) { remote.search("x") } }.exceptionOrNull()

        assertThat(error).isInstanceOf(TimeoutCancellationException::class.java)
    }

    // ------------------------------------------------------------------ traducao de excecoes (API falsa)

    /** API cujo comportamento e uma excecao escolhida: isola a tabela de traducao de excecoes. */
    private class ThrowingApi(private val error: Throwable) : MealApi {
        override suspend fun searchMeals(query: String): MealsResponseDto = throw error
        override suspend fun lookupMeal(id: String): MealsResponseDto = throw error
        override suspend fun randomMeal(): MealsResponseDto = throw error
        override suspend fun categories(): CategoriesResponseDto = throw error
        override suspend fun filterByCategory(category: String): MealSummariesResponseDto = throw error
    }

    private fun remoteThrowing(error: Throwable) = RetrofitRecipeRemoteDataSource(ThrowingApi(error))

    @Test
    fun `CancellationException passa direto, sem traducao, em todos os endpoints`() = runBlocking<Unit> {
        val cancellation = CancellationException("cancelado de proposito")
        val throwing = remoteThrowing(cancellation)

        val errors = listOf(
            runCatching { throwing.search("x") }.exceptionOrNull(),
            runCatching { throwing.lookup("1") }.exceptionOrNull(),
            runCatching { throwing.random() }.exceptionOrNull(),
            runCatching { throwing.categories() }.exceptionOrNull(),
            runCatching { throwing.byCategory("Beef") }.exceptionOrNull(),
        )

        for (error in errors) {
            assertThat(error).isSameInstanceAs(cancellation)
        }
    }

    @Test
    fun `TimeoutCancellationException tambem nao e traduzida`() = runBlocking<Unit> {
        val timeout = runCatching { withTimeout(1) { kotlinx.coroutines.awaitCancellation() } }.exceptionOrNull()!!
        assertThat(timeout).isInstanceOf(TimeoutCancellationException::class.java)

        val error = runCatching { remoteThrowing(timeout).search("x") }.exceptionOrNull()

        assertThat(error).isSameInstanceAs(timeout)
    }

    @Test
    fun `tabela de traducao - HttpException SERVER, IO NETWORK, serializacao e argumento INVALID_DATA`() = runBlocking<Unit> {
        val httpException = HttpException(Response.error<Any>(500, "".toResponseBody()))

        expectFailure(RecipeException.Kind.SERVER) { remoteThrowing(httpException).search("x") }
        expectFailure(RecipeException.Kind.NETWORK) { remoteThrowing(IOException("x")).search("x") }
        expectFailure(RecipeException.Kind.NETWORK) { remoteThrowing(UnknownHostException("sem dns")).search("x") }
        expectFailure(RecipeException.Kind.NETWORK) { remoteThrowing(SocketTimeoutException()).search("x") }
        expectFailure(RecipeException.Kind.INVALID_DATA) { remoteThrowing(SerializationException("x")).search("x") }
        expectFailure(RecipeException.Kind.INVALID_DATA) { remoteThrowing(IllegalArgumentException("x")).search("x") }
    }

    @Test
    fun `a causa original e preservada na RecipeException`() = runBlocking<Unit> {
        val cause = UnknownHostException("www.themealdb.com")

        val error = expectFailure(RecipeException.Kind.NETWORK) { remoteThrowing(cause).lookup("1") }

        assertThat(error.cause).isSameInstanceAs(cause)
    }
}
