package com.yuno

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import java.lang.ref.WeakReference
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.yuno.backdrop.backdrops.layerBackdrop
import com.yuno.backdrop.backdrops.rememberLayerBackdrop
import com.yuno.floatingtabbar.rememberFloatingTabBarScrollConnection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import com.yuno.icons.YunoIcons
import com.yuno.icons.SfIcons

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The system fades the splash out after the first frame, so the mark
            // stays up after the page is already drawn. Removing it is the whole
            // exit: nothing is held, and the page shows as soon as it is ready.
            splashScreen.setOnExitAnimationListener { it.remove() }
        }
        AppLanguage.refresh()
        // One window at a time (one player, one video texture): a newer one closes the one before.
        // That's a bubble opened while the app is open, or the app opened from Home while it sits
        // in a bubble (the bubble then goes away). Its task goes too, so Recents keeps no stale card;
        // PlaybackService.onTaskRemoved sees this window still open and plays on.
        current?.get()?.takeIf { it !== this && !it.isFinishing }?.finishAndRemoveTask()
        current = WeakReference(this)
        enableEdgeToEdge()
        // The media notification (and its lock-screen controls) needs this on Android 13+.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        NewVideos.takeVideo(intent)?.let { NewVideos.opening = it }
        setContent { YunoTheme { YunoApp() } }
    }

    /** A new-video notification tapped while this window is open (singleTop). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        NewVideos.takeVideo(intent)?.let { NewVideos.opening = it }
    }

    override fun onStart() {
        super.onStart()
        AppUpdate.checkIfDue()
    }

    override fun onResume() {
        super.onResume()
        AppUpdate.onWindowResumed(this)
    }

    override fun onDestroy() {
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    companion object {
        private var current: WeakReference<MainActivity>? = null

        /** The task of the app's open window; null when there's none (closed, or on its way out). */
        fun openTaskId(): Int? = current?.get()?.takeUnless { it.isFinishing || it.isDestroyed }?.taskId
    }
}

/**
 * The four tabs. Each glyph fills a different share of its box - [YunoIcons.Home]
 * covers about half of it, while the SF Symbols and font-drawn
 * [YunoIcons.TabLibrary] cover three quarters - so [iconScale] shrinks the
 * other three to Home's optical area instead of letting them run to the edges.
 */
enum class Tab(val label: String, val icon: ImageVector, val iconScale: Float = 1f) {
    Home("Home", YunoIcons.Home),
    Shorts("Shorts", SfIcons.Shorts, 0.85f),
    Subscriptions("Subscriptions", SfIcons.Subscriptions, 1.08f),
    Library("Library", YunoIcons.TabLibrary, 0.84f),
}

/** Backdrop the nav samples for liquid glass. */
internal val LocalAppHaze = staticCompositionLocalOf<HazeState?> { null }

// The mini-player floats 8 dp above the nav pill (NavBar.kt), capped at 368 dp, centered.
private val MiniWidth = 368.dp
private val MiniGap = 8.dp

@Composable
fun YunoApp() {
    val c = MaterialTheme.colorScheme
    var onboardingDone by remember { mutableStateOf(Onboarding.completed) }
    var signingIn by remember { mutableStateOf(false) }
    // First launch: finish setup before the tabs / player / feeds start.
    if (!onboardingDone) {
        OnboardingScreen(
            onFinished = { onboardingDone = true },
            onSignIn = { signingIn = true },
        )
        if (signingIn) {
            SignInScreen(
                onDone = { signingIn = false },
                onCancel = { signingIn = false },
            )
        }
        return
    }
    val player = rememberPlayerState()
    val scope = rememberCoroutineScope()
    val search = remember { SearchState(scope) }
    val appContext = LocalContext.current.applicationContext
    val lib = remember { LibraryData(scope, appContext) }
    LaunchedEffect(Account.signedIn) { if (Account.signedIn) lib.warmUp() }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var openPlaylist by remember { mutableStateOf<PlaylistRef?>(null) }
    var openChannel by remember { mutableStateOf<String?>(null) }
    var openSearch by remember { mutableStateOf(false) }
    LaunchedEffect(openSearch) { if (openSearch) search.reset() }
    // Kept after closing so the page can slide out with its content.
    var channelState by remember { mutableStateOf<ChannelState?>(null) }
    var playlistHold by remember { mutableStateOf<PlaylistRef?>(null) }
    SideEffect { if (openPlaylist != null) playlistHold = openPlaylist }
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    // Shorts: its own feed and players; ‹ (and Back) return to the tab it was opened from.
    // One per process (YunoApplication), so its bank keeps filling with no window open.
    val shorts = (LocalContext.current.applicationContext as YunoApplication).shorts
    val host = LocalContext.current as Activity
    val windowLayout = rememberWindowLayout(host)
    val wide = windowLayout.wide
    DisposableEffect(shorts) { onDispose { if (!host.isChangingConfigurations) shorts.leave() } }
    var shortsFrom by rememberSaveable { mutableStateOf(Tab.Home) }
    var shortsBackTo by rememberSaveable { mutableStateOf<String?>(null) }
    var shortsListOnTab by rememberSaveable { mutableStateOf(false) }
    var playlistOverChannel by remember { mutableStateOf(false) }
    LaunchedEffect(NewVideos.opening) {
        val video = NewVideos.opening ?: return@LaunchedEffect
        NewVideos.opening = null
        settingsOpen = false
        if (tab == Tab.Shorts) tab = shortsFrom
        player.open(video)
    }
    // Bumped when the already-selected tab is tapped; that tab's list scrolls to the top.
    val scrollToTop = remember { mutableStateMapOf<Tab, Int>() }
    val reduced = rememberReducedMotion()
    val playerDock = remember { PlayerDock() }
    var showNowPlaying by remember { mutableStateOf(false) }
    val playerSheetMotion = remember {
        PlayerSheetMotion(
            scope = scope,
            dock = playerDock,
            shown = { showNowPlaying },
            setShown = { up ->
                showNowPlaying = up
                player.expanded = up
            },
        )
    }
    val activeDock = playerDock
    val playerPull = playerSheetMotion.takeIf { !reduced }
    // Known before the sheet exists, so a pull starts at the bottom instead of
    // flashing the page fully open and then clearing it.
    playerSheetMotion.windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    LaunchedEffect(player.current?.id) { HeldFrame.image = null }

    // The screen stays on while a video plays
    val rootView = LocalView.current
    DisposableEffect(player.isPlaying, shorts.playing) {
        rootView.keepScreenOn = player.isPlaying || shorts.playing
        onDispose { rootView.keepScreenOn = false }
    }

    // Opening the player drops any text focus (the search box behind it), so the keyboard
    // can't come back over the video.
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(player.expanded) {
        if (player.expanded) {
            openSearch = false
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    // Off screen: after ~2 s of background *playback*, audio-only; quick switches keep video loaded.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> if (!host.isChangingConfigurations) {
                    player.setBackground(true); shorts.setForeground(false)
                }
                androidx.lifecycle.Lifecycle.Event.ON_START -> { player.setBackground(false); shorts.setForeground(true) }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // floatingBarInsets + NAV_BAR_LIFT: the system bar, never less than a
    // gesture inset, then 6 dp more.
    val stackBottom = navBottom.coerceAtLeast(GestureBarFloor) + NavBarLift
    var optionsFor by remember { mutableStateOf<VideoItem?>(null) }
    var optionsHeld by remember { mutableStateOf<HeldItem?>(null) }
    // On Shorts the app's video waits, paused, out of sight (no mini-player).
    val glassOn = LiquidGlassSetting.enabled && isGlassSupported()
    val hasMini = player.current != null && tab != Tab.Shorts && !glassOn
    val contentPadding = if (wide) {
        PaddingValues(bottom = stackBottom + (if (hasMini) MiniHeight + MiniGap else 0.dp) + 16.dp)
    } else {
        PaddingValues(bottom = if (player.current != null && tab != Tab.Shorts) ListBottomPlaying else ListBottomIdle)
    }
    val railStart = WindowInsets.safeDrawing.only(WindowInsetsSides.Start).asPaddingValues()
        .calculateStartPadding(LayoutDirection.Ltr)
    val showRail = wide && tab != Tab.Shorts
    val pagesStart = if (showRail) RailWidth + railStart else 0.dp

    // Fullscreen is whenever the open player is shown in landscape (rotation or button).
    val activity = LocalContext.current as Activity
    val orientation = rememberOrientationController(activity as ComponentActivity)
    val playerOpen = showNowPlaying && player.current != null
    val config = LocalConfiguration.current
    val windowLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val landscape = remember(windowLandscape, config.screenWidthDp, config.screenHeightDp, config.densityDpi, orientation.multiWindow) {
        if (!orientation.multiWindow && activity.onSquareScreen()) activity.turnedSideways() else windowLandscape
    }
    // Audio only has no picture to go fullscreen with: the open player stays portrait.
    orientation.Bind(playerOpen && !AudioOnly.enabled, landscape)
    val pip = remember(activity) { PipController(activity) }
    // Closing the picture-in-picture window pauses (expanding it just comes back).
    pip.Bind(onClosed = { player.controller?.pause() })
    pip.AutoEnter(AutoPip.on && player.isPlaying && player.current != null && !AudioOnly.enabled, player.videoAspect)
    // The PiP window can be any shape: hold whatever was showing when it opened.
    val fullscreenBeforePip = remember { mutableStateOf(false) }
    val tabletop = windowLayout.tabletopY != null && playerOpen && !pip.active
    val showFullscreen = !tabletop && if (pip.active) fullscreenBeforePip.value else orientation.showsFullscreen(playerOpen, landscape)
    SideEffect { if (!pip.active) fullscreenBeforePip.value = showFullscreen }
    LaunchedEffect(showFullscreen) { player.fullscreen = showFullscreen }
    SystemBarsHidden(showFullscreen || tabletop)
    // A vertical video's fullscreen stays upright (Fullscreen.kt).
    fun toggleFullscreen() = if (showFullscreen) orientation.exitFullscreen() else orientation.enterFullscreen(vertical = player.videoAspect < 1f)

    fun showChannel(url: String) {
        player.expanded = false
        showNowPlaying = false
        if (channelState?.url != url) channelState = ChannelState(url, scope)
        playlistOverChannel = false
        openChannel = url
    }

    fun openShorts(list: ShortsList) {
        shortsBackTo = openChannel
        shortsListOnTab = tab == Tab.Shorts
        openChannel = null
        shorts.openList(list)
        if (tab != Tab.Shorts) { shortsFrom = tab; tab = Tab.Shorts }
    }

    fun leaveShorts() {
        val back = shortsBackTo
        shortsBackTo = null
        when {
            back == null -> tab = shortsFrom
            !shortsListOnTab -> { tab = shortsFrom; showChannel(back) }
            else -> { showChannel(back); shorts.cover(true); shorts.closeList() }
        }
    }

    BackHandler(enabled = showNowPlaying) {
        if (reduced) showNowPlaying = false else playerSheetMotion.close()
    }
    BackHandler(enabled = !showNowPlaying && openSearch) { openSearch = false }
    BackHandler(enabled = !showNowPlaying && tab != Tab.Home && openPlaylist == null && openChannel == null && !openSearch) {
        if (tab == Tab.Shorts) leaveShorts() else tab = Tab.Home
    }
    BackHandler(enabled = !showNowPlaying && openPlaylist != null) { openPlaylist = null }
    BackHandler(enabled = signingIn) { signingIn = false }
    BackHandler(enabled = !showNowPlaying && openChannel != null && !(playlistOverChannel && openPlaylist != null)) { openChannel = null }
    BackHandler(enabled = tab == Tab.Shorts && shorts.commentsOpen) { shorts.commentsOpen = false }
    // Registered last so it wins: in fullscreen, Back returns to the portrait player first.
    BackHandler(enabled = showFullscreen) { orientation.exitFullscreen() }

    // The open player's controls live on the video (VideoView.kt); the nav pill stays the tabs.

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current
    val settingsAway by animateFloatAsState(
        if (settingsOpen) 1f else 0f,
        if (reduced) snap() else Motion.MoodiiSpatial(),
        label = "settingsAway",
    )

    // Shorts (Shorts.kt) play on their own players: the app's video pauses on the way in and
    // carries on, from the same spot in the mini-player, on the way out (Derek, 2026-10-28).
    // One progress turns the tab bar into the glass bar: 620 ms in, 460 ms out, both moving at once.
    val shortsMorph = remember { androidx.compose.animation.core.Animatable(if (tab == Tab.Shorts) 1f else 0f) }
    var resumeMain by remember { mutableStateOf(false) }
    LaunchedEffect(tab) {
        if (tab == Tab.Shorts) {
            resumeMain = resumeMain || player.isPlaying
            player.controller?.pause()
            shorts.enter(mainPlayerActive = player.current != null)
        } else if (shorts.active) {
            shortsBackTo = null
            shorts.leave()
            if (resumeMain) player.controller?.play()
            resumeMain = false
        }
    }
    // Shorts turned off (account sheet) while it was the tab: back to where it was opened from.
    LaunchedEffect(ShortsSetting.enabled) { if (!ShortsSetting.enabled && tab == Tab.Shorts) tab = shortsFrom }
    LaunchedEffect(tab == Tab.Shorts) {
        if (tab == Tab.Shorts) shortsMorph.animateTo(1f, if (reduced) snap() else tween(ShortsEnterMs, easing = androidx.compose.animation.core.LinearEasing))
        else shortsMorph.animateTo(0f, if (reduced) snap() else tween(ShortsExitMs, easing = ShortsExitEasing))
    }
    SideEffect {
        StatusBarOverVideo.shorts = tab == Tab.Shorts && openChannel == null
        shorts.onMessage = { player.actionMessage = it }
        shorts.mainBusy = { player.isBuffering }
    }

    LaunchedEffect(player.expanded, player.current?.id) {
        if (player.current == null) {
            player.expanded = false
            showNowPlaying = false
        } else if (player.expanded && !showNowPlaying) {
            if (reduced) showNowPlaying = true else playerSheetMotion.open()
        } else if (!player.expanded && showNowPlaying && !playerSheetMotion.holding) {
            if (reduced) showNowPlaying = false else playerSheetMotion.close()
        }
    }

    // A channel opened from Shorts sits over the short, which waits paused.
    SideEffect { shorts.cover(tab == Tab.Shorts && openChannel != null) }
    BackHandler(enabled = tab == Tab.Shorts && shorts.channelMenu) { shorts.channelMenu = false }

    val hazeState = remember { HazeState() }
    val navScroll = rememberFloatingTabBarScrollConnection()
    // Captures while the live surface is still up, so the fold scales that
    // frame instead of the TextureView. See GlassNav.
    SideEffect { navScroll.onBeforeToggle = { SharedVideoTexture.captureFrame() } }
    // Shorts is a vertical swipe, not a list: that swipe must not fold the glass bar.
    LaunchedEffect(tab) { if (tab == Tab.Shorts) navScroll.expand() }
    val windowBackground = c.background
    val paintBackdrop: ContentDrawScope.() -> Unit = remember(windowBackground) {
        {
            drawRect(windowBackground)
            drawContent()
        }
    }
    val appBackdrop = rememberLayerBackdrop(onDraw = paintBackdrop)
    val topChrome = remember { TopChromeHost() }
    // One recording of everything on the page, so the glass matches the screen
    // during a transition instead of holding the page that just closed. The
    // top buttons are drawn by TopChromeLayer, outside this box.
    val pageSample = Modifier
        .hazeSource(hazeState)
        .then(if (glassOn) Modifier.layerBackdrop(appBackdrop) else Modifier)
        .then(if (glassOn && tab != Tab.Shorts) Modifier.nestedScroll(navScroll) else Modifier)
    BoxWithConstraints(Modifier.fillMaxSize().background(c.background)) {
        val stageFloor = maxOf(StageMinAspect, constraints.maxWidth / (constraints.maxHeight * StageMaxHeightShare))
        SideEffect { player.stageFloor = stageFloor }
        val sharedSurface = remember(player) {
            androidx.compose.runtime.movableContentOf { m: Modifier -> VideoSurface(player.controller, m) }
        }
        CompositionLocalProvider(
            LocalVideoSurface provides if (pip.active) NoVideoSurface else sharedSurface,
            LocalPictureInPicture provides pip,
            LocalVideoMenu provides { video ->
                optionsHeld = LongPressOrigin.consume()
                optionsFor = video
            },
            LocalChannelOpener provides { url -> showChannel(url) },
            LocalWindowLayout provides windowLayout,
            LocalAppHaze provides hazeState,
            LocalLiquidGlassEnabled provides glassOn,
            LocalAppBackdrop provides appBackdrop,
            LocalTopChrome provides topChrome,
        ) {
        Box(Modifier.fillMaxSize().then(pageSample).padding(start = pagesStart)) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                if (reduced) fadeIn(snap()) togetherWith fadeOut(snap())
                else (fadeIn(tween(Motion.PageEnterMs, easing = Motion.Emphasized)) +
                    slideInVertically(tween(Motion.PageRiseMs, easing = Motion.Emphasized)) { (it / 35).coerceIn(8, 24) }) togetherWith
                    fadeOut(tween(Motion.PageExitMs))
            },
            modifier = Modifier.fillMaxSize(),
            label = "tabs",
        ) { t ->
            when (t) {
                Tab.Home -> HomeScreen(
                    lib, contentPadding, onOpen = player::open, onSearch = { openSearch = true },
                    onSignIn = { signingIn = true }, onAccount = { settingsOpen = true },
                    scrollToTop = scrollToTop[Tab.Home] ?: 0,
                )
                Tab.Subscriptions -> SubscriptionsScreen(
                    lib, contentPadding, onOpen = player::open, onSearch = { openSearch = true },
                    onSignIn = { signingIn = true }, onAccount = { settingsOpen = true },
                    scrollToTop = scrollToTop[Tab.Subscriptions] ?: 0,
                )
                Tab.Shorts -> ShortsScreen(
                    shorts,
                    progress = { shortsMorph.value },
                    navClearance = stackBottom + NavHeight,
                    overlayBottom = stackBottom + NavHeight + 28.dp,
                    onChannel = ::showChannel,
                )
                Tab.Library -> LibraryScreen(
                    lib, contentPadding, onOpen = player::open, onOpenPlaylist = { openPlaylist = it },
                    onSearch = { openSearch = true },
                    onSignIn = { signingIn = true }, onAccount = { settingsOpen = true },
                    scrollToTop = scrollToTop[Tab.Library] ?: 0,
                )
            }
        }

        AnimatedVisibility(
            visible = openSearch,
            enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
            exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
        ) {
            SearchScreen(
                search,
                contentPadding,
                onBack = { openSearch = false },
                onOpen = player::open,
                barShown = openSearch,
            )
        }

        // A playlist opens as a page over the tabs (slides in from the right).
        AnimatedVisibility(
            visible = openPlaylist != null,
            enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
            exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
        ) {
            val pl = openPlaylist ?: playlistHold
            val feed = remember(pl?.id) { pl?.let { lib.playlistFeed(it.id) } }
            if (pl != null && feed != null) {
                PlaylistScreen(
                    pl, feed, contentPadding,
                    onBack = { openPlaylist = null },
                    onOpen = player::open,
                    channelAvatarSeed = lib.channels.associate { it.id to it.avatar },
                    barShown = openPlaylist != null,
                )
            }
        }

        // A channel opens the same way, over everything below the mini-player.
        AnimatedVisibility(
            visible = openChannel != null,
            enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
            exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
        ) {
            channelState?.let { ch ->
                ChannelScreen(
                    ch, contentPadding, onBack = { openChannel = null },
                    onOpen = { v -> if (tab == Tab.Shorts) tab = shortsFrom; player.open(v) },
                    onOpenShorts = ::openShorts,
                    onOpenPlaylist = { ref ->
                        playlistOverChannel = openChannel != null
                        openPlaylist = ref
                    },
                    onMessage = { player.actionMessage = it },
                )
            }
        }
        } // page backdrop (tabs + overlays)

        if (showRail) {
            NavRail(
                selected = tab,
                tabs = Tab.entries.filter { it != Tab.Shorts || ShortsSetting.enabled },
                onSelect = { t ->
                    player.expanded = false
                    showNowPlaying = false
                    when {
                        t == tab && openSearch -> openSearch = false
                        t == tab && openChannel != null -> openChannel = null
                        t == tab && openPlaylist != null -> openPlaylist = null
                        t == tab -> scrollToTop[t] = (scrollToTop[t] ?: 0) + 1
                        else -> {
                            if (t == Tab.Shorts) shortsFrom = tab
                            openPlaylist = null
                            openChannel = null
                            openSearch = false
                            tab = t
                        }
                    }
                },
                modifier = Modifier.graphicsLayer { alpha = 1f - settingsAway },
            )
        }

        // Stays put while the player opens and closes. The player is its own window,
        // so it covers these the way it covers the tab bar; removing them for
        // showNowPlaying made them vanish at the start of the expand and return
        // only after the collapse.
        TopChromeLayer(topChrome, Modifier.align(Alignment.TopCenter).fillMaxWidth())

        // Ledgii's bottom vignette
        val pageColor = c.background
        val vignette = remember(pageColor) { vignetteBrush(pageColor, opaqueAtTop = false) }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(navBottom + 130.dp)
                .graphicsLayer { alpha = 1f - shortsMorph.value }
                .drawBehind { drawRect(vignette) },
        )
        BottomGestureGuard(Modifier.align(Alignment.BottomCenter))

        val playerSheetContext = remember {
            android.view.ContextThemeWrapper(activity, R.style.Theme_Yuno_PlayerSheet)
        }
        CompositionLocalProvider(LocalContext provides playerSheetContext) {
            NowPlayingSheet(
                visible = showNowPlaying,
                fullscreen = showFullscreen,
                player = player,
                motion = playerSheetMotion,
                dock = playerDock,
                reducedMotion = reduced,
                onOpen = player::open,
                onDismiss = { showNowPlaying = false },
                onFullscreen = { toggleFullscreen() },
                bottomClearance = stackBottom + NavHeight + PageGutter,
                heldVideo = optionsFor.takeIf { showNowPlaying },
                heldOrigin = optionsHeld,
                onDismissHeld = { optionsFor = null },
            )
        }

        // The nav pill steps down out of the way of the comments panel (its composer sits there).
        val navAside by animateFloatAsState(
            if ((player.sheet.isPanel && playerOpen) || shorts.commentsOpen) 1f else 0f,
            if (reduced) snap() else tween(280, easing = Motion.Emphasized), label = "navAside",
        )
        val onSelectTab: (Tab) -> Unit = { selected ->
            player.expanded = false
            when {
                selected == tab && openSearch -> openSearch = false
                selected == tab && openChannel != null -> openChannel = null
                selected == tab && openPlaylist != null -> openPlaylist = null
                selected == tab -> scrollToTop[selected] = (scrollToTop[selected] ?: 0) + 1
                else -> {
                    if (selected == Tab.Shorts) shortsFrom = tab
                    openPlaylist = null
                    openChannel = null
                    openSearch = false
                    tab = selected
                }
            }
        }
        val shownTabs = Tab.entries.filter { it != Tab.Shorts || ShortsSetting.enabled }
        val glassChrome = glassOn && !wide
        if (glassChrome) {
            GlassNavBar(
                tabs = shownTabs,
                selected = tab,
                onTabSelected = onSelectTab,
                scrollConnection = navScroll,
                player = player,
                onExpandPlayer = { player.expanded = true },
                dock = activeDock,
                pull = playerPull,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = FloatingBarMaxWidth)
                    .fillMaxWidth()
                    .padding(start = NavBarGutter, end = NavBarGutter, bottom = stackBottom)
                    .graphicsLayer {
                        val away = maxOf(navAside, settingsAway)
                        translationY = away * (NavHeight + stackBottom + 24.dp).toPx()
                        alpha = 1f - away
                    },
            )
        } else {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = FloatingBarMaxWidth)
                    .fillMaxWidth()
                    .padding(bottom = stackBottom)
                    .graphicsLayer {
                        val away = maxOf(navAside, settingsAway)
                        translationY = away * (NavHeight + stackBottom + 24.dp).toPx()
                        alpha = 1f - away
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (player.current != null && tab != Tab.Shorts) {
                    MiniPlayer(
                        player,
                        onExpand = { player.expanded = true },
                        showVideo = !showFullscreen && !playerDock.coversMiniArt,
                        dock = activeDock,
                        pull = playerPull,
                        modifier = Modifier.padding(horizontal = PageGutter).fillMaxWidth(),
                    )
                    Spacer(Modifier.height(MiniGap))
                }
                NavPill(
                    selected = tab,
                    onSelect = onSelectTab,
                    shorts = { 0f },
                    tabsEnabled = true,
                    modifier = Modifier.padding(horizontal = NavBarGutter).fillMaxWidth(),
                )
            }
        }

        // Shorts keeps this same bar. Like, comments, share and the channel sit on the
        // video (Shorts.kt), and the progress line sits just above the bar.
        AnimatedVisibility(
            visible = settingsOpen,
            enter = if (reduced) fadeIn(snap()) else
                scaleIn(spring(dampingRatio = 0.82f, stiffness = 380f), initialScale = 0.94f) +
                    slideInVertically(spring(dampingRatio = 0.82f, stiffness = 380f)) { it / 12 } + fadeIn(tween(220)),
            exit = if (reduced) fadeOut(snap()) else
                scaleOut(spring(dampingRatio = 0.82f, stiffness = 380f), targetScale = 0.96f) +
                    slideOutVertically(spring(dampingRatio = 0.82f, stiffness = 380f)) { it / 16 } + fadeOut(tween(160)),
        ) {
            SettingsScreen(
                player = player,
                active = settingsOpen,
                onClose = { settingsOpen = false },
                onSignIn = { signingIn = true },
                onSignedOut = { lib.reset(); player.refreshAccountState() },
                wide = wide,
            )
        }
        if (signingIn) {
            SignInScreen(
                onDone = { signingIn = false; lib.reset(); player.refreshAccountState() },
                onCancel = { signingIn = false },
            )
        }

        when (player.sheet) {
            PlayerSheet.Quality -> QualitySheet(player, onDismiss = { player.sheet = null })
            PlayerSheet.Speed -> SpeedSheet(player, onDismiss = { player.sheet = null })
            PlayerSheet.Chapters -> ChaptersSheet(player, onDismiss = { player.sheet = null })
            PlayerSheet.Comments, PlayerSheet.Transcript -> Unit // a panel inside the player (PlayerScreen)
            PlayerSheet.Captions -> CaptionsSheet(player, onDismiss = { player.sheet = null }, onTranscript = { player.sheet = PlayerSheet.Transcript })
            PlayerSheet.Queue -> QueueSheet(player, onDismiss = { player.sheet = null })
            null -> Unit
        }
        player.playlistTarget?.let { video ->
            SaveToPlaylistSheet(player, video, maxHeight = maxHeight * 0.55f)
        }
        optionsFor?.let { video -> VideoOptionsSheet(video, player, onDismiss = { optionsFor = null }) }
        windowLayout.tabletopY?.takeIf { tabletop && showNowPlaying }?.let { fold ->
            with(density) { TabletopPlayer(player, foldTop = fold.start.toDp(), foldBottom = fold.endInclusive.toDp()) }
        }
        // Confirmations ("Added to queue") when the full player isn't showing its own.
        if (!showNowPlaying) {
            QueueMessage(player, Modifier.align(Alignment.BottomCenter).padding(bottom = stackBottom + NavHeight + (if (hasMini) MiniHeight + MiniGap else 0.dp) + 12.dp))
        }
        if (pip.active) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                sharedSurface(Modifier.fillMaxSize().aspectFit(player.videoAspect))
            }
        }
        VideoHeldMenu(
            video = optionsFor.takeIf { !showNowPlaying },
            held = optionsHeld,
            hazeState = hazeState,
            player = player,
            onOpen = player::open,
            onDismiss = { optionsFor = null },
        )
        } // CompositionLocalProvider
    }
}

@Composable
private fun QueueMessage(player: PlayerState, modifier: Modifier) {
    val message = player.actionMessage ?: return
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(2_000)
        player.actionMessage = null
    }
    val c = MaterialTheme.colorScheme
    Box(modifier.clip(RoundedCornerShape(50)).background(c.inverseSurface).padding(horizontal = 18.dp, vertical = 11.dp)) {
        Text(message, color = c.inverseOnSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
