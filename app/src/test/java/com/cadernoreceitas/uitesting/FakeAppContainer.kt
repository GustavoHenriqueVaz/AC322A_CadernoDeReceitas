package com.cadernoreceitas.uitesting

import com.cadernoreceitas.di.AppContainer

/**
 * [AppContainer] so com fakes: o app inteiro roda sem rede, banco, DataStore ou WorkManager.
 * O padrao ja vem com os dados de [SampleData] (destaques, categorias, receita do dia).
 */
class FakeAppContainer(
    override val recipeRepository: FakeRecipeRepository = FakeRecipeRepository().seedSample(),
    override val userPreferences: FakeUserPreferencesRepository = FakeUserPreferencesRepository(),
    override val photoStorage: FakePhotoStorage = FakePhotoStorage(),
    override val reminderScheduler: FakeReminderScheduler = FakeReminderScheduler(),
) : AppContainer
