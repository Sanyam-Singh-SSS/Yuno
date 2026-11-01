package com.decio.skipii

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val PLAYER_PULL_FLING_VELOCITY = 400.dp
private const val PLAYER_SCRIM_ALPHA = 0.32f

/**
 * Square, but a full sheet-height above the sheet's top edge.
 *
 * The sheet clips to this shape. On the way down the video travels ahead of
 * the sheet, up above its top edge — a plain rectangle cut it off there, so
 * the picture froze at the edge and jumped into the mini player afterwards.
 */
private object PlayerSheetShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: androidx.compose.ui.unit.Density): Outline =
        Outline.Rectangle(Rect(0f, -size.height, size.width, size.height))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    visible: Boolean,
    player: PlayerState,
    motion: PlayerSheetMotion,
    dock: PlayerDock,
    reducedMotion: Boolean,
    onOpen: (VideoItem) -> Unit,
    onDismiss: () -> Unit,
    onFullscreen: () -> Unit,
    bottomClearance: Dp,
) {
    if (!visible || player.current == null) return
    motion.windowInfo = LocalWindowInfo.current
    motion.flingVelocity = with(LocalDensity.current) { PLAYER_PULL_FLING_VELOCITY.toPx() }
    val density = LocalDensity.current
    val confirmSheetValue: (SheetValue) -> Boolean = { value ->
        if (value == SheetValue.Hidden && !reducedMotion) {
            motion.close()
            false
        } else {
            true
        }
    }
    val sheetState = rememberSaveable(
        saver = SheetState.Saver(
            skipPartiallyExpanded = true,
            confirmValueChange = confirmSheetValue,
            density = density,
            skipHiddenState = false,
        ),
    ) {
        SheetState(
            skipPartiallyExpanded = true,
            density = density,
            initialValue = if (motion.holding) SheetValue.Expanded else SheetValue.Hidden,
            confirmValueChange = confirmSheetValue,
        )
    }
    DisposableEffect(sheetState) {
        motion.sheet = sheetState
        dock.sheetOffset = motion::position
        onDispose {
            dock.sheetOffset = null
            motion.onSheetGone()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        scrimColor = if (reducedMotion) BottomSheetDefaults.ScrimColor else Color.Transparent,
        shape = PlayerSheetShape,
        containerColor = Color.Transparent,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        sheetMaxWidth = Dp.Unspecified,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = reducedMotion),
    ) {
        BackHandler(enabled = !reducedMotion) { motion.close() }
        val sheetWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(sheetWindow, reducedMotion) {
            if (!reducedMotion) motion.attachWindow(sheetWindow)
            onDispose { }
        }
        Box(
            Modifier
                .offset { IntOffset(0, motion.contentOffsetPx()) }
                .drawBehind {
                    val open = dock.openFraction()
                    if (!reducedMotion && open > 0f && !(open >= 1f && dock.attached)) {
                        val top = motion.position().takeUnless { it.isNaN() } ?: 0f
                        drawRect(
                            color = Color.Black.copy(alpha = PLAYER_SCRIM_ALPHA * open),
                            topLeft = Offset(0f, -top),
                            size = Size(size.width, size.height),
                        )
                    }
                }
                .onSizeChanged {
                    dock.sheetTravel = it.height.toFloat()
                    motion.laidOut = true
                }
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
