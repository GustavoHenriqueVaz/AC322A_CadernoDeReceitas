package com.cadernoreceitas.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cadernoreceitas.CadernoApp
import com.cadernoreceitas.domain.model.RecipeException
import com.cadernoreceitas.util.RecipeIds
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Liga/desliga o lembrete diario. Interface para o app e os testes usarem implementacoes diferentes. */
interface ReminderScheduler {
    fun enable()
    fun disable()
}

class WorkManagerReminderScheduler(context: Context) : ReminderScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun enable() {
        val request = PeriodicWorkRequestBuilder<DailyRecipeWorker>(24, TimeUnit.HOURS)
            // Primeira execucao no proximo 09:00; depois a cada 24 h.
            .setInitialDelay(minutesUntilNext(REMINDER_HOUR), TimeUnit.MINUTES)
            // So roda com internet (precisa buscar a receita do dia na API).
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            // Falhas transitorias: tentar de novo com espera exponencial (15 min, 30 min...).
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        // KEEP: ligar de novo (ex.: ao abrir o app) nao reinicia a contagem do periodo.
        workManager.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun disable() {
        workManager.cancelUniqueWork(UNIQUE_NAME)
    }

    internal companion object {
        const val UNIQUE_NAME = "daily_recipe_reminder"
        const val REMINDER_HOUR = 9

        fun minutesUntilNext(hour: Int, now: LocalDateTime = LocalDateTime.now()): Long {
            var next = now.toLocalDate().atTime(LocalTime.of(hour, 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next).toMinutes().coerceAtLeast(1)
        }
    }
}

/**
 * Busca a "receita do dia" e publica a notificacao.
 * Rede/servidor indisponiveis -> `retry()` (o WorkManager aplica o backoff exponencial),
 * ate [MAX_ATTEMPTS] tentativas; erros nao recuperaveis -> `failure()`.
 * Como o trabalho e periodico, o proximo ciclo (24 h) acontece de qualquer forma.
 */
class DailyRecipeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CadernoApp).container
        return try {
            val recipe = container.recipeRepository.recipeOfTheDay().value
            NotificationHelper.showDailyRecipe(applicationContext, recipe)
            Result.success()
        } catch (e: RecipeException) {
            retryOrFail(e, runAttemptCount)
        }
    }

    internal companion object {
        const val MAX_ATTEMPTS = 3

        fun retryOrFail(e: RecipeException, attempt: Int): Result = when (e.kind) {
            RecipeException.Kind.NETWORK, RecipeException.Kind.SERVER ->
                if (attempt < MAX_ATTEMPTS) Result.retry() else Result.failure()
            else -> Result.failure()
        }
    }
}

/**
 * Salva uma receita no caderno em segundo plano (acao "Salvar no caderno" da notificacao).
 * E idempotente: salvar duas vezes a mesma receita nao duplica nada.
 */
class SaveRecipeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_RECIPE_ID)
        if (!RecipeIds.isValid(id)) return Result.failure()
        val container = (applicationContext as CadernoApp).container
        return try {
            container.recipeRepository.saveRecipeById(id!!)
            Result.success()
        } catch (e: RecipeException) {
            if (e.kind == RecipeException.Kind.NETWORK || e.kind == RecipeException.Kind.SERVER) {
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_RECIPE_ID = "recipe_id"
        const val MAX_ATTEMPTS = 5
    }
}
