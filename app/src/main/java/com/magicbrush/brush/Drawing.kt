package com.magicbrush.brush

import kotlin.math.hypot

data class Point(val x: Float, val y: Float) {
    fun distance(other: Point) = hypot(x - other.x, y - other.y)
}

data class Edge(val a: Point, val b: Point) {
    fun height(x: Float): Float? {
        if (x < minOf(a.x, b.x) || x > maxOf(a.x, b.x) || a.x == b.x) return null
        return a.y + (b.y - a.y) * (x - a.x) / (b.x - a.x)
    }

    fun distanceSquared(x: Float, y: Float): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val t = ((x - a.x) * dx + (y - a.y) * dy) / (dx * dx + dy * dy).coerceAtLeast(.001f)
        val ex = x - a.x - dx * t.coerceIn(0f, 1f)
        val ey = y - a.y - dy * t.coerceIn(0f, 1f)
        return ex * ex + ey * ey
    }
}

data class InkShape(val points: List<Point>, val edges: List<Edge>, val cost: Float, val weight: Boolean)

class Drawing {
    val preview = ArrayList<Point>(128)
    var length = 0f
        private set

    fun add(x: Float, y: Float, allowance: Float) {
        if (!x.isFinite() || !y.isFinite() || preview.size >= 220) return
        val next = Point(x.coerceIn(12f, 948f), y.coerceIn(28f, 395f))
        val last = preview.lastOrNull()
        if (last == null) {
            preview.add(next)
            return
        }
        val distance = last.distance(next)
        val available = allowance - length
        if (distance < 3f || available < 3f) return
        val fraction = (available / distance).coerceAtMost(1f)
        preview.add(Point(last.x + (next.x - last.x) * fraction, last.y + (next.y - last.y) * fraction))
        length += distance * fraction
    }

    fun clear() {
        preview.clear()
        length = 0f
    }

    fun finish(): InkShape? {
        if (preview.size < 2 || length < 15f) {
            clear()
            return null
        }
        val closed = preview.size >= 4 && length > 48f && preview.first().distance(preview.last()) < 13f
        val simplified = ArrayList<Point>()
        simplified.add(preview.first())
        simplify(0, preview.lastIndex, simplified)
        val cost = length
        clear()
        if (simplified.size > 72 || simplified.size < 2) return null
        val edges = simplified.zipWithNext(::Edge)
        val width = simplified.maxOf { it.x } - simplified.minOf { it.x }
        val height = simplified.maxOf { it.y } - simplified.minOf { it.y }
        return InkShape(simplified, edges, cost, closed && width >= 15f && height >= 15f)
    }

    private fun simplify(start: Int, end: Int, result: MutableList<Point>) {
        val edge = Edge(preview[start], preview[end])
        var farthest = 0
        var threshold = 5.76f
        for (index in start + 1 until end) {
            val point = preview[index]
            val distance = edge.distanceSquared(point.x, point.y)
            if (distance > threshold) {
                threshold = distance
                farthest = index
            }
        }
        if (farthest > 0) {
            simplify(start, farthest, result)
            simplify(farthest, end, result)
        } else result.add(preview[end])
    }
}
