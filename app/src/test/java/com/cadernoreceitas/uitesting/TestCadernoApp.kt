package com.cadernoreceitas.uitesting

import com.cadernoreceitas.CadernoApp
import com.cadernoreceitas.di.AppContainer

/**
 * Application dos testes Robolectric: troca o container de producao (Retrofit/Room/DataStore/
 * WorkManager) por [FakeAppContainer]. Um container NOVO por teste (o Robolectric cria uma
 * Application nova a cada teste), acessivel por [fakes].
 *
 * Uso: `@Config(application = TestCadernoApp::class)` na classe de teste.
 */
class TestCadernoApp : CadernoApp() {

    /** O container tipado como falso, para o teste programar respostas e ler contadores. */
    val fakes: FakeAppContainer get() = container as FakeAppContainer

    override fun createContainer(): AppContainer = FakeAppContainer()
}
