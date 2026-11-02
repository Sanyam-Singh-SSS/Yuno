package com.yuno

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

// Floating capsule: full width minus gutters, every tab shows its label.
// Mini-player still sits 8 dp above it. Height stays 62 dp, the same the Shorts bar morphs from.
internal val NavHeight = 62.dp
/** How wide the floating bars are ever allowed to get. */
internal val FloatingBarMaxWidth = 440.dp
/** Side margin of the nav and the mini player. The same inset as the lists ([PageGutter]). */
internal val NavBarGutter = PageGutter
/** How far above the navigation inset the bar sits. */
internal val NavBarLift = 6.dp
/** The bar never sits closer than a gesture inset. */
internal val GestureBarFloor = 15.dp
/** List clearance with nothing playing, and with a video up. */
internal val ListBottomIdle = 140.dp
internal val ListBottomPlaying = 210.dp
private val TabPillInset = 4.dp
// Tab sizing: 28 dp glyphs with 6.5 dp of tab padding → 62 dp bar.
private val TabIconSize = 28.dp
private val TabIconLabelGap = 1.dp
private val TabVerticalPadding = 6.5.dp
private val TabRowGap = 6.dp

/**
 * A tab's label: 10sp W600 with 0.5 tracking. [lineHeight] is unspecified so the
 * line is the font's own (~1.2×) rather than Material's 16–24sp; that keeps the bar
 * 62 dp instead of growing taller around the text.
 */
@Composable
internal fun tabLabelStyle(): TextStyle =
    MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        lineHeight = TextUnit.Unspecified,
    )
/** How far the selection pill stretches along its travel. */
private const val PillStretch = 0.16f
private const val PillSquash = 0.5f
internal val GlassSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 320f)

/**
 * Floating capsule: full width minus gutters, every tab showing its label.
 *
 * Tabs: tonal surfaceVariant with a wide, very soft shadow; the active tab grows and
 * reveals its label on a full-primary pill. No ripple, no outline. The pill is only ever
 * the tabs — the open player keeps its controls on the video (VideoView.kt), so a tab is
 * one tap away however the player looks, and picking one folds the player to the mini-player.
 *
 * 0.5.50: on the Shorts tab the tabs drop out and the pill's tone fades along [shorts] (0..1)
 * while the glass bar (Shorts.kt) splits out of the same spot; [tabsEnabled] is off meanwhile.
 */
@Composable
internal fun NavPill(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
    shorts: () -> Float = { 0f },
    tabsEnabled: Boolean = true,
) {
    val c = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    val glassOn = LocalLiquidGlassEnabled.current && isGlassSupported()
    val pillShape = RoundedCornerShape(percent = 50)

    Box(modifier.fillMaxWidth().height(NavHeight)) {
        Box(
            Modifier
                .fillMaxSize()
                // A real, if soft, shadow (Derek: the bar was white on white): full black here, as
                // Android already scales elevation shadows right down. Liquid glass brings its own.
                .graphicsLayer {
                    shape = pillShape
                    clip = true
                    shadowElevation = if (glassOn) 0f else 12.dp.toPx() * (1f - shortsBarFade(shorts()))
                    ambientShadowColor = Color.Black
                    spotShadowColor = Color.Black
                    alpha = 1f - shortsBarFade(shorts())
                }
                .navBarSurface(c.surfaceVariant),
        ) {
            CompositionLocalProvider(LocalContentColor provides c.onSurfaceVariant) {
                GlassTabRow(
                    selected = selected,
                    onSelect = onSelect,
                    shorts = shorts,
                    tabsEnabled = tabsEnabled,
                    reduced = reduced,
                )
            }
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun Modifier.navBarSurface(container: Color): Modifier {
    val pillShape = RoundedCornerShape(percent = 50)
    val glassOn = LocalLiquidGlassEnabled.current && isGlassSupported()
    val haze = LocalAppHaze.current
    // Always a filled capsule first: Haze/glass sit on top. Sampling the bar's own
    // layer used to leave this empty (and crash liquid glass).
    val filled = background(container, pillShape)
    return when {
        glassOn -> filled.then(liquidGlass(pillShape))
        haze != null -> filled.hazeEffect(state = haze, style = HazeMaterials.regular(container.copy(alpha = 0.72f))) {
            inputScale = HazeInputScale.Fixed(0.33f)
        }.border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, pillShape)
        else -> filled.border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, pillShape)
    }
}

@Composable
private fun GlassTabRow(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    shorts: () -> Float,
    tabsEnabled: Boolean,
    reduced: Boolean,
) {
    val c = MaterialTheme.colorScheme
    val view = LocalView.current
    val density = LocalDensity.current
    val shown = Tab.entries.filter { it != Tab.Shorts || ShortsSetting.enabled }
    val selectedIndex = shown.indexOf(selected).coerceAtLeast(0)
    val glassSpec: AnimationSpec<Float> = if (reduced) snap() else GlassSpring
    val glassOn = LocalLiquidGlassEnabled.current
    val glassTint = if (glassOn) glassContentColor() else c.onSurfaceVariant
    val selectedTint = if (glassOn) glassTint else c.primary
    val pillFill = if (glassOn) glassIndicatorColor() else c.primary.copy(alpha = 0.14f)

    var dragOffset by remember { mutableFloatStateOf(0f) }
    var lastHapticTab by remember { mutableIntStateOf(selectedIndex) }
    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    val gapPx = with(density) { TabRowGap.toPx() }
    val n = shown.size
    val tabWidthPx = if (rowSize.width > 0 && n > 0) (rowSize.width - gapPx * (n - 1)) / n else 0f
    val tabStepPx = if (rowSize.width > 0 && n > 0) (rowSize.width + gapPx) / n else 0f
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentTabStepPx by rememberUpdatedState(tabStepPx)
    val currentLastIndex by rememberUpdatedState(shown.lastIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentShown by rememberUpdatedState(shown)

    val pillTargetPx = if (tabStepPx > 0f) selectedIndex * tabStepPx + dragOffset else 0f
    val animatedPillOffset by animateFloatAsState(pillTargetPx, glassSpec, label = "pillOffset")
    val lag = if (tabStepPx > 0f) (abs(pillTargetPx - animatedPillOffset) / tabStepPx).coerceIn(0f, 1f) else 0f
    LaunchedEffect(selectedIndex) { dragOffset = 0f; lastHapticTab = selectedIndex }

    Box(
        Modifier
            .fillMaxSize()
            .padding(TabPillInset),
    ) {
        if (tabWidthPx > 0f) {
            Box(
                Modifier
                    .width(with(density) { tabWidthPx.toDp() })
                    .fillMaxHeight()
                    .graphicsLayer {
                        translationX = animatedPillOffset
                        scaleX = 1f + lag * PillStretch
                        scaleY = 1f - lag * PillStretch * PillSquash
                    }
                    .background(pillFill, RoundedCornerShape(percent = 50)),
            )
        }
        Row(
            Modifier
                .fillMaxSize()
                .onSizeChanged { rowSize = it }
                .pointerInput(tabsEnabled) {
                    if (!tabsEnabled) return@pointerInput
                    var totalDrag = 0f
                    var dragStartIndex = currentSelectedIndex
                    detectHorizontalDragGestures(
                        onDragStart = {
                            totalDrag = 0f
                            dragStartIndex = currentSelectedIndex
                        },
                        onDragCancel = { dragOffset = 0f },
                        onDragEnd = {
                            val step = currentTabStepPx
                            if (step > 0f) {
                                val absolute = (dragStartIndex + totalDrag / step).coerceIn(0f, currentLastIndex.toFloat())
                                val newIndex = absolute.roundToInt()
                                val next = currentShown.getOrNull(newIndex)
                                if (next != null && newIndex != currentSelectedIndex) currentOnSelect(next)
                            }
                            dragOffset = 0f
                        },
                        onHorizontalDrag = { _, delta ->
                            val step = currentTabStepPx
                            if (step <= 0f) return@detectHorizontalDragGestures
                            totalDrag += delta
                            val absolute = (dragStartIndex + totalDrag / step).coerceIn(0f, currentLastIndex.toFloat())
                            dragOffset = absolute * step - currentSelectedIndex * step
                            val approx = absolute.roundToInt()
                            if (approx != lastHapticTab) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                lastHapticTab = approx
                            }
                        },
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(TabRowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            shown.forEachIndexed { i, t ->
                val active = t == selected
                val tint by animateColorAsState(
                    if (active) selectedTint else (if (glassOn) glassTint.copy(alpha = 0.65f) else c.onSurfaceVariant),
                    tween(200),
                    label = "tabTint",
                )
                val scale by animateFloatAsState(if (active) 1.08f else 1f, glassSpec, label = "tabScale")
                val interaction = remember(t) { MutableInteractionSource() }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .graphicsLayer {
                            val sp = shorts()
                            val d = shortsTabDrop(sp, i)
                            alpha = if (i == 0) 1f - shortsHomeFade(sp) else 1f - d
                            translationY = 16.dp.toPx() * d
                            scaleX = 1f - 0.15f * d
                            scaleY = scaleX
                        }
                        .then(
                            if (!tabsEnabled) Modifier
                            else Modifier.clickable(interactionSource = interaction, indication = null) {
                                if (!active) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                onSelect(t)
                            },
                        )
                        .padding(vertical = TabVerticalPadding),
                ) {
                    Icon(
                        t.icon,
                        contentDescription = t.label,
                        tint = tint,
                        modifier = Modifier.size(TabIconSize).graphicsLayer {
                            scaleX = scale * t.iconScale
                            scaleY = scale * t.iconScale
                        },
                    )
                    Spacer(Modifier.height(TabIconLabelGap))
                    Text(
                        t.label,
                        style = tabLabelStyle(),
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

