package com.decio.skipii

import androidx.compose.animation.core.EaseInCubic
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.decio.skipii.icons.SfIcons
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/** Strongest a page vignette gets (Derek: 80% of Ledgii's 95%). */
const val VignetteAlpha = 0.76f

/**
 * Page color fading from [VignetteAlpha] to 0 on a smoothstep ramp: flat at both ends, so
 * there's no visible line where the vignette begins. [opaqueAtTop] puts the strong end on top.
 */
fun vignetteBrush(color: Color, opaqueAtTop: Boolean): Brush {
    val stops = 12
    return Brush.verticalGradient(
        *Array(stops + 1) { i ->
            val t = i / stops.toFloat()
            val strength = if (opaqueAtTop) 1f - t else t
            t to color.copy(alpha = VignetteAlpha * strength * strength * (3f - 2f * strength))
        },
    )
}

/** Bottom padding as given, plus the collapsing header's height on top. */
fun PaddingValues.withTop(top: Dp) = PaddingValues(top = top, bottom = calculateBottomPadding())

/**
 * How far down the window the bar ends: the status-bar inset it sits under, plus
 * [TopBarContentHeight]. Read here, not baked in — a cutout, a flat screen and a
 * freeform window are different insets.
 */
@Composable
fun topBarHeight(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarContentHeight

/**
 * Where a page's first row starts: clear of the bar, plus [TopBarContentGap].
 * Home sits [HomeTitleLift] higher, the same lift BitChord gives its wordmark.
 */
@Composable
fun pageContentTop(home: Boolean = false): Dp {
    val base = topBarHeight() + TopBarContentGap
    return if (home) (base - HomeTitleLift).coerceAtLeast(0.dp) else base
}

/** Home's heading is pulled up under the bar. BitChord's HOME_TITLE_LIFT. */
private val HomeTitleLift = 28.dp

/** Floating controls — the back circle and each action slot. Apple's 44pt. */
private val TopControl = 44.dp

/** Between two actions in the pill, measured off Apple Music's share-and-more pill. */
private val ActionGap = 10.dp

/** Inset at each end of the pill once it holds more than one action. */
private val ActionEdge = 1.5.dp

/** Between the search field's pill and the actions beside it. */
private val AccessoryGap = 8.dp

/**
 * The large in-list page title. BitChord's displayLarge: SF Pro Display at 34sp,
 * weight 800, tracking −0.5, inset by the page gutter with 8dp above and below.
 * It scrolls away with the list; the bar above it never carries a second copy.
 */
@Composable
fun LargePageTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier.padding(horizontal = PageGutter, vertical = 8.dp),
        style = OwnDirectionText.merge(
            TextStyle(
                fontFamily = SfProDisplay,
                fontWeight = FontWeight.W800,
                fontSize = 34.sp,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onBackground,
            ),
        ),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Glass controls drawn above the page layer. The page records itself for them
 * to sample; a control inside that recording nests the render node and the
 * process dies. Each [PageTopBar] registers here while glass is on, and
 * [TopChromeLayer] draws the front one outside the recording.
 */
internal class TopChromeHost {
    val entries = mutableListOf<TopChromeEntry>()
    var tick by mutableStateOf(0)
}

internal class TopChromeEntry {
    var content: (@Composable () -> Unit)? = null
}

internal val LocalTopChrome = staticCompositionLocalOf<TopChromeHost?> { null }
private val LocalTopChromeDrawing = staticCompositionLocalOf { false }

@Composable
internal fun TopChromeLayer(host: TopChromeHost, modifier: Modifier = Modifier) {
    host.tick
    val top = host.entries.lastOrNull() ?: return
    Box(modifier) {
        CompositionLocalProvider(LocalTopChromeDrawing provides true) {
            top.content?.invoke()
        }
    }
}

/**
 * The transparent bar every page shares with BitChord: a circle for back, an
 * optional filling accessory (the search field), and trailing actions. No title
 * and no slab — the page's own top shows through, and the large title lives in
 * the list.
 */
@Composable
fun PageTopBar(
    onBack: (() -> Unit)? = null,
    accessory: (@Composable () -> Unit)? = null,
    // False while the page is closing. The exit animation keeps the screen
    // composed, and without this the search field would stay until that finished.
    shown: Boolean = true,
    actions: @Composable () -> Unit = {},
) {
    val glass = LocalLiquidGlassEnabled.current && isGlassSupported()
    val host = LocalTopChrome.current
    // Drawn later by [TopChromeLayer], outside the page recording, so the
    // glass samples the page that's actually on screen — including mid-transition.
    if (glass && host != null && !LocalTopChromeDrawing.current) {
        if (shown) {
            val entry = remember { TopChromeEntry() }
            DisposableEffect(host, entry) {
                host.entries.add(entry)
                host.tick++
                onDispose {
                    host.entries.remove(entry)
                    host.tick++
                }
            }
            SideEffect {
                entry.content = { PageTopBar(onBack, accessory, actions = actions) }
                host.tick++
            }
        }
        return
    }
    val iconTint = if (glass) glassContentColor() else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(TopBarContentHeight),
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = PageGutter)
                    .size(TopControl)
                    // Glass outside the press scale. The scale is a graphics layer,
                    // and sampling the page from inside one shifts the circle's reflection.
                    .floatingChrome(CircleShape)
                    .pressable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SfIcons.ArrowBack, contentDescription = "Back", tint = iconTint)
            }
        }
        val leading = if (onBack != null) PageGutter + TopControl + AccessoryGap else PageGutter
        Row(
            Modifier
                .align(Alignment.CenterEnd)
                .then(if (accessory != null) Modifier.fillMaxWidth().padding(start = leading) else Modifier)
                .padding(end = PageGutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (accessory != null) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(TopControl)
                        .floatingChrome(RoundedCornerShape(percent = 50)),
                ) { accessory() }
                Spacer(Modifier.width(AccessoryGap))
            }
            actions()
        }
    }
}

/**
 * Search and the account, one pill. A single control would be a circle; these
 * two take Apple's 10dp gap and 1.5dp end inset, which lands the pill at 101dp.
 */
@Composable
fun HeaderActions(onSearch: () -> Unit, onAccount: () -> Unit) {
    val glass = LocalLiquidGlassEnabled.current && isGlassSupported()
    val tint = if (glass) glassContentColor() else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .height(TopControl)
            .floatingChrome(RoundedCornerShape(percent = 50))
            .padding(horizontal = ActionEdge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(TopControl).pressable(onClick = onSearch), contentAlignment = Alignment.Center) {
            Icon(SfIcons.Search, contentDescription = "Search", tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(ActionGap))
        AccountAction(onAccount)
    }
}

/** The account photo at 28dp, or a person glyph on a filled circle when signed out. */
@Composable
fun AccountAction(onClick: () -> Unit) {
    val profile = Account.profile
    val c = MaterialTheme.colorScheme
    Box(Modifier.size(TopControl).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (profile == null) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(c.surfaceVariant)
                    .thumbnailHairline(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    SfIcons.Person,
                    contentDescription = "Sign in",
                    tint = c.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            Box(Modifier.size(28.dp)) {
                ChannelAvatar(profile.avatar, profile.name, size = 28)
                Box(Modifier.matchParentSize().thumbnailHairline(CircleShape))
            }
        }
    }
}

/** Hairline that keeps a light photo from dissolving into the bar. BitChord's thumbnailBorder. */
@Composable
fun Modifier.thumbnailHairline(shape: Shape): Modifier {
    val dark = isDarkTheme()
    return border(
        width = 1.dp,
        color = if (dark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f),
        shape = shape,
    )
}

/**
 * The navbar's material, on a top-bar circle or pill. With glass on, this
 * samples the page layer, so the bar has to be drawn outside that recording
 * ([TopChromeLayer]). With glass off, the haze blur the nav uses.
 */
@OptIn(ExperimentalHazeMaterialsApi::class, ExperimentalHazeApi::class)
@Composable
fun Modifier.floatingChrome(shape: CornerBasedShape): Modifier {
    val container = MaterialTheme.colorScheme.surface
    val glassOn = LocalLiquidGlassEnabled.current && isGlassSupported()
    val haze = LocalAppHaze.current
    if (glassOn) return background(container, shape).then(liquidGlass(shape))
    val filled = clip(shape).background(container, shape)
    return if (haze != null) {
        filled.hazeEffect(
            state = haze,
            style = HazeMaterials.regular(container.copy(alpha = 0.72f)),
        ) {
            inputScale = HazeInputScale.Fixed(0.33f)
        }.border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
    } else {
        filled.border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
    }
}

/**
 * What scrolls under the status bar dissolves into the page colour. BitChord's
 * bottom fade turned over: opaque at the top edge, gone by 180dp, on a cubic
 * ease-in so the join isn't a line.
 *
 * Pass [listState] on Home. The fade stays off until that page's large title
 * has started to leave, the same way BitChord holds it off the wordmark.
 * Every other page keeps it up — content is always free to pass under the bar.
 */
@Composable
fun PageTopFade(listState: LazyListState? = null, modifier: Modifier = Modifier) {
    val page = MaterialTheme.colorScheme.surface
    val brush = remember(page) {
        Brush.verticalGradient(
            colorStops = Array(16) { i ->
                val t = i / 15f
                t to page.copy(alpha = EaseInCubic.transform(1f - t))
            },
        )
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(180.dp)
            .graphicsLayer { alpha = if (listState == null) 1f else headingCover(listState) }
            .drawBehind { drawRect(brush) },
    )
}

/** 0 while the large title is fully on screen, 1 once it has scrolled away. */
private fun headingCover(list: LazyListState): Float {
    if (list.firstVisibleItemIndex > 0) return 1f
    val header = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.size?.toFloat() ?: return 0f
    if (header <= 0f) return 0f
    return (list.firstVisibleItemScrollOffset / header).coerceIn(0f, 1f)
}
