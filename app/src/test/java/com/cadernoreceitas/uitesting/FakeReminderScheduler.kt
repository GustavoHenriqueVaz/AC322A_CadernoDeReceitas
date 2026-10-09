package com.cadernoreceitas.uitesting

import com.cadernoreceitas.work.ReminderScheduler
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Agendador falso: nada de WorkManager, so conta as chamadas (e guarda a ordem delas). */
class FakeReminderScheduler : ReminderScheduler {
    private val enables = AtomicInteger()
    private val disables = AtomicInteger()

    val enableCount: Int get() = enables.get()
    val disableCount: Int get() = disables.get()

    /** "enable"/"disable" na ordem em que foram chamados. */
    val events: MutableList<String> = CopyOnWriteArrayList()

    /** Estado final apos todas as chamadas (ligado = a ultima chamada foi `enable`). */
    @Volatile var isEnabled: Boolean = false
        private set

    override fun enable() {
        enables.incrementAndGet()
        events += "enable"
        isEnabled = true
    }

    override fun disable() {
        disables.incrementAndGet()
        events += "disable"
        isEnabled = false
    }
}
