package com.cadernoreceitas.uitesting

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Troca `Dispatchers.Main` por um dispatcher de teste (o `viewModelScope` usa o Main).
 * Com [StandardTestDispatcher] nada roda sozinho: o teste controla o relogio virtual com
 * `runCurrent()`, `advanceTimeBy()` e `advanceUntilIdle()`. O `runTest` reaproveita o mesmo
 * agendador, entao `delay` dentro do ViewModel/fake respeita o tempo virtual.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
