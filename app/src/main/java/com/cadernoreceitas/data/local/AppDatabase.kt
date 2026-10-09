package com.cadernoreceitas.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.cadernoreceitas.data.local.dao.CategoryDao
import com.cadernoreceitas.data.local.dao.ListCacheDao
import com.cadernoreceitas.data.local.dao.RecipeDao
import com.cadernoreceitas.data.local.dao.SavedRecipeDao
import com.cadernoreceitas.data.local.entity.CategoryEntity
import com.cadernoreceitas.data.local.entity.ListCacheEntity
import com.cadernoreceitas.data.local.entity.RecipeEntity
import com.cadernoreceitas.data.local.entity.SavedRecipeEntity

@Database(
    entities = [
        RecipeEntity::class,
        SavedRecipeEntity::class,
        ListCacheEntity::class,
        CategoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun savedRecipeDao(): SavedRecipeDao
    abstract fun listCacheDao(): ListCacheDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        const val FILE_NAME = "caderno_receitas.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME)
                .build()

        /** Banco em memoria, usado pelos testes. */
        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
