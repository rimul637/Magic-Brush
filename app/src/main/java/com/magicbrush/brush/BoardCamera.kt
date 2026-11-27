package com.magicbrush.brush

import androidx.compose.runtime.FloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class BoardCamera(initialFocus: Float) {
    var overview by mutableStateOf(false)
    var tracking by mutableStateOf(false)
    var focus by mutableFloatStateOf(initialFocus)
    var frozenFocus by mutableStateOf<Float?>(null)
    var width = 1f
        private set
    var height = 1f
        private set

    fun resize(width: Float, height: Float) {
        this.width = width
        this.height = height
    }

    fun measure(width: Float, height: Float, game: Game, interpolation: FloatState): Viewport {
        val center = frozenFocus ?: if (tracking) {
            game.previousX + (game.x - game.previousX) * interpolation.floatValue + 86f
        } else focus
        return Viewport.measure(width, height, center, overview)
    }

    fun select(position: Float) {
        overview = false
        tracking = false
        focus = position.coerceIn(0f, Viewport.WIDTH)
    }

    fun keepVisible(x: Float) {
        val margin = 28f
        val view = Viewport.measure(width, height, focus, overview)
        val px = view.projectX(x)
        if (px < margin || px > width - margin) select(x)
    }
}
