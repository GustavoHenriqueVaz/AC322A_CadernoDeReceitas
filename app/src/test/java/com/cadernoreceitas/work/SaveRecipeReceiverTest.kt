package com.cadernoreceitas.work

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import com.cadernoreceitas.domain.model.RecipeException
import com.cadernoreceitas.testutil.FakeAppContainer
import com.cadernoreceitas.testutil.RepositoryTestEnv
import com.cadernoreceitas.testutil.TestCadernoApp
import com.cadernoreceitas.testutil.TestFixtures
import com.cadernoreceitas.testutil.WorkTestSupport
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * `SaveRecipeReceiver` (botao "Salvar no caderno" da notificacao) com um WorkManager de teste:
 * valida o id, enfileira trabalho UNICO e, de ponta a ponta, o worker salva a receita no caderno.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestCadernoApp::class)
class SaveRecipeReceiverTest {

    private lateinit var app: TestCadernoApp
    private lateinit var env: RepositoryTestEnv
    private val receiver = SaveRecipeReceiver()

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        env = RepositoryTestEnv(app)
        (app.container as FakeAppContainer).recipeRepository = env.repository
        env.remote.putLookup(TestFixtures.lookupResponse().meals!!.single()) // id 52772
        WorkTestSupport.initWorkManager(app)
    }

    @After
    fun tearDown() {
        env.close()
    }

    private fun saveIntent(id: String?, action: String? = NotificationHelper.ACTION_SAVE): Intent =
        Intent(app, SaveRecipeReceiver::class.java).apply {
            this.action = action
            if (id != null) putExtra(NotificationHelper.EXTRA_RECIPE_ID, id)
        }

    private fun workInfos(id: String) = WorkTestSupport.workInfos(app, "save_recipe_$id")

    private fun savedIds(): Set<String> = runBlocking { env.repository.observeSavedIds().first() }

    private fun allUniqueNamesEmpty(vararg ids: String) = ids.all { workInfos(it).isEmpty() }

    // ------------------------------------------------------------------ id valido

    @Test
    fun `id valido enfileira um trabalho unico e a receita acaba salva no caderno`() {
        receiver.onReceive(app, saveIntent("52772"))

        WorkTestSupport.awaitCondition("worker concluir") {
            workInfos("52772").singleOrNull()?.state == WorkInfo.State.SUCCEEDED
        }
        assertThat(workInfos("52772")).hasSize(1)
        assertThat(savedIds()).containsExactly("52772")
        assertThat(env.remote.lookupIds).containsExactly("52772")
    }

    @Test
    fun `varios toques seguidos geram um unico salvamento (nome unico com KEEP)`() {
        // rede fora: o trabalho fica ENQUEUED aguardando nova tentativa (ainda pendente)
        env.remote.failWith(RecipeException.Kind.NETWORK)
        receiver.onReceive(app, saveIntent("52772"))
        WorkTestSupport.awaitCondition("1a tentativa falhar e reagendar") {
            workInfos("52772").singleOrNull()?.let { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount >= 1 } == true
        }
        val firstId = workInfos("52772").single().id

        receiver.onReceive(app, saveIntent("52772"))
        receiver.onReceive(app, saveIntent("52772"))

        val after = workInfos("52772")
        assertThat(after).hasSize(1)
        assertThat(after.single().id).isEqualTo(firstId) // o pedido original foi mantido
    }

    @Test
    fun `ids diferentes geram trabalhos independentes`() {
        env.remote.failWith(RecipeException.Kind.NETWORK)

        receiver.onReceive(app, saveIntent("111"))
        receiver.onReceive(app, saveIntent("222"))

        assertThat(workInfos("111")).hasSize(1)
        assertThat(workInfos("222")).hasSize(1)
        assertThat(workInfos("111").single().id).isNotEqualTo(workInfos("222").single().id)
    }

    @Test
    fun `o toque cancela a notificacao do lembrete`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationHelper.showDailyRecipe(app, TestFixtures.recipe(id = "52772"))
        val manager = app.getSystemService(NotificationManager::class.java)
        assertThat(shadowOf(manager).allNotifications).hasSize(1)

        receiver.onReceive(app, saveIntent("52772"))

        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }

    // ------------------------------------------------------------------ entradas rejeitadas

    @Test
    fun `id invalido nao enfileira nada e nao cancela a notificacao`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationHelper.showDailyRecipe(app, TestFixtures.recipe(id = "52772"))
        val manager = app.getSystemService(NotificationManager::class.java)

        for (id in listOf("abc", "", "  ", "../x", "12 3", "1".repeat(13), "1;DROP TABLE x")) {
            receiver.onReceive(app, saveIntent(id))
        }

        assertThat(allUniqueNamesEmpty("abc", "", "  ", "../x", "12 3", "1".repeat(13), "1;DROP TABLE x")).isTrue()
        assertThat(env.remote.totalCalls).isEqualTo(0)
        assertThat(shadowOf(manager).allNotifications).hasSize(1) // retornou antes de cancelar
    }

    @Test
    fun `intent sem o id extra nao enfileira nada`() {
        receiver.onReceive(app, saveIntent(id = null))

        assertThat(env.remote.totalCalls).isEqualTo(0)
        assertThat(savedIds()).isEmpty()
    }

    @Test
    fun `acao errada ou ausente e ignorada mesmo com id valido`() {
        receiver.onReceive(app, saveIntent("52772", action = "com.outra.app.ACAO"))
        receiver.onReceive(app, saveIntent("52772", action = null))
        receiver.onReceive(app, saveIntent("52772", action = Intent.ACTION_VIEW))
        receiver.onReceive(app, saveIntent("52772", action = NotificationHelper.ACTION_SAVE.lowercase()))

        assertThat(workInfos("52772")).isEmpty()
        assertThat(env.remote.totalCalls).isEqualTo(0)
        assertThat(savedIds()).isEmpty()
    }

    @Test
    fun `extra com tipo errado (inteiro no lugar do texto) e tratado como id ausente`() {
        val intent = Intent(app, SaveRecipeReceiver::class.java).apply {
            action = NotificationHelper.ACTION_SAVE
            putExtra(NotificationHelper.EXTRA_RECIPE_ID, 52772)
        }

        receiver.onReceive(app, intent)

        assertThat(workInfos("52772")).isEmpty()
        assertThat(env.remote.totalCalls).isEqualTo(0)
    }
}
