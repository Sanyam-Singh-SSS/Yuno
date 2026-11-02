package com.decio.skipii

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import com.decio.skipii.icons.SfIcons
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * BitChord's held-item menu (HeldContextMenu): a hold lifts the video off the
 * page, the page behind it blurs, and the actions hang off the lifted picture.
 * What lifts is the row exactly as it was drawn.
 */
@Stable
class HeldItem internal constructor(val bounds: Rect) {
    var image: ImageBitmap? by mutableStateOf(null)
        internal set
}

/** The hold that just opened a menu. [consume] is valid for the call that opens it. */
object LongPressOrigin {
    private var held: HeldItem? = null
    private var recordedAt = 0L

    internal fun record(item: HeldItem) {
        held = item
        recordedAt = System.nanoTime()
    }

    fun consume(): HeldItem? {
        val item = held?.takeIf { System.nanoTime() - recordedAt < 500_000_000L }
        held = null
        return item
    }
}

/** Records the held row the frame after the hold, for [HeldItem.image]. */
internal class HoldCapture {
    var coordinates: LayoutCoordinates? = null
    var pending: HeldItem? = null
    var requests by mutableIntStateOf(0)
    var handled = 0

    fun onHold() {
        val coordinates = coordinates?.takeIf { it.isAttached } ?: return
        val item = HeldItem(Rect(coordinates.positionInWindow(), coordinates.size.toSize()))
        LongPressOrigin.record(item)
        pending = item
        requests++
    }

    fun draw(scope: ContentDrawScope, graphics: GraphicsContext, launch: CoroutineScope) {
        scope.drawContent()
        val request = requests
        if (request == handled) return
        handled = request
        val item = pending ?: return
        pending = null
        val layer = graphics.createGraphicsLayer()
        val width = scope.size.width.roundToInt().coerceAtLeast(1)
        val height = scope.size.height.roundToInt().coerceAtLeast(1)
        layer.record(
            size = IntSize(width, height),
            density = scope,
            layoutDirection = scope.layoutDirection,
        ) {
            // drawContent paints with the scope's own canvas. Point that at the
            // layer's canvas for this pass, or the picture comes back empty.
            val context = scope.drawContext
            val previous = context.canvas
            context.canvas = drawContext.canvas
            try {
                scope.drawContent()
            } finally {
                context.canvas = previous
            }
        }
        launch.launch {
            try {
                item.image = layer.toImageBitmap()
            } finally {
                graphics.releaseGraphicsLayer(layer)
            }
        }
    }
}

@Composable
internal fun rememberHoldCapture(): HoldCapture = remember { HoldCapture() }

/**
 * The lifted video and its actions. [video] and [held] are both set while the
 * menu is wanted and both null once it has been closed; the exit keeps playing
 * after that.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun VideoHeldMenu(
    video: VideoItem?,
    held: HeldItem?,
    hazeState: HazeState,
    player: PlayerState,
    onOpen: (VideoItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val requested = if (video != null && held != null) video to held else null
    var last by remember { mutableStateOf<Pair<VideoItem, HeldItem>?>(null) }
    if (requested != null && requested != last) {
        androidx.compose.runtime.SideEffect { last = requested }
    }
    val shown = requested ?: last ?: return
    val (menuVideo, heldItem) = shown
    val open = requested != null
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(open) {
        if (open) {
            progress.animateTo(
                1f,
                if (reduced) tween(140, easing = LinearEasing) else spring(dampingRatio = 0.72f, stiffness = 420f),
            )
        } else {
            progress.animateTo(0f, tween(if (reduced) 110 else 230, easing = FastOutSlowInEasing))
            last = null
        }
    }
    BackHandler(enabled = open, onBack = onDismiss)

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val plate = (if (dark) Color.Black else Color.White).copy(alpha = 0.5f)
    val hairline = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
    val scrim = if (dark) Color.Black.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.10f)
    val cardShape = RoundedCornerShape(20.dp)
    val platePadding = 8.dp
    val pictureShape = RoundedCornerShape(12.dp)
    val menuShape = RoundedCornerShape(30.dp)
    val context = LocalContext.current
    val insets = WindowInsets.safeDrawing
    var overlayOffset by remember { mutableStateOf(Offset.Zero) }

    Layout(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOffset = it.positionInWindow() }
            .then(if (open) Modifier else Modifier.pointerInput(Unit) { swallowAll() }),
        content = {
            val backdropAlpha = { progress.value.coerceIn(0f, 1f) }
            Spacer(
                Modifier
                    .fillMaxSize()
                    .hazeEffect(state = hazeState, style = HazeMaterials.ultraThin(MaterialTheme.colorScheme.background)) {
                        alpha = backdropAlpha()
                    }
                    .graphicsLayer { alpha = backdropAlpha() }
                    .background(scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = open,
                        onClick = onDismiss,
                    ),
            )
            Box(
                Modifier
                    .then(if (reduced) Modifier else Modifier.shadow(20.dp, cardShape, clip = false))
                    .pointerInput(Unit) {}
                    .clip(cardShape)
                    .background(plate)
                    .border(0.5.dp, hairline, cardShape)
                    .clickable(enabled = open, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        onDismiss()
                        onOpen(menuVideo)
                    }
                    .padding(platePadding),
            ) {
                heldItem.image?.let { image ->
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize().clip(pictureShape),
                    )
                }
            }
            Column(
                Modifier
                    .pointerInput(Unit) {}
                    .shadow(24.dp, menuShape, ambientColor = Color.Black.copy(alpha = 0.30f), spotColor = Color.Black.copy(alpha = 0.30f))
                    .clip(menuShape)
                    .background(plate)
                    .border(0.5.dp, hairline, menuShape)
                    .padding(vertical = 8.dp),
            ) {
                MenuRow(SfIcons.PlaylistPlay, "Play next", open) { player.playNext(menuVideo); onDismiss() }
                MenuRow(SfIcons.PlaylistAdd, "Add to queue", open) { player.addToQueue(menuVideo); onDismiss() }
                MenuSeparator(hairline)
                MenuRow(SfIcons.Clock, "Save to Watch later", open) { player.saveToWatchLater(menuVideo); onDismiss() }
                MenuRow(SfIcons.LibraryAdd, "Save to playlist", open) { onDismiss(); player.openPlaylists(menuVideo) }
                MenuSeparator(hairline)
                MenuRow(SfIcons.Share, "Share", open) {
                    shareLink(context, "https://youtu.be/${menuVideo.id}", menuVideo.title)
                    onDismiss()
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val margin = 12.dp.roundToPx()
        val gap = 10.dp.roundToPx()
        val pad = platePadding.roundToPx()
        val top = insets.getTop(this) + 8.dp.roundToPx()
        val bottom = height - insets.getBottom(this) - 8.dp.roundToPx()
        val backdrop = measurables[0].measure(Constraints.fixed(width, height))
        val itemBounds = heldItem.bounds.translate(-overlayOffset)
        val plateW = (itemBounds.width.roundToInt() + pad * 2).coerceAtLeast(1)
        val plateH = (itemBounds.height.roundToInt() + pad * 2).coerceAtLeast(1)
        val card = measurables[1].measure(Constraints.fixed(plateW, plateH))
        val liftScale = ((width - margin * 2f) / plateW).coerceAtMost(1f)
        val visualW = plateW * liftScale
        val visualH = plateH * liftScale
        val menuWidth = (width - margin * 2).coerceAtMost(256.dp.roundToPx()).coerceAtLeast(0)
        val menuMaxHeight = (bottom - top - visualH.roundToInt() - gap).coerceAtLeast(0)
        val menu = measurables[2].measure(Constraints(minWidth = menuWidth, maxWidth = menuWidth, maxHeight = menuMaxHeight))
        val visualLeft = (itemBounds.center.x - visualW / 2f)
            .coerceIn(margin.toFloat(), (width - margin - visualW).coerceAtLeast(margin.toFloat()))
        var visualTop = (itemBounds.center.y - visualH / 2f)
            .coerceIn(top.toFloat(), (bottom - visualH).coerceAtLeast(top.toFloat()))
        val menuBelow: Boolean
        when {
            visualTop + visualH + gap + menu.height <= bottom -> menuBelow = true
            visualTop - gap - menu.height >= top -> menuBelow = false
            else -> {
                menuBelow = true
                visualTop = (bottom - menu.height - gap - visualH).coerceAtLeast(top.toFloat())
            }
        }
        val visualCenterX = visualLeft + visualW / 2f
        val visualCenterY = visualTop + visualH / 2f
        val alignEnd = visualCenterX > width / 2f
        val menuX = (if (alignEnd) visualLeft + visualW - menuWidth else visualLeft).roundToInt()
            .coerceIn(margin, (width - margin - menuWidth).coerceAtLeast(margin))
        val menuY = if (menuBelow) (visualTop + visualH + gap).roundToInt() else (visualTop - gap - menu.height).roundToInt()
        val cardX = (visualCenterX - plateW / 2f).roundToInt()
        val cardY = (visualCenterY - plateH / 2f).roundToInt()
        val fromDx = itemBounds.center.x - visualCenterX
        val fromDy = itemBounds.center.y - visualCenterY
        layout(width, height) {
            backdrop.place(0, 0)
            card.placeWithLayer(cardX, cardY) {
                val p = progress.value
                if (reduced) {
                    scaleX = liftScale
                    scaleY = liftScale
                    alpha = p.coerceIn(0f, 1f)
                } else {
                    val scale = lerp(1f, liftScale, p)
                    scaleX = scale
                    scaleY = scale
                    translationX = lerp(fromDx, 0f, p)
                    translationY = lerp(fromDy, 0f, p)
                    alpha = (p * 3f).coerceIn(0f, 1f)
                }
            }
            menu.placeWithLayer(menuX, menuY) {
                val p = progress.value
                alpha = ((p - 0.1f) / 0.9f).coerceIn(0f, 1f)
                if (!reduced) {
                    transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, if (menuBelow) 0f else 1f)
                    val scale = lerp(0.55f, 1f, p)
                    scaleX = scale
                    scaleY = scale
                    translationX = lerp(fromDx, 0f, p)
                    translationY = lerp(fromDy, 0f, p)
                }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val labelColor = if (dark) Color.White else Color(0xFF1A1A1A)
    val pressedFill = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.07f)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (pressed) pressedFill else Color.Transparent)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(start = 12.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = labelColor, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = labelColor,
            fontSize = 17.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MenuSeparator(color: Color) {
    Box(Modifier.fillMaxWidth().height(17.dp).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(color))
    }
}

private suspend fun PointerInputScope.swallowAll() {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

/** Draws the hold's picture. Applied just inside [pressable] when a hold should lift the row. */
@Composable
internal fun Modifier.captureHold(capture: HoldCapture): Modifier {
    val graphics = LocalGraphicsContext.current
    val scope = rememberCoroutineScope()
    return this
        .onPlaced { capture.coordinates = it }
        .drawWithContent { capture.draw(this, graphics, scope) }
}
