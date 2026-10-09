package com.cadernoreceitas.testutil

import com.cadernoreceitas.CadernoApp
import com.cadernoreceitas.di.AppContainer

/**
 * `Application` dos testes Robolectric: igual ao `CadernoApp`, mas com um [FakeAppContainer]
 * (sem rede, sem banco em disco). Use com `@Config(application = TestCadernoApp::class)`.
 * Para trocar uma dependencia dentro do teste:
 *
 *     val container = ApplicationProvider.getApplicationContext<TestCadernoApp>().container as FakeAppContainer
 *     container.recipeRepository = ...
 */
class TestCadernoApp : CadernoApp() {
    override fun createContainer(): AppContainer = FakeAppContainer()
}
