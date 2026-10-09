package com.cadernoreceitas.testutil

import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper

/** Apoio aos testes de WorkManager (Robolectric). */
object WorkTestSupport {

    /** WorkManager de teste: executor sincrono e banco em memoria. Chame no `@Before`. */
    fun initWorkManager(context: Context) {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    fun workInfos(context: Context, uniqueName: String): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(uniqueName).get()

    /** Espera (tempo real) ate a condicao ficar verdadeira; falha com [description] se estourar. */
    fun awaitCondition(description: String, timeoutMillis: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Tempo esgotado esperando: $description" }
            Thread.sleep(20)
        }
    }
}
