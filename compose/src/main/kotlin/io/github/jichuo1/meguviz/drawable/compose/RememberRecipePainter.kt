package io.github.jichuo1.meguviz.drawable.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import io.github.jichuo1.meguviz.drawable.model.DrawingRecipe

/**
 * Remembers a [RecipePainter] for [recipe] at the current density. A different recipe (by value),
 * density or [mirrorInRtl] creates a new painter and releases the previous one when the composition
 * forgets it; [state] changes update the existing painter.
 */
@Composable
public fun rememberRecipePainter(
    recipe: DrawingRecipe,
    state: DrawableState = DrawableState.Default,
    mirrorInRtl: Boolean = false,
): Painter {
    val density = LocalDensity.current.density
    val painter = remember(recipe, density, mirrorInRtl) { RecipePainter(recipe, density, mirrorInRtl, state) }
    SideEffect { painter.state = state }
    return painter
}
