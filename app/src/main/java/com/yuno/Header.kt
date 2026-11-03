package com.yuno

import androidx.compose.animation.core.EaseInCubic
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuno.icons.SfIcons
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
 * Where a page's first row starts: [LargePageTitle]'s vertical centre lines up with
 * the top bar's controls (same row as search and account).
 */
@Composable
fun pageContentTop(): Dp {
    val barMid = topBarHeight() - TopBarContentHeight / 2
    return (barMid - LargePageTitleBlockHeight / 2).coerceAtLeast(0.dp)
}

/** First body row when there is no in-list title (Search tab): same clearance as Home after its title. */
@Composable
fun pageBodyTop(): Dp = pageContentTop() + LargePageTitleBlockHeight + LargePageTitleContentGap

/** [LargePageTitle] vertical padding plus ~41dp line box for 34sp display type. */
private val LargePageTitleBlockHeight = 8.dp + 41.dp + 8.dp

/** Floating controls — the back circle and each action slot. Apple's 44pt. */
private val TopControl = 44.dp

/** Between two actions in the pill, measured off Apple Music's share-and-more pill. */
private val ActionGap = 10.dp

/** Inset at each end of the pill once it holds more than one action. */
private val ActionEdge = 1.5.dp

/** Between the search field's pill and the actions beside it. */
private val AccessoryGap = 8.dp

/** Room below a [LargePageTitle] before the page body (not counted in bar alignment). */
private val LargePageTitleContentGap = 12.dp

/**
 * The large in-list page title: SF Pro Display at 34sp,
 * weight 800, tracking −0.5, inset by the page gutter with 8dp above and
 * [LargePageTitleContentGap] extra below before the first row of content.
 * It scrolls away with the list; the bar above it never carries a second copy.
 */
/** Matches [LargePageTitle]'s 34sp cap height in the bar-alignment row. */
private val LargePageBrandMarkSize = 42.dp

/** Wordmark aspect (1438×736 source). */
private const val WordmarkAspect = 1438f / 736f

@Composable
fun LargePageBrandMark(modifier: Modifier = Modifier) {
    val inset = (LargePageTitleBlockHeight - LargePageBrandMarkSize) / 2
    val ink = MaterialTheme.colorScheme.onBackground
    Image(
        painter = painterResource(R.drawable.wordmark),
        contentDescription = "Yuno",
        modifier = modifier
            .padding(
                start = PageGutter,
                end = PageGutter,
                top = inset,
                bottom = inset + LargePageTitleContentGap,
            )
            .height(LargePageBrandMarkSize)
            .aspectRatio(WordmarkAspect),
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(ink, BlendMode.SrcIn),
    )
}

/** Default in-list page title size; [LargePageTitleCompactFontSize] on Subscriptions / Library. */
private val LargePageTitleFontSize = 34.sp
internal val LargePageTitleCompactFontSize = 30.sp

private fun titleCapHeight(fontSize: TextUnit): Dp =
    (fontSize.value * 41f / LargePageTitleFontSize.value).dp

@Composable
fun LargePageTitle(
    title: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = LargePageTitleFontSize,
) {
    val inset = (LargePageTitleBlockHeight - titleCapHeight(fontSize)) / 2
    Text(
        text = title,
        modifier = modifier.padding(
            start = PageGutter,
            end = PageGutter,
            top = inset,
            bottom = inset + LargePageTitleContentGap,
        ),
        style = OwnDirectionText.merge(
            TextStyle(
                fontFamily = SfProDisplay,
                fontWeight = FontWeight.W800,
                fontSize = fontSize,
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
 * The transparent bar every page shares: a circle for back, an
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
    // Drawn later by [TopChromeLayer], outside the page. Liquid glass has to
    // sample the recording from outside it, and the haze blur has to sit
    // outside the haze source the way the nav does. Inside that source the
    // buttons are part of the snapshot, so there is nothing behind them to blur.
    if (host != null && !LocalTopChromeDrawing.current) {
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
    if (!shown) return
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

/** Hairline that keeps a light photo from dissolving into the bar. */
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
    // No solid fill under the glass. surface is nearly black in the dark theme,
    // and the glass layer sits inside that circle — wherever it fell short, the
    // fill showed as a black circle behind the button.
    if (glassOn) return liquidGlass(shape)
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
 * What scrolls under the status bar dissolves into the page colour: opaque at the
 * top edge, gone by 180dp, on a cubic ease-in so the join isn't a line.
 *
 * Pass [listState] when the list's first item is a [LargePageTitle]. The fade stays
 * off until that title has started to scroll away (Home, Subscriptions, Library, …).
 * Without it the scrim sits over the title and reads grey.
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
