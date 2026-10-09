package com.cadernoreceitas.parcial.model

import android.os.Parcel
import androidx.core.os.ParcelCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cadernoreceitas.parcial.data.MockRecipes
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** O Intent leva a receita como Parcelable: tudo precisa sobreviver ao "empacotamento". */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RecipeParcelTest {

    /** Escreve a receita em um Parcel e le de volta, como o Android faz ao enviar um Intent. */
    private fun roundTrip(recipe: Recipe): Recipe {
        val parcel = Parcel.obtain()
        try {
            parcel.writeParcelable(recipe, 0)
            parcel.setDataPosition(0)
            return ParcelCompat.readParcelable(parcel, Recipe::class.java.classLoader, Recipe::class.java)!!
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun `todas as receitas sobrevivem a ida e volta por Parcel`() {
        MockRecipes.all.forEach { recipe ->
            assertThat(roundTrip(recipe)).isEqualTo(recipe)
        }
    }

    @Test
    fun `valores nulos e favorito sobrevivem ao Parcel`() {
        val recipe = MockRecipes.all.first { it.tip == null && it.videoUrl == null }
            .copy(isFavorite = true)

        val restored = roundTrip(recipe)

        assertThat(restored.tip).isNull()
        assertThat(restored.videoUrl).isNull()
        assertThat(restored.isFavorite).isTrue()
        assertThat(restored.ingredients.any { it.quantity == null }).isTrue()
    }
}
