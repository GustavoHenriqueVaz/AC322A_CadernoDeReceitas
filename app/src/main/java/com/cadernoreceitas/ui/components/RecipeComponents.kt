package com.cadernoreceitas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.cadernoreceitas.R
import com.cadernoreceitas.domain.model.RecipeSummary

/**
 * Imagem remota (Coil) com fundo neutro e icone enquanto carrega, quando falha
 * ou quando nao ha URL. O Coil mantem cache em disco, entao fotos ja vistas
 * continuam aparecendo offline.
 */
@Composable
fun RecipeImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    var state by remember(model) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (state !is AsyncImagePainter.State.Success) {
            Icon(
                imageVector = Icons.Outlined.Restaurant,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onState = { state = it },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** Linha "Categoria · Origem" mostrada abaixo do nome; ignora partes ausentes. */
fun metaLine(category: String?, area: String?): String? =
    listOfNotNull(category, area).takeIf { it.isNotEmpty() }?.joinToString(" · ")

@Composable
fun RecipeCard(
    recipe: RecipeSummary,
    isSaved: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val savedLabel = stringResource(R.string.saved_state)
    ElevatedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            // Um unico "item" para o leitor de tela: nome, detalhes e se esta salva.
            .semantics(mergeDescendants = true) { if (isSaved) stateDescription = savedLabel },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 88.dp)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecipeImage(
                model = recipe.thumbnailUrl,
                contentDescription = null, // decorativa: o nome da receita ja esta no texto
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                metaLine(recipe.category, recipe.area)?.let { meta ->
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isSaved) {
                Spacer(Modifier.size(8.dp))
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = null, // o estado ja e anunciado via stateDescription
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private val StarSize = 20.dp

/** Estrelas somente leitura. `rating == null` significa "sem nota". */
@Composable
fun RatingDisplay(rating: Int?, modifier: Modifier = Modifier) {
    val description = if (rating == null) {
        stringResource(R.string.rating_none)
    } else {
        stringResource(R.string.rating_value, rating)
    }
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(MAX_RATING) { index ->
            val filled = rating != null && index < rating
            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(StarSize),
            )
        }
    }
}

const val MAX_RATING = 5

/**
 * Seletor de nota. Cada estrela tem area de toque de 48 dp, e o conjunto e um grupo de
 * "radio buttons" para os leitores de tela ("3 estrelas, selecionado").
 */
@Composable
fun RatingInput(
    rating: Int,
    onRatingChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Row(modifier = modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        for (value in 1..MAX_RATING) {
            val selected = value == rating
            val filled = value <= rating
            val label = pluralStringResource(R.plurals.stars, value, value)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .selectable(
                        selected = selected,
                        onClick = { onRatingChange(value) },
                        role = Role.RadioButton,
                    )
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint = when {
                        filled -> MaterialTheme.colorScheme.primary
                        isError -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

/** Titulo de secao; marcado como "heading" para navegacao por titulos nos leitores de tela. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier
            .padding(top = 16.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}
