package com.magicbrush.brush

enum class World(val title: String, val detail: String, val accent: Long) {
    FOREST("Green Forest", "Rivers & mossy ledges", 0xFF6DE8A5),
    DESERT("Golden Desert", "Sandstone ruins & falling rocks", 0xFFFFBC66),
    ICE("Frozen Kingdom", "Slippery ice & falling icicles", 0xFF82DFFF),
    LAVA("Lava Valley", "Molten trails & volcanic embers", 0xFFFF8B64),
    SKY("Sky Adventure", "Floating islands & wind", 0xFFC4B1FF),
    MAGIC("Magic Kingdom", "Ancient switches & moving gates", 0xFFFF9EEE)
}

enum class HazardKind { ROCK, ICICLE, EMBER, BEE, ORB }
data class Ground(val left: Float, val right: Float, val top: Float, val grip: Float)
data class Hazard(val x: Float, val y: Float, val kind: HazardKind, val drift: Float = 0f)
data class Switch(val x: Float, val y: Float, val gate: Float, val weightOnly: Boolean)
data class Lift(val x: Float, val y: Float, val travel: Float, val period: Float)
data class Wind(val left: Float, val right: Float, val force: Float)
data class Spring(val x: Float, val y: Float, val impulse: Float)
data class Hint(val x: Float, val y: Float, val message: String)

data class Level(
    val number: Int,
    val title: String,
    val world: World,
    val objective: String,
    val terrain: List<Ground>,
    val hazards: List<Hazard>,
    val switches: List<Switch>,
    val lifts: List<Lift>,
    val winds: List<Wind>,
    val springs: List<Spring>,
    val hints: List<Hint>,
    val portalY: Float,
    val ink: Float,
    val gold: Float,
    val silver: Float,
    val solution: List<List<Point>>
)

private class Course(val world: World) {
    var left = 8f
    var y = 337f
    val terrain = mutableListOf<Ground>()
    val hazards = mutableListOf<Hazard>()
    val switches = mutableListOf<Switch>()
    val lifts = mutableListOf<Lift>()
    val winds = mutableListOf<Wind>()
    val springs = mutableListOf<Spring>()
    val hints = mutableListOf<Hint>()
    val strokes = mutableListOf<List<Point>>()

    fun floor(right: Float, grip: Float = if (world == World.ICE) .21f else .83f) {
        if (right > left) terrain += Ground(left, right, y, grip)
        left = right
    }

    fun span(at: Int, width: Int, rise: Int = 0, arch: Int = 0) {
        val x = at.toFloat()
        floor(x)
        val end = x + width
        val nextY = y - rise
        val shape = mutableListOf(Point(x - 13, y - 2))
        if (arch > 0) {
            shape += Point(x + width * .28f, y - rise * .28f - arch)
            shape += Point(x + width * .72f, y - rise * .72f - arch)
        }
        shape += Point(end + 13, nextY - 2)
        strokes += shape
        hints += Hint(x + width / 2f, (y + nextY) / 2 - maxOf(24, arch + 12), when {
            rise > 0 -> "Ramp uphill"
            rise < 0 -> "Draw a landing"
            else -> "Bridge the gap"
        })
        left = end
        y = nextY
    }

    fun roof(x: Int, kind: HazardKind, pair: Boolean = false) {
        hazards += Hazard(x.toFloat(), y - 146, kind)
        if (pair) hazards += Hazard(x + 22f, y - 173, kind, -14f)
        strokes += listOf(Point(x - 42f, y - 62), Point(x - 4f, y - 85), Point(x + 48f, y - 69))
        hints += Hint(x.toFloat(), y - 105, "Draw a roof")
    }

    fun barrier(x: Int, kind: HazardKind) {
        hazards += Hazard(x.toFloat(), y - 24, kind, -210f)
        strokes += listOf(Point(x + 4f, y - 35f), Point(x + 4f, y - 7f))
        hints += Hint(x.toFloat(), y - 60, "Block the flyer")
    }

    fun gate(x: Int, weighted: Boolean) {
        val plateY = if (weighted) y else y - 58
        switches += Switch(x.toFloat(), plateY, x + 64f, weighted)
        strokes += if (weighted) listOf(
            Point(x - 11f, y - 52), Point(x + 11f, y - 52), Point(x + 11f, y - 30),
            Point(x - 11f, y - 30), Point(x - 11f, y - 52)
        ) else listOf(Point(x - 13f, plateY), Point(x + 13f, plateY))
        hints += Hint(x.toFloat(), plateY - 15, if (weighted) "Draw a closed loop above" else "Paint across the rune")
    }

    fun ferry(x: Int, width: Int) {
        floor(x.toFloat())
        lifts += Lift(x + 5f, y + 7f, width - 10f, 3.0f)
        left = (x + width).toFloat()
        hints += Hint(x + width / 2f, y - 26, "Ride the moving ferry")
    }

    fun jump(x: Int, width: Int) {
        floor(x.toFloat())
        springs += Spring(x - 14f, y, -350f)
        left = (x + width).toFloat()
        hints += Hint(x - 14f, y - 36, "Springboard")
    }

    fun wind(left: Int, right: Int, strength: Int) {
        winds += Wind(left.toFloat(), right.toFloat(), strength.toFloat())
    }

    fun build(index: Int, title: String, objective: String, margin: Int): Level {
        floor(952f)
        val length = strokes.sumOf { points -> points.zipWithNext { a, b -> a.distance(b).toDouble() }.sum() }.toFloat()
        return Level(index, title, world, objective, terrain, hazards, switches, lifts, winds, springs, hints,
            y, length + margin, length + 14f, length + margin * .66f, strokes)
    }
}

object Levels {
    val all = buildList {
        fun stage(world: World, title: String, objective: String, margin: Int = 120, plan: Course.() -> Unit) {
            add(Course(world).apply(plan).build(size + 1, title, objective, margin))
        }
        val f = World.FOREST
        stage(f, "First Stroke", "Paint a bridge across the brook.") { span(396, 98) }
        stage(f, "Up the Bank", "Join the banks with an upward ramp.") { span(365, 132, 65) }
        stage(f, "Fern Descent", "Paint a ramp down to the fern bed.") { span(404, 124, -66) }
        stage(f, "Twin Streams", "Save enough ink for both streams.") { span(270, 76); span(610, 102) }
        stage(f, "The Root Ferry", "Let the ferry carry you across. Press Go to start.") { ferry(421, 148) }
        stage(f, "Foxglove Leap", "Jump over the thorns using the springflower.") { jump(282, 94); span(605, 100, 47) }
        stage(f, "Hive Hollow", "Keep the bee from reaching the trail.") { barrier(431, HazardKind.BEE); span(627, 98) }
        stage(f, "Canopy Shelter", "Draw a roof over the falling stone.") { roof(350, HazardKind.ROCK); ferry(599, 144) }
        stage(f, "Waterfall Steps", "Climb to the falls and paint a safe route back down.") { span(225, 123, 57); span(467, 90); span(693, 118, -62) }
        stage(f, "An Emerald Crossing", "Use the ferry, block the bee, then bridge the last river.") { ferry(211, 140); barrier(505, HazardKind.BEE); span(685, 131, 43) }

        val d = World.DESERT
        stage(d, "Sandstone Shade", "Deflect the falling rocks with a roof.") { roof(427, HazardKind.ROCK, true) }
        stage(d, "The First Rune", "Paint through the floating rune to open the gate.") { gate(464, false) }
        stage(d, "The Stone Scale", "Paint a closed loop above the switch to weigh it down.") { gate(478, true) }
        stage(d, "Buried Stairway", "Climb the buried stair and activate the upper rune.") { span(280, 130, 80); gate(621, false) }
        stage(d, "Scarab Alley", "Block the flying scarab and protect the trail from rocks.") { barrier(286, HazardKind.ORB); roof(643, HazardKind.ROCK) }
        stage(d, "The Sunken Court", "Take a painted ramp down to the low ferry.") { span(214, 108, -35); ferry(543, 176) }
        stage(d, "Temple Foundations", "Use a falling ink weight between two ravines.") { span(216, 98); gate(441, true); span(668, 110, 55) }
        stage(d, "Echoing Ruins", "Open the rune gate and shield two sections of the path.") { roof(210, HazardKind.ROCK); gate(438, false); roof(747, HazardKind.ROCK, true) }
        stage(d, "The Sand Hoist", "Take the springflower, balance a weight, and ride the ferry.") { jump(188, 75); gate(400, true); ferry(650, 155) }
        stage(d, "The Sun Disc", "Climb through two gates and shelter the temple exit.") { gate(233, false); span(390, 126, 57); gate(600, true); roof(805, HazardKind.ROCK) }

        val i = World.ICE
        stage(i, "Meltwater", "Join the icy banks with an easy descent.") { span(394, 147, -44) }
        stage(i, "Icicle Gallery", "Divert the icicles before crossing the meltwater.") { roof(291, HazardKind.ICICLE, true); span(589, 106) }
        stage(i, "Frozen Lever", "Weigh down a switch and climb the glacier.") { gate(255, true); span(538, 153, 81) }
        stage(i, "Ice Floe", "Ride the drifting ice across the opening.") { ferry(321, 188); roof(713, HazardKind.ICICLE) }
        stage(i, "Crown of Ice", "Paint a sloped bridge to cross two wet ledges.") { span(225, 110, 58); span(479, 126, -73, 14); barrier(753, HazardKind.ORB) }
        stage(i, "Whiteout", "Cross the wind corridor on a painted bridge.") { wind(285, 684, -100); span(361, 212, 32); gate(734, false) }
        stage(i, "Frost Flowers", "Land the spring leap and activate the high rune.") { jump(251, 95); span(470, 129, 58); gate(712, false) }
        stage(i, "Cold Storage", "Catch icicles while two switches release the exit.") { gate(206, true); roof(444, HazardKind.ICICLE, true); gate(699, true) }
        stage(i, "Glacial Shelf", "Climb, ride a floe, and paint the last descent.") { span(206, 115, 71); ferry(457, 141); span(679, 113, -70) }
        stage(i, "The Crystal Crown", "Navigate the ice under falling crystals and a headwind.") { roof(184, HazardKind.ICICLE); wind(287, 678, -155); span(307, 199, 51); roof(622, HazardKind.ICICLE); gate(786, false) }

        val l = World.LAVA
        stage(l, "Red River", "Bridge the lava without touching it.") { span(350, 215, 18, 22) }
        stage(l, "Ember Eaves", "Guard against the embers and cross the lava.") { roof(259, HazardKind.EMBER, true); span(559, 163, 30) }
        stage(l, "Cinder Moths", "Block the two cinder moths at different heights.") { barrier(242, HazardKind.BEE); span(394, 146, 55); barrier(705, HazardKind.BEE) }
        stage(l, "Basalt Exchange", "Lower the basalt lock with a weight before the ferry.") { gate(265, true); ferry(555, 180) }
        stage(l, "Caldera Terrace", "Walk down into the caldera and shield both banks.") { roof(199, HazardKind.EMBER); span(362, 145, -61); roof(683, HazardKind.EMBER, true) }
        stage(l, "Forge Bellows", "Cross a hot updraft and open the forge gate.") { jump(203, 73); wind(314, 676, 180); span(368, 185, 42); gate(724, false) }
        stage(l, "Glass Causeway", "Spend ink carefully on three lava crossings.") { span(219, 98, 47); span(461, 112, -39); span(696, 114, 26) }
        stage(l, "Volcanic Lift", "Shelter the ferry approach and land above the lava.") { roof(200, HazardKind.ROCK, true); ferry(343, 162); span(636, 120, 67) }
        stage(l, "The Obsidian Vault", "Lower a weight, paint a rune, and block a cinder moth.") { gate(198, true); span(364, 130, 25); gate(553, false); barrier(805, HazardKind.BEE) }
        stage(l, "Heart of the Caldera", "Build two bridges with a shelter on the middle island.") { span(211, 132, 49); roof(488, HazardKind.EMBER, true); span(657, 147, -60) }

        val s = World.SKY
        stage(s, "First Flight", "Launch over the first gap and bridge to the last island.") { jump(249, 91); span(598, 156, 44) }
        stage(s, "Cloud Ferry", "Wait for two moving ferries.") { ferry(223, 175); ferry(603, 197) }
        stage(s, "Crossbreeze", "Build a continuous bridge through the headwind.") { wind(258, 725, -220); span(348, 251, 40) }
        stage(s, "Wind Chimes", "Paint through two airborne runes at different heights.") { gate(207, false); span(389, 122, 66); gate(717, false) }
        stage(s, "Thunder Roof", "Cover the trail before the ferry and after the descent.") { roof(192, HazardKind.ROCK); ferry(355, 135); span(574, 115, -27); roof(788, HazardKind.ORB) }
        stage(s, "Kite Strings", "Block a flyer, follow the tailwind, and land on the far island.") { barrier(235, HazardKind.BEE); wind(347, 714, 115); span(382, 228, -58) }
        stage(s, "Balanced Skies", "Drop ink weights onto both floating switches.") { gate(203, true); span(367, 137, 75); gate(666, true) }
        stage(s, "Nimbus Relay", "Link the springflower to a descending ramp and a ferry.") { jump(207, 82); span(373, 145, -38); ferry(665, 168) }
        stage(s, "Aerial Foundry", "Shield falling debris and reach the suspended rune.") { roof(196, HazardKind.ROCK, true); span(331, 144, 50); gate(550, false); roof(758, HazardKind.ORB, true) }
        stage(s, "Above the Tempest", "Use the ferry and two bridges to ride out changing winds.") { wind(208, 425, 100); span(232, 105, 46); ferry(441, 132); wind(625, 848, -180); span(665, 137, -44) }

        val m = World.MAGIC
        stage(m, "Palace Seal", "Open the rune gate and load the palace scale.") { gate(260, false); gate(627, true) }
        stage(m, "Starlit Aqueduct", "Cross a raised channel while shielding the path from orbs.") { span(224, 132, 63); roof(516, HazardKind.ORB, true); span(686, 119, -44) }
        stage(m, "Mirror Court", "Drop a counterweight and block two sentries.") { barrier(231, HazardKind.ORB); gate(456, true); barrier(743, HazardKind.ORB) }
        stage(m, "The Lantern Lift", "Open a gate, ride the ferry, and launch from the springflower.") { gate(198, false); ferry(390, 153); jump(694, 85) }
        stage(m, "Wishing Well", "Descend into the well, weigh the switch, and draw uphill.") { span(194, 114, -45); gate(434, true); span(640, 140, 87) }
        stage(m, "Celestial Siege", "Create roofs against three waves of falling orbs.") { roof(212, HazardKind.ORB, true); roof(475, HazardKind.ORB); roof(745, HazardKind.ORB, true) }
        stage(m, "Rift of Echoes", "Cross the rift against the wind and open two gates.") { gate(196, true); wind(291, 643, -185); span(340, 189, 42, 12); gate(716, false) }
        stage(m, "The Painted Tower", "Build three ramps and paint through the tower rune.") { span(179, 103, 43); span(358, 106, 45); span(544, 111, 45); gate(743, false) }
        stage(m, "The Crown Workshop", "Combine a painted rune, weighted switch, roof and ferry.") { gate(167, false); gate(340, true); roof(548, HazardKind.ORB); ferry(704, 131) }
        stage(m, "The Rainbow Gate", "Cross the royal moat, shield the path, weigh the lock and block the sentry.", 93) { span(184, 139, 48, 12); roof(421, HazardKind.ORB, true); gate(593, true); barrier(801, HazardKind.ORB) }
    }
}
