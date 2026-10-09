package com.cadernoreceitas.work

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import androidx.work.WorkInfo
import com.cadernoreceitas.testutil.WorkTestSupport
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** `WorkManagerReminderScheduler.enable/disable` com um WorkManager de teste. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class WorkManagerReminderSchedulerTest {

    private lateinit var app: Application
    private lateinit var scheduler: WorkManagerReminderScheduler

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        WorkTestSupport.initWorkManager(app)
        scheduler = WorkManagerReminderScheduler(app)
    }

    private fun infos() = WorkTestSupport.workInfos(app, WorkManagerReminderScheduler.UNIQUE_NAME)

    @Test
    fun `enable agenda um trabalho periodico de 24 horas que exige internet`() {
        scheduler.enable()

        val info = infos().single()
        assertThat(info.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(info.tags).contains(DailyRecipeWorker::class.java.name)
        assertThat(info.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
        assertThat(info.periodicityInfo!!.repeatIntervalMillis).isEqualTo(TimeUnit.HOURS.toMillis(24))
    }

    @Test
    fun `a primeira execucao espera ate o proximo horario do lembrete (no maximo 24 horas)`() {
        scheduler.enable()

        val delay = infos().single().initialDelayMillis
        assertThat(delay).isAtLeast(TimeUnit.MINUTES.toMillis(1))
        assertThat(delay).isAtMost(TimeUnit.HOURS.toMillis(24))
    }

    @Test
    fun `ligar de novo nao reinicia nem duplica (KEEP)`() {
        scheduler.enable()
        val firstId = infos().single().id

        scheduler.enable()
        scheduler.enable()

        assertThat(infos()).hasSize(1)
        assertThat(infos().single().id).isEqualTo(firstId)
    }

    @Test
    fun `disable cancela o trabalho`() {
        scheduler.enable()

        scheduler.disable()

        assertThat(infos().single().state).isEqualTo(WorkInfo.State.CANCELLED)
    }

    @Test
    fun `disable sem ter ligado nao falha`() {
        scheduler.disable()

        assertThat(infos()).isEmpty()
    }

    @Test
    fun `desligar e ligar de novo agenda um trabalho novo`() {
        scheduler.enable()
        val firstId = infos().single().id
        scheduler.disable()

        scheduler.enable()

        val active = infos().filter { it.state == WorkInfo.State.ENQUEUED }
        assertThat(active).hasSize(1)
        assertThat(active.single().id).isNotEqualTo(firstId)
    }

    @Test
    fun `nome unico do trabalho`() {
        assertThat(WorkManagerReminderScheduler.UNIQUE_NAME).isEqualTo("daily_recipe_reminder")
    }
}
