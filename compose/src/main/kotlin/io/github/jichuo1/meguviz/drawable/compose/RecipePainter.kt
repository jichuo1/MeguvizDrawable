package io.github.jichuo1.meguviz.drawable.compose

import android.graphics.drawable.Drawable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.LayoutDirection
import io.github.jichuo1.meguviz.drawable.android.RecipeDrawable
import io.github.jichuo1.meguviz.drawable.model.DrawingRecipe
import kotlin.math.roundToInt

/**
 * Draws a [DrawingRecipe] with the shared Canvas backend ([RecipeDrawable]); Compose adds no second
 * renderer. One painter owns one drawable, so it must not be shared between unrelated owners.
 *
 * What invalidates what:
 * - **recipe, density**: the drawable fixes both for its lifetime, so a change needs a new painter;
 *   [rememberRecipePainter] keys on them and releases the old one.
 * - **state**: [state] updates the drawable and redraws only if its appearance changed.
 * - **size**: applied as drawable bounds at draw time; the drawable recomputes geometry only when
 *   the rounded pixel size changes.
 * - **layout direction**: with [mirrorInRtl], right-to-left draws the recipe flipped horizontally
 *   (corner radii and gradient direction included). The recipe itself is direction-agnostic, so
 *   nothing is cached per direction.
 *
 * Sizes are rounded to whole pixels because the backend works on integer bounds. Call [close] when
 * constructing a painter outside [rememberRecipePainter]; composition releases remembered painters.
 */
public class RecipePainter(
    public val recipe: DrawingRecipe,
    density: Float,
    public val mirrorInRtl: Boolean = false,
    state: DrawableState = DrawableState.Default,
) : Painter(), RememberObserver, AutoCloseable {
    private val drawable = RecipeDrawable(recipe, density)
    private var redrawTick by mutableIntStateOf(0)
    private var drawing = false
    private var closed = false

    /** True once released, either by composition forgetting the painter or by [close]. */
    public val isClosed: Boolean get() = closed

    private val callback = object : Drawable.Callback {
        override fun invalidateDrawable(who: Drawable) {
            // Bounds changes made while drawing invalidate the drawable; that must not schedule another draw.
            if (!drawing && !closed) redrawTick++
        }

        override fun scheduleDrawable(who: Drawable, what: Runnable, whenMillis: Long) = Unit

        override fun unscheduleDrawable(who: Drawable, what: Runnable) = Unit
    }

    init {
        drawable.setState(state.stateSet)
        drawable.callback = callback
    }

    /**
     * The state last applied; assigning an equal value is free. A closed painter ignores
     * assignments: a side effect from the composition that replaced it may still run.
     */
    public var state: DrawableState = state
        set(value) {
            if (closed) return
            if (field != value) {
                field = value
                if (drawable.setState(value.stateSet)) redrawTick++
            }
        }

    override val intrinsicSize: Size
        get() {
            val metrics = recipe.metrics()
            val width = metrics.intrinsicWidth()
            val height = metrics.intrinsicHeight()
            return if (width < 0 || height < 0) Size.Unspecified else Size(width.toFloat(), height.toFloat())
        }

    // Painter calls these from inside draw(); the draw in progress already uses the new values, so
    // the drawable's own invalidation must not schedule yet another one.
    override fun applyAlpha(alpha: Float): Boolean {
        val next = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        drawingDuring { drawable.alpha = next }
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        val next = colorFilter?.asAndroidColorFilter()
        drawingDuring { drawable.colorFilter = next }
        return true
    }

    private inline fun drawingDuring(action: () -> Unit) {
        val outer = drawing
        drawing = true
        try {
            action()
        } finally {
            drawing = outer
        }
    }

    override fun DrawScope.onDraw() {
        // Reading the tick subscribes this draw to external invalidation.
        @Suppress("UNUSED_VARIABLE") val subscription = redrawTick
        if (closed || !size.width.isFinite() || !size.height.isFinite()) return
        val width = size.width.roundToInt()
        val height = size.height.roundToInt()
        if (width <= 0 || height <= 0) return
        val mirror = mirrorInRtl && layoutDirection == LayoutDirection.Rtl
        drawingDuring {
            drawable.setBounds(0, 0, width, height)
            drawIntoCanvas { canvas ->
                val target = canvas.nativeCanvas
                val saved = target.save()
                try {
                    if (mirror) target.scale(-1f, 1f, width / 2f, height / 2f)
                    drawable.draw(target)
                } finally {
                    target.restoreToCount(saved)
                }
            }
        }
    }

    /** Detaches the drawable from this painter. Idempotent; drawing afterwards is a no-op. */
    override fun close() {
        if (closed) return
        closed = true
        drawable.callback = null
        drawable.setVisible(false, false)
    }

    override fun onRemembered(): Unit = Unit

    override fun onForgotten(): Unit = close()

    override fun onAbandoned(): Unit = close()
}
