package com.magicbrush.brush

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RadialGradient
import java.util.WeakHashMap
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.abs
import kotlin.math.sin

private val backgrounds = intArrayOf(R.drawable.world_forest, R.drawable.world_desert, R.drawable.world_ice,
    R.drawable.world_lava, R.drawable.world_sky, R.drawable.world_magic)
private val ledges = intArrayOf(R.drawable.ledge_forest, R.drawable.ledge_desert, R.drawable.ledge_ice,
    R.drawable.ledge_lava, R.drawable.ledge_sky, R.drawable.ledge_magic)

class Art(context: Context) {
    private val resources = context.resources
    private val images = HashMap<Int, Bitmap>()
    fun image(id: Int): Bitmap = images.getOrPut(id) { BitmapFactory.decodeResource(resources, id) }
    fun backdrop(world: World) = image(backgrounds[world.ordinal])
    fun ledge(world: World) = image(ledges[world.ordinal])
    fun preload() {
        for (world in World.entries) {
            backdrop(world)
            ledge(world)
        }
        for (id in intArrayOf(R.drawable.hero_idle, R.drawable.hero_walk, R.drawable.hero_jump,
            R.drawable.portal, R.drawable.bee, R.drawable.star, R.drawable.trophy, R.drawable.paintbrush,
            R.drawable.hazard_rock, R.drawable.hazard_icicle, R.drawable.hazard_ember, R.drawable.hazard_orb,
            R.drawable.rune_plate, R.drawable.spring_flower)) image(id)
    }
}

@Composable
fun Background(art: Art, world: World, clock: FloatState, modifier: Modifier = Modifier, dim: Boolean = false) {
    val renderer = remember(art) { Scene(art) }
    Canvas(modifier) {
        drawIntoCanvas { renderer.backdrop(it.nativeCanvas, world, size.width, size.height, clock.floatValue, dim) }
    }
}

@Composable
fun Board(art: Art, game: Game, clock: FloatState, interpolation: FloatState, cursor: Cursor,
          showHints: Boolean, camera: BoardCamera, modifier: Modifier = Modifier) {
    val renderer = remember(art) { Scene(art) }
    Canvas(modifier) {
        val view = camera.measure(size.width, size.height, game, interpolation)
        drawIntoCanvas {
            val canvas = it.nativeCanvas
            val save = canvas.save()
            canvas.clipRect(0f, 0f, size.width, size.height)
            canvas.translate(view.x, view.y)
            canvas.scale(view.scale, view.scale)
            renderer.board(canvas, game, clock.floatValue, interpolation.floatValue, cursor, showHints,
                view.unprojectX(0f), view.unprojectX(size.width))
            canvas.restoreToCount(save)
        }
    }
}

class Scene(private val art: Art) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val committedPaths = WeakHashMap<Game.Body, Path>()
    private val portalHalo = RadialGradient(0f, 0f, 1f, 0xBBCA8BFF.toInt(), 0x00A663EC, Shader.TileMode.CLAMP)
    private val floorShadow = RadialGradient(0f, 0f, 1f, 0xAA1C0B26.toInt(), 0x001C0B26, Shader.TileMode.CLAMP)
    private var backgroundShade: LinearGradient? = null
    private var shadeHeight = -1f
    private var shadeDim = false
    private val hintInk = 0xFFF8F5FF.toInt()

    fun backdrop(c: AndroidCanvas, world: World, w: Float, h: Float, t: Float, dim: Boolean) {
        val bitmap = art.backdrop(world)
        val ratio = maxOf(w / bitmap.width, h / bitmap.height) * 1.075f
        val width = ratio * bitmap.width
        val height = ratio * bitmap.height
        val xf = (w - width) * .5f + sin(t * .22f) * (width - w) * .28f
        val yf = (h - height) * .44f
        bitmap(c, bitmap, xf, yf, width, height)
        if (shadeHeight != h || shadeDim != dim) {
            backgroundShade = LinearGradient(0f, 0f, 0f, h.coerceAtLeast(1f),
                if (dim) intArrayOf(0x28120B2B, 0x3B161537, 0xF4120C26.toInt())
                else intArrayOf(0xED100B26.toInt(), 0x361B163F, 0xD2110C27.toInt()),
                floatArrayOf(0f, .51f, 1f), Shader.TileMode.CLAMP)
            shadeHeight = h
            shadeDim = dim
        }
        paint.color = -1
        paint.shader = backgroundShade
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        for (i in 0 until 31) {
            val px = ((i * .6180339f + t * (.007f + (i % 3) * .003f)) % 1f) * w
            val py = (i * .38447f % 1f) * h + sin(t * .9f + i) * h * .026f
            val radius = (1.25f + i % 4 * .55f) * (w / 520f).coerceIn(.6f, 1.5f)
            val alpha = (48 + (sin(t * 2.2f + i) + 1f) * 76).toInt()
            solid((world.accent.toInt() and 0xFFFFFF) or (alpha shl 24))
            c.drawCircle(px, py, radius, paint)
        }
    }

    fun board(c: AndroidCanvas, game: Game, clock: Float, alpha: Float, cursor: Cursor, showHints: Boolean, left: Float, right: Float) {
        val level = game.level
        val accent = level.world.accent.toInt()
        val ledge = art.ledge(level.world)
        val sceneTime = game.previousTime + (game.time - game.previousTime) * alpha
        liquid(c, game, clock, left, right)
        for (g in level.terrain) {
            if (g.right < left || g.left > right) continue
            // Repeat ledge art at a fixed proportion on long platforms, with overlapping joins.
            val width = g.right - g.left
            val pieces = (width / 213f).toInt() + 1
            val step = width / pieces
            for (i in 0 until pieces) {
                val tileWidth = step + if (i < pieces - 1) 10f else 0f
                val tileX = g.left + step * i
                if (tileX + tileWidth < left || tileX > right) continue
                val tileHeight = tileWidth * ledge.height / ledge.width
                val topFraction = if (level.world == World.SKY) .43f else .34f
                bitmap(c, ledge, tileX, g.top - tileHeight * topFraction, tileWidth, tileHeight)
            }
            line(c, g.left + 2, g.top + 1, g.right - 2, g.top + 1, 2.1f, accent)
        }
        level.winds.forEachIndexed { j, wind ->
            for (i in 0..7) {
                val motion = (clock * (.5f + abs(wind.force) * .005f) + i * .17f) % 1f
                val px = wind.left + (if (wind.force > 0f) motion else 1f - motion) * (wind.right - wind.left)
                val py = 130f + 24f * (i % 6) + sin(clock * 3f + j + i) * 6f
                line(c, px - 18, py, px + 18, py, 2f, 0x77BCF3FF)
            }
        }
        level.lifts.forEachIndexed { i, lift ->
            line(c, lift.x, lift.y + 12, lift.x + lift.travel, lift.y + 12, 2f, 0x62FFEDC1)
            val ferry = game.ferryX(i, sceneTime)
            bitmap(c, ledge, ferry - 24f, lift.y - 11, 48f, 33f)
            line(c, ferry - 21f, lift.y, ferry + 21f, lift.y, 3.6f, accent)
        }
        for (spring in level.springs) {
            if (spring.x !in (left - 44f)..(right + 44f)) continue
            val flower = art.image(R.drawable.spring_flower)
            val flowerHeight = 48f + sin(clock * 4f) * 2f
            val flowerWidth = flowerHeight * flower.width / flower.height
            halo(c, spring.x, spring.y - 25, 22f, 24f, portalHalo, 127)
            bitmap(c, flower, spring.x - flowerWidth / 2f, spring.y - flowerHeight, flowerWidth, flowerHeight)
        }
        level.switches.forEachIndexed { i, rune ->
            val open = game.opened[i]
            val color = if (open) 0xFF95FFBF.toInt() else 0xFFF6ACE9.toInt()
            val base = level.terrain.firstOrNull { rune.gate in it.left..it.right }?.top ?: rune.y
            if (!open) {
                line(c, rune.gate, base - 61, rune.gate, base, 13f, 0x9938137A.toInt())
                line(c, rune.gate, base - 60, rune.gate, base, 3.2f, color)
                for (j in 0..4) star(c, rune.gate, base - j * 14f + sin(clock * 4 + j) * 3f, 4f, hintInk)
            }
            if (rune.weightOnly) {
                val plate = art.image(R.drawable.rune_plate)
                val plateDepth = 57f * plate.height / plate.width
                bitmap(c, plate, rune.x - 28.5f, rune.y - plateDepth * .52f, 57f, plateDepth)
                if (open) star(c, rune.x, rune.y - 8f, 6f, color)
            } else {
                solid(0xD655247D.toInt())
                c.drawCircle(rune.x, rune.y, 15f, paint)
                star(c, rune.x, rune.y, 12f, color)
            }
        }

        val exit = art.image(R.drawable.portal)
        val portalHeight = 100f + sin(clock * 3.4f) * 2.4f
        halo(c, 910f, level.portalY - 48f, 61f, 68f, portalHalo, (155 + sin(clock * 2.8f) * 30).toInt())
        bitmap(c, exit, 910 - portalHeight * exit.width / exit.height / 2, level.portalY - portalHeight,
            portalHeight * exit.width / exit.height, portalHeight)
        repeat(8) { i ->
            val dx = sin(clock * 1.8f + i * 1.63f) * 26f
            val dy = (clock * (12f + i) + i * 19f) % 79f
            star(c, 911 + dx, level.portalY - dy, 2f + (i % 3), 0xCDECD0FF.toInt())
        }
        for (shot in game.shots) {
            if (shot.state == 2 || shot.x < left - 45 || shot.x > right + 45) continue
            val height = if (shot.state == 0) sin(clock * 4f + shot.x) * 3f else 0f
            hazard(c, shot.seed.kind, shot.previousX + (shot.x - shot.previousX) * alpha,
                shot.previousY + (shot.y - shot.previousY) * alpha + height, clock, shot.state == 3)
        }
        for (body in game.ink) {
            if (body.edges.isEmpty()) continue
            val outline = if (body.shape.weight) trace(body, path) else
                committedPaths.getOrPut(body) { trace(body, Path()) }
            ink(c, outline, body.shape.weight)
        }
        val active = game.drawing.preview
        if (active.size > 1) {
            path.rewind()
            path.moveTo(active[0].x, active[0].y)
            for (i in 1 until active.lastIndex) {
                val a = active[i]
                val b = active[i + 1]
                path.quadTo(a.x, a.y, (a.x + b.x) * .5f, (a.y + b.y) * .5f)
            }
            path.lineTo(active.last().x, active.last().y)
            ink(c, path, false)
            val tip = active.last()
            repeat(7) { i ->
                val delta = (clock * 3f + i * .19f) % 1f
                star(c, tip.x + sin(i * 3.8f) * 19f * delta,
                    tip.y - delta * 24f, 6f * (1f - delta), hintInk)
            }
        }
        if (showHints && game.phase == Phase.DRAW && level.number <= 4) {
            for (hint in level.hints) {
                val h = hint.y - 15f
                solid(0xD72A1A45.toInt())
                rect.set(hint.x - 75, h - 16, hint.x + 75, h + 13)
                c.drawRoundRect(rect, 12f, 12f, paint)
                solid(hintInk)
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = 17f
                c.drawText(hint.message, hint.x, h + 4, paint)
                line(c, hint.x, h + 14, hint.x, h + 31, 1.8f, hintInk)
            }
        }
        val heroX = game.previousX + (game.x - game.previousX) * alpha
        val heroY = game.previousY + (game.y - game.previousY) * alpha
        val pose = when {
            game.phase == Phase.WIN || !game.grounded -> R.drawable.hero_jump
            game.phase == Phase.RUN && (clock * 9f).toInt() % 2 == 1 -> R.drawable.hero_walk
            else -> R.drawable.hero_idle
        }
        val hero = art.image(pose)
        val bob = if (game.grounded) kotlin.math.abs(sin(clock * (if (game.phase == Phase.RUN) 12f else 2.7f))) * 2.1f else 0f
        val heroHeight = 71f + bob
        val heroWidth = heroHeight * hero.width / hero.height
        if (game.grounded) halo(c, heroX + 2, heroY + 1, 32f, 8f, floorShadow, 215)
        bitmap(c, hero, heroX - heroWidth * .50f, heroY - heroHeight, heroWidth, heroHeight)
        if (cursor.visible) {
            val color = if (cursor.drawing) 0xFFFFDEA0.toInt() else 0xFFEEC9FF.toInt()
            line(c, cursor.x - 13, cursor.y, cursor.x + 13, cursor.y, 2.6f, color)
            line(c, cursor.x, cursor.y - 13, cursor.x, cursor.y + 13, 2.6f, color)
        }
    }

    private fun liquid(c: AndroidCanvas, game: Game, seconds: Float, left: Float, right: Float) {
        val kind = game.level.world
        if (kind != World.LAVA && kind != World.FOREST && kind != World.ICE) return
        val water = if (kind == World.LAVA) 0xCBDF4B18.toInt() else 0x9952CBE7.toInt()
        val highlight = if (kind == World.LAVA) 0xFFFFC23D.toInt() else 0xBCACF0FF.toInt()
        val pieces = game.level.terrain
        for (i in 0 until pieces.lastIndex) {
            val a = pieces[i]
            val b = pieces[i + 1]
            if (b.left < left || a.right > right) continue
            val lower = maxOf(a.top, b.top) + 40
            path.rewind()
            path.moveTo(a.right, lower)
            for (j in 1..14) {
                val px = a.right + (b.left - a.right) * j / 14f
                path.lineTo(px, lower + sin(px * .055f + seconds * 5f) * 3f)
            }
            solid(highlight)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            c.drawPath(path, paint)
            path.lineTo(b.left, lower + 19)
            path.lineTo(a.right, lower + 26)
            path.close()
            solid(water)
            c.drawPath(path, paint)
        }
    }

    private fun hazard(c: AndroidCanvas, kind: HazardKind, x: Float, y: Float, t: Float, bouncing: Boolean) {
        val sprite = art.image(when (kind) {
            HazardKind.BEE -> R.drawable.bee
            HazardKind.ICICLE -> R.drawable.hazard_icicle
            HazardKind.EMBER -> R.drawable.hazard_ember
            HazardKind.ORB -> R.drawable.hazard_orb
            HazardKind.ROCK -> R.drawable.hazard_rock
        })
        val height = when (kind) {
            HazardKind.BEE -> 34f + sin(t * 28f) * 2f
            HazardKind.ICICLE -> 41f
            else -> 31f
        }
        val width = height * sprite.width / sprite.height
        if (kind == HazardKind.ORB) halo(c, x, y, 30f, 30f, portalHalo, 142)
        val save = c.save()
        if (bouncing) c.rotate(sin(t * 13f) * 43f, x, y)
        bitmap(c, sprite, x - width / 2f, y - height / 2f, width, height)
        c.restoreToCount(save)
    }

    private fun trace(body: Game.Body, outline: Path): Path {
        outline.rewind()
        outline.moveTo(body.edges[0].a.x, body.edges[0].a.y)
        for (edge in body.edges) outline.lineTo(edge.b.x, edge.b.y)
        return outline
    }

    private fun halo(c: AndroidCanvas, x: Float, y: Float, rx: Float, ry: Float, shader: Shader, opacity: Int) {
        val save = c.save()
        c.translate(x, y)
        c.scale(rx, ry)
        solid(-1)
        paint.alpha = opacity
        paint.shader = shader
        c.drawCircle(0f, 0f, 1f, paint)
        paint.shader = null
        c.restoreToCount(save)
    }

    private fun ink(c: AndroidCanvas, outline: Path, weighted: Boolean) {
        val hues = if (weighted) weightedInk else trailInk
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        for (i in hues.indices) {
            solid(hues[i])
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = widths[i]
            c.drawPath(outline, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun star(c: AndroidCanvas, x: Float, y: Float, r: Float, color: Int) {
        path.rewind()
        path.moveTo(x, y - r)
        path.lineTo(x + r * .22f, y - r * .22f)
        path.lineTo(x + r, y)
        path.lineTo(x + r * .22f, y + r * .22f)
        path.lineTo(x, y + r)
        path.lineTo(x - r * .22f, y + r * .22f)
        path.lineTo(x - r, y)
        path.lineTo(x - r * .22f, y - r * .22f)
        path.close()
        solid(color)
        c.drawPath(path, paint)
    }

    private fun line(c: AndroidCanvas, ax: Float, ay: Float, bx: Float, by: Float, w: Float, color: Int) {
        solid(color)
        paint.strokeWidth = w
        paint.strokeCap = Paint.Cap.ROUND
        c.drawLine(ax, ay, bx, by, paint)
    }

    private fun bitmap(c: AndroidCanvas, b: Bitmap, x: Float, y: Float, w: Float, h: Float) {
        solid(-1)
        rect.set(x, y, x + w, y + h)
        c.drawBitmap(b, null, rect, paint)
    }

    private fun solid(color: Int) {
        paint.color = color
        paint.style = Paint.Style.FILL
    }

    companion object {
        private val trailInk = intArrayOf(0x424482FF, 0xC7955CFA.toInt(), 0xFF63EBFC.toInt(), 0xFFFFF4C1.toInt())
        private val weightedInk = intArrayOf(0x44AD4EFA, 0xD0CD56F5.toInt(), 0xFFF6B1FE.toInt(), 0xFFFFEAC0.toInt())
        private val widths = floatArrayOf(18f, 11f, 7f, 3f)
    }
}
