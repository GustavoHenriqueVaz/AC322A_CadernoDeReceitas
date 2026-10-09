package com.cadernoreceitas.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.cadernoreceitas.data.local.entity.CategoryEntity
import com.cadernoreceitas.data.local.entity.ListCacheEntity
import com.cadernoreceitas.data.local.entity.RecipeEntity
import com.cadernoreceitas.data.local.entity.SavedRecipeEntity
import com.cadernoreceitas.data.local.entity.SavedWithRecipe
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    /** `@Upsert` atualiza a linha no lugar (sem apagar+inserir), preservando o que depende dela. */
    @Upsert
    suspend fun upsert(recipe: RecipeEntity)

    @Upsert
    suspend fun upsertAll(recipes: List<RecipeEntity>)

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getById(id: String): RecipeEntity?

    /** [pattern] ja deve vir com `%` e com caracteres especiais escapados por `\`. */
    @Query("SELECT * FROM recipes WHERE name LIKE :pattern ESCAPE '\\' ORDER BY name LIMIT 50")
    suspend fun searchByName(pattern: String): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE category = :category ORDER BY name LIMIT 50")
    suspend fun getByCategory(category: String): List<RecipeEntity>

    @Query("SELECT * FROM recipes ORDER BY fetchedAt DESC, name LIMIT 25")
    suspend fun getMostRecent(): List<RecipeEntity>

    /** Limpeza do cache: remove receitas antigas, NUNCA as que estao no caderno. */
    @Query(
        "DELETE FROM recipes WHERE fetchedAt < :olderThan " +
            "AND id NOT IN (SELECT recipeId FROM saved_recipes)",
    )
    suspend fun purgeOlderThan(olderThan: Long): Int

    @Query("SELECT COUNT(*) FROM recipes")
    suspend fun count(): Int
}

@Dao
interface SavedRecipeDao {

    @Transaction
    @Query("SELECT * FROM saved_recipes ORDER BY savedAt DESC, recipeId")
    fun observeAll(): Flow<List<SavedWithRecipe>>

    @Transaction
    @Query("SELECT * FROM saved_recipes WHERE recipeId = :id")
    fun observeById(id: String): Flow<SavedWithRecipe?>

    @Query("SELECT recipeId FROM saved_recipes")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT recipeId, photoPath FROM saved_recipes WHERE photoPath IS NOT NULL")
    suspend fun getAllPhotoPaths(): List<PhotoRef>

    @Transaction
    @Query("SELECT * FROM saved_recipes WHERE recipeId = :id")
    suspend fun getWithRecipe(id: String): SavedWithRecipe?

    /** Idempotente: salvar duas vezes a mesma receita nao duplica nem sobrescreve a anotacao. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: SavedRecipeEntity): Long

    @Upsert
    suspend fun upsert(entity: SavedRecipeEntity)

    @Query(
        "UPDATE saved_recipes SET rating = :rating, comment = :comment, " +
            "prepMinutes = :prepMinutes, photoPath = :photoPath, updatedAt = :now " +
            "WHERE recipeId = :id",
    )
    suspend fun updateNote(
        id: String,
        rating: Int?,
        comment: String?,
        prepMinutes: Int?,
        photoPath: String?,
        now: Long,
    ): Int

    @Query("DELETE FROM saved_recipes WHERE recipeId = :id")
    suspend fun delete(id: String): Int
}

data class PhotoRef(val recipeId: String, val photoPath: String?)

@Dao
interface ListCacheDao {
    @Query("SELECT * FROM list_cache WHERE cacheKey = :key")
    suspend fun get(key: String): ListCacheEntity?

    @Upsert
    suspend fun put(entity: ListCacheEntity)

    @Query("DELETE FROM list_cache WHERE fetchedAt < :olderThan")
    suspend fun purgeOlderThan(olderThan: Long): Int
}

@Dao
abstract class CategoryDao {
    @Query("SELECT * FROM categories ORDER BY position")
    abstract suspend fun getAll(): List<CategoryEntity>

    @Query("DELETE FROM categories")
    protected abstract suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertAll(items: List<CategoryEntity>)

    @Transaction
    open suspend fun replaceAll(items: List<CategoryEntity>) {
        deleteAll()
        insertAll(items)
    }
}
