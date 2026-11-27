package com.magicbrush.brush

import org.junit.Assert.*
import org.junit.Test

class GameTest {
    @Test fun allSixtyPuzzlesHaveAPlayableThreeStarRoute() {
        assertEquals((1..60).toList(), Levels.all.map { it.number })
        assertEquals(List(6) { 10 }, Levels.all.groupBy { it.world }.values.map { it.size })
        val failures = Levels.all.mapNotNull { level ->
            val game = Game(level)
            level.solution.forEach { points ->
                points.forEach { game.draw(it.x, it.y) }
                game.finish()
            }
            game.start()
            repeat(4800) { game.step() }
            if (game.phase == Phase.WIN && game.stars == 3) null
            else "${level.number}. ${level.title} ${game.phase} x=${game.x} y=${game.y} ${game.failure} stars=${game.stars}"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun budgetUndoAndDegenerateStroke() {
        val game = Game(Levels.all.first())
        game.draw(30f, 100f)
        game.draw(34f, 100f)
        game.finish()
        assertEquals(0f, game.used, .001f)
        game.draw(30f, 100f)
        game.draw(900f, 100f)
        game.finish()
        assertEquals(0f, game.left, .001f)
        game.undo()
        assertEquals(game.level.ink, game.left, .001f)
    }

    @Test fun missingBridgeFailsAndMissingGateStaysLocked() {
        val gap = Game(Levels.all.first())
        gap.start()
        repeat(1400) { gap.step() }
        assertEquals(Phase.FAIL, gap.phase)
        val gate = Game(Levels.all[12])
        gate.start()
        repeat(2000) { gate.step() }
        assertEquals(Phase.FAIL, gate.phase)
        assertFalse(gate.opened[0])
    }
}
