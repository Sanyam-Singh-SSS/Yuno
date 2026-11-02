package com.decio.skipii

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.exp
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.decio.skipii.icons.BitChordIcons
import com.decio.skipii.icons.SfIcons
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

// ── Geometry shared with the morph (MainActivity builds MorphGeometry from these) ──

// The video runs edge to edge as a plain rectangle, right under the status bar (Derek).
internal val StageTopGap = 0.dp
/** How far up the screen (share of its height) the video's top edge gets before the status bar starts turning black. */
private const val StatusBlackFromUp = 0.25f
/** Morph progress by which the status bar over the video is fully black. */
private const val StatusBlackEnd = 0.9f
internal val StageSideGap = 0.dp
internal val StageRadius = 0.dp
internal val MiniHeight = 70.dp
/** The same bar folded into the glass nav's inline row, level with its tab pill. */
internal val MiniHeightInline = 48.dp
internal val MiniRadius = 24.dp
internal val MiniThumbStart = 7.dp
internal val MiniThumbWidth = 92.dp
internal val MiniThumbHeight = 52.dp
internal val MiniThumbRadius = 17.dp
/**
 * Portrait stage shape: tall videos can use up to 4:5; wide ones letterbox inside a 16:9 frame
 * (2.35:1 and wider no longer squash the whole player flat).
 */
internal const val StageMinAspect = 0.8f
internal const val StageMaxAspect = 2.4f
/**
 * The stage never takes more than this share of the window's height: in a short split-screen
 * pane a tall video would otherwise push the details (and half the video) off the bottom.
 * On a full phone screen 4:5 stays under it, so nothing changes there.
 */
internal const val StageMaxHeightShare = 0.6f

/** Fades the page with the sheet. Nothing is drawn while it should be clear, so it can't flash solid first. */
private fun Modifier.playerPageFade(alpha: () -> Float): Modifier = drawWithContent {
    val a = alpha()
    if (a <= 0f) return@drawWithContent
    if (a >= 1f) {
        drawContent()
        return@drawWithContent
    }
    val paint = pageFadePaint
    paint.alpha = a
    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), paint)
    drawContent()
    drawContext.canvas.restore()
}

private val pageFadePaint = androidx.compose.ui.graphics.Paint()

// ── Full player (portrait) ─────────────────────────────────────────────────────

@Composable
fun PlayerScreen(
    state: PlayerState,
    sheetMotion: PlayerSheetMotion,
    dock: PlayerDock,
    onOpen: (VideoItem) -> Unit,
    onCollapse: () -> Unit,
    onFullscreen: () -> Unit,
    /** Room the nav pill takes at the bottom, so the player's list and toast clear it. */
    bottomClearance: androidx.compose.ui.unit.Dp,
) {
    val c = MaterialTheme.colorScheme
    val item = state.current ?: return
    val progress = { dock.openFraction() }
    // Only changes when the threshold is crossed. Reading the fraction itself
    // here recomposed the whole player on every pixel of a drag, which flashed it.
    val interactive by remember { derivedStateOf { dock.openFraction() > 0.5f } }
    val details = state.details
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var descriptionOpen by remember(item.id) { mutableStateOf(false) }
    // Audio only (Derek, 2026-10-25): the open player has no video space at all — the stage
    // folds away (the picture fading and lifting as its height closes) and unfolds for video.
    // Only on the fully open player: swiping it down unfolds it again, so the thumbnail still
    // lands in the mini-player.
    val reduced = rememberReducedMotion()
    val fold = remember { Animatable(if (AudioOnly.enabled) 1f else 0f) }
    LaunchedEffect(AudioOnly.enabled) {
        val target = if (AudioOnly.enabled) 1f else 0f
        if (reduced) fold.snapTo(target) else fold.animateTo(target, spring(dampingRatio = 1f, stiffness = 260f, visibilityThreshold = 0.0005f))
    }
    // Derek, 2026-10-26: over a video the status bar is pitch black (white icons); audio only
    // and the pages keep the page look. It fades from the page's own color once the video's top
    // edge is a quarter of the way up the screen (black before the growing frame reaches the
    // top), and out with the fold.
    val blackStatus = {
        val p = progress()
        ramp(p, StatusBlackFromUp, StatusBlackEnd) * (1f - fold.value)
    }
    LaunchedEffect(Unit) { snapshotFlow { blackStatus() > 0.5f }.collect { StatusBarOverVideo.on = it } }
    DisposableEffect(Unit) { onDispose { StatusBarOverVideo.on = false } }

    val pageColor = c.surface
    DisposableEffect(dock) { onDispose { dock.attached = false } }
    Box(Modifier.fillMaxSize()) {
        // The page fades in behind the video. The video itself stays solid and
        // flies to the mini-player's thumbnail (BitChord's dock fade).
        Box(
            Modifier
                .fillMaxSize()
                .playerPageFade { dock.pageAlpha(sheetMotion.holding) }
                .background(pageColor),
        )
        Box(
            Modifier.fillMaxWidth().height(520.dp)
                .playerPageFade { dock.pageAlpha(sheetMotion.holding) }
                .background(Brush.verticalGradient(listOf(c.primaryContainer.copy(alpha = 0.75f), c.surface))),
        )
        ActionMessage(
            state,
            Modifier.align(Alignment.BottomCenter).zIndex(1f)
                .playerPageFade { dock.pageAlpha(sheetMotion.holding) }
                .padding(bottom = bottomClearance + 8.dp),
        )
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(top + StageTopGap))
            Box(Modifier.zIndex(2f).onGloballyPositioned {
                if (dock.offset() != null) dock.attached = true
            }) {
                VideoStage(
                    state, sheetMotion, dock, progress, fold, onCollapse, onFullscreen, interactive,
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .zIndex(0f)
                    .nestedScroll(sheetDismissScroll(sheetMotion))
                    .playerPageFade { dock.pageAlpha(sheetMotion.holding) },
                contentPadding = PaddingValues(bottom = bottomClearance + PageGutter),
            ) {
                item(key = "info") {
                    Column(Modifier.padding(horizontal = PageGutter)) {
                        Spacer(Modifier.height(18.dp))
                        Text(
                            item.title,
                            fontFamily = RoundedHeavy,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            lineHeight = 26.sp,
                            letterSpacing = (-0.6).sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        val stats = listOfNotNull(
                            item.views.takeIf { it >= 0 }?.let { "${formatCount(it)} views" },
                            item.uploaded?.ifBlank { null },
                            details?.likes?.takeIf { it > 0 }?.let { "${formatCount(it)} likes" },
                        ).joinDots()
                        val hasDescription = !details?.description.isNullOrBlank()
                        Row(
                            modifier = if (hasDescription) Modifier.pressable(pressedScale = 0.98f) { descriptionOpen = !descriptionOpen } else Modifier,
                        ) {
                            Text(stats, color = c.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            if (hasDescription) {
                                Text(
                                    if (descriptionOpen) "  less" else "  …more",
                                    color = c.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }
                        if (descriptionOpen && details != null) {
                            Text(
                                details.description,
                                modifier = Modifier.padding(top = 10.dp),
                                color = c.onSurfaceVariant,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val openChannel = LocalChannelOpener.current
                            val channelUrl = details?.item?.channelUrl ?: item.channelUrl
                            // Avatar + name open the channel (the Subscribe button beside them doesn't).
                            Row(
                                Modifier.weight(1f).pressable(enabled = channelUrl != null, pressedScale = 0.97f) { channelUrl?.let(openChannel) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                            ChannelAvatar(details?.item?.avatar ?: item.avatar, item.channel, size = 46)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.channel, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                details?.subscribers?.takeIf { it > 0 }?.let {
                                    Text("${formatCount(it)} subscribers", color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            }
                            SubscribeButton(state.accountState?.subscribed == true, onClick = state::toggleSubscribe)
                        }
                        // Speed, quality and audio only live on the video's own controls (VideoView.kt).
                        PlayerActions(state)
                        QueueCard(state, onOpen = { state.sheet = PlayerSheet.Queue })
                        CommentsCard(state.comments, onOpen = { state.sheet = PlayerSheet.Comments })
                    }
                }
                val related = details?.related.orEmpty()
                if (related.isNotEmpty()) {
                    item(key = "moreTitle") { SectionTitle("More videos") }
                    items(related, key = { "rel-" + it.id }) { video ->
                        Box(Modifier.padding(horizontal = PageGutter)) { VideoRow(video, onClick = { onOpen(video) }) }
                    }
                } else if (state.loading) {
                    item(key = "loading") {
                        Box(Modifier.padding(horizontal = PageGutter)) { MoreVideoSkeletons() }
                    }
                }
            }
        }
        // The page's bottom vignette, as on the tabs: the details dissolve behind the nav
        // pill instead of running hard into it. Drawn only, never touchable.
        val vignette = remember(pageColor) { vignetteBrush(pageColor, opaqueAtTop = false) }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottom + 130.dp)
                .playerPageFade { dock.pageAlpha(sheetMotion.holding) }
                .drawBehind { drawRect(vignette) },
        )
        // Swiping home from the bottom edge never scrolls the details (open player only: while
        // collapsing, touches must still fall through to the mini-player).
        if (interactive) BottomGestureGuard(Modifier.align(Alignment.BottomCenter))

        // Comments open as a panel whose top sits just under the video.
        if (state.sheet == PlayerSheet.Comments) {
            val screenW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
            val videoBottom = top + StageTopGap + (screenW - StageSideGap * 2) / state.stageAspect
            val item = state.details?.item
            CommentsPanel(
                state.comments,
                partialTop = videoBottom + 10.dp,
                onClose = { state.sheet = null },
                creatorAvatar = item?.avatar,
                creatorName = item?.channel.orEmpty(),
            )
        }
    }
    // Outside the morph's frame, so it never slides in with the card: it just fades over
    // whatever the status bar shows.
    Box(
        Modifier.fillMaxWidth().height(top)
            .graphicsLayer { alpha = blackStatus() }
            .background(Color.Black),
    )
}

/**
 * The pinned video in portrait: the morph's container-transform layer around [VideoContent].
 * Swiping it down minimizes (the morph). Swiping it up while open goes fullscreen, as in the
 * YouTube app (Derek, 2026-10-10): the video swells under the finger, and past
 * [FullscreenPull] or with a flick it turns to landscape; short of that it eases back.
 */
/** Hands downward list scroll to the player sheet once the list is at the top. */
private fun sheetDismissScroll(motion: PlayerSheetMotion): NestedScrollConnection =
    object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && available.y > 0f) {
                motion.drag(available.y)
                return available
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!motion.holding) return Velocity.Zero
            motion.release(available.y)
            return available
        }
    }

@Composable
private fun VideoStage(
    state: PlayerState,
    sheetMotion: PlayerSheetMotion,
    dock: PlayerDock,
    progress: () -> Float,
    fold: Animatable<Float, *>,
    onCollapse: () -> Unit,
    onFullscreen: () -> Unit,
    interactive: Boolean,
) {
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    // The height follows the video; when its real shape arrives the change eases in.
    val aspect by animateFloatAsState(
        state.stageAspect,
        if (reduced) snap() else spring(dampingRatio = 1f, stiffness = 380f),
        label = "stageAspect",
    )
    val pullUp = remember { Animatable(0f) }
    val pullDistance = with(LocalDensity.current) { FullscreenPull.toPx() }
    val pulling = remember { mutableStateOf(false) }
    val onFullscreenNow = rememberUpdatedState(onFullscreen)
    val reducedNow = rememberUpdatedState(reduced)
    // Same drag the title uses: a scroll that never moves its own content, so
    // every pixel of the finger is handed to the sheet. It sits outside the
    // video's scale, which was turning that pixel count into a different one.
    val dragScroll = rememberScrollableState { 0f }
    val sheetDrag = remember(sheetMotion, dock, pullUp, scope, pullDistance) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
                if (sheetMotion.holding) {
                    pulling.value = false
                    sheetMotion.drag(available.y)
                    return Offset(0f, available.y)
                }
                val upward = available.y < 0f
                val canFullscreen = upward && !state.fullscreen && !AudioOnly.enabled && dock.openFraction() >= 0.999f
                if (canFullscreen || pulling.value && upward) {
                    pulling.value = true
                    val next = (pullUp.value - available.y).coerceAtLeast(0f)
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { pullUp.snapTo(next) }
                    return Offset(0f, available.y)
                }
                if (pulling.value && available.y > 0f) {
                    pulling.value = false
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { pullUp.snapTo(0f) }
                    sheetMotion.drag(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f && !sheetMotion.holding && !pulling.value) {
                    sheetMotion.drag(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (sheetMotion.holding) {
                    sheetMotion.release(available.y)
                    return available
                }
                if (!pulling.value) return Velocity.Zero
                val traveled = pullUp.value
                pulling.value = false
                if (traveled > pullDistance || available.y < -1400f) onFullscreenNow.value()
                if (reducedNow.value) pullUp.snapTo(0f) else pullUp.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f))
                return available
            }
        }
    }
    val folded = { fold.value * ramp(progress(), 0.8f) }
    val density = LocalDensity.current
    // Where the stage sits with the sheet fully open. Kept off snapshot state:
    // writing it every placement recomposed the player mid-drag.
    val stageAnchor = remember { StageAnchor() }
    val stage = Modifier
        .graphicsLayer { clip = folded() > 0.001f }
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val k = 1f - folded()
            layout(placeable.width, (placeable.height * k).roundToInt()) {
                placeable.placeWithLayer(0, 0) {
                    alpha = ramp(k, 0.1f, 0.85f)
                    translationY = -placeable.height * 0.3f * (1f - k)
                }
            }
        }
        .padding(horizontal = StageSideGap)
        .fillMaxWidth()
        .aspectRatio(aspect)
        .onGloballyPositioned { coords ->
            val off = dock.offset() ?: 0f
            stageAnchor.origin = coords.positionOnScreen() - Offset(0f, off)
            if (!dock.attached) dock.attached = true
            // Only a measurement from after the sheet has settled into place.
            // The one before that sits a step low and flashes under the slot.
            if (sheetMotion.placed && dock.miniArtOnScreen() != null) dock.notePose()
        }
        .graphicsLayer {
            // Rubber band: up to 6% bigger, ever more slowly, growing down from the top edge.
            val k = 1f + 0.06f * (1f - exp(-pullUp.value / (pullDistance * 1.5f)))
            scaleX = k
            scaleY = k
            transformOrigin = TransformOrigin(0.5f, 0f)
        }
        .graphicsLayer {
            val p = progress()
            val origin = stageAnchor.origin
            val mini = dock.miniArtOnScreen()
            val off = dock.offset() ?: 0f
            // Hidden until a real dock pose exists. Drawing earlier paints the
            // frame a step under the mini player's video.
            val parkedOpen = p >= 0.999f
            alpha = if (dock.poseReady && (p > 0f || parkedOpen)) 1f else 0f
            if (origin != null && mini != null && p < 0.999f) {
                // Predict this frame's place from the anchor and the offset the
                // finger is on now. The layout position is a frame behind a fast
                // drag, which left the picture short of the thumbnail and then
                // snapped it home.
                val stageScreen = origin + Offset(0f, off)
                val miniLocal = Rect(
                    mini.left - stageScreen.x,
                    mini.top - stageScreen.y,
                    mini.right - stageScreen.x,
                    mini.bottom - stageScreen.y,
                )
                val g = MorphGeometry(
                    screen = size,
                    miniCard = miniLocal,
                    miniThumb = miniLocal,
                    stage = Rect(Offset.Zero, size),
                    miniRadius = with(density) { dock.miniArtCorner().toPx() },
                    thumbRadius = with(density) { dock.miniArtCorner().toPx() },
                    stageRadius = with(density) { StageRadius.toPx() },
                )
                val f = g.videoFrame(size, p)
                scaleX = f.scale
                scaleY = f.scale
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = f.offset.x
                translationY = f.offset.y
                val screenRadius = lerpF(g.thumbRadius, g.stageRadius, p.coerceIn(0f, 1f))
                shape = RoundRectClip(f.window, screenRadius / f.scale.coerceAtLeast(0.01f))
                clip = true
            } else {
                shape = RoundedCornerShape(StageRadius)
                clip = false
            }
            shadowElevation = 14.dp.toPx() * (1f - p).coerceIn(0f, 1f)
            ambientShadowColor = Color.Black.copy(alpha = 0.25f)
            spotShadowColor = Color.Black.copy(alpha = 0.35f)
        }
    // Controls appear only once the video has nearly landed.
    val controlsAlpha = { ramp(progress(), 0.7f) }
    // Audio only ↔ video in place (Derek, 2026-10-14): [cover] is how much of the audio stage
    // lies over the video, [gather] its controls coming together.
    val audio = AudioOnly.enabled
    val cover = remember { Animatable(if (audio) 1f else 0f) }
    val gather = remember { Animatable(if (audio) 1f else 0f) }
    LaunchedEffect(audio) {
        if (reduced) { cover.snapTo(if (audio) 1f else 0f); gather.snapTo(if (audio) 1f else 0f); return@LaunchedEffect }
        if (audio) {
            launch { cover.animateTo(1f, tween(AudioSwitchMs, easing = Motion.Emphasized)) }
            delay(AudioSwitchMs / 3L)
            gather.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 420f))
        } else {
            // Straight away (Derek: waiting for the video felt like the tap hadn't registered).
            // Underneath, the video shows its thumbnail and a spinner until a fresh frame
            // arrives ([PlayerState.videoStale]).
            launch { gather.animateTo(0f, tween(AudioSwitchMs / 2, easing = Motion.Emphasized)) }
            cover.animateTo(0f, tween(AudioSwitchMs, easing = Motion.Emphasized))
        }
    }
    val showVideo by remember { derivedStateOf { !AudioOnly.enabled || cover.value < 1f } }
    val showAudio by remember { derivedStateOf { AudioOnly.enabled || cover.value > 0f } }
    Box(
        Modifier
            .nestedScroll(sheetDrag)
            .scrollable(dragScroll, Orientation.Vertical, overscrollEffect = NoOverscroll)
            .then(stage),
    ) {
        if (showVideo) {
            val flying by remember { derivedStateOf { dock.poseReady && dock.openFraction() > 0f } }
            VideoContent(
                state = state,
                fullscreen = false,
                interactive = interactive,
                onInactiveTap = { },
                onCollapse = onCollapse,
                onToggleFullscreen = onFullscreen,
                controlsAlpha = controlsAlpha,
                portraitEdgeScrub = true,
                liveSurface = flying,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (flying) 1f else 0f },
            )
        }
        if (showAudio) {
            AudioOnlyStage(
                state = state,
                interactive = interactive && audio,
                onInactiveTap = { },
                progress = progress,
                controlsAlpha = controlsAlpha,
                cover = { cover.value },
                gather = { gather.value },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Fully-open screen position of the portrait stage. Not snapshot state — see [VideoStage]. */
private class StageAnchor {
    var origin: Offset? = null
}

/** Audio only ↔ video: how long the audio stage takes to cover (or uncover) the picture. */
private const val AudioSwitchMs = 360

/** How far a swipe up on the open video travels before it goes fullscreen on release. */
private val FullscreenPull = 64.dp

/** The video's drag scroll should move the sheet, not stretch the picture. */
private object NoOverscroll : OverscrollEffect {
    override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset =
        performScroll(delta)

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        performFling(velocity)
    }

    override val isInProgress: Boolean get() = false
    override val effectModifier: Modifier get() = Modifier
}

// ── Account actions (signed in) ────────────────────────────────────────────────

@Composable
private fun SubscribeButton(subscribed: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    // Expressive state change: the pill rounds into a softer tonal shape once subscribed,
    // squishes as it does, and the bell rings.
    val radius by animateDpAsState(if (subscribed) 16.dp else 22.dp, if (reduced) tween(0) else spring(dampingRatio = 0.6f, stiffness = 500f), label = "subShape")
    Row(
        modifier = Modifier
            .squishOn(subscribed)
            .height(44.dp)
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(radius))
            .background(if (subscribed) c.secondaryContainer else c.onSurface)
            .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 500f))
            .padding(horizontal = if (subscribed) 14.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (subscribed) {
            FlourishIcon(SfIcons.Notifications, null, active = subscribed, kind = Flourish.Ring, tint = c.onSecondaryContainer, size = 18.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            if (subscribed) "Subscribed" else "Subscribe",
            color = if (subscribed) c.onSecondaryContainer else c.surface,
            fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
        )
    }
}

/**
 * The action row: like | dislike, Save, Playlist, then Share. Signed out, those account
 * buttons stay and a tap says to sign in. The row runs edge to edge inside so a long like
 * count scrolls it sideways instead of squeezing the buttons, and the like sparks still have room to fly.
 */
@Composable
private fun PlayerActions(state: PlayerState) {
    Row(
        Modifier
            .padding(top = 14.dp)
            .bleed(PageGutter)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = PageGutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountActions(state)
        val context = LocalContext.current
        val item = state.current
        ActionPill(
            icon = SfIcons.Share,
            label = "Share",
            active = false,
            flourish = null,
            shape = RoundedCornerShape(22.dp),
            description = "Share",
        ) { item?.let { shareLink(context, "https://youtu.be/${it.id}", it.title) } }
    }
}

/** Lays the content out [x] wider on each side than the parent's padding allows. */
private fun Modifier.bleed(x: androidx.compose.ui.unit.Dp) = layout { measurable, constraints ->
    val extra = (x * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** Connected like | dislike group, Save (Watch later) and Playlist, all synced to the account; placed in [PlayerActions]' row. */
@Composable
private fun AccountActions(state: PlayerState) {
    val like = state.accountState?.like ?: LikeStatus.None
    var lastLikes by remember { mutableStateOf(state.displayLikes) }
    val likes = state.displayLikes
    val rising = likes >= lastLikes
    SideEffect { lastLikes = likes }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        ActionPill(
            icon = if (like == LikeStatus.Like) SfIcons.ThumbUpFill else SfIcons.ThumbUp,
            label = likes.takeIf { it > 0 }?.let { formatCount(it) },
            active = like == LikeStatus.Like,
            flourish = Flourish.Pop,
            labelRollsUp = rising,
            shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp, topEnd = 8.dp, bottomEnd = 8.dp),
            description = "Like",
        ) { state.setLike(LikeStatus.Like) }
        ActionPill(
            icon = if (like == LikeStatus.Dislike) SfIcons.ThumbDownFill else SfIcons.ThumbDown,
            label = null,
            active = like == LikeStatus.Dislike,
            flourish = Flourish.Droop,
            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 22.dp, bottomEnd = 22.dp),
            description = "Dislike",
        ) { state.setLike(LikeStatus.Dislike) }
    }
    ActionPill(
        icon = if (state.inWatchLater) SfIcons.Clock else BitChordIcons.Clock,
        label = if (state.inWatchLater) "Saved" else "Save",
        active = state.inWatchLater,
        flourish = Flourish.Drop,
        shape = RoundedCornerShape(22.dp),
        description = "Save to Watch later",
    ) { state.toggleWatchLater() }
    // Any of the account's playlists (GitHub issue #12).
    ActionPill(
        icon = SfIcons.LibraryAdd,
        label = "Playlist",
        active = false,
        flourish = null,
        shape = RoundedCornerShape(22.dp),
        description = "Save to playlist",
    ) { state.current?.let(state::openPlaylists) }
}

@Composable
private fun ActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    active: Boolean,
    /** Null: a plain icon (an action with no on/off state). */
    flourish: Flourish?,
    shape: androidx.compose.ui.graphics.Shape,
    description: String,
    labelRollsUp: Boolean = true,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val bg by androidx.compose.animation.animateColorAsState(if (active) c.primary else c.surfaceContainerHigh, tween(220), label = "pillBg")
    val tint by androidx.compose.animation.animateColorAsState(if (active) c.onPrimary else c.onSurface, tween(220), label = "pillInk")
    Row(
        modifier = Modifier
            .squishOn(active)
            .height(44.dp)
            .pressable(onClick = onClick)
            // background(shape), not clip: the like sparks may fly past the pill's edge.
            .background(bg, shape)
            .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 500f))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (flourish == null) Icon(icon, description, tint = tint, modifier = Modifier.size(20.dp))
        else FlourishIcon(icon, description, active = active, kind = flourish, tint = tint, size = 20.dp, sparkColor = c.primary)
        if (label != null) {
            Spacer(Modifier.width(7.dp))
            RollingText(label, tint, up = labelRollsUp)
        }
    }
}

/** Brief confirmation or failure note for account actions; clears itself. */
@Composable
private fun ActionMessage(state: PlayerState, modifier: Modifier) {
    val message = state.actionMessage ?: return
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(2_500)
        state.actionMessage = null
    }
    val c = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(CircleShape)
            .background(c.inverseSurface)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) { Text(message, color = c.inverseOnSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

// ── Mini player ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun MiniPlayer(
    state: PlayerState,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    showVideo: Boolean = true,
    shape: CornerBasedShape = RoundedCornerShape(MiniRadius),
    /** The glass nav's inline form: 48dp, matching its tab pill, with a smaller slot. */
    compact: Boolean = false,
    dock: PlayerDock? = null,
    pull: MiniPlayerPull? = null,
    /** False when a parent already owns the pull (the glass bar, outside its clip). */
    gestures: Boolean = true,
) {
    val c = MaterialTheme.colorScheme
    val item = state.current ?: return
    // Liquid glass when the setting is on (the glass nav bar hands this component its
    // own pill shape), haze when it is off. Either way the page shows through instead
    // of the card sitting on a flat Material fill.
    val glassOn = LocalLiquidGlassEnabled.current && isGlassSupported()
    val haze = LocalAppHaze.current
    val container = c.surfaceContainerHighest
    val surface = when {
        glassOn -> Modifier.liquidGlass(shape = shape)
        haze != null -> Modifier
            .background(container.copy(alpha = 0.72f), shape)
            .hazeEffect(state = haze, style = HazeMaterials.regular(container.copy(alpha = 0.72f))) {
                inputScale = HazeInputScale.Fixed(0.33f)
            }
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
        else -> Modifier.background(container, shape)
    }
    val thumbWidth = if (compact) 58.dp else MiniThumbWidth
    val thumbHeight = if (compact) 34.dp else MiniThumbHeight
    val thumbRadius = if (compact) 11.dp else MiniThumbRadius
    val thumbStart = if (compact) 7.dp else MiniThumbStart
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) MiniHeightInline else MiniHeight)
            .then(if (gestures) Modifier.miniPlayerPullGestures(pull, onDismiss = state::close) else Modifier)
            .pressable(pressedScale = 0.98f, onClick = onExpand)
            .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.18f))
            .clip(shape)
            .then(surface)
    ) {
        Row(Modifier.fillMaxSize().padding(start = thumbStart, end = if (compact) 6.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // The live video, not a still. While the player is open or morphing, this slot
            // stays EMPTY: the shrinking video is on its way here, and a thumbnail already
            // sitting in the slot read as a second copy it was flying towards.
            Box(
                Modifier
                    .size(width = thumbWidth, height = thumbHeight)
                    .playerDockArt(dock, thumbRadius)
                    .clip(RoundedCornerShape(thumbRadius)),
            ) {
                // The captured frame, not the poster. The poster was a different
                // picture, so it flashed in whenever the live view moved.
                Box(Modifier.fillMaxSize().aspectFit(state.stageAspect, cover = true)) {
                    if (state.blankUntilFrame) {
                        Box(Modifier.fillMaxSize().background(Color.Black))
                    } else if (HeldFrame.image != null && !state.hidesStaleVideo) {
                        HeldFrameImage(Modifier.fillMaxSize().aspectFit(state.videoAspect))
                    } else {
                        Thumbnail(item.thumbnail, Modifier.fillMaxSize().aspectFit(state.videoAspect), radius = 0)
                    }
                }
                if (showVideo && !AudioOnly.enabled) {
                    Box(Modifier.fillMaxSize().aspectFit(state.stageAspect, cover = true)) {
                        LocalVideoSurface.current(Modifier.fillMaxSize().aspectFit(state.videoAspect).hideStaleVideo(state))
                    }
                }
            }
            Spacer(Modifier.width(if (compact) 10.dp else 12.dp))
            MiniDetails(state, interactive = true, compact = compact)
        }
    }
}

/**
 * Everything right of the mini-player's video: the title, channel and play/pause.
 * Shared by the real mini-player and the morph's look-alike ([interactive] false:
 * drawn only, touches go to the real one underneath).
 */
@Composable
private fun RowScope.MiniDetails(state: PlayerState, interactive: Boolean, compact: Boolean = false) {
    val c = MaterialTheme.colorScheme
    val item = state.current ?: return
    val playSize = if (compact) 38.dp else 46.dp
    Row(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                fontSize = if (compact) 13.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.channel,
                fontSize = if (compact) 11.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
        MorphPlayButton(
            playing = state.isPlaying,
            buffering = state.isBuffering,
            size = playSize,
            // No disc behind it — the glyph alone, in the accent colour.
            container = Color.Transparent,
            content = c.primary,
            filled = true,
            enabled = interactive,
            onClick = state::togglePlay,
        )
    }
}

/**
 * The mini-player's details, drawn inside the morphing page: always just right of the
 * shrinking video and centred on it, fading in over the last 30% of the collapse (out over
 * the first 30% of an open). At progress 0 they sit exactly where the real mini-player's
 * are, so the handoff is invisible.
 */
@Composable
private fun MorphingMiniDetails(state: PlayerState, morph: PlayerMorph) {
    val g = morph.geometry ?: return
    Row(
        Modifier
            // Laid out per frame (this small row only, no recomposition): it spans from the
            // video's right edge to the page's right edge, so the buttons hug the page while
            // it narrows, and it sits vertically centred on the video.
            .layout { measurable, _ ->
                val p = morph.progress.value
                val gap = 12.dp.toPx()
                val f = g.videoFrame(g.stage.size, p)
                val start = g.stage.left + f.offset.x + f.scale * f.window.right + gap
                val videoCenterY = g.stage.top + f.offset.y + f.scale * f.window.center.y
                val frameRight = lerpF(g.miniCard.right, g.screen.width, p)
                val width = (frameRight - 8.dp.toPx() - start).coerceAtLeast(0f).toInt()
                val height = MiniHeight.roundToPx()
                val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(width, height))
                layout(width, height) {
                    placeable.placeRelative(start.toInt(), (videoCenterY - height / 2f).toInt())
                }
            }
            .graphicsLayer { alpha = 1f - ramp(morph.progress.value, 0f, 0.3f) },
        verticalAlignment = Alignment.CenterVertically,
    ) { MiniDetails(state, interactive = false) }
}
