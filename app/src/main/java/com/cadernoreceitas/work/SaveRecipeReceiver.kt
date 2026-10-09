package com.cadernoreceitas.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.cadernoreceitas.util.RecipeIds

/**
 * Recebe o toque no botao "Salvar no caderno" da notificacao.
 * Nao faz trabalho pesado aqui (um receiver tem poucos segundos): apenas valida o id,
 * enfileira o [SaveRecipeWorker] e fecha a notificacao.
 */
class SaveRecipeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationHelper.ACTION_SAVE) return
        val id = intent.getStringExtra(NotificationHelper.EXTRA_RECIPE_ID)
        if (!RecipeIds.isValid(id)) return // entrada externa: nunca confiar sem validar

        val request = OneTimeWorkRequestBuilder<SaveRecipeWorker>()
            .setInputData(workDataOf(SaveRecipeWorker.KEY_RECIPE_ID to id))
            .build()
        // Nome unico + KEEP: varios toques seguidos geram um unico salvamento.
        WorkManager.getInstance(context).enqueueUniqueWork("save_recipe_$id", ExistingWorkPolicy.KEEP, request)
        NotificationHelper.cancel(context)
    }
}
