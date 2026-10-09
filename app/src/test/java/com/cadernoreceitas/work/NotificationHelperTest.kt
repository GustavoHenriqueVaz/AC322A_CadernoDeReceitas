package com.cadernoreceitas.work

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cadernoreceitas.testutil.TestFixtures
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** `NotificationHelper`: canal, permissao, publicacao e cancelamento. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class NotificationHelperTest {

    private lateinit var app: Application
    private lateinit var manager: NotificationManager

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        manager = app.getSystemService(NotificationManager::class.java)
    }

    private fun posted() = shadowOf(manager).allNotifications

    @Test
    fun `createChannel cria o canal com nome, descricao e importancia padrao`() {
        NotificationHelper.createChannel(app)

        val channel = manager.getNotificationChannel(NotificationHelper.CHANNEL_ID)
        assertThat(channel).isNotNull()
        assertThat(channel.name.toString()).isEqualTo("Receita do dia")
        assertThat(channel.description).isEqualTo("Uma sugestão de receita por dia")
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_DEFAULT)
    }

    @Test
    fun `createChannel chamado varias vezes nao duplica`() {
        repeat(3) { NotificationHelper.createChannel(app) }

        assertThat(manager.notificationChannels.filter { it.id == NotificationHelper.CHANNEL_ID }).hasSize(1)
    }

    @Test
    fun `sem permissao canPostNotifications e falso e nada e postado`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertThat(NotificationHelper.canPostNotifications(app)).isFalse()
        NotificationHelper.showDailyRecipe(app, TestFixtures.recipe(id = "5"))

        assertThat(posted()).isEmpty()
    }

    @Test
    fun `com permissao canPostNotifications e verdadeiro e a notificacao e postada`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertThat(NotificationHelper.canPostNotifications(app)).isTrue()
        NotificationHelper.showDailyRecipe(app, TestFixtures.recipe(id = "5", name = "Bolo de fubá"))

        assertThat(posted()).hasSize(1)
        val notification = posted().single()
        assertThat(notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
            .isEqualTo("Receita do dia: Bolo de fubá")
        assertThat(shadowOf(notification.contentIntent).savedIntent.data.toString())
            .isEqualTo("cadernoreceitas://receita/5")
    }

    @Test
    fun `permissao concedida mas notificacoes desligadas nas configuracoes - nao posta`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(manager).setNotificationsEnabled(false)

        assertThat(NotificationHelper.canPostNotifications(app)).isFalse()
        NotificationHelper.showDailyRecipe(app, TestFixtures.recipe(id = "5"))

        assertThat(posted()).isEmpty()
    }

    @Test
    fun `uma nova receita substitui a notificacao anterior (mesmo id de notificacao)`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        NotificationHelper.showDailyRecipe(app, TestFixtures.recipe(id = "1", name = "Primeira"))
        NotificationHelper.showDailyRecipe(app, TestFixtures.recipe(id = "2", name = "Segunda"))

        assertThat(posted()).hasSize(1)
        assertThat(shadowOf(posted().single().contentIntent).savedIntent.data.toString())
            .isEqualTo("cadernoreceitas://receita/2")
    }

    @Test
    fun `cancel remove a notificacao`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationHelper.showDailyRecipe(app, TestFixtures.recipe(id = "1"))

        NotificationHelper.cancel(app)

        assertThat(posted()).isEmpty()
    }

    @Test
    fun `cancel sem notificacao nao falha`() {
        NotificationHelper.cancel(app)

        assertThat(posted()).isEmpty()
    }

    @Test
    fun `acoes e extras tem os nomes que o receiver espera`() {
        assertThat(NotificationHelper.ACTION_SAVE).isEqualTo("com.cadernoreceitas.action.SAVE_RECIPE")
        assertThat(NotificationHelper.EXTRA_RECIPE_ID).isEqualTo("recipe_id")
        assertThat(NotificationHelper.CHANNEL_ID).isEqualTo("daily_recipe")
    }
}
