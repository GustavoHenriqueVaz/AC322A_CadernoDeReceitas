package com.cadernoreceitas.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.cadernoreceitas.data.local.entity.ListCacheEntity
import com.cadernoreceitas.data.mapper.fromCacheJson
import com.cadernoreceitas.data.mapper.toRecipe
import com.cadernoreceitas.domain.model.DataSource
import com.cadernoreceitas.domain.model.RecipeException
import com.cadernoreceitas.domain.model.RecipeNote
import com.cadernoreceitas.testutil.RepositoryTestEnv
import com.cadernoreceitas.testutil.TestFixtures
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Repositorio REAL (Room em memoria via Robolectric) com rede, preferencias, fotos e relogio falsos.
 * Cobre a politica de cache (TTL), o modo offline, as buscas locais e o caderno.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class DefaultRecipeRepositoryTest {

    private lateinit var env: RepositoryTestEnv

    private val remote get() = env.remote
    private val clock get() = env.clock
    private val repo get() = env.repository
    private val db get() = env.db

    @Before
    fun setUp() {
        env = RepositoryTestEnv(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        env.close()
    }

    // ------------------------------------------------------------------ helpers

    private fun chickenSoup() = TestFixtures.mealDto(id = "1", name = "Chicken Soup", category = "Chicken")
    private fun chickenPie() = TestFixtures.mealDto(id = "2", name = "Chicken Pie", category = "Chicken")
    private fun beefStew() = TestFixtures.mealDto(id = "3", name = "Beef Stew", category = "Beef")

    private suspend fun expectFailure(kind: RecipeException.Kind, block: suspend () -> Unit) {
        val error = runCatching { block() }.exceptionOrNull()
        assertThat(error).isInstanceOf(RecipeException::class.java)
        assertThat((error as RecipeException).kind).isEqualTo(kind)
    }

    /** Room pode emitir de novo sem mudanca visivel: espera o primeiro item que satisfaz o criterio. */
    private suspend fun <T> ReceiveTurbine<T>.awaitMatching(predicate: (T) -> Boolean): T {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    private val note = RecipeNote(rating = 5, comment = "Ficou muito bom, repetiria", prepMinutes = 40, photoPath = null)

    // ================================================================== search

    @Test
    fun `busca remota devolve REMOTE e grava o cache da lista e os detalhes completos`() = runTest {
        remote.searchResponse = listOf(chickenSoup(), chickenPie())

        val result = repo.search("chicken")

        assertThat(result.source).isEqualTo(DataSource.REMOTE)
        assertThat(result.isOffline).isFalse()
        assertThat(result.value.map { it.id }).containsExactly("1", "2").inOrder()
        assertThat(result.value[0].category).isEqualTo("Chicken")
        assertThat(remote.searchQueries).containsExactly("chicken")

        val cached = db.listCacheDao().get("search:chicken")!!
        assertThat(cached.fetchedAt).isEqualTo(clock.millis())
        assertThat(cached.payload.fromCacheJson()).isEqualTo(result.value)

        // a busca ja traz o detalhe completo: fica guardado para uso offline
        val entity = db.recipeDao().getById("1")!!
        assertThat(entity.fetchedAt).isEqualTo(clock.millis())
        assertThat(entity.toRecipe().ingredients).hasSize(2)
        assertThat(db.recipeDao().count()).isEqualTo(2)
    }

    @Test
    fun `segunda busca dentro do TTL nao chama a rede (CACHE_FRESH)`() = runTest {
        remote.searchResponse = listOf(chickenSoup(), chickenPie())
        val first = repo.search("chicken")

        clock.advance(5.hours + 59.minutes)
        val second = repo.search("chicken")

        assertThat(second.source).isEqualTo(DataSource.CACHE_FRESH)
        assertThat(second.value).isEqualTo(first.value)
        assertThat(remote.searchCalls).isEqualTo(1)
    }

    @Test
    fun `um milissegundo antes do TTL ainda e cache, e no TTL exato ja chama a rede`() = runTest {
        remote.searchResponse = listOf(chickenSoup())
        repo.search("chicken")

        clock.advance(6.hours - 1.milliseconds)
        assertThat(repo.search("chicken").source).isEqualTo(DataSource.CACHE_FRESH)
        assertThat(remote.searchCalls).isEqualTo(1)

        clock.advance(1.milliseconds) // agora faz exatamente 6 h
        assertThat(repo.search("chicken").source).isEqualTo(DataSource.REMOTE)
        assertThat(remote.searchCalls).isEqualTo(2)
    }

    @Test
    fun `apos o TTL a rede e chamada de novo e o cache e renovado`() = runTest {
        remote.enqueueSearch(listOf(chickenSoup()), listOf(chickenSoup(), chickenPie()))
        repo.search("chicken")

        clock.advance(7.hours)
        val second = repo.search("chicken")

        assertThat(second.source).isEqualTo(DataSource.REMOTE)
        assertThat(second.value.map { it.id }).containsExactly("1", "2")
        assertThat(remote.searchCalls).isEqualTo(2)
        // renovou a data: 1 h depois ainda e cache
        clock.advance(1.hours)
        assertThat(repo.search("chicken").source).isEqualTo(DataSource.CACHE_FRESH)
        assertThat(remote.searchCalls).isEqualTo(2)
    }

    @Test
    fun `forceRefresh ignora o cache valido`() = runTest {
        remote.searchResponse = listOf(chickenSoup())
        repo.search("chicken")

        val refreshed = repo.search("chicken", forceRefresh = true)

        assertThat(refreshed.source).isEqualTo(DataSource.REMOTE)
        assertThat(remote.searchCalls).isEqualTo(2)
    }

    @Test
    fun `forceRefresh com a rede fora devolve o cache como CACHE_STALE`() = runTest {
        remote.searchResponse = listOf(chickenSoup())
        val first = repo.search("chicken")
        remote.failWith(RecipeException.Kind.NETWORK)

        val refreshed = repo.search("chicken", forceRefresh = true)

        assertThat(refreshed.source).isEqualTo(DataSource.CACHE_STALE)
        assertThat(refreshed.isOffline).isTrue()
        assertThat(refreshed.value).isEqualTo(first.value)
    }

    @Test
    fun `rede falha com cache vencido - devolve CACHE_STALE (modo offline) para qualquer tipo de erro`() = runTest {
        remote.searchResponse = listOf(chickenSoup(), chickenPie())
        val first = repo.search("chicken")
        clock.advance(7.hours)

        for (kind in RecipeException.Kind.entries) {
            remote.failWith(kind)
            val offline = repo.search("chicken")

            assertThat(offline.source).isEqualTo(DataSource.CACHE_STALE)
            assertThat(offline.isOffline).isTrue()
            assertThat(offline.value).isEqualTo(first.value)
        }
    }

    @Test
    fun `rede falha sem nenhum cache - lanca RecipeException com o mesmo tipo`() = runTest {
        remote.failWith(RecipeException.Kind.NETWORK)
        expectFailure(RecipeException.Kind.NETWORK) { repo.search("chicken") }

        remote.failWith(RecipeException.Kind.SERVER)
        expectFailure(RecipeException.Kind.SERVER) { repo.search("chicken") }

        remote.failWith(RecipeException.Kind.INVALID_DATA)
        expectFailure(RecipeException.Kind.INVALID_DATA) { repo.search("") }
    }

    @Test
    fun `busca que falha nao grava nada no cache`() = runTest {
        remote.failWith(RecipeException.Kind.NETWORK)
        runCatching { repo.search("chicken") }

        assertThat(db.listCacheDao().get("search:chicken")).isNull()
        assertThat(db.recipeDao().count()).isEqualTo(0)
    }

    @Test
    fun `texto digitado e normalizado antes de ir a rede e a chave de cache ignora maiusculas e espacos`() = runTest {
        remote.searchResponse = listOf(chickenSoup())

        val first = repo.search("  Chicken   Soup ")
        val second = repo.search("chicken soup")

        assertThat(remote.searchQueries).containsExactly("Chicken Soup") // so uma ida a rede, ja normalizada
        assertThat(first.source).isEqualTo(DataSource.REMOTE)
        assertThat(second.source).isEqualTo(DataSource.CACHE_FRESH)
        assertThat(db.listCacheDao().get("search:chicken soup")).isNotNull()
    }

    @Test
    fun `consultas diferentes usam caches independentes`() = runTest {
        remote.searchByQuery["chicken"] = listOf(chickenSoup())
        remote.searchByQuery["beef"] = listOf(beefStew())

        val chicken = repo.search("chicken")
        val beef = repo.search("beef")

        assertThat(chicken.value.map { it.name }).containsExactly("Chicken Soup")
        assertThat(beef.value.map { it.name }).containsExactly("Beef Stew")
        assertThat(remote.searchCalls).isEqualTo(2)
    }

    @Test
    fun `busca sem resultados (meals null) e guardada e a repeticao nao chama a rede`() = runTest {
        remote.searchResponse = emptyList()

        val first = repo.search("zzzz")
        val second = repo.search("zzzz")

        assertThat(first.source).isEqualTo(DataSource.REMOTE)
        assertThat(first.value).isEmpty()
        assertThat(second.source).isEqualTo(DataSource.CACHE_FRESH)
        assertThat(second.value).isEmpty()
        assertThat(remote.searchCalls).isEqualTo(1)
    }

    @Test
    fun `receitas invalidas da API (sem nome ou id) sao ignoradas`() = runTest {
        remote.searchResponse = listOf(
            chickenSoup(),
            TestFixtures.mealDto(id = "9", name = "  "),
            TestFixtures.mealDto(id = "", name = "Sem id"),
        )

        val result = repo.search("x")

        assertThat(result.value.map { it.id }).containsExactly("1")
        assertThat(db.recipeDao().count()).isEqualTo(1)
    }

    @Test
    fun `fixtures reais - busca devolve receitas com pais no lugar da area e fonte em https`() = runTest {
        remote.searchResponse = TestFixtures.searchResponse().meals!!

        val result = repo.search("")

        assertThat(result.value.map { it.name }).containsExactly("Satee", "Kumpir", "Flan").inOrder()
        assertThat(result.value[0].area).isEqualTo("Aruba")
        assertThat(db.recipeDao().getById("52978")!!.sourceUrl).startsWith("https://")
    }

    @Test
    fun `cache de lista corrompido e ignorado e a rede e consultada`() = runTest {
        db.listCacheDao().put(ListCacheEntity("search:chicken", "{{ isto nao e json", clock.millis()))
        remote.searchResponse = listOf(chickenSoup())

        val result = repo.search("chicken")

        assertThat(result.source).isEqualTo(DataSource.REMOTE)
        assertThat(remote.searchCalls).isEqualTo(1)
        // e foi sobrescrito por um valido
        assertThat(db.listCacheDao().get("search:chicken")!!.payload.fromCacheJson()).isNotNull()
    }

    // ------------------------------------------------------------------ fallback local por nome

    @Test
    fun `sem rede e sem cache da consulta, busca por nome nas receitas locais (sem diferenciar maiusculas)`() = runTest {
        remote.searchResponse = listOf(
            chickenSoup(),
            beefStew(),
            TestFixtures.mealDto(id = "4", name = "chicken wings", category = "Chicken"),
        )
        repo.search("") // popula a tabela de receitas
        remote.failWith(RecipeException.Kind.NETWORK)

        val result = repo.search("CHICK")

        assertThat(result.source).isEqualTo(DataSource.CACHE_STALE)
        assertThat(result.isOffline).isTrue()
        // ordenado por nome (comparacao binaria: maiuscula vem antes)
        assertThat(result.value.map { it.name }).containsExactly("Chicken Soup", "chicken wings").inOrder()
        // o resultado do fallback NAO vira cache de lista
        assertThat(db.listCacheDao().get("search:chick")).isNull()
    }

    @Test
    fun `fallback local encontra no meio do nome`() = runTest {
        remote.searchResponse = listOf(chickenSoup(), beefStew())
        repo.search("")
        remote.failWith(RecipeException.Kind.NETWORK)

        assertThat(repo.search("soup").value.map { it.id }).containsExactly("1")
        assertThat(repo.search("ew").value.map { it.id }).containsExactly("3")
    }

    @Test
    fun `fallback local sem nenhuma correspondencia lanca o erro de rede`() = runTest {
        remote.searchResponse = listOf(chickenSoup())
        repo.search("")
        remote.failWith(RecipeException.Kind.NETWORK)

        expectFailure(RecipeException.Kind.NETWORK) { repo.search("lasagna") }
    }

    @Test
    fun `fallback local escapa porcentagem sublinhado e barra do LIKE`() = runTest {
        remote.searchResponse = listOf(
            TestFixtures.mealDto(id = "1", name = "50% Off Cake"),
            TestFixtures.mealDto(id = "2", name = "50X Cake"),
            TestFixtures.mealDto(id = "3", name = "A_B Soup"),
            TestFixtures.mealDto(id = "4", name = "AXB Soup"),
            TestFixtures.mealDto(id = "5", name = "100% Juice"),
            TestFixtures.mealDto(id = "6", name = "Back\\Slash Pie"),
            TestFixtures.mealDto(id = "7", name = "Plain Bread"),
        )
        repo.search("")
        remote.failWith(RecipeException.Kind.NETWORK)

        // "50%" nao pode funcionar como curinga ("50X Cake" ficaria de fora)
        assertThat(repo.search("50%").value.map { it.name }).containsExactly("50% Off Cake")
        // "a_b" nao pode casar "AXB"
        assertThat(repo.search("a_b").value.map { it.name }).containsExactly("A_B Soup")
        // "%" sozinho so casa nomes que TEM o caractere (e nao todas as receitas)
        assertThat(repo.search("%").value.map { it.name }).containsExactly("100% Juice", "50% Off Cake").inOrder()
        assertThat(repo.search("_").value.map { it.name }).containsExactly("A_B Soup")
        // barra invertida literal
        assertThat(repo.search("\\").value.map { it.name }).containsExactly("Back\\Slash Pie")
    }

    @Test
    fun `consulta vazia sem rede nem cache mostra as receitas vistas mais recentemente`() = runTest {
        remote.searchByQuery["a"] = listOf(
            TestFixtures.mealDto(id = "1", name = "Old A"),
            TestFixtures.mealDto(id = "2", name = "Old B"),
        )
        remote.searchByQuery["b"] = listOf(TestFixtures.mealDto(id = "3", name = "New Z"))
        repo.search("a")
        clock.advance(10.minutes)
        repo.search("b")
        remote.failWith(RecipeException.Kind.NETWORK)

        val result = repo.search("") // sem lista em cache para ""

        assertThat(result.source).isEqualTo(DataSource.CACHE_STALE)
        assertThat(result.value.map { it.name }).containsExactly("New Z", "Old A", "Old B").inOrder()
    }

    @Test
    fun `fallback local limita a 25 receitas recentes`() = runTest {
        remote.searchByQuery["lots"] = (1..30).map {
            TestFixtures.mealDto(id = it.toString(), name = "Recipe %02d".format(it))
        }
        repo.search("lots")
        remote.failWith(RecipeException.Kind.NETWORK)

        assertThat(repo.search("").value).hasSize(25)
    }

    @Test
    fun `lista de cache vencida tem prioridade sobre o fallback local`() = runTest {
        remote.searchResponse = listOf(chickenSoup())
        repo.search("chicken")
        remote.searchResponse = listOf(chickenSoup(), beefStew())
        repo.search("") // receitas locais agora: Chicken Soup e Beef Stew
        clock.advance(7.hours)
        remote.failWith(RecipeException.Kind.NETWORK)

        val offline = repo.search("chicken")

        // a lista em cache (so a sopa) e a resposta, e nao a busca por LIKE
        assertThat(offline.source).isEqualTo(DataSource.CACHE_STALE)
        assertThat(offline.value.map { it.id }).containsExactly("1")
    }

    // ================================================================== recipesByCategory

    @Test
    fun `categoria - remoto devolve resumos com a categoria pedida e guarda em cache`() = runTest {
        remote.byCategoryResults["Seafood"] = TestFixtures.filterResponse().meals!!

        val result = repo.recipesByCategory(" Seafood ")

        assertThat(result.source).isEqualTo(DataSource.REMOTE)
        assertThat(remote.byCategoryNames).containsExactly("Seafood") // aparado
        assertThat(result.value).hasSize(3)
        assertThat(result.value.all { it.category == "Seafood" }).isTrue()
        assertThat(result.value[0].area).isEqualTo("Brazil")
        assertThat(db.listCacheDao().get("category:seafood")).isNotNull()
        // filter.php nao traz detalhes, entao nada entra na tabela de receitas
        assertThat(db.recipeDao().count()).isEqualTo(0)
    }

    @Test
    fun `categoria - dentro do TTL usa cache, depois do TTL chama a rede`() = runTest {
        remote.byCategoryResults["Seafood"] = TestFixtures.filterResponse().meals!!
        repo.recipesByCategory("Seafood")

        clock.advance(5.hours)
        assertThat(repo.recipesByCategory("seafood").source).isEqualTo(DataSource.CACHE_FRESH)
        assertThat(remote.byCategoryCalls).isEqualTo(1)

        clock.advance(1.hours)
        assertThat(repo.recipesByCategory("Seafood").source).isEqualTo(DataSource.REMOTE)
        assertThat(remote.byCategoryCalls).isEqualTo(2)
    }

    @Test
    fun `categoria - rede falha com cache vencido devolve CACHE_STALE`() = runTest {
        remote.byCategoryResults["Seafood"] = TestFixtures.filterResponse().meals!!
        val first = repo.recipesByCategory("Seafood")
        clock.advance(1.days)
        remote.failWith(RecipeException.Kind.SERVER)

        val offline = repo.recipesByCategory("Seafood")

        assertThat(offline.source).isEqualTo(DataSource.CACHE_STALE)
        assertThat(offline.value).isEqualTo(first.value)
    }

    @Test
    fun `categoria - sem rede e sem cache usa as receitas locais da categoria`() = runTest {
        remote.searchResponse = listOf(chickenSoup(), chickenPie(), beefStew())
        repo.search("")
        remote.failWith(RecipeException.Kind.NETWORK)

        val result = repo.recipesByCategory("Chicken")

        assertThat(result.source).isEqualTo(DataSource.CACHE_STALE)
        assertThat(result.value.map { it.name }).containsExactly("Chicken Pie", "Chicken Soup").inOrder()
    }

    @Test
    fun `categoria - sem rede, sem cache e sem receitas locais lanca erro`() = runTest {
        remote.failWith(RecipeException.Kind.NETWORK)

        expectFailure(RecipeException.Kind.NETWORK) { repo.recipesByCategory("Dessert") }
    }

    @Test
    fun `categoria vazia no servidor e uma lista vazia valida (REMOTE)`() = runTest {
        remote.byCategoryResults["Goat"] = emptyList()

        val result = repo.recipesByCategory("Goat")

        assertThat(result.source).isEqualTo(DataSource.REMOTE)
        assertThat(result.value).isEmpty()
    }

    @Test
    fun `categoria - forceRefresh ignora o cache`() = runTest {
        remote.byCategoryResults["Seafood"] = TestFixtures.filterResponse().meals!!
        repo.recipesByCategory("Seafood")

        assertThat(repo.recipesByCategory("Seafood", forceRefresh = true).source).isEqualTo(DataSource.REMOTE)
        assertThat(remote.byCategoryCalls).isEqualTo(2)
    }

    // ================================================================== recipe(id)

    @Test
    fun `detalhe - primeira leitura vai a rede e a segunda dentro do TTL usa o cache`() = runTest {
        remote.putLookup(TestFixtures.lookupResponse().meals!!.single())

        val first = repo.recipe("52772")
        val second = repo.recipe("52772")

        assertThat(first.source).isEqualTo(DataSource.REMOTE)
        assertThat(first.value.name).isEqualTo("Teriyaki Chicken Casserole")
        assertThat(first.value.ingredients).hasSize(9)
        assertThat(second.source).isEqualTo(DataSource.CACHE_FRESH)
        assertThat(second.value).isEqualTo(first.value)
        assertThat(remote.lookupCalls).isEqualTo(1)
    }

    @Test
    fun `detalhe - vence exatamente em 24 horas`() = runTest {
        remote.putLookup(chickenSoup())
        repo.recipe("1")

        clock.advance(24.hours - 1.milliseconds)
        assertThat(repo.recipe("1").source).isEqualTo(DataSource.CACHE_FRESH)

        clock.advance(1.milliseconds)
        assertThat(repo.recipe("1").source).isEqualTo(DataSource.REMOTE)
        assertThat(remote.lookupCalls).isEqualTo(2)
    }

    @Test
    fun `detalhe - receita vinda da busca ja esta em cache e dispensa o lookup`() = runTest {
        remote.searchResponse = listOf(chickenSoup())
        repo.search("chicken")

        val detail = repo.recipe("1")

        assertThat(detail.source).isEqualTo(DataSource.CACHE_FRESH)
        assertThat(remote.lookupCalls).isEqualTo(0)
    }

    @Test
    fun `detalhe - vencido com a rede fora devolve o cache como CACHE_STALE`() = runTest {
        remote.putLookup(chickenSoup())
        val first = repo.recipe("1")
        clock.advance(2.days)
        remote.failWith(RecipeException.Kind.NETWORK)

        val offline = repo.recipe("1")

        assertThat(offline.source).isEqualTo(DataSource.CACHE_STALE)
        assertThat(offline.isOffline).isTrue()
        assertThat(offline.value).isEqualTo(first.value)
    }

    @Test
    fun `detalhe - sem cache e a API nao conhece o id lanca NOT_FOUND`() = runTest {
        // lookups vazio: a fonte devolve null (resposta {"meals":null})
        expectFailure(RecipeException.Kind.NOT_FOUND) { repo.recipe("99999999") }
    }

    @Test
    fun `detalhe - DTO sem nome conta como NOT_FOUND`() = runTest {
        remote.lookups["5"] = TestFixtures.mealDto(id = "5", name = "")

        expectFailure(RecipeException.Kind.NOT_FOUND) { repo.recipe("5") }
    }

    @Test
    fun `detalhe - sem cache e com erro de rede ou servidor propaga o tipo`() = runTest {
        remote.failWith(RecipeException.Kind.NETWORK)
        expectFailure(RecipeException.Kind.NETWORK) { repo.recipe("1") }

        remote.failWith(RecipeException.Kind.SERVER)
        expectFailure(RecipeException.Kind.SERVER) { repo.recipe("1") }
    }

    @Test
    fun `detalhe - forceRefresh atualiza a receita e o banco`() = runTest {
        remote.putLookup(TestFixtures.mealDto(id = "1", name = "Nome antigo"))
        repo.recipe("1")
        remote.putLookup(TestFixtures.mealDto(id = "1", name = "Nome novo"))

        val refreshed = repo.recipe("1", forceRefresh = true)

        assertThat(refreshed.source).isEqualTo(DataSource.REMOTE)
        assertThat(refreshed.value.name).isEqualTo("Nome novo")
        assertThat(db.recipeDao().getById("1")!!.name).isEqualTo("Nome novo")
    }

    @Test
    fun `detalhe - renovar reinicia a contagem do TTL`() = runTest {
        remote.putLookup(chickenSoup())
        repo.recipe("1")
        clock.advance(25.hours)
        repo.recipe("1") // renova (REMOTE)

        clock.advance(23.hours)

        assertThat(repo.recipe("1").source).isEqualTo(DataSource.CACHE_FRESH)
        assertThat(remote.lookupCalls).isEqualTo(2)
    }

    @Test
    fun `detalhe - cache vencido e API sem a receita (null) mantem o cache como CACHE_STALE`() = runTest {
        remote.putLookup(chickenSoup())
        repo.recipe("1")
        clock.advance(2.days)
        remote.lookups.clear() // agora a API devolve {"meals":null} para esse id

        val result = repo.recipe("1")

        assertThat(result.source).isEqualTo(DataSource.CACHE_STALE)
        assertThat(result.value.name).isEqualTo("Chicken Soup")
    }

    // ================================================================== recipeOfTheDay

    @Test
    fun `receita do dia - primeira chamada busca na rede e registra o dia nas preferencias`() = runTest {
        remote.randomResponse = TestFixtures.randomResponse().meals!!.single()

        val result = repo.recipeOfTheDay()

        assertThat(result.source).isEqualTo(DataSource.REMOTE)
        assertThat(result.value.id).isEqualTo("53318")
        assertThat(result.value.name).isEqualTo("Torta de fiambre")
        assertThat(remote.randomCalls).isEqualTo(1)
        assertThat(env.preferences.state.value.dailyRecipeId).isEqualTo("53318")
        assertThat(env.preferences.state.value.dailyRecipeEpochDay)
            .isEqualTo(LocalDate.of(2026, 3, 10).toEpochDay())
        assertThat(db.recipeDao().getById("53318")).isNotNull()
    }

    @Test
    fun `receita do dia - no mesmo dia nao chama a rede (mesmo na virada para o fim da noite)`() = runTest {
        remote.randomResponse = TestFixtures.randomResponse().meals!!.single()
        val first = repo.recipeOfTheDay()

        clock.advance(11.hours + 59.minutes) // 23:59 do mesmo dia
        val second = repo.recipeOfTheDay()

        assertThat(second.source).isEqualTo(DataSource.CACHE_FRESH)
        assertThat(second.value).isEqualTo(first.value)
        assertThat(remote.randomCalls).isEqualTo(1)
    }

    @Test
    fun `receita do dia - em um novo dia chama a rede e troca a sugestao`() = runTest {
        remote.enqueueRandom(
            TestFixtures.mealDto(id = "100", name = "Receita de ontem"),
            TestFixtures.mealDto(id = "200", name = "Receita de hoje"),
        )
        repo.recipeOfTheDay()

        clock.advance(12.hours) // 00:00 do dia seguinte
        val next = repo.recipeOfTheDay()

        assertThat(next.source).isEqualTo(DataSource.REMOTE)
        assertThat(next.value.id).isEqualTo("200")
        assertThat(remote.randomCalls).isEqualTo(2)
        assertThat(env.preferences.state.value.dailyRecipeId).isEqualTo("200")
        assertThat(env.preferences.state.value.dailyRecipeEpochDay)
            .isEqualTo(LocalDate.of(2026, 3, 11).toEpochDay())
    }

    @Test
    fun `receita do dia - offline em dia novo reaproveita a ultima sugestao`() = runTest {
        remote.randomResponse = TestFixtures.mealDto(id = "100", name = "Receita de ontem")
        repo.recipeOfTheDay()
        clock.advance(1.days)
        remote.failWith(RecipeException.Kind.NETWORK)

        val offline = repo.recipeOfTheDay()

        assertThat(offline.source).isEqualTo(DataSource.CACHE_STALE)
        assertThat(offline.isOffline).isTrue()
        assertThat(offline.value.id).isEqualTo("100")
        // nao registrou o novo dia: tentara de novo quando voltar a rede
        assertThat(env.preferences.state.value.dailyRecipeEpochDay)
            .isEqualTo(LocalDate.of(2026, 3, 10).toEpochDay())
    }

    @Test
    fun `receita do dia - offline e sem sugestao anterior lanca o erro`() = runTest {
        remote.failWith(RecipeException.Kind.NETWORK)

        expectFailure(RecipeException.Kind.NETWORK) { repo.recipeOfTheDay() }
        assertThat(env.preferences.state.value.dailyRecipeId).isNull()
    }

    @Test
    fun `receita do dia - resposta vazia da API e INVALID_DATA (ou reaproveita a anterior)`() = runTest {
        remote.randomResponse = null
        expectFailure(RecipeException.Kind.INVALID_DATA) { repo.recipeOfTheDay() }

        remote.randomResponse = TestFixtures.mealDto(id = "100", name = "Anterior")
        repo.recipeOfTheDay()
        clock.advance(1.days)
        remote.randomResponse = null

        val result = repo.recipeOfTheDay()
        assertThat(result.source).isEqualTo(DataSource.CACHE_STALE)
        assertThat(result.value.id).isEqualTo("100")
    }

    @Test
    fun `receita do dia - se a receita do dia saiu do cache a rede e chamada de novo`() = runTest {
        remote.randomResponse = TestFixtures.mealDto(id = "100", name = "Do dia")
        repo.recipeOfTheDay()
        // a limpeza do cache apagou a linha (receita nao salva) mas as preferencias ainda apontam para ela
        db.recipeDao().purgeOlderThan(Long.MAX_VALUE)
        assertThat(db.recipeDao().getById("100")).isNull()
        assertThat(env.preferences.state.value.dailyRecipeId).isEqualTo("100")

        val result = repo.recipeOfTheDay()

        assertThat(result.source).isEqualTo(DataSource.REMOTE)
        assertThat(remote.randomCalls).isEqualTo(2)
    }

    // ================================================================== categories

    @Test
    fun `categorias - remoto grava na ordem da API e a segunda leitura usa o cache`() = runTest {
        remote.categoriesResponse = TestFixtures.categoriesResponse().categories!!

        val first = repo.categories()
        val second = repo.categories()

        assertThat(first.source).isEqualTo(DataSource.REMOTE)
        assertThat(first.value.map { it.name }).containsExactly("Beef", "Chicken", "Dessert").inOrder()
        assertThat(second.source).isEqualTo(DataSource.CACHE_FRESH)
        assertThat(second.value).isEqualTo(first.value)
        assertThat(remote.categoriesCalls).isEqualTo(1)
        assertThat(db.categoryDao().getAll().map { it.position }).containsExactly(0, 1, 2).inOrder()
    }

    @Test
    fun `categorias - valem 7 dias`() = runTest {
        remote.categoriesResponse = TestFixtures.categoriesResponse().categories!!
        repo.categories()

        clock.advance(7.days - 1.milliseconds)
        assertThat(repo.categories().source).isEqualTo(DataSource.CACHE_FRESH)

        clock.advance(1.milliseconds)
        assertThat(repo.categories().source).isEqualTo(DataSource.REMOTE)
        assertThat(remote.categoriesCalls).isEqualTo(2)
    }

    @Test
    fun `categorias - forceRefresh e atualizacao substituem a lista antiga`() = runTest {
        remote.categoriesResponse = listOf(
            TestFixtures.categoryDto(name = "A"),
            TestFixtures.categoryDto(name = "B"),
        )
        repo.categories()
        remote.categoriesResponse = listOf(TestFixtures.categoryDto(name = "C"))

        val refreshed = repo.categories(forceRefresh = true)

        assertThat(refreshed.source).isEqualTo(DataSource.REMOTE)
        assertThat(refreshed.value.map { it.name }).containsExactly("C")
        assertThat(db.categoryDao().getAll().map { it.name }).containsExactly("C")
    }

    @Test
    fun `categorias - rede falha com cache devolve CACHE_STALE, sem cache lanca`() = runTest {
        remote.failWith(RecipeException.Kind.NETWORK)
        expectFailure(RecipeException.Kind.NETWORK) { repo.categories() }

        remote.recover()
        remote.categoriesResponse = TestFixtures.categoriesResponse().categories!!
        val first = repo.categories()
        clock.advance(8.days)
        remote.failWith(RecipeException.Kind.SERVER)

        val offline = repo.categories()
        assertThat(offline.source).isEqualTo(DataSource.CACHE_STALE)
        assertThat(offline.value).isEqualTo(first.value)
    }

    @Test
    fun `categorias - resposta vazia da API e INVALID_DATA e nao apaga o cache`() = runTest {
        remote.categoriesResponse = emptyList()
        expectFailure(RecipeException.Kind.INVALID_DATA) { repo.categories() }

        remote.categoriesResponse = TestFixtures.categoriesResponse().categories!!
        repo.categories()
        clock.advance(8.days)
        remote.categoriesResponse = emptyList()

        val result = repo.categories()
        assertThat(result.source).isEqualTo(DataSource.CACHE_STALE)
        assertThat(result.value).hasSize(3)
        assertThat(db.categoryDao().getAll()).hasSize(3)
    }

    @Test
    fun `categorias - itens sem nome sao descartados`() = runTest {
        remote.categoriesResponse = listOf(
            TestFixtures.categoryDto(name = "Beef"),
            TestFixtures.categoryDto(name = " "),
            TestFixtures.categoryDto(name = null),
            TestFixtures.categoryDto(name = "Pork"),
        )

        val result = repo.categories()

        assertThat(result.value.map { it.name }).containsExactly("Beef", "Pork").inOrder()
    }

    // ================================================================== caderno (escrita)

    @Test
    fun `saveRecipe salva a receita, sem anotacao, e guarda os detalhes para uso offline`() = runTest {
        val recipe = TestFixtures.recipe(id = "7", name = "Bolo")

        repo.saveRecipe(recipe)

        val saved = repo.observeSavedRecipe("7").first()!!
        assertThat(saved.recipe).isEqualTo(recipe)
        assertThat(saved.rating).isNull()
        assertThat(saved.comment).isNull()
        assertThat(saved.hasNote).isFalse()
        assertThat(saved.savedAt).isEqualTo(clock.millis())
        assertThat(saved.updatedAt).isEqualTo(clock.millis())
        assertThat(repo.observeSavedIds().first()).containsExactly("7")
    }

    @Test
    fun `saveRecipe e idempotente - salvar de novo nao duplica nem muda as datas`() = runTest {
        val recipe = TestFixtures.recipe(id = "7")
        repo.saveRecipe(recipe)
        val savedAt = clock.millis()

        clock.advance(1.hours)
        repo.saveRecipe(recipe)
        repo.saveRecipe(recipe)

        val all = repo.observeSaved().first()
        assertThat(all).hasSize(1)
        assertThat(all.single().savedAt).isEqualTo(savedAt)
        assertThat(all.single().updatedAt).isEqualTo(savedAt)
    }

    @Test
    fun `saveRecipe depois da anotacao nao apaga a anotacao`() = runTest {
        val recipe = TestFixtures.recipe(id = "7")
        repo.saveNote(recipe, note)

        repo.saveRecipe(recipe)

        val saved = repo.observeSavedRecipe("7").first()!!
        assertThat(saved.rating).isEqualTo(5)
        assertThat(saved.comment).isEqualTo("Ficou muito bom, repetiria")
    }

    @Test
    fun `saveNote cria o registro quando a receita ainda nao estava salva`() = runTest {
        val recipe = TestFixtures.recipe(id = "7")

        repo.saveNote(recipe, note.copy(photoPath = "/photos/7.jpg"))

        val saved = repo.observeSavedRecipe("7").first()!!
        assertThat(saved.rating).isEqualTo(5)
        assertThat(saved.comment).isEqualTo("Ficou muito bom, repetiria")
        assertThat(saved.prepMinutes).isEqualTo(40)
        assertThat(saved.photoPath).isEqualTo("/photos/7.jpg")
        assertThat(saved.savedAt).isEqualTo(clock.millis())
        assertThat(saved.updatedAt).isEqualTo(clock.millis())
        assertThat(saved.hasNote).isTrue()
        assertThat(saved.recipe).isEqualTo(recipe)
    }

    @Test
    fun `saveNote atualiza uma anotacao existente mantendo savedAt e mudando updatedAt`() = runTest {
        val recipe = TestFixtures.recipe(id = "7")
        repo.saveRecipe(recipe)
        val savedAt = clock.millis()
        clock.advance(2.hours)

        repo.saveNote(recipe, note.copy(photoPath = "/photos/a.jpg"))
        clock.advance(1.hours)
        repo.saveNote(recipe, RecipeNote(rating = 2, comment = "Segunda tentativa, nem tanto", prepMinutes = null, photoPath = null))

        val saved = repo.observeSavedRecipe("7").first()!!
        assertThat(repo.observeSaved().first()).hasSize(1)
        assertThat(saved.rating).isEqualTo(2)
        assertThat(saved.comment).isEqualTo("Segunda tentativa, nem tanto")
        assertThat(saved.prepMinutes).isNull() // limpar o campo apaga o valor anterior
        assertThat(saved.photoPath).isNull() // idem para a foto
        assertThat(saved.savedAt).isEqualTo(savedAt)
        assertThat(saved.updatedAt).isEqualTo(savedAt + 3.hours.inWholeMilliseconds)
    }

    @Test
    fun `saveRecipeById busca a receita (cache ou rede) e salva`() = runTest {
        remote.putLookup(TestFixtures.lookupResponse().meals!!.single())

        repo.saveRecipeById("52772")

        assertThat(repo.observeSavedIds().first()).containsExactly("52772")
        assertThat(remote.lookupCalls).isEqualTo(1)
        // de novo: ja salva e em cache valido, sem rede e sem duplicar
        repo.saveRecipeById("52772")
        assertThat(remote.lookupCalls).isEqualTo(1)
        assertThat(repo.observeSaved().first()).hasSize(1)
    }

    @Test
    fun `saveRecipeById propaga NOT_FOUND e erros de rede sem salvar nada`() = runTest {
        expectFailure(RecipeException.Kind.NOT_FOUND) { repo.saveRecipeById("123") }

        remote.failWith(RecipeException.Kind.NETWORK)
        expectFailure(RecipeException.Kind.NETWORK) { repo.saveRecipeById("123") }

        assertThat(repo.observeSaved().first()).isEmpty()
    }

    @Test
    fun `removeSaved devolve o item removido com a anotacao e mantem o cache da receita`() = runTest {
        val recipe = TestFixtures.recipe(id = "7")
        repo.saveNote(recipe, note)

        val removed = repo.removeSaved("7")

        assertThat(removed).isNotNull()
        assertThat(removed!!.recipe).isEqualTo(recipe)
        assertThat(removed.rating).isEqualTo(5)
        assertThat(removed.comment).isEqualTo("Ficou muito bom, repetiria")
        assertThat(repo.observeSaved().first()).isEmpty()
        assertThat(repo.observeSavedRecipe("7").first()).isNull()
        assertThat(db.recipeDao().getById("7")).isNotNull() // o cache de receitas nao e apagado
    }

    @Test
    fun `removeSaved de uma receita que nao esta salva devolve null`() = runTest {
        assertThat(repo.removeSaved("404")).isNull()
    }

    @Test
    fun `restoreSaved desfaz a remocao preservando datas e anotacao`() = runTest {
        val recipe = TestFixtures.recipe(id = "7")
        repo.saveRecipe(recipe)
        val savedAt = clock.millis()
        clock.advance(3.hours)
        repo.saveNote(recipe, note.copy(photoPath = "/photos/a.jpg"))
        val before = repo.observeSavedRecipe("7").first()!!
        val removed = repo.removeSaved("7")!!
        clock.advance(1.hours) // o undo acontece depois

        repo.restoreSaved(removed)

        val after = repo.observeSavedRecipe("7").first()!!
        assertThat(after).isEqualTo(before)
        assertThat(after.savedAt).isEqualTo(savedAt)
        assertThat(after.updatedAt).isEqualTo(before.updatedAt)
        assertThat(after.comment).isEqualTo("Ficou muito bom, repetiria")
        assertThat(after.photoPath).isEqualTo("/photos/a.jpg")
    }

    @Test
    fun `restoreSaved recria a receita mesmo que o cache a tenha perdido`() = runTest {
        val recipe = TestFixtures.recipe(id = "7")
        repo.saveRecipe(recipe)
        val removed = repo.removeSaved("7")!!
        db.recipeDao().purgeOlderThan(Long.MAX_VALUE) // limpeza apagou a receita nao salva
        assertThat(db.recipeDao().getById("7")).isNull()

        repo.restoreSaved(removed)

        assertThat(repo.observeSavedRecipe("7").first()!!.recipe).isEqualTo(recipe)
    }

    // ================================================================== observacao reativa (Turbine)

    @Test
    fun `observeSaved emite de forma reativa a cada mudanca no caderno`() = runTest {
        val recipe = TestFixtures.recipe(id = "7")

        repo.observeSaved().test {
            assertThat(awaitItem()).isEmpty()

            repo.saveRecipe(recipe)
            val afterSave = awaitMatching { it.isNotEmpty() }
            assertThat(afterSave.single().recipe).isEqualTo(recipe)
            assertThat(afterSave.single().hasNote).isFalse()

            repo.saveNote(recipe, note)
            val afterNote = awaitMatching { it.singleOrNull()?.hasNote == true }
            assertThat(afterNote.single().rating).isEqualTo(5)

            repo.removeSaved("7")
            assertThat(awaitMatching { it.isEmpty() }).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeSaved ordena do mais recente para o mais antigo`() = runTest {
        repo.saveRecipe(TestFixtures.recipe(id = "1", name = "Primeira"))
        clock.advance(1.minutes)
        repo.saveRecipe(TestFixtures.recipe(id = "2", name = "Segunda"))
        clock.advance(1.minutes)

        repo.observeSaved().test {
            assertThat(awaitItem().map { it.recipe.name }).containsExactly("Segunda", "Primeira").inOrder()

            repo.saveRecipe(TestFixtures.recipe(id = "3", name = "Terceira"))
            val updated = awaitMatching { it.size == 3 }
            assertThat(updated.map { it.recipe.name }).containsExactly("Terceira", "Segunda", "Primeira").inOrder()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeSavedIds emite o conjunto de ids salvos`() = runTest {
        repo.observeSavedIds().test {
            assertThat(awaitItem()).isEmpty()

            repo.saveRecipe(TestFixtures.recipe(id = "1"))
            assertThat(awaitMatching { it.isNotEmpty() }).containsExactly("1")

            repo.saveRecipe(TestFixtures.recipe(id = "2"))
            assertThat(awaitMatching { it.size == 2 }).containsExactly("1", "2")

            repo.removeSaved("1")
            assertThat(awaitMatching { it.size == 1 }).containsExactly("2")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeSavedRecipe acompanha uma receita - ausente, salva, anotada e removida`() = runTest {
        val recipe = TestFixtures.recipe(id = "7")

        repo.observeSavedRecipe("7").test {
            assertThat(awaitItem()).isNull()

            repo.saveRecipe(recipe)
            assertThat(awaitMatching { it != null }!!.hasNote).isFalse()

            repo.saveNote(recipe, note)
            assertThat(awaitMatching { it?.hasNote == true }!!.comment).isEqualTo("Ficou muito bom, repetiria")

            repo.removeSaved("7")
            assertThat(awaitMatching { it == null }).isNull()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeSavedRecipe de outra receita nao reage ao salvamento de uma receita diferente`() = runTest {
        repo.observeSavedRecipe("7").test {
            assertThat(awaitItem()).isNull()

            repo.saveRecipe(TestFixtures.recipe(id = "8"))
            repo.saveRecipe(TestFixtures.recipe(id = "7"))

            // so interessa que, no fim, a receita 7 aparece e a 8 nunca foi confundida com ela
            val seen = awaitMatching { it != null }
            assertThat(seen!!.recipe.id).isEqualTo("7")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ================================================================== purgeExpiredCache

    @Test
    fun `purge nao apaga receita salva, apaga as antigas nao salvas e preserva as recentes`() = runTest {
        // t0: uma receita salva e outra apenas vista (nao salva)
        remote.searchByQuery["old"] = listOf(TestFixtures.mealDto(id = "2", name = "Antiga nao salva"))
        repo.saveRecipe(TestFixtures.recipe(id = "1", name = "Antiga salva"))
        repo.search("old")
        // passam 31 dias; so entao entra uma receita nova
        clock.advance(31.days)
        remote.searchByQuery["new"] = listOf(TestFixtures.mealDto(id = "3", name = "Recente nao salva"))
        repo.search("new")

        repo.purgeExpiredCache()

        assertThat(db.recipeDao().getById("1")).isNotNull() // salva: nunca some, por mais antiga que seja
        assertThat(db.recipeDao().getById("2")).isNull() // antiga e nao salva: removida
        assertThat(db.recipeDao().getById("3")).isNotNull() // recente: fica
        assertThat(repo.observeSavedIds().first()).containsExactly("1")
        // as listas antigas tambem saem; a recente fica
        assertThat(db.listCacheDao().get("search:old")).isNull()
        assertThat(db.listCacheDao().get("search:new")).isNotNull()
    }

    @Test
    fun `purge respeita a fronteira de 30 dias`() = runTest {
        remote.searchByQuery["x"] = listOf(TestFixtures.mealDto(id = "2", name = "Receita X"))
        repo.search("x")

        clock.advance(30.days) // fetchedAt == corte: ainda NAO e mais antigo que o corte
        repo.purgeExpiredCache()
        assertThat(db.recipeDao().getById("2")).isNotNull()

        clock.advance(1.milliseconds) // agora ultrapassou
        repo.purgeExpiredCache()
        assertThat(db.recipeDao().getById("2")).isNull()
    }

    @Test
    fun `purge chama deleteOrphans com as fotos referenciadas no caderno`() = runTest {
        repo.saveNote(TestFixtures.recipe(id = "1"), note.copy(photoPath = "/files/photos/1.jpg"))
        repo.saveNote(TestFixtures.recipe(id = "2"), note.copy(photoPath = "/files/photos/2.jpg"))
        repo.saveNote(TestFixtures.recipe(id = "3"), note.copy(photoPath = null)) // sem foto
        repo.saveRecipe(TestFixtures.recipe(id = "4")) // sem anotacao

        repo.purgeExpiredCache()

        assertThat(env.photos.orphanCalls).hasSize(1)
        assertThat(env.photos.orphanCalls.single()).containsExactly("/files/photos/1.jpg", "/files/photos/2.jpg")
    }

    @Test
    fun `purge sem fotos ainda chama deleteOrphans com conjunto vazio (limpa fotos soltas)`() = runTest {
        repo.purgeExpiredCache()

        assertThat(env.photos.orphanCalls).containsExactly(emptySet<String>())
    }

    @Test
    fun `foto removida do caderno deixa de ser referenciada`() = runTest {
        repo.saveNote(TestFixtures.recipe(id = "1"), note.copy(photoPath = "/files/photos/1.jpg"))
        repo.removeSaved("1")

        repo.purgeExpiredCache()

        assertThat(env.photos.orphanCalls.single()).isEmpty()
    }
}
