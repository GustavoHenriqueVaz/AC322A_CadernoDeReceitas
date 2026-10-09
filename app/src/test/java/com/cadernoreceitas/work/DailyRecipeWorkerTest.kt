package com.cadernoreceitas.work

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.cadernoreceitas.domain.model.DataSource
import com.cadernoreceitas.domain.model.RecipeException
import com.cadernoreceitas.testutil.FakeAppContainer
import com.cadernoreceitas.testutil.NoOpRecipeRepository
import com.cadernoreceitas.testutil.RepositoryTestEnv
import com.cadernoreceitas.testutil.TestCadernoApp
import com.cadernoreceitas.testutil.TestFixtures
import com.cadernoreceitas.util.RecipeIds
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.days

/**
 * `DailyRecipeWorker` de ponta a ponta: Application de teste (`TestCadernoApp`) com um repositorio
 * REAL (Room em memoria) sobre uma rede falsa, e a notificacao conferida no `NotificationManager`
 * do Robolectric.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestCadernoApp::class)
class DailyRecipeWorkerTest {

    private lateinit var app: TestCadernoApp
    private lateinit var env: RepositoryTestEnv
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        env = RepositoryTestEnv(app)
        (app.container as FakeAppContainer).recipeRepository = env.repository
        notificationManager = app.getSystemService(NotificationManager::class.java)
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        env.remote.randomResponse = TestFixtures.randomResponse().meals!!.single() // id 53318
    }

    @After
    fun tearDown() {
        env.close()
    }

    private fun worker(attempt: Int = 0): DailyRecipeWorker =
        TestListenableWorkerBuilder<DailyRecipeWorker>(app).setRunAttemptCount(attempt).build()

    private fun run(attempt: Int = 0): Result = runBlocking { worker(attempt).doWork() }

    private fun posted(): List<Notification> = shadowOf(notificationManager).allNotifications

    // ------------------------------------------------------------------ sucesso

    @Test
    fun `sucesso com permissao posta uma notificacao com a receita do dia`() {
        val result = run()

        assertThat(result).isEqualTo(Result.success())
        assertThat(env.remote.randomCalls).isEqualTo(1)
        assertThat(posted()).hasSize(1)
        val notification = posted().single()
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            .isEqualTo("Receita do dia: Torta de fiambre")
        assertThat(notification.channelId).isEqualTo(NotificationHelper.CHANNEL_ID)
        assertThat(notification.flags and Notification.FLAG_AUTO_CANCEL).isNotEqualTo(0)
    }

    @Test
    fun `a notificacao tem a acao Salvar no caderno apontando para o receiver com o id da receita`() {
        run()

        val notification = posted().single()
        assertThat(notification.actions).hasLength(1)
        val action = notification.actions.single()
        assertThat(action.title.toString()).isEqualTo("Salvar no caderno")
        val pending = shadowOf(action.actionIntent)
        assertThat(pending.isBroadcastIntent).isTrue()
        val intent = pending.savedIntent
        assertThat(intent.action).isEqualTo(NotificationHelper.ACTION_SAVE)
        assertThat(intent.getStringExtra(NotificationHelper.EXTRA_RECIPE_ID)).isEqualTo("53318")
        assertThat(intent.component!!.className).isEqualTo(SaveRecipeReceiver::class.java.name)
    }

    @Test
    fun `tocar na notificacao abre o deep link cadernoreceitas barra barra receita barra id`() {
        run()

        val notification = posted().single()
        val pending = shadowOf(notification.contentIntent)
        assertThat(pending.isActivityIntent).isTrue()
        val intent = pending.savedIntent
        assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent.data.toString()).isEqualTo("cadernoreceitas://receita/53318")
        assertThat(intent.getPackage()).isEqualTo(app.packageName) // intent explicita para o proprio app
        // e o link e aceito pelo validador de deep links do app
        assertThat(
            RecipeIds.fromDeepLink(intent.data!!.scheme, intent.data!!.host, intent.data!!.pathSegments),
        ).isEqualTo("53318")
    }

    @Test
    fun `o canal de notificacao e criado`() {
        run()

        val channel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_ID)
        assertThat(channel).isNotNull()
        assertThat(channel.name.toString()).isEqualTo("Receita do dia")
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_DEFAULT)
    }

    @Test
    fun `a receita do dia fica guardada para uso offline e nas preferencias`() {
        run()

        assertThat(env.preferences.state.value.dailyRecipeId).isEqualTo("53318")
        val cached = runBlocking { env.repository.recipe("53318") }
        assertThat(cached.source).isEqualTo(DataSource.CACHE_FRESH)
    }

    @Test
    fun `rodar de novo no mesmo dia mantem uma unica notificacao com a mesma receita`() {
        run()
        run()

        assertThat(env.remote.randomCalls).isEqualTo(1) // a segunda usou o cache do dia
        assertThat(posted()).hasSize(1) // mesmo NOTIFICATION_ID: substitui
    }

    @Test
    fun `no dia seguinte sem rede reaproveita a sugestao anterior e ainda notifica`() {
        run()
        env.clock.advance(1.days)
        env.remote.failWith(RecipeException.Kind.NETWORK)

        val result = run()

        assertThat(result).isEqualTo(Result.success())
        assertThat(posted()).hasSize(1)
    }

    // ------------------------------------------------------------------ permissao

    @Test
    fun `sem a permissao POST_NOTIFICATIONS o trabalho termina com sucesso mas nada e postado`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val result = run()

        assertThat(result).isEqualTo(Result.success())
        assertThat(posted()).isEmpty()
        assertThat(env.remote.randomCalls).isEqualTo(1) // a receita foi buscada mesmo assim
    }

    @Test
    fun `com notificacoes desativadas nas configuracoes nada e postado`() {
        shadowOf(notificationManager).setNotificationsEnabled(false)

        val result = run()

        assertThat(result).isEqualTo(Result.success())
        assertThat(posted()).isEmpty()
    }

    // ------------------------------------------------------------------ falhas

    @Test
    fun `falha de rede tenta de novo nas tentativas 0, 1 e 2 e desiste na 3`() {
        env.remote.failWith(RecipeException.Kind.NETWORK)

        assertThat(run(attempt = 0)).isEqualTo(Result.retry())
        assertThat(run(attempt = 1)).isEqualTo(Result.retry())
        assertThat(run(attempt = 2)).isEqualTo(Result.retry())
        assertThat(run(attempt = 3)).isEqualTo(Result.failure())
        assertThat(posted()).isEmpty()
    }

    @Test
    fun `erro de servidor tambem tenta de novo ate o limite`() {
        env.remote.failWith(RecipeException.Kind.SERVER)

        assertThat(run(attempt = 0)).isEqualTo(Result.retry())
        assertThat(run(attempt = DailyRecipeWorker.MAX_ATTEMPTS)).isEqualTo(Result.failure())
        assertThat(posted()).isEmpty()
    }

    @Test
    fun `resposta invalida da API falha sem retentativa`() {
        env.remote.randomResponse = null // meals null -> INVALID_DATA no repositorio

        assertThat(run(attempt = 0)).isEqualTo(Result.failure())
        assertThat(posted()).isEmpty()
    }

    @Test
    fun `recuperada a rede o trabalho volta a ter sucesso`() {
        env.remote.failWith(RecipeException.Kind.NETWORK)
        assertThat(run(attempt = 0)).isEqualTo(Result.retry())

        env.remote.recover()

        assertThat(run(attempt = 1)).isEqualTo(Result.success())
        assertThat(posted()).hasSize(1)
    }

    // ------------------------------------------------------------------ o worker usa o container do app

    @Test
    fun `o worker pega o repositorio do container da Application`() {
        val other = NoOpRecipeRepository
        (app.container as FakeAppContainer).recipeRepository = other // so rede falsa que sempre falha

        assertThat(run(attempt = 0)).isEqualTo(Result.retry()) // NoOp lanca NETWORK
        assertThat(env.remote.randomCalls).isEqualTo(0)
    }

}
