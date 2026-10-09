package com.cadernoreceitas.data.local.prefs

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferencesSerializer
import app.cash.turbine.test
import com.cadernoreceitas.domain.model.UserPreferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * DataStore REAL gravando em arquivo temporario (JVM puro, sem Android). Cada teste usa a
 * `backgroundScope` do `runTest`, que e cancelada no fim.
 */
class DataStoreUserPreferencesRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    /**
     * DataStore de Preferences REAL sobre um arquivo, mas com o `OkioStorage`: o armazenamento
     * `java.io` padrao grava em `arquivo.tmp` e chama `File.renameTo`, que no Windows FALHA quando
     * o destino ja existe (no Android/Linux o rename substitui). O `OkioStorage` faz a troca
     * atomica (`atomicMove`) corretamente nos dois sistemas e usa o mesmo serializador de producao.
     */
    private fun storeIn(scope: CoroutineScope, file: File) = DataStoreUserPreferencesRepository(
        DataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.toOkioPath() },
            scope = scope,
        ),
    )

    private fun TestScope.newRepository(
        file: File = File(tmpFolder.root, "prefs-${System.nanoTime()}.preferences_pb"),
    ): DataStoreUserPreferencesRepository = storeIn(backgroundScope, file)

    @Test
    fun `valores padrao quando nada foi gravado`() = runTest {
        val repository = newRepository()

        val prefs = repository.preferences.first()

        assertThat(prefs).isEqualTo(UserPreferences())
        assertThat(prefs.lastQuery).isEmpty()
        assertThat(prefs.lastCategory).isNull()
        assertThat(prefs.reminderEnabled).isFalse()
        assertThat(prefs.dailyRecipeId).isNull()
        assertThat(prefs.dailyRecipeEpochDay).isNull()
    }

    @Test
    fun `ultima busca e categoria sao gravadas e lidas`() = runTest {
        val repository = newRepository()

        repository.setLastSearch("chicken soup", "Chicken")

        val prefs = repository.preferences.first()
        assertThat(prefs.lastQuery).isEqualTo("chicken soup")
        assertThat(prefs.lastCategory).isEqualTo("Chicken")
    }

    @Test
    fun `categoria nula remove a categoria gravada`() = runTest {
        val repository = newRepository()
        repository.setLastSearch("chicken", "Chicken")

        repository.setLastSearch("beef", null)

        val prefs = repository.preferences.first()
        assertThat(prefs.lastQuery).isEqualTo("beef")
        assertThat(prefs.lastCategory).isNull() // removida de verdade, nao ficou a antiga
    }

    @Test
    fun `busca vazia e gravada como texto vazio`() = runTest {
        val repository = newRepository()
        repository.setLastSearch("chicken", "Chicken")

        repository.setLastSearch("", "Chicken")

        val prefs = repository.preferences.first()
        assertThat(prefs.lastQuery).isEmpty()
        assertThat(prefs.lastCategory).isEqualTo("Chicken")
    }

    @Test
    fun `texto com acentos e emojis volta identico`() = runTest {
        val repository = newRepository()
        val query = "pão de queijo 🧀"

        repository.setLastSearch(query, "Sobremesa ç")

        val prefs = repository.preferences.first()
        assertThat(prefs.lastQuery).isEqualTo(query)
        assertThat(prefs.lastCategory).isEqualTo("Sobremesa ç")
    }

    @Test
    fun `lembrete diario liga e desliga`() = runTest {
        val repository = newRepository()

        repository.setReminderEnabled(true)
        assertThat(repository.preferences.first().reminderEnabled).isTrue()

        repository.setReminderEnabled(false)
        assertThat(repository.preferences.first().reminderEnabled).isFalse()
    }

    @Test
    fun `receita do dia grava o id e o dia, e a seguinte sobrescreve`() = runTest {
        val repository = newRepository()

        repository.setDailyRecipe("53318", 20_522L)
        val first = repository.preferences.first()
        assertThat(first.dailyRecipeId).isEqualTo("53318")
        assertThat(first.dailyRecipeEpochDay).isEqualTo(20_522L)

        repository.setDailyRecipe("52772", 20_523L)
        val second = repository.preferences.first()
        assertThat(second.dailyRecipeId).isEqualTo("52772")
        assertThat(second.dailyRecipeEpochDay).isEqualTo(20_523L)
    }

    @Test
    fun `cada escrita preserva os outros campos`() = runTest {
        val repository = newRepository()

        repository.setLastSearch("cake", "Dessert")
        repository.setReminderEnabled(true)
        repository.setDailyRecipe("1", 100L)
        repository.setLastSearch("pie", null) // mexe so na busca/categoria

        assertThat(repository.preferences.first()).isEqualTo(
            UserPreferences(
                lastQuery = "pie",
                lastCategory = null,
                reminderEnabled = true,
                dailyRecipeId = "1",
                dailyRecipeEpochDay = 100L,
            ),
        )
    }

    @Test
    fun `o fluxo emite a cada alteracao`() = runTest {
        val repository = newRepository()

        repository.preferences.test {
            assertThat(awaitItem()).isEqualTo(UserPreferences())

            repository.setReminderEnabled(true)
            assertThat(awaitItem().reminderEnabled).isTrue()

            repository.setLastSearch("soup", "Starter")
            val afterSearch = awaitItem()
            assertThat(afterSearch.lastQuery).isEqualTo("soup")
            assertThat(afterSearch.lastCategory).isEqualTo("Starter")
            assertThat(afterSearch.reminderEnabled).isTrue()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `os dados sobrevivem a recriacao do DataStore (persistem no arquivo)`() = runTest {
        val file = tmpFolder.newFile("persist.preferences_pb")

        // 1a "execucao do app": grava e encerra o DataStore
        val firstJob = Job()
        val firstScope = CoroutineScope(firstJob + StandardTestDispatcher(testScheduler))
        val first = storeIn(firstScope, file)
        first.setLastSearch("lasagna", "Pasta")
        first.setReminderEnabled(true)
        first.setDailyRecipe("777", 12_345L)
        firstJob.cancelAndJoin()

        // 2a "execucao": le o que ficou no disco
        val second = newRepository(file)
        val prefs = second.preferences.first()

        assertThat(prefs.lastQuery).isEqualTo("lasagna")
        assertThat(prefs.lastCategory).isEqualTo("Pasta")
        assertThat(prefs.reminderEnabled).isTrue()
        assertThat(prefs.dailyRecipeId).isEqualTo("777")
        assertThat(prefs.dailyRecipeEpochDay).isEqualTo(12_345L)
    }

    @Test
    fun `arquivo corrompido nao derruba o app - cai nos valores padrao`() = runTest {
        val file = tmpFolder.newFile("corrupt.preferences_pb")
        file.writeBytes(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F, 0x00, 0x13))
        val repository = newRepository(file)

        val prefs = repository.preferences.first()

        assertThat(prefs).isEqualTo(UserPreferences())
    }
}
