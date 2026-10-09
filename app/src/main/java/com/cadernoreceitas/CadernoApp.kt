package com.cadernoreceitas

import android.app.Application
import android.content.Context
import com.cadernoreceitas.di.AppContainer
import com.cadernoreceitas.di.DefaultAppContainer
import com.cadernoreceitas.work.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * `open` para que os testes possam subclassear e entregar um [AppContainer] com fakes
 * (veja `TestCadernoApp` em src/test).
 */
open class CadernoApp : Application() {

    lateinit var container: AppContainer
        private set

    /** Escopo do processo: vive tanto quanto o app (usado so para manutencao em segundo plano). */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    protected open fun createContainer(): AppContainer = DefaultAppContainer(this)

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        NotificationHelper.createChannel(this)

        applicationScope.launch {
            // Garante que o lembrete diario continue agendado se o usuario o habilitou.
            runCatching {
                if (container.userPreferences.preferences.first().reminderEnabled) {
                    container.reminderScheduler.enable()
                }
            }
            // Manutencao do cache (receitas antigas nao salvas e fotos orfas).
            runCatching { container.recipeRepository.purgeExpiredCache() }
        }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as CadernoApp).container
