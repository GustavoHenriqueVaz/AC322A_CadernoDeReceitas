package com.cadernoreceitas.work

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cadernoreceitas.CadernoApp
import com.cadernoreceitas.appContainer
import com.cadernoreceitas.data.repository.RecipeRepository
import com.cadernoreceitas.di.AppContainer
import com.cadernoreceitas.domain.model.UserPreferences
import com.cadernoreceitas.testutil.FakeAppContainer
import com.cadernoreceitas.testutil.FakePhotoStorage
import com.cadernoreceitas.testutil.FakeReminderScheduler
import com.cadernoreceitas.testutil.FakeUserPreferencesRepository
import com.cadernoreceitas.testutil.NoOpRecipeRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Repositorio que so registra a chamada de manutencao (o resto e o `NoOpRecipeRepository`). */
internal class PurgeSpyRepository : RecipeRepository by NoOpRecipeRepository {
    val purgeCalls = AtomicInteger()
    val purged = CountDownLatch(1)

    override suspend fun purgeExpiredCache() {
        purgeCalls.incrementAndGet()
        purged.countDown()
    }
}

/** Agendador cujo `enable()` quebra: o app nao pode cair por causa disso. */
internal class BrokenReminderScheduler : ReminderScheduler {
    override fun enable() = throw IllegalStateException("WorkManager indisponivel")
    override fun disable() = Unit
}

/**
 * `CadernoApp` com o container ja preparado: as propriedades sao inicializadas no construtor,
 * antes do `onCreate` (que e quando o app chama `createContainer()`).
 */
internal open class StartupApp(
    reminderEnabled: Boolean,
    val scheduler: ReminderScheduler = FakeReminderScheduler(),
) : CadernoApp() {
    val spy = PurgeSpyRepository()
    val preferences = FakeUserPreferencesRepository(UserPreferences(reminderEnabled = reminderEnabled))

    override fun createContainer(): AppContainer = FakeAppContainer(spy, preferences, FakePhotoStorage(), scheduler)
}

internal class ReminderOnApp : StartupApp(reminderEnabled = true)

internal class ReminderOffApp : StartupApp(reminderEnabled = false)

internal class BrokenSchedulerApp : StartupApp(reminderEnabled = true, scheduler = BrokenReminderScheduler())

/**
 * Inicializacao do `CadernoApp`: container, canal de notificacao, religar o lembrete diario
 * (se estiver habilitado) e manutencao do cache.
 */
@RunWith(AndroidJUnit4::class)
class CadernoAppStartupTest {

    private fun StartupApp.awaitStartup() {
        // a manutencao e a ultima etapa do `launch` de inicializacao
        assertThat(spy.purged.await(10, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    @Config(application = ReminderOnApp::class)
    fun `com o lembrete habilitado o app religa o agendamento ao iniciar`() {
        val app = ApplicationProvider.getApplicationContext<ReminderOnApp>()

        app.awaitStartup()

        val scheduler = app.scheduler as FakeReminderScheduler
        assertThat(scheduler.enableCalls).isEqualTo(1)
        assertThat(scheduler.disableCalls).isEqualTo(0)
    }

    @Test
    @Config(application = ReminderOffApp::class)
    fun `com o lembrete desligado o app nao agenda nada`() {
        val app = ApplicationProvider.getApplicationContext<ReminderOffApp>()

        app.awaitStartup()

        assertThat((app.scheduler as FakeReminderScheduler).enableCalls).isEqualTo(0)
    }

    @Test
    @Config(application = ReminderOffApp::class)
    fun `ao iniciar roda a manutencao do cache uma vez`() {
        val app = ApplicationProvider.getApplicationContext<ReminderOffApp>()

        app.awaitStartup()

        assertThat(app.spy.purgeCalls.get()).isEqualTo(1)
    }

    @Test
    @Config(application = BrokenSchedulerApp::class)
    fun `falha ao religar o lembrete nao derruba o app nem impede a manutencao`() {
        val app = ApplicationProvider.getApplicationContext<BrokenSchedulerApp>()

        app.awaitStartup()

        assertThat(app.spy.purgeCalls.get()).isEqualTo(1)
    }

    @Test
    @Config(application = ReminderOffApp::class)
    fun `o container fica acessivel pelo Context appContainer`() {
        val app = ApplicationProvider.getApplicationContext<ReminderOffApp>()

        assertThat(app.appContainer).isSameInstanceAs(app.container)
        assertThat(app.appContainer.recipeRepository).isSameInstanceAs(app.spy)
        assertThat(app.appContainer.userPreferences).isSameInstanceAs(app.preferences)
    }

    @Test
    @Config(application = ReminderOffApp::class)
    fun `o canal de notificacao e criado na inicializacao`() {
        val app = ApplicationProvider.getApplicationContext<ReminderOffApp>()

        val channel = app.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(NotificationHelper.CHANNEL_ID)

        assertThat(channel).isNotNull()
    }
}
