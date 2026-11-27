package com.magicbrush.brush

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportTest {
    @Test fun overviewExposesEveryReferenceStrokeOnNarrowAndWideScreens() {
        val displays = listOf(280f to 578f, 360f to 640f, 1024f to 590f, 960f to 220f, 1280f to 300f)
        for ((width, height) in displays) {
            val view = Viewport.measure(width, height, 45f, overview = true)
            for (level in Levels.all) for (stroke in level.solution) for (p in stroke) {
                val x = view.projectX(p.x)
                val y = view.projectY(p.y)
                assertTrue("Level ${level.number}: $p unavailable in $width × $height", x in 0f..width && y in 0f..height)
                assertEquals(p.x, view.unprojectX(x), .001f)
                assertEquals(p.y, view.unprojectY(y), .001f)
            }
        }
    }

    @Test fun portraitCanReachBothEdgesAndUsesEqualScaleForBothAxes() {
        val left = Viewport.measure(360f, 600f, 0f, overview = false)
        val right = Viewport.measure(360f, 600f, 960f, overview = false)
        assertEquals(0f, left.unprojectX(0f), .001f)
        assertEquals(960f, right.unprojectX(360f), .001f)
        assertTrue(left.scale > Viewport.measure(360f, 600f, 0f, overview = true).scale)
        assertEquals(right.projectX(700f) - right.projectX(600f),
            right.projectY(200f) - right.projectY(100f), .001f)
    }
}
