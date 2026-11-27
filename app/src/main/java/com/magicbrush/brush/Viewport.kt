package com.magicbrush.brush

/** A uniform transform shared by the board, route map and input. */
data class Viewport(val x: Float, val y: Float, val scale: Float, val halfWidth: Float) {
    fun unprojectX(px: Float) = (px - x) / scale
    fun unprojectY(py: Float) = (py - y) / scale
    fun projectX(worldX: Float) = x + worldX * scale
    fun projectY(worldY: Float) = y + worldY * scale

    companion object {
        const val WIDTH = 960f
        const val HEIGHT = 424f
        private const val CLOSE_VIEW_WIDTH = 400f

        fun measure(width: Float, height: Float, focus: Float, overview: Boolean): Viewport {
            val w = width.coerceAtLeast(1f)
            val h = height.coerceAtLeast(1f)
            val scale = minOf(w / (if (overview) WIDTH else CLOSE_VIEW_WIDTH), h / HEIGHT)
            val half = w / (2f * scale)
            val center = if (half >= WIDTH / 2f) WIDTH / 2f else focus.coerceIn(half, WIDTH - half)
            return Viewport(w / 2f - center * scale, (h - HEIGHT * scale) * .54f, scale, half)
        }
    }
}
