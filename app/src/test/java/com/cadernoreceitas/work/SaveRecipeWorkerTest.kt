package com.cadernoreceitas.work

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.cadernoreceitas.domain.model.RecipeException
import com.cadernoreceitas.testutil.FakeAppContainer
import com.cadernoreceitas.testutil.RepositoryTestEnv
import com.cadernoreceitas.testutil.TestCadernoApp
import com.cadernoreceitas.testutil.TestFixtures
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.hours

/** `SaveRecipeWorker` de ponta a ponta (repositorio real em memoria + rede falsa). */
@RunWith(AndroidJUnit4::class)
@Config(application = TestCadernoApp::class)
class SaveRecipeWorkerTest {

    private lateinit var app: TestCadernoApp
    private lateinit var env: RepositoryTestEnv

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        env = RepositoryTestEnv(app)
        (app.container as FakeAppContainer).recipeRepository = env.repository
        env.remote.putLookup(TestFixtures.lookupResponse().meals!!.single()) // id 52772
    }

    @After
    fun tearDown() {
        env.close()
    }

    private fun run(id: String?, attempt: Int = 0): Result = runBlocking {
        val data = if (id == null) androidx.work.Data.EMPTY else workDataOf(SaveRecipeWorker.KEY_RECIPE_ID to id)
        TestListenableWorkerBuilder<SaveRecipeWorker>(app)
            .setInputData(data)
            .setRunAttemptCount(attempt)
            .build()
            .doWork()
    }

    private fun savedIds(): Set<String> = runBlocking { env.repository.observeSavedIds().first() }

    // ------------------------------------------------------------------ entrada invalida

    @Test
    fun `id invalido falha sem tocar na rede`() {
        val invalid = listOf("abc", "", " ", "12 3", "../x", "1".repeat(13), "52772/../1", "1;DROP")
        for (id in invalid) {
            assertThat(run(id)).isEqualTo(Result.failure())
        }

        assertThat(env.remote.totalCalls).isEqualTo(0)
        assertThat(savedIds()).isEmpty()
    }

    @Test
    fun `sem o dado de entrada o trabalho falha`() {
        assertThat(run(id = null)).isEqualTo(Result.failure())
        assertThat(env.remote.totalCalls).isEqualTo(0)
    }

    // ------------------------------------------------------------------ sucesso

    @Test
    fun `id valido busca a receita, salva no caderno e termina com sucesso`() {
        val result = run("52772")

        assertThat(result).isEqualTo(Result.success())
        assertThat(savedIds()).containsExactly("52772")
        val saved = runBlocking { env.repository.observeSavedRecipe("52772").first() }!!
        assertThat(saved.recipe.name).isEqualTo("Teriyaki Chicken Casserole")
        assertThat(saved.hasNote).isFalse()
        assertThat(env.remote.lookupIds).containsExactly("52772")
    }

    @Test
    fun `rodar duas vezes e idempotente - uma unica receita salva e sem nova ida a rede`() {
        assertThat(run("52772")).isEqualTo(Result.success())
        val savedAt = runBlocking { env.repository.observeSavedRecipe("52772").first() }!!.savedAt
        env.clock.advance(1.hours)

        assertThat(run("52772")).isEqualTo(Result.success())

        assertThat(savedIds()).containsExactly("52772")
        assertThat(runBlocking { env.repository.observeSavedRecipe("52772").first() }!!.savedAt).isEqualTo(savedAt)
        assertThat(env.remote.lookupCalls).isEqualTo(1) // a 2a leitura veio do cache valido
    }

    @Test
    fun `receita ja em cache e salva mesmo sem rede`() {
        run("52772") // grava o cache
        runBlocking { env.repository.removeSaved("52772") }
        env.remote.failWith(RecipeException.Kind.NETWORK)

        val result = run("52772")

        assertThat(result).isEqualTo(Result.success())
        assertThat(savedIds()).containsExactly("52772")
    }

    // ------------------------------------------------------------------ falhas

    @Test
    fun `falha de rede tenta de novo ate MAX_ATTEMPTS e depois desiste`() {
        env.remote.failWith(RecipeException.Kind.NETWORK)

        for (attempt in 0 until SaveRecipeWorker.MAX_ATTEMPTS) {
            assertThat(run("52772", attempt)).isEqualTo(Result.retry())
        }
        assertThat(run("52772", SaveRecipeWorker.MAX_ATTEMPTS)).isEqualTo(Result.failure())
        assertThat(savedIds()).isEmpty()
    }

    @Test
    fun `erro de servidor tambem tenta de novo ate MAX_ATTEMPTS`() {
        env.remote.failWith(RecipeException.Kind.SERVER)

        assertThat(run("52772", 0)).isEqualTo(Result.retry())
        assertThat(run("52772", SaveRecipeWorker.MAX_ATTEMPTS - 1)).isEqualTo(Result.retry())
        assertThat(run("52772", SaveRecipeWorker.MAX_ATTEMPTS)).isEqualTo(Result.failure())
    }

    @Test
    fun `receita inexistente (NOT_FOUND) falha sem retentativa`() {
        // 123456 nao esta em `lookups`: a API devolveria {"meals":null}
        assertThat(run("123456", 0)).isEqualTo(Result.failure())
        assertThat(savedIds()).isEmpty()
    }

    @Test
    fun `resposta invalida da API falha sem retentativa`() {
        env.remote.failWith(RecipeException.Kind.INVALID_DATA)

        assertThat(run("52772", 0)).isEqualTo(Result.failure())
    }

    @Test
    fun `depois que a rede volta a nova tentativa salva`() {
        env.remote.failWith(RecipeException.Kind.NETWORK)
        assertThat(run("52772", 0)).isEqualTo(Result.retry())
        env.remote.recover()

        assertThat(run("52772", 1)).isEqualTo(Result.success())
        assertThat(savedIds()).containsExactly("52772")
    }
}
