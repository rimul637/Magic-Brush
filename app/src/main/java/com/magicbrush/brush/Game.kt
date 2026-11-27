package com.magicbrush.brush

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class Phase { DRAW, RUN, WIN, FAIL }
enum class Cue { CLICK, INK, COLLISION, STEP, PORTAL, WIN, FAIL, STAR, FINALE }

class Game(val level: Level, private val play: (Cue) -> Unit = {}) {
    class Body(val shape: InkShape) {
        var fall = 0f
        var velocity = 0f
        var angle = 0f
        var spin = if (shape.weight) .68f else 0f
        private val centerX = shape.points.map { it.x }.average().toFloat()
        private val centerY = shape.points.map { it.y }.average().toFloat()
        val edges = shape.edges.toMutableList()

        fun move() {
            val c = cos(angle)
            val s = sin(angle)
            fun rotate(p: Point): Point {
                val x = p.x - centerX
                val y = p.y - centerY
                return Point(centerX + x * c - y * s, centerY + y * c + x * s + fall)
            }
            shape.edges.forEachIndexed { i, e -> edges[i] = Edge(rotate(e.a), rotate(e.b)) }
        }
    }

    class Shot(val seed: Hazard) {
        var x = seed.x
        var y = seed.y
        var vx = seed.drift
        var vy = 0f
        var state = 0
        var safeFor = 0f
        val radius = if (seed.kind == HazardKind.ICICLE) 9f else 13f
    }

    val drawing = Drawing()
    val ink = ArrayList<Body>(12)
    val shots = level.hazards.map(::Shot)
    val opened = BooleanArray(level.switches.size)
    var phase = Phase.DRAW
        private set
    var x = 45f
        private set
    var y = level.terrain.first().top
        private set
    var previousX = x
        private set
    var previousY = y
        private set
    var time = 0f
        private set
    var used = 0f
        private set
    var grounded = true
        private set
    var failure = ""
        private set
    private var vy = 0f
    private var speed = 0f
    private var ride = -1
    private var footstep = 0f
    private var blocked = 0f

    val left: Float get() = (level.ink - used - drawing.length).coerceAtLeast(0f)
    val stars: Int get() = when {
        used <= level.gold -> 3
        used <= level.silver -> 2
        else -> 1
    }

    fun draw(x: Float, y: Float) {
        if (phase == Phase.DRAW || phase == Phase.RUN) drawing.add(x, y, level.ink - used)
    }

    fun finish() {
        val shape = drawing.finish() ?: return
        if ((phase != Phase.DRAW && phase != Phase.RUN) || used + shape.cost > level.ink + .001f || ink.size >= 24) return
        ink += Body(shape)
        used += shape.cost
        play(Cue.INK)
    }

    fun undo() {
        drawing.clear()
        if (phase != Phase.DRAW && phase != Phase.RUN) return
        val removed = ink.removeLastOrNull() ?: return
        used = (used - removed.shape.cost).coerceAtLeast(0f)
    }

    fun start() {
        if (phase == Phase.DRAW) {
            finish()
            phase = Phase.RUN
        }
    }

    fun ferryX(index: Int, t: Float = time): Float {
        val lift = level.lifts[index]
        return lift.x + lift.travel * (.5f - .5f * cos(t * 6.2831855f / lift.period))
    }

    fun step(dt: Float = STEP) {
        if (phase != Phase.RUN) return
        previousX = x
        previousY = y
        val lastTime = time
        time += dt
        fallWeights(dt)
        activateRunes()
        updateHero(dt, lastTime)
        updateShots(dt)
        if (y > 414f) fail("Your explorer fell. Paint a continuous path.")
        if (time > 36f) fail("The portal timed out. Check the path and the switches.")
        if (phase == Phase.RUN && x >= 911f && abs(y - level.portalY) < 22f) {
            phase = Phase.WIN
            drawing.clear()
            play(Cue.PORTAL)
            play(if (level.number == 60) Cue.FINALE else Cue.WIN)
        }
    }

    private fun fallWeights(dt: Float) {
        for (body in ink) {
            if (!body.shape.weight) continue
            val lowest = body.edges.maxOf { maxOf(it.a.y, it.b.y) }
            val center = (body.edges.minOf { minOf(it.a.x, it.b.x) } + body.edges.maxOf { maxOf(it.a.x, it.b.x) }) / 2f
            if (lowest > 490f) continue
            val floor = level.terrain.firstOrNull { center in it.left..it.right }?.top ?: 505f
            if (body.velocity == 0f && body.spin == 0f && abs(floor - lowest) < .1f) continue
            body.velocity += 610f * dt
            body.fall += body.velocity * dt
            body.angle += body.spin * dt
            body.move()
            val bottom = body.edges.maxOf { maxOf(it.a.y, it.b.y) }
            if (bottom >= floor) {
                body.fall -= bottom - floor
                if (body.velocity > 58f) {
                    body.velocity *= -.22f
                    body.spin *= .28f
                    play(Cue.COLLISION)
                } else {
                    body.velocity = 0f
                    body.spin = 0f
                }
                body.move()
            }
        }
    }

    private fun activateRunes() {
        level.switches.forEachIndexed { i, rune ->
            if (opened[i]) return@forEachIndexed
            for (body in ink) {
                if (rune.weightOnly && !body.shape.weight) continue
                if (body.edges.any { it.distanceSquared(rune.x, rune.y - if (rune.weightOnly) 3f else 0f) < 194f }) {
                    opened[i] = true
                    play(Cue.STAR)
                    break
                }
            }
        }
    }

    private fun updateHero(dt: Float, lastTime: Float) {
        val ground = level.terrain.firstOrNull { x in it.left..it.right }
        val target = if (ground?.grip == .21f && grounded) 143f else 127f
        speed += (target - speed) * (if (ground?.grip == .21f) 2.8f else 11f) * dt
        val breeze = if (grounded) 0f else level.winds.firstOrNull { x in it.left..it.right }?.force ?: 0f
        var nextX = x + (speed + breeze * .25f) * dt
        if (ride >= 0) {
            val ferry = ferryX(ride)
            val drift = ferry - ferryX(ride, lastTime)
            nextX = maxOf(x, x + drift)
            val landing = level.terrain.firstOrNull { it.left > level.lifts[ride].x }
            if (landing != null && ferry + 12f >= landing.left) {
                nextX = landing.left + 1f
                y = landing.top
                ride = -1
            }
        }

        val gate = level.switches.indices.firstOrNull { !opened[it] && x < level.switches[it].gate && nextX >= level.switches[it].gate - 13f }
        if (gate != null) {
            nextX = level.switches[gate].gate - 13f
            blocked += dt
            if (blocked > 3.2f) fail("This gate needs its matching switch.")
        } else blocked = 0f

        val spring = level.springs.firstOrNull { grounded && x <= it.x && nextX >= it.x }
        if (spring != null) {
            grounded = false
            vy = spring.impulse
            play(Cue.STEP)
        }

        if (ride < 0 && grounded && ground != null && nextX > ground.right) {
            val index = level.lifts.indexOfFirst { abs(it.x - ground.right) < 8f && abs(it.y - y) < 13f }
            if (index >= 0 && !hasInkSupport(nextX, y)) {
                if (abs(ferryX(index) - x) < 29f) {
                    ride = index
                    nextX = ferryX(index)
                    y = level.lifts[index].y
                } else nextX = ground.right - .2f
            }
        }
        x = nextX
        if (ride >= 0) return

        vy += 650f * dt
        val nextY = y + vy * dt
        val lower = if (grounded) y - 8f else y - .55f
        val upper = if (grounded) y + 9f else nextY + .4f
        var best = Float.POSITIVE_INFINITY
        if (vy >= 0f) {
            for (platform in level.terrain) {
                if (x in platform.left..platform.right && platform.top in lower..upper) best = minOf(best, platform.top)
            }
            for (body in ink) {
                if (body.shape.weight) continue
                for (edge in body.edges) {
                    if (abs(edge.b.y - edge.a.y) > abs(edge.b.x - edge.a.x) * 1.65f) continue
                    val hit = edge.height(x) ?: continue
                    if (hit in lower..upper) best = minOf(best, hit)
                }
            }
        }
        if (best.isFinite()) {
            y = best
            grounded = true
            vy = 0f
            if (x - footstep > 63f) {
                footstep = x
                play(Cue.STEP)
            }
        } else {
            y = nextY
            grounded = false
        }
    }

    private fun hasInkSupport(x: Float, height: Float): Boolean = ink.any { body ->
        !body.shape.weight && body.edges.any { abs((it.height(x) ?: 999f) - height) < 9f }
    }

    private fun updateShots(dt: Float) {
        for (shot in shots) {
            val flying = shot.seed.drift < -100f
            if (shot.state == 0) {
                val trigger = if (flying) 153f else 49f
                if (x < shot.seed.x - trigger) continue
                shot.state = 1
            }
            if (shot.state == 2) continue
            if (shot.state == 3) {
                shot.safeFor -= dt
                shot.vy += 610f * dt
                if (shot.safeFor <= 0f) {
                    shot.state = 2
                    continue
                }
            } else if (!flying) shot.vy += 610f * dt
            shot.x += shot.vx * dt
            shot.y += shot.vy * dt
            if (shot.y > 419 || shot.x < 9f) {
                shot.state = 2
                continue
            }
            if (shot.state == 3) continue
            val radiusSquared = (shot.radius + 3f) * (shot.radius + 3f)
            var contact: Edge? = null
            for (body in ink) {
                contact = body.edges.firstOrNull { it.distanceSquared(shot.x, shot.y) < radiusSquared }
                if (contact != null) break
            }
            if (contact != null && (flying || shot.vy > 0f)) {
                val tangent = contact.b.x - contact.a.x
                val slope = if (abs(tangent) < 1f) 0f else (contact.b.y - contact.a.y) / tangent
                shot.vx = if (flying) 117f else (slope * 125f + 33f).coerceIn(-145f, 145f)
                shot.vy = if (flying) -150f else -shot.vy * .42f
                shot.safeFor = .47f
                shot.state = 3
                play(Cue.COLLISION)
                continue
            }
            val dx = shot.x - x
            val dy = shot.y - (y - 19f)
            val touch = shot.radius + 12f
            if (dx * dx + dy * dy < touch * touch) fail("Draw a shield to keep hazards off the trail.")
        }
    }

    private fun fail(reason: String) {
        if (phase != Phase.RUN) return
        phase = Phase.FAIL
        failure = reason
        drawing.clear()
        play(Cue.FAIL)
    }

    companion object {
        const val STEP = 1f / 120f
    }
}
