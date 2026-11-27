package com.magicbrush.brush

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin

private enum class Screen { MENU, WORLDS, LEVELS, GAME }
private enum class Popup { PAUSE, SETTINGS, RESTART, EXIT, HOME, WON, LOST, FINALE }
private val LocalControls = compositionLocalOf { true }
private val Cream = Color(0xFFFFF3E2)
private val Gold = Color(0xFFFFD687)
private val Amethyst = Color(0xFF26153E)

class Cursor {
    var x = 432f
    var y = 280f
    var visible = false
    var drawing = false
}

class MainActivity : ComponentActivity() {
    private var active by mutableStateOf(false)
    private lateinit var sound: Audio

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        sound = Audio(this)
        val progress = ProgressRepository(applicationContext)
        setContent { MagicBrush(progress, sound, active, ::finish) }
    }

    override fun onResume() {
        super.onResume()
        active = true
        immerse()
    }

    override fun onPause() {
        active = false
        super.onPause()
    }

    override fun onDestroy() {
        sound.release()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immerse()
    }

    private fun immerse() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun MagicBrush(repository: ProgressRepository, audio: Audio, active: Boolean, exit: () -> Unit) {
    val progress by repository.state.collectAsState(initial = Progress())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var art by remember { mutableStateOf<Art?>(null) }
    var screen by remember { mutableStateOf(Screen.MENU) }
    var world by remember { mutableStateOf(World.FOREST) }
    var popup by remember { mutableStateOf<Popup?>(null) }
    var settingsFromPause by remember { mutableStateOf(false) }
    var game by remember { mutableStateOf<Game?>(null) }
    var ink by remember { mutableFloatStateOf(1f) }
    var playing by remember { mutableStateOf(false) }
    var unlocked by remember { mutableIntStateOf(1) }
    var totalStars by remember { mutableIntStateOf(0) }
    val clock = remember { mutableFloatStateOf(0f) }
    val interpolation = remember { mutableFloatStateOf(1f) }
    val selected = game

    fun updateInk() {
        val session = game ?: return
        ink = session.left / session.level.ink
    }

    fun open(number: Int) {
        val level = Levels.all[number - 1]
        world = level.world
        game = Game(level, audio::play)
        playing = false
        ink = 1f
        popup = null
        interpolation.floatValue = 1f
        screen = Screen.GAME
    }

    fun home() {
        screen = Screen.MENU
        popup = null
        game = null
    }

    fun settings(paused: Boolean) {
        settingsFromPause = paused
        popup = Popup.SETTINGS
    }

    fun start() {
        game?.start()
        updateInk()
        playing = true
    }

    fun undo() {
        game?.undo()
        updateInk()
    }

    LaunchedEffect(Unit) { art = withContext(Dispatchers.IO) { Art(context.applicationContext).also { it.preload() } } }
    LaunchedEffect(active, progress.music, progress.effects, screen) {
        audio.update(active, progress.music, progress.effects, screen == Screen.GAME)
        if (!active && screen == Screen.GAME && popup == null) popup = Popup.PAUSE
    }
    LaunchedEffect(active, selected, popup) {
        if (!active) return@LaunchedEffect
        var last = 0L
        var accumulator = 0f
        while (true) {
            withFrameNanos { now ->
                val elapsed = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceIn(0f, .05f)
                last = now
                clock.floatValue = (clock.floatValue + elapsed) % 3600f
                if (selected != null && popup == null && selected.phase == Phase.RUN) {
                    accumulator += elapsed
                    while (accumulator >= Game.STEP && selected.phase == Phase.RUN) {
                        selected.step()
                        accumulator -= Game.STEP
                    }
                    interpolation.floatValue = (accumulator / Game.STEP).coerceIn(0f, 1f)
                    if (selected.phase == Phase.WIN) {
                        interpolation.floatValue = 1f
                        val level = selected.level
                        unlocked = maxOf(progress.unlocked, (level.number + 1).coerceAtMost(60))
                        totalStars = progress.total + (selected.stars - progress.stars[level.number - 1]).coerceAtLeast(0)
                        scope.launch { repository.complete(level.number, selected.stars, selected.used) }
                        popup = if (level.number == 60) Popup.FINALE else Popup.WON
                    } else if (selected.phase == Phase.FAIL) {
                        interpolation.floatValue = 1f
                        popup = Popup.LOST
                    }
                }
            }
        }
    }

    BackHandler {
        popup = when (popup) {
            Popup.SETTINGS -> if (settingsFromPause) Popup.PAUSE else null
            Popup.RESTART, Popup.HOME -> Popup.PAUSE
            Popup.PAUSE, Popup.EXIT -> null
            Popup.WON, Popup.FINALE, Popup.LOST -> { home(); null }
            null -> when (screen) {
                Screen.MENU -> Popup.EXIT
                Screen.GAME -> Popup.PAUSE
                Screen.WORLDS -> { screen = Screen.MENU; null }
                Screen.LEVELS -> { screen = Screen.WORLDS; null }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF161436))) {
        val ready = art
        if (ready == null) {
            Label("MAGIC BRUSH", Modifier.align(Alignment.Center), 32, Gold, FontWeight.Black)
            return@Box
        }
        Background(ready, if (screen == Screen.MENU || screen == Screen.WORLDS) World.MAGIC else world,
            clock, Modifier.fillMaxSize(), dim = screen != Screen.GAME)
        CompositionLocalProvider(LocalControls provides (popup == null)) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)
                .windowInsetsPadding(WindowInsets.waterfall)) {
                AnimatedContent(screen, transitionSpec = { fadeIn(tween(190)) togetherWith fadeOut(tween(150)) },
                    label = "screen") { destination ->
                    when (destination) {
                        Screen.MENU -> Menu(clock, audio::play,
                            { open(maxOf(progress.unlocked, unlocked)) }, { screen = Screen.WORLDS },
                            { settings(false) }, { popup = Popup.EXIT })
                        Screen.WORLDS -> Worlds(progress, { screen = Screen.MENU }, { world = it; screen = Screen.LEVELS })
                        Screen.LEVELS -> LevelsScreen(world, progress, maxOf(unlocked, progress.unlocked),
                            { screen = Screen.WORLDS }, ::open)
                        Screen.GAME -> if (selected != null) key(selected) {
                            GameScreen(ready, selected, clock, interpolation, popup == null, ink, playing,
                                ::updateInk, ::start, ::undo, { popup = Popup.PAUSE }, { popup = Popup.RESTART })
                        }
                    }
                }
            }
        }
        val visiblePopup = popup
        var displayed by remember { mutableStateOf<Popup?>(null) }
        SideEffect { if (visiblePopup != null) displayed = visiblePopup }
        AnimatedVisibility(visible = visiblePopup != null, enter = fadeIn(tween(160)),
            exit = fadeOut(tween(130)), modifier = Modifier.fillMaxSize()) {
            val current = visiblePopup ?: displayed ?: return@AnimatedVisibility
            PopupFrame(title = when (current) {
                Popup.PAUSE -> "Paused"
                Popup.SETTINGS -> "Settings"
                Popup.RESTART -> "Restart level?"
                Popup.EXIT -> "Exit Magic Brush?"
                Popup.HOME -> "Return to the menu?"
                Popup.WON -> "Portal reached!"
                Popup.LOST -> "Try another route"
                Popup.FINALE -> "Kingdom restored!"
            }, clock = clock, finale = current == Popup.FINALE) {
                when (current) {
                    Popup.PAUSE -> {
                        Gem("RESUME", action = { popup = null })
                        Gem("RESTART", secondary = true, action = { popup = Popup.RESTART })
                        Gem("SETTINGS", secondary = true, action = { settings(true) })
                        Gem("HOME", secondary = true, action = { popup = Popup.HOME })
                    }
                    Popup.SETTINGS -> {
                        Gem("MUSIC  ${if (progress.music) "ON" else "OFF"}", action = { scope.launch { repository.music(!progress.music) } })
                        Gem("SOUND FX  ${if (progress.effects) "ON" else "OFF"}", action = { scope.launch { repository.effects(!progress.effects) } })
                        Gem("BACK", secondary = true, action = { popup = if (settingsFromPause) Popup.PAUSE else null })
                    }
                    Popup.RESTART -> {
                        Label("This attempt and its ink strokes will be reset.", size = 14)
                        Gem("RESTART", action = { selected?.let { open(it.level.number) } })
                        Gem("CANCEL", secondary = true, action = { popup = if (playing) Popup.PAUSE else null })
                    }
                    Popup.EXIT -> {
                        Gem("EXIT", action = exit)
                        Gem("CANCEL", secondary = true, action = { popup = null })
                    }
                    Popup.HOME -> {
                        Label("Your current attempt will end.", size = 14)
                        Gem("HOME", action = ::home)
                        Gem("CANCEL", secondary = true, action = { popup = Popup.PAUSE })
                    }
                    Popup.WON, Popup.FINALE -> if (selected != null) {
                        ResultStars(selected.stars)
                        if (current == Popup.FINALE) {
                            Image(painterResource(R.drawable.trophy), "Golden trophy", Modifier.size(80.dp))
                            Label("$totalStars / 180 stars", size = 19, color = Gold, weight = FontWeight.Bold)
                        }
                        Label("Ink used  ${selected.used.toInt()} / ${selected.level.ink.toInt()}", size = 14)
                        if (current == Popup.WON) Gem("NEXT LEVEL", action = { open(selected.level.number + 1) })
                        Gem("REPLAY", secondary = current == Popup.WON, action = { open(selected.level.number) })
                        Gem("HOME", secondary = true, action = ::home)
                    }
                    Popup.LOST -> {
                        Label(selected?.failure.orEmpty(), size = 15)
                        Gem("RETRY", action = { selected?.let { open(it.level.number) } })
                        Gem("HOME", secondary = true, action = ::home)
                    }
                }
            }
        }
    }
}

@Composable
private fun Menu(clock: FloatState, playAudio: (Cue) -> Unit, play: () -> Unit, levels: () -> Unit,
                 settings: () -> Unit, exit: () -> Unit) {
    val buttons: @Composable ColumnScope.() -> Unit = {
        Gem("PLAY", action = { playAudio(Cue.CLICK); play() })
        Gem("LEVELS", secondary = true, action = { playAudio(Cue.CLICK); levels() })
        Gem("SETTINGS", secondary = true, action = { playAudio(Cue.CLICK); settings() })
        Gem("EXIT", secondary = true, action = { playAudio(Cue.CLICK); exit() })
    }
    val crest: @Composable ColumnScope.() -> Unit = {
        Image(painterResource(R.drawable.paintbrush), null, Modifier.height(158.dp).width(160.dp)
            .graphicsLayer {
                translationY = sin(clock.floatValue * 1.83f) * 8.dp.toPx()
                rotationZ = sin(clock.floatValue * 1.28f) * 7f
            })
        Label("Magic Brush", size = 49, color = Gold, weight = FontWeight.Black, serif = true)
        Label("RAINBOW BRIDGE ADVENTURE", size = 12, color = Cream, weight = FontWeight.Bold)
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        val availableHeight = maxHeight
        if (maxWidth >= 600.dp || maxWidth > availableHeight) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, content = crest)
                Column(Modifier.weight(1f).heightIn(max = availableHeight).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), content = buttons)
            }
        } else {
            Column(Modifier.fillMaxHeight().widthIn(max = 420.dp).align(Alignment.Center)
                .verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(11.dp, Alignment.CenterVertically)) {
                crest()
                Spacer(Modifier.height(11.dp))
                buttons()
            }
        }
    }
}

@Composable
private fun Worlds(progress: Progress, back: () -> Unit, select: (World) -> Unit) {
    val worldArt = intArrayOf(R.drawable.world_forest, R.drawable.world_desert, R.drawable.world_ice,
        R.drawable.world_lava, R.drawable.world_sky, R.drawable.world_magic)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header("Choose a world", "${progress.total} / 180 stars", back)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (world in World.entries) {
                val count = progress.stars.drop(world.ordinal * 10).take(10).count { it > 0 }
                val form = RoundedCornerShape(17.dp)
                val source = remember { MutableInteractionSource() }
                val focused by source.collectIsFocusedAsState()
                Box(Modifier.fillMaxWidth().height(88.dp).clip(form)
                    .border(if (focused) 3.dp else 1.dp, Color(world.accent), form)
                    .clickable(interactionSource = source, indication = null, enabled = LocalControls.current, role = Role.Button) { select(world) }) {
                    Image(painterResource(worldArt[world.ordinal]), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xF015102C), Color(0x2615102C)))))
                    Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp)) {
                        Label(world.title, size = 23, color = Color(world.accent), weight = FontWeight.Black, serif = true)
                        Label(world.detail, size = 13)
                    }
                    Label("$count/10", Modifier.align(Alignment.TopEnd).padding(9.dp).background(Amethyst, RoundedCornerShape(10.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp), 13, Cream, FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LevelsScreen(world: World, progress: Progress, unlocked: Int, back: () -> Unit, select: (Int) -> Unit) {
    val levels = Levels.all.filter { it.world == world }
    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val columns = if (maxWidth > 570.dp) 5 else 2
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Header(world.title, "Levels ${levels.first().number}–${levels.last().number}", back)
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (group in levels.chunked(columns)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        for (level in group) {
                            val count = progress.stars[level.number - 1]
                            Gem(if (level.number <= unlocked) "${level.number}  ${level.title}" else "${level.number}  Locked",
                                Modifier.weight(1f), secondary = level.number != unlocked, enabled = level.number <= unlocked,
                                note = if (count > 0) "$count / 3 stars" else if (level.number == unlocked) "Current level" else null,
                                action = { select(level.number) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, detail: String, back: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Gem("BACK", Modifier.width(96.dp), small = true, secondary = true, action = back)
        Column {
            Label(title, size = 26, color = Gold, weight = FontWeight.Black, serif = true, align = TextAlign.Start)
            Label(detail, size = 13, align = TextAlign.Start)
        }
    }
}

@Composable
private fun GameScreen(art: Art, game: Game, clock: FloatState, interpolation: FloatState, interactive: Boolean,
                       ink: Float, playing: Boolean, updateInk: () -> Unit, go: () -> Unit, undo: () -> Unit,
                       pause: () -> Unit, restart: () -> Unit) {
    val focused = remember { FocusRequester() }
    val cursor = remember { Cursor() }
    val currentUpdate by rememberUpdatedState(updateInk)
    val inkWidth by animateFloatAsState(ink, tween(155), label = "ink")
    LaunchedEffect(interactive) {
        if (interactive) focused.requestFocus()
        else {
            cursor.drawing = false
            game.drawing.clear()
            currentUpdate()
        }
    }
    Column(Modifier.fillMaxSize().focusRequester(focused).onPreviewKeyEvent { event ->
        if (!interactive || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> {
                cursor.visible = true
                when (event.key) {
                    Key.DirectionLeft -> cursor.x = (cursor.x - 7).coerceAtLeast(12f)
                    Key.DirectionRight -> cursor.x = (cursor.x + 7).coerceAtMost(948f)
                    Key.DirectionUp -> cursor.y = (cursor.y - 7).coerceAtLeast(28f)
                    else -> cursor.y = (cursor.y + 7).coerceAtMost(395f)
                }
                if (cursor.drawing) game.draw(cursor.x, cursor.y)
                currentUpdate()
            }
            Key.Spacebar -> if (cursor.visible) {
                if (event.nativeKeyEvent.repeatCount > 0) return@onPreviewKeyEvent true
                cursor.drawing = !cursor.drawing
                if (cursor.drawing) game.draw(cursor.x, cursor.y) else game.finish()
                currentUpdate()
            } else return@onPreviewKeyEvent false
            Key.G -> if (!playing) go()
            Key.P, Key.Escape -> pause()
            Key.Z -> undo()
            Key.R -> restart()
            else -> return@onPreviewKeyEvent false
        }
        true
    }.focusable(enabled = interactive)) {
        Row(Modifier.fillMaxWidth().padding(start = 13.dp, end = 13.dp, top = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).background(Color(0xCB211633), RoundedCornerShape(15.dp)).padding(10.dp)) {
                Label("LEVEL ${game.level.number}  ·  ${game.level.title}", size = 15, color = Gold,
                    weight = FontWeight.Black, align = TextAlign.Start)
                Spacer(Modifier.height(5.dp))
                Label("INK  ${(ink * 100).toInt()}%", size = 10, color = Cream, weight = FontWeight.Bold,
                    align = TextAlign.Start)
                Spacer(Modifier.height(3.dp))
                val shape = RoundedCornerShape(12.dp)
                Box(Modifier.fillMaxWidth().height(9.dp).clip(shape).background(Color(0xFF100D22))
                    .semantics { contentDescription = "Ink remaining, ${(ink * 100).toInt()} percent" }) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(inkWidth.coerceIn(0f, 1f))
                        .background(Brush.horizontalGradient(listOf(Color(0xFFC56EFF), Color(0xFF72F7EA), Gold))))
                }
            }
            Gem("PAUSE", Modifier.width(94.dp), small = true, secondary = true, action = pause)
        }
        Label(game.level.objective, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
            .background(Color(0xAD211633), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
            size = 14)
        Board(art, game, clock, interpolation, cursor, true, Modifier.fillMaxWidth().weight(1f)
            .pointerInput(game, interactive) {
                if (!interactive) return@pointerInput
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    val view = Viewport.measure(size.width.toFloat(), size.height.toFloat())
                    val x = view.unprojectX(first.position.x)
                    val y = view.unprojectY(first.position.y)
                    if (x !in 0f..960f || y !in 13f..408f) return@awaitEachGesture
                    cursor.visible = false
                    cursor.drawing = false
                    game.drawing.clear()
                    game.draw(x, y)
                    first.consume()
                    currentUpdate()
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == first.id } ?: break
                            for (sample in change.historical) {
                                game.draw(view.unprojectX(sample.position.x), view.unprojectY(sample.position.y))
                            }
                            game.draw(view.unprojectX(change.position.x), view.unprojectY(change.position.y))
                            change.consume()
                            currentUpdate()
                            if (!change.pressed) {
                                game.finish()
                                currentUpdate()
                                break
                            }
                        } while (true)
                    } finally {
                        game.drawing.clear()
                        currentUpdate()
                    }
                }
            })
        Row(Modifier.widthIn(max = 520.dp).align(Alignment.CenterHorizontally).fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Gem("UNDO", Modifier.weight(1f), small = true, secondary = true, action = undo)
            Gem(if (playing) "RUNNING" else "GO", Modifier.weight(1.2f), small = true, enabled = !playing, action = go)
            Gem("RESTART", Modifier.weight(1f), small = true, secondary = true, action = restart)
        }
    }
}

@Composable
private fun PopupFrame(title: String, clock: FloatState, finale: Boolean, body: @Composable ColumnScope.() -> Unit) {
    val noRipple = remember { MutableInteractionSource() }
    var entered by remember(title) { mutableStateOf(false) }
    val scale by animateFloatAsState(if (entered) 1f else .89f, tween(210), label = "popup")
    LaunchedEffect(title) { entered = true }
    Box(Modifier.fillMaxSize().background(Color(0xB10A0717))
        .clickable(noRipple, indication = null, onClick = {})
        .windowInsetsPadding(WindowInsets.displayCutout).padding(15.dp)) {
        if (finale) Canvas(Modifier.fillMaxSize()) {
            val t = clock.floatValue
            repeat(65) { i ->
                val x = (i * .618034f % 1f) * size.width + sin(t * 2f + i) * 32f
                val y = ((t * (.105f + (i % 7) * .015f) + i * .38197f) % 1f) * size.height
                drawLine(if (i % 3 == 0) Gold else if (i % 2 == 0) Color(0xFFECA4FF) else Color(0xFF7AF9D7),
                    Offset(x, y), Offset(x + 7f, y + 12f), 3f)
            }
        }
        val form = RoundedCornerShape(27.dp)
        Column(Modifier.align(Alignment.Center).widthIn(max = 380.dp).fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }.shadow(19.dp, form).clip(form)
            .background(Brush.verticalGradient(listOf(Color(0xFF603968), Amethyst, Color(0xFF1D153A))))
            .border(2.dp, Brush.verticalGradient(listOf(Color(0xFFFFF4B4), Color(0xFFA35A28), Gold)), form)
            .verticalScroll(rememberScrollState()).padding(horizontal = 19.dp, vertical = 17.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Label(title, size = 28, color = Gold, weight = FontWeight.Black, serif = true)
            body()
        }
    }
}

@Composable
private fun ResultStars(count: Int) {
    var revealed by remember(count) { mutableIntStateOf(0) }
    LaunchedEffect(count) {
        for (i in 1..count) {
            kotlinx.coroutines.delay(120)
            revealed = i
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 1..3) {
            val scale by animateFloatAsState(if (i <= revealed) 1f else .56f, tween(220), label = "star")
            Image(painterResource(R.drawable.star), if (i <= count) "Earned star" else "Unfilled star",
                Modifier.size(if (i == 2) 57.dp else 49.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (i <= revealed) 1f else .24f })
        }
    }
}

@Composable
private fun Gem(label: String, modifier: Modifier = Modifier, small: Boolean = false, secondary: Boolean = false,
                enabled: Boolean = true, note: String? = null, action: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val sink by animateFloatAsState(if (pressed) .954f else 1f, tween(110), label = "press")
    val shape = RoundedCornerShape(if (small) 13.dp else 18.dp)
    val eligible = enabled && LocalControls.current
    val trim = if (focused) listOf(Color.White, Color(0xFFF5E799), Color.White)
        else listOf(Color(0xFFFFF4AC), Color(0xFFC6863D), Color(0xFF7D3E2C), Color(0xFFFFD17A))
    val face = if (!enabled) listOf(Color(0xFF575269), Color(0xFF34314F), Color(0xFF272439))
        else if (secondary) listOf(Color(0xFF936DCD), Color(0xFF5B368C), Color(0xFF382356))
        else listOf(Color(0xFF36BBAB), Color(0xFF168677), Color(0xFF105956))
    Box(modifier.widthIn(max = 315.dp).fillMaxWidth().heightIn(min = if (small) 45.dp else 52.dp)
        .graphicsLayer { scaleX = sink; scaleY = sink }.shadow(7.dp, shape).clip(shape)
        .background(Brush.verticalGradient(trim))
        .clickable(interactionSource = interaction, indication = null, enabled = eligible, role = Role.Button, onClick = action)
        .padding(start = 3.dp, end = 3.dp, top = 2.dp, bottom = if (pressed) 3.dp else 6.dp)) {
        Column(Modifier.align(Alignment.Center).fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(face))
            .border(1.dp, Color(0xB8FAEAD0), shape).padding(horizontal = 7.dp, vertical = if (small) 9.dp else 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Label(label, size = if (small) 12 else 16, color = if (enabled) Cream else Color(0xFFE6DDE8), weight = FontWeight.Black)
            if (note != null) Label(note, size = 11, color = Gold, weight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Label(value: String, modifier: Modifier = Modifier, size: Int = 17, color: Color = Cream,
                  weight: FontWeight = FontWeight.Medium, serif: Boolean = false,
                  align: TextAlign = TextAlign.Center) {
    BasicText(value, modifier, style = TextStyle(color = color, fontSize = size.sp,
        lineHeight = (size * 1.14f).sp, fontFamily = if (serif) FontFamily.Serif else FontFamily.SansSerif,
        fontWeight = weight, textAlign = align))
}
