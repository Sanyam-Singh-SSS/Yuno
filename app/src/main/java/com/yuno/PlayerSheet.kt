package com.yuno

import android.os.Build
import android.view.WindowManager
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val PLAYER_PULL_FLING_VELOCITY = 400.dp
private const val PLAYER_SCRIM_ALPHA = 0.32f

/**
 * The expanded player, in a window that does not animate itself.
 *
 * Material's sheet window slides and fades on its own, and it has already
 * chosen that animation by the time anything in the app can see the window.
 * This dialog's theme has no window animation, and the animation is cleared
 * again before [ComponentDialog.show].
 */
@Composable
fun NowPlayingSheet(
    visible: Boolean,
    fullscreen: Boolean,
    player: PlayerState,
    motion: PlayerSheetMotion,
    dock: PlayerDock,
    reducedMotion: Boolean,
    onOpen: (VideoItem) -> Unit,
    onDismiss: () -> Unit,
    onFullscreen: () -> Unit,
    bottomClearance: Dp,
    heldVideo: VideoItem? = null,
    heldOrigin: HeldItem? = null,
    onDismissHeld: () -> Unit = {},
) {
    if (!visible || player.current == null) return
    motion.windowInfo = LocalWindowInfo.current
    motion.flingVelocity = with(LocalDensity.current) { PLAYER_PULL_FLING_VELOCITY.toPx() }
    // Reduced motion never goes through [PlayerSheetMotion.open], which is
    // otherwise what marks the sheet as up.
    SideEffect { if (!dock.sheetUp) dock.sheetUp = true }
    LaunchedEffect(motion.laidOut) { if (motion.laidOut) motion.markPlaced() }
    DisposableEffect(motion) {
        dock.sheetOffset = motion::position
        onDispose {
            dock.sheetOffset = null
            motion.onSheetGone()
        }
    }
    val sheetHaze = remember { HazeState() }
    PlayerSheetWindow(motion, reducedMotion, fullscreen) {
        Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().hazeSource(sheetHaze)) {
        BackHandler(enabled = true) {
            when {
                // The sheet window has the back press. Leaving fullscreen has to
                // happen here; closing the sheet instead used to drop the window.
                fullscreen -> onFullscreen()
                reducedMotion -> onDismiss()
                else -> motion.close()
            }
        }
        if (fullscreen) {
            // Same window as the portrait player. Moving the picture into the
            // activity's window made playback stutter, and building this window
            // again on the way out crashed.
            FullscreenPlayer(player, onExit = onFullscreen)
        } else {
        Box(
            Modifier
                .offset { IntOffset(0, motion.contentOffsetPx()) }
                .graphicsLayer { alpha = if (motion.contentReady()) 1f else 0f }
                .drawBehind {
                    val open = dock.openFraction()
                    val fade = dock.pageAlpha(motion.holding)
                    if (!reducedMotion && fade > 0f && open < 1f) {
                        val top = motion.position().takeUnless { it.isNaN() } ?: 0f
                        drawRect(
                            color = Color.Black.copy(alpha = PLAYER_SCRIM_ALPHA * fade),
                            topLeft = Offset(0f, -top),
                            size = Size(size.width, size.height),
                        )
                    }
                }
                .onSizeChanged { motion.noteLaidOut(it.height.toFloat()) }
                .fillMaxSize(),
        ) {
            CompositionLocalProvider(LocalPlayerDock provides dock) {
                PlayerScreen(
                    state = player,
                    sheetMotion = motion,
                    dock = dock,
                    onOpen = onOpen,
                    onCollapse = { motion.close() },
                    onFullscreen = onFullscreen,
                    bottomClearance = bottomClearance,
                )
            }
        }
        }
        }
        VideoHeldMenu(
            video = heldVideo,
            held = heldOrigin,
            hazeState = sheetHaze,
            player = player,
            onOpen = onOpen,
            onDismiss = onDismissHeld,
        )
        }
    }
}

@Composable
private fun PlayerSheetWindow(
    motion: PlayerSheetMotion,
    reducedMotion: Boolean,
    fullscreen: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val parentView = LocalView.current
    val configuration = LocalConfiguration.current
    val composition = rememberCompositionContext()
    val currentContent by rememberUpdatedState(content)
    val dialog = remember(parentView, context) {
        ComponentDialog(context, R.style.Theme_Yuno_PlayerSheet).apply {
            val window = window ?: error("Player sheet has no window")
            window.requestFeature(android.view.Window.FEATURE_NO_TITLE)
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.setDimAmount(0f)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            // Before the decor is installed, and again before show. The theme
            // already has no animation; this is the backstop for the first frame.
            window.setWindowAnimations(0)
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setSoftInputMode(
                if (Build.VERSION.SDK_INT >= 30) {
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                },
            )
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            val composeView = ComposeView(context).apply {
                setParentCompositionContext(composition)
                setContent {
                    CompositionLocalProvider(LocalPlayerWindow provides window) {
                        currentContent()
                    }
                }
            }
            setContentView(composeView)
            // setContentView copies the theme's animation into the window. Clear it
            // again now, still before show(), so the first frame cannot slide.
            window.setWindowAnimations(0)
        }
    }
    DisposableEffect(dialog) {
        val window = dialog.window
        window?.setWindowAnimations(0)
        if (!reducedMotion) motion.attachWindow(window)
        dialog.show()
        onDispose {
            // The exit animation is chosen as the window is removed.
            window?.setWindowAnimations(0)
            dialog.dismiss()
        }
    }
    SideEffect {
        configuration.orientation
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
        )
    }
    DisposableEffect(dialog, fullscreen) {
        val window = dialog.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (fullscreen) {
            controller?.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose { }
    }
}

/** The sheet's own window, so fullscreen brightness changes the window that's actually showing. */
internal val LocalPlayerWindow = androidx.compose.runtime.staticCompositionLocalOf<android.view.Window?> { null }
