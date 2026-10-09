package com.cadernoreceitas.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Cache local das receitas ja vistas (detalhes completos). Serve como fonte de dados
 * "offline first": a tela de detalhes e o caderno funcionam sem internet.
 */
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String?,
    val area: String?,
    val instructions: String,
    val thumbnailUrl: String?,
    /** Tags separadas por virgula. */
    val tags: String,
    val youtubeUrl: String?,
    val sourceUrl: String?,
    /** Lista de ingredientes serializada em JSON. */
    val ingredientsJson: String,
    /** Quando veio da API (epoch millis); base da politica de validade (TTL). */
    val fetchedAt: Long,
)

/**
 * Receita guardada no caderno + anotacao pessoal. Nao ha chave estrangeira com CASCADE
 * de proposito: o cache de `recipes` pode ser atualizado/limpo sem apagar o caderno
 * (veja `RecipeDao.purgeOlderThan`, que nunca remove receitas salvas).
 */
@Entity(tableName = "saved_recipes")
data class SavedRecipeEntity(
    @PrimaryKey val recipeId: String,
    val rating: Int?,
    val comment: String?,
    val prepMinutes: Int?,
    val photoPath: String?,
    val savedAt: Long,
    val updatedAt: Long,
)

data class SavedWithRecipe(
    @Embedded val saved: SavedRecipeEntity,
    @Relation(parentColumn = "recipeId", entityColumn = "id")
    val recipe: RecipeEntity?,
)

/** Cache de listas (resultado de busca, categoria, destaques) como JSON + data de busca. */
@Entity(tableName = "list_cache")
data class ListCacheEntity(
    @PrimaryKey val cacheKey: String,
    val payload: String,
    val fetchedAt: Long,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val name: String,
    val thumbnailUrl: String?,
    val description: String?,
    val position: Int,
    val fetchedAt: Long,
)
