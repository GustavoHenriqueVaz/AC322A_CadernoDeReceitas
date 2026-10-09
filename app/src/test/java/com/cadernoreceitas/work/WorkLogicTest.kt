package com.cadernoreceitas.work

import androidx.work.ListenableWorker.Result
import com.cadernoreceitas.domain.model.RecipeException
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Logica pura do modulo `work` (sem WorkManager nem Android): horario do lembrete e politica de retentativas. */
class WorkLogicTest {

    private fun at(hour: Int, minute: Int = 0, second: Int = 0): LocalDateTime =
        LocalDateTime.of(LocalDate.of(2026, 3, 10), LocalTime.of(hour, minute, second))

    // ------------------------------------------------------------------ minutesUntilNext

    @Test
    fun `antes das 9h espera ate as 9h do mesmo dia`() {
        assertThat(WorkManagerReminderScheduler.minutesUntilNext(9, at(8, 0))).isEqualTo(60)
        assertThat(WorkManagerReminderScheduler.minutesUntilNext(9, at(8, 30))).isEqualTo(30)
        assertThat(WorkManagerReminderScheduler.minutesUntilNext(9, at(0, 0))).isEqualTo(9 * 60)
        assertThat(WorkManagerReminderScheduler.minutesUntilNext(9, at(7, 15))).isEqualTo(105)
    }

    @Test
    fun `depois das 9h espera ate as 9h do dia seguinte`() {
        assertThat(WorkManagerReminderScheduler.minutesUntilNext(9, at(10, 0))).isEqualTo(23 * 60)
        assertThat(WorkManagerReminderScheduler.minutesUntilNext(9, at(12, 0))).isEqualTo(21 * 60)
        assertThat(WorkManagerReminderScheduler.minutesUntilNext(9, at(23, 59))).isEqualTo(9 * 60 + 1)
        assertThat(WorkManagerReminderScheduler.minutesUntilNext(9, at(9, 1))).isEqualTo(24 * 60 - 1)
    }

    @Test
    fun `exatamente as 9h agenda para o dia seguinte (24 horas)`() {
        assertThat(WorkManagerReminderScheduler.minutesUntilNext(9, at(9, 0))).isEqualTo(24 * 60)
    }

    @Test
    fun `um segundo depois das 9h nao agenda para agora (quase 24 horas)`() {
        // 23h59m59s -> arredonda para baixo para 1439 minutos
        assertThat(WorkManagerReminderScheduler.minutesUntilNext(9, at(9, 0, 1))).isEqualTo(24 * 60 - 1)
    }

    @Test
    fun `faltando menos de um minuto o atraso e no minimo 1 minuto`() {
        assertThat(WorkManagerReminderScheduler.minutesUntilNext(9, at(8, 59, 30))).isEqualTo(1)
        assertThat(WorkManagerReminderScheduler.minutesUntilNext(9, at(8, 59, 59))).isEqualTo(1)
    }

    @Test
    fun `funciona com outros horarios`() {
        assertThat(WorkManagerReminderScheduler.minutesUntilNext(0, at(23, 0))).isEqualTo(60) // meia-noite
        assertThat(WorkManagerReminderScheduler.minutesUntilNext(18, at(17, 0))).isEqualTo(60)
        assertThat(WorkManagerReminderScheduler.minutesUntilNext(18, at(18, 0))).isEqualTo(24 * 60)
    }

    @Test
    fun `o horario do lembrete e as 9h`() {
        assertThat(WorkManagerReminderScheduler.REMINDER_HOUR).isEqualTo(9)
    }

    @Test
    fun `virada de mes e de ano nao atrapalha`() {
        val newYearsEve = LocalDateTime.of(2026, 12, 31, 20, 0)

        assertThat(WorkManagerReminderScheduler.minutesUntilNext(9, newYearsEve)).isEqualTo(13 * 60)
    }

    // ------------------------------------------------------------------ DailyRecipeWorker.retryOrFail

    @Test
    fun `NETWORK e SERVER tentam de novo ate o limite de tentativas`() {
        for (kind in listOf(RecipeException.Kind.NETWORK, RecipeException.Kind.SERVER)) {
            for (attempt in 0 until DailyRecipeWorker.MAX_ATTEMPTS) {
                assertThat(DailyRecipeWorker.retryOrFail(RecipeException(kind), attempt)).isEqualTo(Result.retry())
            }
        }
    }

    @Test
    fun `NETWORK e SERVER falham ao atingir o limite de tentativas`() {
        for (kind in listOf(RecipeException.Kind.NETWORK, RecipeException.Kind.SERVER)) {
            assertThat(DailyRecipeWorker.retryOrFail(RecipeException(kind), DailyRecipeWorker.MAX_ATTEMPTS))
                .isEqualTo(Result.failure())
            assertThat(DailyRecipeWorker.retryOrFail(RecipeException(kind), DailyRecipeWorker.MAX_ATTEMPTS + 10))
                .isEqualTo(Result.failure())
        }
    }

    @Test
    fun `INVALID_DATA e NOT_FOUND falham logo, sem retentativa`() {
        for (kind in listOf(RecipeException.Kind.INVALID_DATA, RecipeException.Kind.NOT_FOUND)) {
            assertThat(DailyRecipeWorker.retryOrFail(RecipeException(kind), 0)).isEqualTo(Result.failure())
            assertThat(DailyRecipeWorker.retryOrFail(RecipeException(kind), 1)).isEqualTo(Result.failure())
        }
    }

    @Test
    fun `limites de tentativas documentados`() {
        assertThat(DailyRecipeWorker.MAX_ATTEMPTS).isEqualTo(3)
        assertThat(SaveRecipeWorker.MAX_ATTEMPTS).isEqualTo(5)
        assertThat(SaveRecipeWorker.KEY_RECIPE_ID).isEqualTo("recipe_id")
    }
}
