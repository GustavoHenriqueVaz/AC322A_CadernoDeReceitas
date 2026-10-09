package com.cadernoreceitas.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.cadernoreceitas.R
import com.cadernoreceitas.domain.model.Recipe
import com.cadernoreceitas.util.RecipeIds

/** Cria o canal e publica a notificacao da "Receita do dia" (com acao "Salvar no caderno"). */
object NotificationHelper {
    const val CHANNEL_ID = "daily_recipe"
    const val ACTION_SAVE = "com.cadernoreceitas.action.SAVE_RECIPE"
    const val EXTRA_RECIPE_ID = "recipe_id"
    const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notif_channel_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** No Android 13+ a notificacao so aparece se o usuario concedeu POST_NOTIFICATIONS. */
    fun canPostNotifications(context: Context): Boolean {
        val permissionOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permissionOk && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission") // a permissao e checada em canPostNotifications()
    fun showDailyRecipe(context: Context, recipe: Recipe) {
        if (!canPostNotifications(context)) return
        createChannel(context)

        // Toque na notificacao -> deep link para a tela de detalhes.
        val openIntent = Intent(Intent.ACTION_VIEW, RecipeIds.deepLink(recipe.id).toUri()).apply {
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPending = PendingIntent.getActivity(
            context,
            recipe.id.hashCode(),
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // Acao da notificacao -> receiver (nao exportado) que enfileira o salvamento.
        val saveIntent = Intent(context, SaveRecipeReceiver::class.java).apply {
            action = ACTION_SAVE
            putExtra(EXTRA_RECIPE_ID, recipe.id)
        }
        val savePending = PendingIntent.getBroadcast(
            context,
            recipe.id.hashCode(),
            saveIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_recipe)
            .setContentTitle(context.getString(R.string.notif_title, recipe.name))
            .setContentText(context.getString(R.string.notif_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notif_text)))
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.notif_action_save), savePending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
