package com.yuno

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CoroutineScope
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import com.yuno.icons.SfIcons

/**
 * The video with its overlay controls — shared by the portrait player and fullscreen.
 * A tap toggles the controls; they auto-hide while playing. Double-tap seek, the loading
 * spinner, the SponsorBlock Ask pill / skipped note and errors stay on the video.
 * [controlsAlpha] lets the morph hold them back until the video has landed.
 */
@Composable
fun VideoContent(
    state: PlayerState,
    fullscreen: Boolean,
    onCollapse: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    controlsAlpha: () -> Float = { 1f },
    /** False while the player collapses: no controls, and a tap calls [onInactiveTap]. */
    interactive: Boolean = true,
    onInactiveTap: () -> Unit = {},
    /** Portrait: seam scrubber on the morphing stage (not a screen-fixed overlay). */
    portraitEdgeScrub: Boolean = false,
    /** False while the picture still lives in the mini slot. Taking the view earlier blanks that slot. */
    liveSurface: Boolean = true,
) {
    // Hidden until the video is tapped: opening the player (from the mini-player too) and
    // rotating in or out of fullscreen never pop the controls up.
    var controlsVisible by remember { mutableStateOf(false) }
    var drop by remember { mutableStateOf<DropKind?>(null) }
    BackHandler(enabled = drop != null) { drop = null }
    var touchedAt by remember { mutableLongStateOf(0L) }
    fun touched() { touchedAt = System.nanoTime() }
    LaunchedEffect(controlsVisible, state.isPlaying, touchedAt) {
        if (controlsVisible && state.isPlaying) {
            delay(Motion.ControlsHideMs)
            controlsVisible = false
        }
    }
    val reduced = rememberReducedMotion()
    val visibleAlpha by animateFloatAsState(
        targetValue = if (controlsVisible) 1f else 0f,
        animationSpec = if (reduced) tween(0) else tween(160, easing = Motion.Emphasized),
        label = "controlsAlpha",
    )

    // Double-tap seek: a double tap on the left/right 40% starts a burst of -/+10 s; each
    // further tap on that side within SeekBurstWindowMs adds 10 s more (SeekBurstPill).
    var burstSide by remember { mutableIntStateOf(1) }
    var burstSeconds by remember { mutableIntStateOf(0) }
    var burstTaps by remember { mutableIntStateOf(0) }
    var burstActive by remember { mutableStateOf(false) }
    LaunchedEffect(burstTaps) {
        if (burstTaps == 0) return@LaunchedEffect
        delay(SeekBurstWindowMs)
        burstActive = false
    }
    // Each tap nudges the picture a few dp the way it seeks (scaled up so no edge shows).
    val nudge = remember { Animatable(0f) }
    LaunchedEffect(burstTaps) {
        if (burstTaps > 0 && !reduced) nudge.animateTo(0f, keyframes { durationMillis = 440; 1f at 130 })
    }
    val peekAlpha by animateFloatAsState(
        targetValue = if (burstActive) 1f else 0f,
        animationSpec = if (reduced) tween(0) else tween(200, easing = Motion.Emphasized),
        label = "seekPeek",
    )
    LaunchedEffect(interactive) {
        if (!interactive) {
            controlsVisible = false
            burstActive = false
            state.endScrubDragging()
            state.endHoldSpeed()
        }
    }
    val currentInteractive by androidx.compose.runtime.rememberUpdatedState(interactive)
    val currentInactiveTap by androidx.compose.runtime.rememberUpdatedState(onInactiveTap)
    fun toggleControls() { controlsVisible = !controlsVisible; touched() }
    fun seekStep(side: Int) {
        val moved = state.seekBy(side * SeekStep.ms)
        burstSeconds += ((abs(moved) + 500) / 1000).toInt()
        burstTaps++
    }
    val pip = LocalPictureInPicture.current
    val view = LocalView.current
    val currentView by rememberUpdatedState(view)
    val fullscreenControlsScrub = fullscreen && !portraitEdgeScrub
    val scrubbing = state.scrubDragging
    val scrubPreviewMs = state.scrubPreviewMs
    val zoomTarget = if (fullscreen && state.zoomedToFill) 1f else 0f
    val zoom = remember { Animatable(0f, visibilityThreshold = 0.0005f) }
    var pinching by remember { mutableStateOf(false) }
    val zoomFling = remember { floatArrayOf(0f) }
    LaunchedEffect(zoomTarget, pinching) {
        if (pinching) return@LaunchedEffect
        val velocity = zoomFling[0].also { zoomFling[0] = 0f }
        if (reduced) zoom.snapTo(zoomTarget)
        else zoom.animateTo(zoomTarget, spring(dampingRatio = 0.5f, stiffness = 380f, visibilityThreshold = 0.0005f), initialVelocity = velocity)
    }
    var stage by remember { mutableStateOf(IntSize.Zero) }
    val zoomScope = rememberCoroutineScope()
    LaunchedEffect(state.current?.id) {
        state.current?.id?.let { ScrubStoryboards.preload(it) }
    }
    Box(
        modifier.background(Color.Black)
            .then(if (fullscreen) Modifier.clipToBounds() else Modifier)
            .onSizeChanged { stage = it }
            .onGloballyPositioned { pip?.videoBounds = it.boundsInWindow() }
            .then(if (!fullscreen) Modifier else Modifier.pointerInput(state) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var event = awaitPointerEvent(PointerEventPass.Initial)
                    while (event.changes.count { it.pressed } < 2) {
                        if (event.changes.none { it.pressed }) return@awaitEachGesture
                        event = awaitPointerEvent(PointerEventPass.Initial)
                    }
                    if (state.holdSpeedActive || state.error != null || !currentInteractive) return@awaitEachGesture
                    val fill = fillScale(size.width.toFloat(), size.height.toFloat(), state.videoAspect)
                    val from = 1f + (fill - 1f) * zoom.value
                    var total = 1f
                    var started = false
                    val tracker = VelocityTracker1D(isDataDifferential = false)
                    try {
                        do {
                            total *= event.calculateZoom()
                            if (!started && abs(1f - total) * event.calculateCentroidSize(useCurrent = false) > viewConfiguration.touchSlop) {
                                started = true
                                pinching = true
                                controlsVisible = false
                                burstActive = false
                            }
                            if (started) {
                                event.changes.forEach { it.consume() }
                                if (fill > 1.005f) {
                                    val to = (stretched(from * total, fill) - 1f) / (fill - 1f)
                                    tracker.addDataPoint(event.changes.first().uptimeMillis, to)
                                    zoomScope.launch { zoom.snapTo(to) }
                                }
                            }
                            event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.count { it.pressed } >= 2)
                        if (!started) return@awaitEachGesture
                        val toFill = when {
                            total > 1f + ZoomDecide -> true
                            total < 1f - ZoomDecide -> false
                            else -> null
                        }
                        if (toFill != null && toFill != state.zoomedToFill) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        if (toFill != null) state.setZoomedToFill(toFill)
                        if (fill > 1.005f) zoomFling[0] = tracker.calculateVelocity().coerceIn(-ZoomFlingMax, ZoomFlingMax)
                    } finally {
                        pinching = false
                    }
                    while (event.changes.any { it.pressed }) {
                        event.changes.forEach { it.consume() }
                        event = awaitPointerEvent(PointerEventPass.Initial)
                    }
                }
            })
            .pointerInput(state, portraitEdgeScrub) {
        val doubleTapMs = viewConfiguration.doubleTapTimeoutMillis
        val holdSpeedMs = Motion.HoldSpeedMs
        coroutineScope {
            // Side taps show/hide controls at once; a second tap on that side inside the double-tap window seeks.
            var seekWindow: Job? = null
            var pendingSide = 0
            var pendingAt = 0L
            awaitEachGesture {
                val down = awaitFirstDown()
                if (portraitEdgeScrub && down.position.y >= size.height - EdgeScrubTouchPadAbove.roundToPx()) {
                    return@awaitEachGesture
                }
                if (!currentInteractive) {
                    down.consume()
                    waitForUpOrCancellation()
                    currentInactiveTap()
                    return@awaitEachGesture
                }
                if (state.error != null) {
                    down.consume()
                    waitForUpOrCancellation()
                    return@awaitEachGesture
                }
                // A vertical drag belongs to the stage's sheet scroll, the same
                // one the title uses. Taking it here measured the moving picture
                // and shook the player. A finger inside the slop is still a tap,
                // and one that holds is still 2×.
                val slop = viewConfiguration.touchSlop
                var mode = 0
                val upOrHold = withTimeoutOrNull(holdSpeedMs) {
                    var latest = down
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull latest
                        if (change.pressed && mode == 0) {
                            val moved = change.position - down.position
                            if (moved.getDistance() >= slop) {
                                if (kotlin.math.abs(moved.y) > kotlin.math.abs(moved.x)) {
                                    mode = 1
                                    return@withTimeoutOrNull change
                                } else {
                                    mode = 2
                                }
                            }
                        }
                        if (mode != 0) change.consume()
                        latest = change
                        if (!change.pressed) return@withTimeoutOrNull change
                    }
                    @Suppress("UNREACHABLE_CODE")
                    latest
                }
                if (mode == 1) return@awaitEachGesture
                if (mode == 2) {
                    waitForUpOrCancellation()
                    return@awaitEachGesture
                }
                down.consume()
                val up = upOrHold
                if (up == null) {
                    seekWindow?.cancel()
                    if (!state.scrubDragging && state.beginHoldSpeed()) {
                        currentView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        controlsVisible = false
                        burstActive = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                        }
                        state.endHoldSpeed()
                    } else {
                        waitForUpOrCancellation()?.consume()
                    }
                    return@awaitEachGesture
                }
                up.consume()
                val x = up.position.x / size.width
                val side = if (x < 0.4f) -1 else if (x > 0.6f) 1 else 0
                if (burstActive && side == burstSide) { seekStep(side); return@awaitEachGesture }
                burstActive = false
                // A lone tap waits out the double-tap timeout before the controls come up, so a second
                // tap on that side only seeks (Skipii pendingTap).
                val follows = seekWindow?.isActive == true && side != 0 && side == pendingSide && up.uptimeMillis - pendingAt < doubleTapMs
                seekWindow?.cancel()
                if (follows) {
                    controlsVisible = false
                    burstSide = side
                    burstSeconds = 0
                    burstActive = true
                    seekStep(side)
                } else {
                    pendingSide = side
                    pendingAt = up.uptimeMillis
                    seekWindow = launch { delay(doubleTapMs); toggleControls() }
                }
            }
        }
    }) {
        Box(
            Modifier
                // Letterbox/pillarbox inside the stage. As large as fits; bars appear past its limits.
                .fillMaxSize()
                .aspectFit(state.videoAspect),
        ) {
            // Zoomed, the picture (and its clip) grows past this box to cover the stage; the captions
            // and the burst line below stay on the unzoomed box. (Skipii VideoView)
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = if (fullscreen) 1f + (fillScale(stage.width.toFloat(), stage.height.toFloat(), state.videoAspect) - 1f) * zoom.value else 1f
                        scaleX = s
                        scaleY = s
                    }
                    .clipToBounds(),
            ) {
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val n = nudge.value
                        translationX = burstSide * 5.dp.toPx() * n
                        scaleX = 1f + 0.03f * n
                        scaleY = 1f + 0.03f * n
                    },
                ) {
                    // Switching videos: nothing of the last one, and not the next one's poster.
                    // Black until its own frame is ready.
                    if (state.blankUntilFrame) {
                        Box(Modifier.fillMaxSize().background(Color.Black))
                    } else if (state.hidesStaleVideo || HeldFrame.image == null) {
                        // The captured frame sits under the live view. The poster is a
                        // different picture and flashed through whenever the view moved.
                        Thumbnail(
                            state.current?.thumbnail,
                            Modifier.fillMaxSize().graphicsLayer { alpha = if (scrubbing) 0f else 1f },
                            radius = 0,
                        )
                    } else {
                        HeldFrameImage(Modifier.fillMaxSize().graphicsLayer { alpha = if (scrubbing) 0f else 1f })
                    }
                    if (liveSurface) {
                        LocalVideoSurface.current(Modifier.fillMaxSize().hideStaleVideo(state))
                    }
                    if (scrubbing && scrubPreviewMs != null) {
                        ScrubVideoPreview(
                            state.current?.id,
                            scrubPreviewMs,
                            Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            if (state.error == null) {
                val captionLift by animateDpAsState(
                    when {
                        controlsVisible && interactive -> if (fullscreen) 72.dp else 56.dp
                        state.askSegment != null -> 50.dp
                        else -> if (fullscreen) 22.dp else 12.dp
                    },
                    if (reduced) tween(0) else spring(dampingRatio = 0.9f, stiffness = 380f),
                    label = "captionLift",
                )
                CaptionLayer(state, bottom = captionLift, alpha = { if (scrubbing) 0f else 1f })
            }
            // Where the burst has got to, while the controls (and their scrubber) are hidden.
            Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).graphicsLayer { alpha = peekAlpha }) {
                drawRect(Color.White.copy(alpha = 0.28f))
                val played = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
                drawRect(Color.White, size = Size(size.width * played, size.height))
            }
        }
        if (!scrubbing) {
            SeekBurstPill(burstSide, burstSeconds, burstTaps, burstActive, fullscreen)
        }
        if (state.holdSpeedActive) {
            HoldSpeedPill(Modifier.align(Alignment.TopCenter).padding(top = 12.dp).graphicsLayer { alpha = controlsAlpha() })
        }

        val error = state.error
        if (error != null) {
            ErrorPanel(error, state, onCollapse = onCollapse)
            return@Box
        }

        val enabled = controlsVisible && interactive
        // With the controls hidden, loading still shows: a small spinner in the middle.
        if (!scrubbing && !controlsVisible && (state.isBuffering || state.hidesStaleVideo || state.blankUntilFrame || (state.loading && state.details == null))) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(36.dp))
        }
        // Waiting out a bot check (BotCheck): the spinner says why this is taking a while.
        val botWait by animateFloatAsState(
            if (!controlsVisible && BotCheck.waiting != null && BotCheck.waiting == state.current?.id) 1f else 0f,
            label = "botWait",
        )
        if (botWait > 0f) {
            Text(
                "YouTube is checking this connection. Trying again…",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                style = LocalTextStyle.current.copy(shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 8f)),
                modifier = Modifier.align(Alignment.Center).padding(top = 76.dp, start = 32.dp, end = 32.dp).graphicsLayer { alpha = botWait },
            )
        }
        if (!scrubbing) {
            SkippedNote(state, Modifier.align(Alignment.TopCenter).padding(top = 12.dp).graphicsLayer { alpha = controlsAlpha() })
        }
        // The Ask pill stays reachable while the controls are hidden.
        if (!scrubbing && !controlsVisible) {
            state.askSegment?.let { segment ->
                SkipSegmentPill(segment, enabled = true, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, top = 12.dp, bottom = 40.dp)) {
                    touched(); state.skipSegment(segment)
                }
            }
        }
        val chromeAlpha = if (scrubbing) 0f else visibleAlpha * controlsAlpha()
        VideoControlsOverlay.dimAlpha = chromeAlpha
        DisposableEffect(Unit) { onDispose { VideoControlsOverlay.dimAlpha = 0f } }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = chromeAlpha }) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.5f),
                        0.32f to Color.Black.copy(alpha = 0.08f),
                        0.58f to Color.Black.copy(alpha = 0.08f),
                        1f to Color.Black.copy(alpha = 0.62f),
                    ),
                ),
            )
            // Skipii's on-video controls: tonal tiles, seek step from Settings, cards in fullscreen.
            val tool = if (fullscreen) 40.dp else 36.dp
            val inset = if (fullscreen) Modifier.windowInsetsPadding(WindowInsets.displayCutout).padding(horizontal = 12.dp, vertical = 8.dp) else Modifier
            val dropAnchors = remember { arrayOfNulls<LayoutCoordinates>(4) }
            var dropAnchor by remember { mutableStateOf(Rect.Zero) }
            var dropRoom by remember { mutableFloatStateOf(0f) }
            fun toggleDrop(kind: DropKind, slot: Int): Boolean {
                val box = dropAnchors[0]
                val pill = dropAnchors[slot]
                if (!fullscreen || box == null || pill == null || !box.isAttached || !pill.isAttached) return false
                dropAnchor = box.localBoundingBoxOf(pill, clipBounds = false)
                dropRoom = box.size.height.toFloat()
                drop = if (drop == kind) null else kind
                return true
            }
            Box(Modifier.fillMaxSize().then(inset).onGloballyPositioned { dropAnchors[0] = it }) {
                Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (fullscreen) {
                        Text(
                            state.current?.title.orEmpty(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                        OverlayTile(stringResource(R.string.video_audio_only), enabled, tool, filled = AudioOnly.enabled, onClick = { touched(); state.setAudioOnly(!AudioOnly.enabled) }) { ink ->
                            Icon(Icons.Outlined.Headphones, null, tint = ink, modifier = Modifier.size(tool * 0.5f))
                        }
                    }
                    val stickySpeed = PlaybackSpeed.remember && !PlaybackSpeed.isNormal(state.speed)
                    OverlayPill(
                        PlaybackSpeed.label(state.speed) + "×", stringResource(R.string.video_playback_speed), enabled, tool, filled = stickySpeed,
                        modifier = Modifier.onGloballyPositioned { dropAnchors[2] = it },
                    ) {
                        touched()
                        if (!toggleDrop(DropKind.Speed, 2)) state.sheet = PlayerSheet.Speed
                    }
                    if (state.captions.tracks.isNotEmpty()) {
                        OverlayTile(
                            stringResource(R.string.video_captions), enabled, tool, filled = state.captions.on,
                            modifier = Modifier.onGloballyPositioned { dropAnchors[3] = it },
                            onClick = {
                                touched()
                                state.captions.turn(true)
                                if (!toggleDrop(DropKind.Captions, 3)) state.sheet = PlayerSheet.Captions
                            },
                        ) { ink -> Icon(Icons.Rounded.ClosedCaption, null, tint = ink, modifier = Modifier.size(tool * 0.52f)) }
                    }
                    (state.decodedHeight?.let { "${it}p" } ?: state.details?.quality)?.let { quality ->
                        OverlayPill(
                            if (QualityPreference.height == null) stringResource(R.string.video_quality_auto) + " · $quality" else quality, stringResource(R.string.video_quality), enabled, tool,
                            modifier = Modifier.onGloballyPositioned { dropAnchors[1] = it },
                        ) {
                            touched()
                            if (!toggleDrop(DropKind.Quality, 1)) state.sheet = PlayerSheet.Quality
                        }
                    }
                    if (pip?.supported == true) {
                        OverlayTile(stringResource(R.string.video_pip), enabled, tool, onClick = {
                            controlsVisible = false
                            if (!pip.enter(state.videoAspect)) state.actionMessage = str(R.string.video_pip_off)
                        }) { ink -> Icon(Icons.Rounded.PictureInPictureAlt, null, tint = ink, modifier = Modifier.size(tool * 0.5f)) }
                    }
                }
                val seek = if (fullscreen) 60.dp else 51.dp
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 32.dp else 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OverlaySeek(-1, enabled, seek) { touched(); state.seekBy(-SeekStep.ms) }
                    OverlayPlay(
                        playing = state.isPlaying,
                        buffering = state.isBuffering || (state.loading && state.details == null),
                        size = if (fullscreen) 78.dp else 66.dp,
                        enabled = enabled,
                    ) { touched(); state.togglePlay() }
                    OverlaySeek(1, enabled, seek) { touched(); state.seekBy(SeekStep.ms) }
                }
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .padding(
                            start = 14.dp,
                            end = 8.dp,
                            bottom = EdgeScrubTimeRowBottomPad + if (fullscreenControlsScrub) FullscreenControlsLift else 0.dp,
                        ),
                ) {
                    val shownMs = state.scrubPreviewMs ?: state.positionMs
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(formatDuration(shownMs), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(
                                " / " + formatDuration(state.durationMs),
                                color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                            )
                            state.chapterAt(shownMs)?.let {
                                Text(
                                    " · $it", color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        state.askSegment?.let { segment ->
                            SkipSegmentPill(segment, enabled = true) { touched(); state.skipSegment(segment) }
                        }
                        if (state.queue.isNotEmpty()) {
                            OverlayTile(stringResource(R.string.video_next), enabled, 34.dp, onClick = { touched(); state.playNextUp() }) { ink ->
                                Icon(Icons.Rounded.SkipNext, null, tint = ink, modifier = Modifier.size(20.dp))
                            }
                        }
                        OverlayTile(stringResource(if (fullscreen) R.string.video_exit_fullscreen else R.string.video_fullscreen), enabled, 34.dp, onClick = onToggleFullscreen) { ink ->
                            Icon(if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, null, tint = ink, modifier = Modifier.size(21.dp))
                        }
                    }
                }
                if (fullscreen) {
                    QualityDrop(state, drop == DropKind.Quality, dropAnchor, dropRoom, onClose = { drop = null })
                    SpeedDrop(state, drop == DropKind.Speed, dropAnchor, dropRoom, onClose = { drop = null })
                    CaptionsDrop(state, drop == DropKind.Captions, dropAnchor, dropRoom, onClose = { drop = null })
                }
            }
        }
        // Portrait keeps the thin edge bar when controls are hidden; fullscreen hides the scrubber until controls (or a drag) show.
        if (state.error == null && (!fullscreenControlsScrub || controlsVisible || scrubbing)) {
            val scrubActive = controlsVisible && interactive
            val morphFade = controlsAlpha()
            val scrubFade = if (scrubbing) 1f else morphFade
            val segments = if (SponsorBlock.enabled) state.segments.filter { it.action != SponsorBlock.Action.Off } else emptyList()
            val scrubModifier = when {
                portraitEdgeScrub -> Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // Fixed on the stage/page seam; the morph layer moves it with the video.
                    .offset(y = EdgeScrubTouchPadBelow)
                    .height(EdgeScrubTouchHeight)
                    .graphicsLayer { alpha = scrubFade }
                fullscreenControlsScrub -> Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fullscreenControlsHorizontalInsets()
                    .padding(bottom = FullscreenScrubBottomPad + FullscreenControlsLift)
                    .height(FullscreenScrubTouchHeight)
                    .graphicsLayer { alpha = scrubFade }
                else -> Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(EdgeScrubTouchPadAbove * 2)
                    .graphicsLayer { alpha = scrubFade }
            }
            Scrubber(
                segments = segments,
                positionMs = state.positionMs,
                bufferedMs = state.bufferedMs,
                durationMs = state.durationMs,
                enabled = interactive,
                active = scrubActive,
                relativeDrag = true,
                edgeToEdge = portraitEdgeScrub,
                tapToSeek = false,
                edgeTouchPadAbove = EdgeScrubTouchPadAbove,
                trackHeight = if (fullscreenControlsScrub) FullscreenScrubTrackHeight else 4.dp,
                touchHeight = when {
                    portraitEdgeScrub -> EdgeScrubTouchHeight
                    fullscreenControlsScrub -> FullscreenScrubTouchHeight
                    else -> EdgeScrubTouchPadAbove * 2
                },
                knob = if (fullscreenControlsScrub) FullscreenScrubKnob else 7.dp,
                knobDragging = if (fullscreenControlsScrub) FullscreenScrubKnobDragging else 9.dp,
                onPreview = { state.scrubPreviewMs = it; touched() },
                onSeek = { state.endScrubDragging(it); touched() },
                onDraggingChanged = { dragging ->
                    if (dragging) state.beginScrubDragging()
                    else state.endScrubDragging()
                },
                modifier = scrubModifier.zIndex(10f),
            )
        }
        if (scrubbing && scrubPreviewMs != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .then(
                        if (fullscreenControlsScrub) Modifier.fullscreenControlsHorizontalInsets() else Modifier,
                    )
                    .padding(
                        bottom = if (fullscreenControlsScrub) {
                            FullscreenTimeRowBottomFromEdge
                        } else {
                            EdgeScrubTimeRowBottomPad
                        },
                    )
                    .height(EdgeScrubTimeRowHeight)
                    .zIndex(11f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    formatDuration(scrubPreviewMs),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * The stage in audio only (Derek, 2026-10-13): no picture, just the playback controls, always
 * shown, on a tonal container the video's own shape. Every control sits exactly where the
 * video's overlay puts it (Derek, 2026-10-14): skips and play centred at the same sizes, the
 * times row and scrubber along the bottom, the skipped note at the top; only the look changes
 * (tonal buttons, primary play, thicker scrubber). The thumbnail sits underneath at the
 * stage's centred 16:9 window and shows through as it shrinks, so it lands as the mini-player,
 * which shows the thumbnail in audio only.
 *
 * Switching from or to video happens in place: [cover] fades the whole stage over the picture,
 * and [gather] brings the controls together (skips slide out from behind the play button,
 * the bottom row rises in), with a soft overshoot.
 */
@Composable
fun AudioOnlyStage(
    state: PlayerState,
    interactive: Boolean,
    onInactiveTap: () -> Unit,
    progress: () -> Float,
    controlsAlpha: () -> Float,
    modifier: Modifier = Modifier,
    cover: () -> Float = { 1f },
    gather: () -> Float = { 1f },
) {
    val c = MaterialTheme.colorScheme
    val currentInteractive by androidx.compose.runtime.rememberUpdatedState(interactive)
    val currentInactiveTap by androidx.compose.runtime.rememberUpdatedState(onInactiveTap)
    // While collapsing the controls are off (their taps fall through): a tap re-expands.
    Box(
        modifier
            .graphicsLayer { alpha = cover().coerceIn(0f, 1f) }
            .pointerInput(Unit) { detectTapGestures { if (!currentInteractive) currentInactiveTap() } },
    ) {
        Box(Modifier.fillMaxSize().aspectFit(16f / 9f)) {
            Thumbnail(state.current?.thumbnail, Modifier.fillMaxSize(), radius = 0)
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = ramp(progress(), 0.25f, 0.85f) }.background(c.surfaceContainer))
        val error = state.error
        if (error != null) {
            ErrorPanel(error, state, onCollapse = {})
            return@Box
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = controlsAlpha() * gather().coerceIn(0f, 1f) }) {
            SkippedNote(state, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
            // Same row as the video's: 48 dp skips, 66 dp play, 30 dp apart.
            val gap = 30.dp
            val skip = 48.dp
            val play = 66.dp
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Each skip starts tucked behind the play button and slides out to its place.
                fun Modifier.slide(side: Int) = graphicsLayer {
                    translationX = -side * (1f - gather()) * (gap + (play + skip) / 2).toPx()
                }
                Box(Modifier.slide(-1)) { TonalSkip(seekIcon(false), seekLabel(-1), interactive, skip) { state.seekBy(-SeekStep.ms) } }
                Box(Modifier.graphicsLayer { val k = lerpF(0.6f, 1f, gather()); scaleX = k; scaleY = k }) {
                    MorphPlayButton(
                        playing = state.isPlaying,
                        buffering = state.isBuffering || (state.loading && state.details == null),
                        size = play,
                        container = c.primary,
                        content = c.onPrimary,
                        enabled = interactive,
                    ) { state.togglePlay() }
                }
                Box(Modifier.slide(1)) { TonalSkip(seekIcon(true), seekLabel(1), interactive, skip) { state.seekBy(SeekStep.ms) } }
            }
            // Same place and padding as the video's bottom bar (its row is the fullscreen button's 34 dp).
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 8.dp, bottom = 6.dp)
                    .graphicsLayer { translationY = (1f - gather()) * 16.dp.toPx() },
            ) {
                var scrubPreview by remember { mutableStateOf<Long?>(null) }
                Row(Modifier.height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(scrubPreview ?: state.positionMs), color = c.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        " / " + formatDuration(state.durationMs),
                        color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    state.askSegment?.let { ask -> SkipSegmentPill(ask, enabled = interactive) { state.skipSegment(ask) } }
                }
                Scrubber(
                    segments = if (SponsorBlock.enabled) state.segments.filter { it.action != SponsorBlock.Action.Off } else emptyList(),
                    positionMs = state.positionMs,
                    bufferedMs = state.bufferedMs,
                    durationMs = state.durationMs,
                    enabled = interactive,
                    onPreview = { scrubPreview = it },
                    onSeek = { scrubPreview = null; state.seekTo(it) },
                    track = c.surfaceContainerHighest,
                    buffered = c.primary.copy(alpha = 0.3f),
                    played = c.primary,
                    trackHeight = 6.dp,
                    knob = 8.dp,
                    knobDragging = 10.dp,
                )
            }
        }
    }
}

@Composable
private fun TonalSkip(icon: ImageVector, description: String, enabled: Boolean, size: Dp, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier.size(size).pressable(enabled = enabled, onClick = onClick).clip(CircleShape).background(c.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = c.onSurface, modifier = Modifier.size(size * 0.62f)) }
}

/**
 * Hides the picture while the shared surface still holds the frame from before audio only
 * after video is turned back on ([PlayerState.hidesStaleVideo]); the thumbnail underneath shows
 * instead, and the fresh frame fades in.
 */
@Composable
fun Modifier.hideStaleVideo(state: PlayerState): Modifier {
    val hide = state.hidesStaleVideo || state.blankUntilFrame
    val alpha by animateFloatAsState(
        targetValue = if (hide) 0f else 1f,
        animationSpec = if (hide || rememberReducedMotion()) tween(0) else tween(220, easing = Motion.Emphasized),
        label = "freshVideo",
    )
    return graphicsLayer { this.alpha = alpha }
}

/**
 * Lays the content out at [aspect], centred: as large as fits inside the incoming box, or
 * with [cover] as small as covers it (the overflow is for the caller to clip).
 */
fun Modifier.aspectFit(aspect: Float, cover: Boolean = false) = layout { measurable, constraints ->
    val boxW = constraints.maxWidth.toFloat()
    val boxH = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else boxW / aspect
    // Height decides when the box is wider than the content (fit) or narrower (cover).
    val byHeight = (boxW / boxH > aspect) != cover
    val w = (if (byHeight) boxH * aspect else boxW).roundToInt()
    val h = (if (byHeight) boxH else boxW / aspect).roundToInt()
    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
    layout(boxW.roundToInt(), boxH.roundToInt()) {
        placeable.place(((boxW - w) / 2f).roundToInt(), ((boxH - h) / 2f).roundToInt())
    }
}

/**
 * Fullscreen (landscape, or upright for a vertical video). Swipe down (or Back, or the exit button) returns to portrait.
 * System bars are hidden by the caller while this is shown.
 */
@Composable
fun FullscreenPlayer(state: PlayerState, onExit: () -> Unit) {
    val reduced = rememberReducedMotion()
    var drag by remember { mutableStateOf(0f) }
    // Up/down on the left = brightness, on the right = volume; the middle swipes down to exit.
    val sliders = rememberFullscreenSliders()
    Box(Modifier.fillMaxSize()) {
        VideoContent(
            state = state,
            fullscreen = true,
            onCollapse = onExit,
            onToggleFullscreen = onExit,
            modifier = Modifier
                .fillMaxSize()
                .fullscreenGestures(sliders, onPull = { drag = it }, onSwipeDown = onExit)
                .then(
                    // A layer around the picture for the whole time it's fullscreen
                    // stops the video using the screen's own plane, so it stutters.
                    if (reduced || drag == 0f) Modifier
                    else Modifier.graphicsLayer { translationY = drag * 0.35f },
                ),
        )
        SliderLevel(sliders)
    }
}

@Composable
private fun HoldSpeedPill(modifier: Modifier = Modifier) {
    Text(
        "2×",
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        lineHeight = 13.sp,
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** "Skip sponsor ›" — shown while playback is inside a segment set to Ask. */
@Composable
private fun SkipSegmentPill(segment: SponsorBlock.Segment, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(32.dp)
            .pressable(enabled = enabled, onClick = onClick)
            .clip(CircleShape)
            .background(segment.category.color.copy(alpha = 0.92f))
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (segment.category.color.luminance() > 0.45f) Color(0xFF151515) else Color.White
        Text("Skip ${segment.category.short}", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Icon(SfIcons.SkipNext, null, tint = ink, modifier = Modifier.size(18.dp))
    }
}

/** "Skipped 1:12 sponsor · Undo" after an automatic skip; fades after a few seconds. */
@Composable
private fun SkippedNote(state: PlayerState, modifier: Modifier) {
    val skip = SponsorBlock.lastSkip?.takeIf { it.videoId == state.current?.id } ?: return
    LaunchedEffect(skip) {
        delay(4_000)
        if (SponsorBlock.lastSkip == skip) SponsorBlock.lastSkip = null
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Skipped ${formatDuration(skip.segment.lengthMs)} ${skip.segment.category.short}",
            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        )
        Text(
            "Undo",
            color = skip.segment.category.color, fontSize = 12.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.pressable { state.undoSkip(skip) }.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySheet(state: PlayerState, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val details = state.details ?: return
    val chosen = QualityPreference.height
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        // Scrolls: in fullscreen (landscape) only the first few rows fit (GitHub issue #1).
        Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(
            "Quality",
            fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp,
            modifier = Modifier.padding(start = PageGutter, end = PageGutter, bottom = 8.dp),
        )
        state.playingFormat?.let {
            Text(
                "Playing $it",
                color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = PageGutter, end = PageGutter, bottom = 8.dp),
            )
        }
        SheetRow("Auto", "Best up to 1080p" + ((state.decodedHeight?.let { "${it}p" } ?: details.quality)?.let { " · playing $it" } ?: ""), chosen == null) {
            state.setQuality(null); onDismiss()
        }
        details.qualities.forEach { h ->
            SheetRow("${h}p", qualityTag(h), chosen == h) { state.setQuality(h); onDismiss() }
        }
        Spacer(Modifier.height(28.dp))
        }
    }
}

/** The speed ruler (SpeedPanel.kt) on its own, opened from the controls' speed pill. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(state: PlayerState, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                "Speed",
                fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp,
                modifier = Modifier.padding(start = PageGutter, end = PageGutter, bottom = 8.dp),
            )
            SpeedPanelContent(state)
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** The video's chapters, the one playing marked; picking one seeks there and closes the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersSheet(state: PlayerState, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val chapters = state.details?.chapters.orEmpty()
    if (chapters.isEmpty()) return
    val current = chapters.indexOfLast { it.startMs <= state.positionMs }.coerceAtLeast(0)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Text(
            "Chapters",
            fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp,
            modifier = Modifier.padding(start = PageGutter, end = PageGutter, bottom = 8.dp),
        )
        Text(
            "${current + 1} of ${chapters.size}",
            color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = PageGutter, end = PageGutter, bottom = 8.dp),
        )
        // Scrolls: a long chapter list must stay reachable in landscape, where the sheet is short.
        LazyColumn(Modifier.height(340.dp)) {
            itemsIndexed(chapters) { index, chapter ->
                SheetRow(chapter.title, formatDuration(chapter.startMs), index == current) {
                    state.seekTo(chapter.startMs)
                    if (!ChaptersStayOpen.on) onDismiss()
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

/** How much the picture grows to cover a stage when it's laid out to fit it at [aspect]. */
private fun fillScale(w: Float, h: Float, aspect: Float): Float {
    if (w <= 0f || h <= 0f || aspect <= 0f) return 1f
    val stage = w / h
    return max(stage / aspect, aspect / stage)
}

private const val ZoomDecide = 0.04f
private const val ZoomStretch = 0.06f
private const val ZoomFlingMax = 8f

private fun stretched(raw: Float, fill: Float): Float {
    fun give(over: Float) = ZoomStretch * (1f - exp(-over / (2f * ZoomStretch)))
    return when {
        raw > fill -> fill * (1f + give(raw / fill - 1f))
        raw < 1f -> 1f - give(1f - raw)
        else -> raw
    }
}

private fun qualityTag(height: Int): String? = when {
    height >= 2160 -> "4K"
    height >= 1440 -> "QHD"
    height >= 1080 -> "Full HD"
    height >= 720 -> "HD"
    height <= 240 -> "Data saver"
    else -> null
}

@Composable
private fun SheetRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = PageGutter, vertical = 2.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) c.primaryContainer else c.primaryContainer.copy(alpha = 0f))
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (selected) c.onPrimaryContainer else c.onSurface)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) c.onPrimaryContainer.copy(alpha = 0.75f) else c.onSurfaceVariant)
            }
        }
        if (selected) Icon(SfIcons.Check, "Selected", tint = c.onPrimaryContainer)
    }
}

@Composable
private fun BoxScope.ErrorPanel(message: String, state: PlayerState, onCollapse: () -> Unit) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)))
    Column(
        Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(SfIcons.ErrorOutline, null, tint = Color.White, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(8.dp))
        if (state.botCheck) {
            Text(stringResource(R.string.player_error_bot_check), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.player_error_bot_check_hint), color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
        } else {
            Text(message, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.pressable(onClick = state::retry).clip(CircleShape).background(Color.White).padding(horizontal = 18.dp, vertical = 9.dp),
            ) { Text(stringResource(R.string.video_try_again), color = Color(0xFF141414), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1) }
            if (state.botCheck) {
                Box(
                    Modifier.pressable { state.openInYouTube(context) }.clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 18.dp, vertical = 9.dp),
                ) { Text(stringResource(R.string.video_open_in_youtube), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun GlassButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    iconSize: Dp = 22.dp,
    tinted: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
            .pressable(enabled = enabled, onClick = onClick)
            .clip(CircleShape)
            .background(
                when {
                    active -> c.primary
                    tinted -> Color.Black.copy(alpha = 0.38f)
                    else -> Color.Black.copy(alpha = 0f)
                },
            ),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = if (active) c.onPrimary else Color.White, modifier = Modifier.size(iconSize)) }
}

@Composable
private fun OverlayTile(
    description: String,
    enabled: Boolean,
    size: Dp,
    filled: Boolean = false,
    tile: Color = MaterialTheme.colorScheme.surfaceVariant,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable (ink: Color) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val view = LocalView.current
    Box(
        modifier
            .squishOn(filled)
            .size(size)
            .pressable(enabled = enabled) { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); onClick() }
            .clip(CircleShape)
            .background(if (filled) c.primary else tile)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content(if (filled) c.onPrimary else c.onSurface) }
}

/** A worded control on the video (speed, quality): a capsule [height] tall, otherwise [OverlayTile]'s look. */
@Composable
private fun OverlayPill(
    label: String,
    description: String,
    enabled: Boolean,
    height: Dp,
    filled: Boolean = false,
    tile: Color = MaterialTheme.colorScheme.surfaceVariant,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val view = LocalView.current
    Box(
        modifier
            .squishOn(filled)
            .height(height)
            .widthIn(min = height)
            .pressable(enabled = enabled) { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); onClick() }
            .clip(CircleShape)
            .background(if (filled) c.primary else tile)
            .padding(horizontal = 11.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (filled) c.onPrimary else c.onSurface, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/** −10 / +10 on the video, as on the pill: the arrow kicks the way it seeks. */
@Composable
private fun OverlaySeek(side: Int, enabled: Boolean, size: Dp, tile: Color = MaterialTheme.colorScheme.surfaceVariant, onSeek: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    val spin = remember { Animatable(0f) }
    Box(
        Modifier
            .size(size)
            .pressable(enabled = enabled) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onSeek()
                if (!reduced) scope.launch { spin.animateTo(0f, keyframes { durationMillis = 420; side * 50f at 150 }) }
            }
            .clip(CircleShape)
            .background(tile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            seekIcon(forward = side > 0),
            contentDescription = seekLabel(side),
            tint = c.onSurface,
            modifier = Modifier.size(size * 0.51f).graphicsLayer { rotationZ = spin.value },
        )
    }
}

/** Play / pause on the video: a full-primary circle [size] across (Derek, 2026-10-31); a spinner while loading. */
@Composable
private fun OverlayPlay(playing: Boolean, buffering: Boolean, size: Dp, enabled: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val view = LocalView.current
    Box(
        Modifier
            .size(size)
            .pressable(enabled = enabled) { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); onClick() }
            .clip(CircleShape)
            .background(c.primary),
        contentAlignment = Alignment.Center,
    ) {
        if (buffering) {
            CircularProgressIndicator(color = c.onPrimary, strokeWidth = 3.dp, modifier = Modifier.size(size * 0.4f))
        } else {
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                stringResource(if (playing) R.string.video_pause else R.string.video_play),
                tint = c.onPrimary,
                modifier = Modifier.size(size * 0.46f).squishOn(playing),
            )
        }
    }
}

/**
 * Hides the picture while the shared surface still holds the frame from before audio only
 * after video is turned back on ([PlayerState.hidesStaleVideo]); the thumbnail underneath shows
 * instead, and the fresh frame fades in.
 */

private enum class DropKind { Quality, Speed, Captions }

/**
 * Fullscreen's captions card (docs/design/captions.html), the quality card's twin hanging from the CC tile: Off, every
 * track, then the translations made on the phone. A pick lands its check and the card folds; picking a translation
 * that still has to download starts it (the captions say so on the video).
 */
@Composable
private fun BoxScope.CaptionsDrop(state: PlayerState, open: Boolean, anchor: Rect, boxHeight: Float, onClose: () -> Unit) {
    val shown = dropShown(open)
    if (shown <= 0f || anchor == Rect.Zero) return
    val currentClose by androidx.compose.runtime.rememberUpdatedState(onClose)
    val cap = state.captions
    // Fixed while the card is up, so a pick never reshuffles the rows under the finger.
    val options = remember { captionOptions(cap, all = false).filterIsInstance<CaptionOption.Pick>() }
    OverlayDrop(open, shown, anchor, boxHeight, stringResource(R.string.video_drop_captions), openAt = { if (!cap.on) 0 else 1 + options.indexOfFirst { cap.shows(it) } }, onClose = onClose) { scope ->
        DropRow(title = stringResource(R.string.video_captions_off), tag = null, selected = !cap.on) {
            if (!open) return@DropRow
            cap.turn(false)
            scope.launch { delay(420); currentClose() }
        }
        options.forEach { option ->
            DropRow(
                title = option.title,
                tag = when {
                    option.choice.translateTo == null -> if (option.choice.track.auto) stringResource(R.string.video_captions_tag_auto) else null
                    option.busy -> stringResource(R.string.video_captions_tag_downloading)
                    option.ready -> stringResource(R.string.video_captions_tag_translated)
                    else -> stringResource(R.string.video_captions_tag_download)
                },
                selected = cap.shows(option),
            ) {
                if (!open) return@DropRow
                cap.pick(option.choice)
                scope.launch { delay(420); currentClose() }
            }
        }
    }
}

/**
 * Fullscreen's quality picker (GitHub #25; Derek picked A "Drop" in `docs/design/fullscreen-quality.html`): a small
 * card of the overlay's glass hanging from the quality pill instead of a sheet over the whole video. Auto, then every
 * height with its tag. A pick lands its check, then the card folds after 420 ms.
 */
@Composable
private fun BoxScope.QualityDrop(state: PlayerState, open: Boolean, anchor: Rect, boxHeight: Float, onClose: () -> Unit) {
    val details = state.details ?: return
    val shown = dropShown(open)
    if (shown <= 0f || anchor == Rect.Zero) return
    val currentClose by androidx.compose.runtime.rememberUpdatedState(onClose)
    val rows = remember(details) { listOf<Int?>(null) + details.qualities }
    var picked by remember { mutableStateOf(QualityPreference.height) }
    OverlayDrop(open, shown, anchor, boxHeight, stringResource(R.string.video_drop_quality), openAt = { rows.indexOf(picked) }, onClose = onClose) { scope ->
        val playing = state.decodedHeight?.let { "${it}p" } ?: details.quality
        rows.forEach { h ->
            DropRow(
                title = if (h == null) stringResource(R.string.video_quality_auto) else "${h}p",
                tag = if (h == null) playing else qualityTag(h),
                selected = picked == h,
            ) {
                if (!open) return@DropRow
                picked = h
                if (h != QualityPreference.height) state.setQuality(h)
                scope.launch { delay(420); currentClose() }
            }
        }
    }
}

/**
 * Fullscreen's speed picker (GitHub #31: the ruler's sheet filled the landscape screen): the quality card's look,
 * hanging from the speed pill, with preset speeds. A speed set on the ruler (portrait) that isn't a preset shows as
 * its own "Custom" row. Under them, "Every video" is the ruler's "Remember for every video" (it stays open on that one).
 */
@Composable
private fun BoxScope.SpeedDrop(state: PlayerState, open: Boolean, anchor: Rect, boxHeight: Float, onClose: () -> Unit) {
    val shown = dropShown(open)
    if (shown <= 0f || anchor == Rect.Zero) return
    val currentClose by androidx.compose.runtime.rememberUpdatedState(onClose)
    var picked by remember { mutableFloatStateOf(PlaybackSpeed.snap(state.speed)) }
    // Fixed while the card is up, so a pick never reshuffles the rows under the finger.
    val rows = remember {
        val custom = picked.takeIf { s -> SpeedPresets.none { abs(it - s) < PlaybackSpeed.STEP / 2 } }
        (SpeedPresets + listOfNotNull(custom)).sorted()
    }
    fun same(a: Float, b: Float) = abs(a - b) < PlaybackSpeed.STEP / 2
    OverlayDrop(open, shown, anchor, boxHeight, stringResource(R.string.video_drop_speed), openAt = { rows.indexOfFirst { same(it, picked) } }, onClose = onClose) { scope ->
        rows.forEach { s ->
            DropRow(
                title = PlaybackSpeed.label(s) + "×",
                tag = when {
                    PlaybackSpeed.isNormal(s) -> stringResource(R.string.video_speed_normal)
                    SpeedPresets.none { same(it, s) } -> stringResource(R.string.video_speed_custom)
                    else -> null
                },
                selected = same(picked, s),
            ) {
                if (!open) return@DropRow
                picked = s
                state.commitSpeed(s)
                scope.launch { delay(420); currentClose() }
            }
        }
        Spacer(Modifier.height(5.dp))
        DropRow(title = stringResource(R.string.video_speed_every_video), tag = null, selected = PlaybackSpeed.remember) { if (open) state.toggleRememberSpeed() }
    }
}

/** 0.5–3×, the ruler's range. */
private val SpeedPresets = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

/**
 * The card both pickers share ([shown] from [dropShown]): the overlay's glass, hanging 8 dp under its pill ([anchor], in this box's pixels;
 * [boxHeight] = that box's), right edges aligned, popping out of the pill. It scrolls when the rows are taller than the
 * room above the scrubber, opening scrolled to row [openAt] (centred). A tap anywhere else (a layer BEHIND the card,
 * which keeps its own touches) closes it; Back too (VideoContent).
 */
/** How far a card is out (0 gone … 1 open); its state starts over each time it opens from 0. */
@Composable
private fun dropShown(open: Boolean): Float {
    val reduced = rememberReducedMotion()
    val shown by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = if (reduced) tween(0) else if (open) tween(420, easing = Motion.Pop) else tween(220, easing = Motion.Exit),
        label = "overlayDrop",
    )
    return shown
}

@Composable
private fun BoxScope.OverlayDrop(
    open: Boolean,
    shown: Float,
    anchor: Rect,
    boxHeight: Float,
    title: String,
    openAt: () -> Int,
    onClose: () -> Unit,
    rows: @Composable ColumnScope.(CoroutineScope) -> Unit,
) {
    val currentClose by androidx.compose.runtime.rememberUpdatedState(onClose)
    if (open) {
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    // Everything this touch does is taken, so a tap beside the card only closes it.
                    awaitFirstDown(requireUnconsumed = false).consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                    currentClose()
                }
            },
        )
    }
    val density = LocalDensity.current
    val gap = with(density) { 8.dp.toPx() }
    val width = with(density) { DropWidth.toPx() }
    // Never over the time row and scrubber.
    val room = with(density) { (boxHeight - anchor.bottom - gap).toDp() } - DropBottomReserve
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    LaunchedEffect(Unit) {
        // Open scrolled to the chosen row, centred when the list scrolls.
        withFrameNanos { }
        val index = openAt().coerceAtLeast(0)
        val row = with(density) { (DropRowHeight + DropRowGap).toPx() }
        val top = with(density) { DropHeaderHeight.toPx() } + index * row
        scroll.scrollTo((top - (scroll.viewportSize - row) / 2f).roundToInt().coerceAtLeast(0))
    }
    Column(
        Modifier
            .offset { IntOffset((anchor.right - width).roundToInt(), (anchor.bottom + gap).roundToInt()) }
            .width(DropWidth)
            .heightIn(max = room.coerceAtLeast(DropHeaderHeight + DropRowHeight * 2))
            .graphicsLayer {
                val s = 0.4f + 0.6f * shown
                scaleX = s
                scaleY = s
                alpha = (shown * 3f).coerceIn(0f, 1f)
                // Grows out of the pill, from under its middle.
                transformOrigin = TransformOrigin(1f - anchor.width / 2f / size.width, -10.dp.toPx() / size.height)
                shape = RoundedCornerShape(22.dp)
                clip = true
            }
            .background(Color(0xFF121216).copy(alpha = 0.72f))
            .verticalScroll(scroll)
            .padding(6.dp),
    ) {
        Text(
            title,
            color = Color.White.copy(alpha = 0.55f), fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp,
            maxLines = 1,
            modifier = Modifier.height(DropHeaderHeight).padding(start = 12.dp, top = 6.dp),
        )
        rows(scope)
    }
}

@Composable
private fun DropRow(title: String, tag: String?, selected: Boolean, onClick: () -> Unit) {
    val reduced = rememberReducedMotion()
    val check by animateFloatAsState(
        if (selected) 1f else 0f,
        if (reduced) tween(0) else if (selected) tween(380, easing = Motion.Pop) else tween(150),
        label = "dropCheck",
    )
    val fill by animateFloatAsState(if (selected) 0.16f else 0f, tween(if (reduced) 0 else 250), label = "dropFill")
    Row(
        Modifier
            .padding(bottom = DropRowGap)
            .fillMaxWidth()
            .height(DropRowHeight)
            .pressable(pressedScale = 0.97f, onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = fill))
            .padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        Text(
            tag.orEmpty(),
            color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Rounded.Check, if (selected) stringResource(R.string.video_selected) else null, tint = Color.White,
            modifier = Modifier.size(18.dp).graphicsLayer {
                scaleX = 0.3f + 0.7f * check
                scaleY = 0.3f + 0.7f * check
                alpha = check.coerceIn(0f, 1f)
            },
        )
    }
}

private val DropWidth = 184.dp
private val DropRowHeight = 38.dp
private val DropRowGap = 1.dp
private val DropHeaderHeight = 24.dp
/** Kept clear under the card: the time row and the scrubber. */
private val DropBottomReserve = 64.dp

/** A labelled pill in the controls' top row (chapters, speed, quality), the same glass as the buttons. */
@Composable
private fun ControlPill(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .pressable(enabled = enabled, onClick = onClick)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.38f))
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) { Text(label, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Black) }
}

/** A burst stays open this long after its last tap; further taps on that side add to it. */
private const val SeekBurstWindowMs = 600L

/**
 * Double-tap seek feedback: a glass pill at 22% / 78% across the video with the rewind /
 * fast-forward glyph and the burst's running total. Springs in; every tap ([taps] changes)
 * squishes it, dashes the glyph the way it seeks and throws sparks (the like Pop's burst).
 * Reduced motion: fade only.
 */
@Composable
private fun SeekBurstPill(side: Int, seconds: Int, taps: Int, active: Boolean, fullscreen: Boolean) {
    val reduced = rememberReducedMotion()
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(if (reduced) 0 else if (active) 140 else 200, easing = Motion.Emphasized),
        label = "seekPillAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (active || reduced) 1f else 0.4f,
        animationSpec = if (active) spring(dampingRatio = 0.5f, stiffness = 500f) else tween(200, easing = Motion.Emphasized),
        label = "seekPillScale",
    )
    val squishX = remember { Animatable(1f) }
    val squishY = remember { Animatable(1f) }
    val dash = remember { Animatable(0f) }
    val sparks = remember { Animatable(1f) } // 0→1 = sparks flying; 1 = none
    LaunchedEffect(taps) {
        if (taps == 0 || reduced) return@LaunchedEffect
        launch { squishX.animateTo(1f, keyframes { durationMillis = 440; 1.14f at 120; 0.96f at 270 }) }
        launch { squishY.animateTo(1f, keyframes { durationMillis = 440; 0.88f at 120; 1.04f at 270 }) }
        launch { dash.animateTo(0f, keyframes { durationMillis = 380; 1f at 115 }) }
        launch { sparks.snapTo(0f); sparks.animateTo(1f, tween(520, easing = Motion.Emphasized)) }
    }
    val sparkColor = MaterialTheme.colorScheme.primary
    val font = if (fullscreen) 20.sp else 16.sp
    Row(
        Modifier
            .layout { measurable, constraints ->
                val pill = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val centerX = constraints.maxWidth * (if (side < 0) 0.22f else 0.78f)
                    pill.place((centerX - pill.width / 2f).roundToInt(), (constraints.maxHeight - pill.height) / 2)
                }
            }
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale * squishX.value
                scaleY = scale * squishY.value
            }
            .drawBehind {
                val t = sparks.value
                if (t >= 1f) return@drawBehind
                // Eight sparks, from just inside the pill out past its edge, fading.
                val reach = size.width / 2f + 16.dp.toPx()
                for (i in 0 until 8) {
                    val a = (i / 8f + 1f / 16f) * 2f * PI.toFloat()
                    val from = Offset(cos(a) * size.width * 0.36f, sin(a) * 12.dp.toPx())
                    val to = Offset(cos(a) * reach, sin(a) * reach * 0.62f)
                    drawCircle(
                        (if (i % 2 == 0) sparkColor else Color.White).copy(alpha = 1f - t),
                        radius = 3.5.dp.toPx() * (1f - 0.65f * t),
                        center = center + from + (to - from) * t,
                    )
                }
            }
            .height(if (fullscreen) 48.dp else 40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.42f))
            .padding(start = if (fullscreen) 14.dp else 11.dp, end = if (fullscreen) 18.dp else 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (side < 0) SfIcons.FastRewind else SfIcons.FastForward,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(if (fullscreen) 28.dp else 22.dp)
                .graphicsLayer { translationX = side * 6.dp.toPx() * dash.value },
        )
        Spacer(Modifier.width(8.dp))
        Text(if (side < 0) "−" else "+", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = font)
        RollingText("$seconds", Color.White, up = true, fontSize = font)
    }
}

/**
 * Play/pause that changes shape with state: a squircle while playing, a circle when
 * paused (Material 3 Expressive shape morph). Shows a spinner while buffering.
 *
 * [filled] swaps in Apple's solid glyphs for the outlined ones — for the bare button in
 * the mini-player, where nothing but the glyph carries the play/pause state.
 */
@Composable
fun MorphPlayButton(
    playing: Boolean,
    buffering: Boolean,
    size: Dp,
    container: Color,
    content: Color,
    enabled: Boolean = true,
    filled: Boolean = false,
    backdrop: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val reduced = rememberReducedMotion()
    val radius by animateDpAsState(
        targetValue = if (playing) size * 0.33f else size / 2,
        animationSpec = if (reduced) tween(0) else spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "playShape",
    )
    Box(
        modifier = Modifier
            .size(size)
            .pressable(enabled = enabled, onClick = onClick)
            .clip(RoundedCornerShape(radius))
            .then(backdrop)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        if (buffering) {
            CircularProgressIndicator(color = content, strokeWidth = 3.dp, modifier = Modifier.size(size * 0.42f))
        } else {
            Icon(
                when {
                    playing -> if (filled) SfIcons.PauseFill else SfIcons.Pause
                    else -> if (filled) SfIcons.PlayFill else SfIcons.Play
                },
                if (playing) "Pause" else "Play",
                tint = content,
                modifier = Modifier.size(size * 0.46f),
            )
        }
    }
}

private val ScrubberActiveRed = Color(0xFFFF0033)

/** Touch above the bar (on the video) and below it (into the page — the list must not steal this). */
internal val EdgeScrubTouchPadAbove = 14.dp
internal val EdgeScrubTouchPadBelow = 36.dp
internal val EdgeScrubTouchHeight = EdgeScrubTouchPadAbove + EdgeScrubTouchPadBelow
/** Current / total row above the bar (fullscreen chip sets the row height). */
private val EdgeScrubTimeRowBottomPad = EdgeScrubTouchPadAbove + 6.dp
private val EdgeScrubTimeRowHeight = 34.dp
/** Landscape fullscreen: shared horizontal inset for the time row and scrubber. */
private val FullscreenControlsHorizontalPad = 20.dp

/** Equal left/right margin: notch cutout often hits one side only — balance to the larger inset. */
private fun Modifier.fullscreenControlsHorizontalInsets(): Modifier = composed {
    val layoutDirection = LocalLayoutDirection.current
    val cutout = WindowInsets.displayCutout.asPaddingValues()
    val balanced = maxOf(
        cutout.calculateStartPadding(layoutDirection),
        cutout.calculateEndPadding(layoutDirection),
    )
    padding(
        start = balanced + FullscreenControlsHorizontalPad,
        end = balanced + FullscreenControlsHorizontalPad,
    )
}
private val FullscreenControlsVerticalPad = 8.dp
private val FullscreenScrubTrackHeight = 4.dp
private val FullscreenScrubTouchHeight = 30.dp
private val FullscreenScrubBottomPad = 6.dp
private val FullscreenControlsLift = 10.dp
/** Bottom of the time row from the video edge (controls overlay includes [FullscreenControlsVerticalPad]). */
private val FullscreenTimeRowBottomFromEdge: Dp
    get() = FullscreenControlsVerticalPad + EdgeScrubTimeRowBottomPad + FullscreenControlsLift
/** Knob radii (not diameters). */
private val FullscreenScrubKnob = 5.dp
private val FullscreenScrubKnobDragging = 6.dp

/**
 * Scrubber: buffered and played tracks, optional knob. On the video stage the bar is pinned to
 * the bottom edge (full width); [active] turns the played track red and shows the knob. Dragging
 * can follow the finger ([relativeDrag] false) or slide from where you started (true).
 */
@Composable
private fun Scrubber(
    segments: List<SponsorBlock.Segment>,
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onPreview: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onDraggingChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    active: Boolean = true,
    relativeDrag: Boolean = false,
    edgeToEdge: Boolean = false,
    edgeTouchPadAbove: Dp = EdgeScrubTouchPadAbove,
    tapToSeek: Boolean = true,
    track: Color = Color.White.copy(alpha = 0.3f),
    buffered: Color = Color.White.copy(alpha = 0.5f),
    played: Color = Color.White,
    trackHeight: Dp = 4.dp,
    touchHeight: Dp = 24.dp,
    knob: Dp = 7.dp,
    knobDragging: Dp = 9.dp,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val currentPositionMs by rememberUpdatedState(positionMs)
    val duration = durationMs.coerceAtLeast(1L)
    val playedFraction = dragFraction ?: (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val bufferedFraction = (bufferedMs.toFloat() / duration).coerceIn(0f, 1f)
    val dragging = dragFraction != null
    // Controls visible, or a lone drag while they're hidden: red + thumb, no overlay.
    val chromeActive = active || dragging
    val playedColor by animateColorAsState(
        if (chromeActive) ScrubberActiveRed else played,
        tween(160, easing = Motion.Emphasized),
        label = "scrubPlayed",
    )
    val knobRadius by animateDpAsState(if (dragging) knobDragging else knob, label = "knob")
    val h = if (edgeToEdge) 2.dp else trackHeight
    val insetEnds = !edgeToEdge
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(touchHeight)
            .pointerInput(enabled, durationMs, relativeDrag, tapToSeek, edgeToEdge, trackHeight) {
                if (!enabled || durationMs <= 0) return@pointerInput
                val slop = viewConfiguration.touchSlop * 0.35f
                val endInset = if (edgeToEdge) 0f else (trackHeight / 2).toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val width = (size.width - endInset * 2f).coerceAtLeast(1f)
                    var fraction = (currentPositionMs.toFloat() / duration).coerceIn(0f, 1f)
                    var dragged = false
                    var slopAccum = Offset.Zero
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: run {
                            if (dragged) onDraggingChanged(false)
                            break
                        }
                        if (!change.pressed) {
                            if (dragged) onSeek((fraction * durationMs).toLong())
                            else if (tapToSeek) {
                                val x = (change.position.x - endInset).coerceIn(0f, width)
                                onSeek((x / width * durationMs).toLong())
                            }
                            dragFraction = null
                            if (dragged) onDraggingChanged(false)
                            break
                        }
                        val delta = change.positionChange()
                        if (!dragged) {
                            slopAccum += delta
                            if (slopAccum.getDistance() < slop) continue
                            dragged = true
                            onDraggingChanged(true)
                            fraction = (currentPositionMs.toFloat() / duration).coerceIn(0f, 1f)
                            dragFraction = fraction
                            onPreview((fraction * durationMs).toLong())
                        } else {
                            fraction = if (relativeDrag) {
                                (fraction + delta.x / width).coerceIn(0f, 1f)
                            } else {
                                ((change.position.x - endInset) / width).coerceIn(0f, 1f)
                            }
                            dragFraction = fraction
                            onPreview((fraction * durationMs).toLong())
                        }
                        change.consume()
                    }
                }
            },
    ) {
        val trackH = h.toPx()
        val centerY = if (edgeToEdge) edgeTouchPadAbove.toPx() else size.height / 2f
        val y = centerY - trackH / 2f
        val r = CornerRadius(trackH / 2f)
        val endInset = if (insetEnds) trackH / 2f else 0f
        val trackW = size.width - endInset * 2f
        val left = endInset
        drawRoundRect(track, Offset(left, y), Size(trackW, trackH), r)
        drawRoundRect(buffered, Offset(left, y), Size(trackW * bufferedFraction, trackH), r)
        drawRoundRect(playedColor, Offset(left, y), Size(trackW * playedFraction.coerceAtLeast(0f), trackH), r)
        segments.forEach { seg ->
            val x0 = left + trackW * (seg.startMs.toFloat() / duration).coerceIn(0f, 1f)
            val x1 = left + trackW * (seg.endMs.toFloat() / duration).coerceIn(0f, 1f)
            if (x1 > x0) drawRoundRect(seg.category.color, Offset(x0, y), Size(x1 - x0, trackH), r)
        }
        if (chromeActive) {
            val knobR = knobRadius.toPx()
            val knobX = (left + trackW * playedFraction).coerceIn(left + knobR, left + trackW - knobR)
            drawCircle(playedColor, radius = knobR, center = Offset(knobX, centerY))
        }
    }
}
