package com.yuno

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import com.yuno.icons.SfIcons
import kotlin.math.roundToInt

// ── Home / Subscriptions / Library ─────────────────────────────────────────────
// Signed in, these show the account's real feeds. Signed out, they explain what signing
// in brings rather than showing sample content.

@Composable
fun HomeScreen(
    lib: LibraryData,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    onSearch: () -> Unit,
    onSignIn: () -> Unit,
    onAccount: () -> Unit,
    scrollToTop: Int = 0,
) {
    // Guest or signed in: same Home feed. Signed out uses an anonymous visitor; sign-in swaps
    // to the account's recommendations after [LibraryData.reset].
    val signedIn = Account.signedIn
    LaunchedEffect(signedIn) { lib.home.ensureLoaded() }
    val chips = lib.home.chips
    val chipIndex = feedChipSelectedIndex(chips, lib.home.chip)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val pagerState = if (chips.size > 1) {
        rememberPagerState(
            initialPage = chipIndex.coerceIn(0, chips.lastIndex),
            pageCount = { chips.size },
        )
    } else null
    if (pagerState != null) {
        LaunchedEffect(chipIndex) {
            if (pagerState.currentPage != chipIndex && !pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage(chipIndex)
            }
        }
        LaunchedEffect(pagerState, chips) {
            snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { page ->
                val pick = chips.getOrNull(page) ?: return@collect
                if (feedChipSelectedIndex(chips, lib.home.chip) != page) {
                    lib.home.selectChip(pick)
                }
            }
        }
    }
    val tabPosition by remember(pagerState) {
        derivedStateOf { pagerState?.let { it.currentPage + it.currentPageOffsetFraction } }
    }
    val tabDragging by remember(pagerState) {
        derivedStateOf { pagerState?.isScrollInProgress == true }
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            HomeFeedColumn(
                lib.home,
                contentPadding.withTop(pageContentTop()),
                onOpen,
                listState = listState,
                scrollToTop = scrollToTop,
                indicatorTop = topBarHeight(),
                chips = chips,
                onPickChip = { ch ->
                    if (pagerState != null) {
                        val i = chips.indexOf(ch)
                        if (i >= 0) scope.launch { pagerState.animateScrollToPage(i) }
                    }
                    lib.home.selectChip(ch)
                },
                tabPosition = tabPosition,
                tabDragging = tabDragging,
                chipPagerState = pagerState,
                chipIndex = chipIndex,
                chipTargetPage = pagerState?.targetPage ?: chipIndex,
                chipScrolling = tabDragging,
            )
            PageTopFade(listState)
        }
        PageTopBar { UpdateAction(); HeaderActions(onSearch, onAccount) }
    }
}

@Composable
private fun HomeFeedColumn(
    feed: FeedState,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    listState: LazyListState,
    scrollToTop: Int,
    indicatorTop: Dp,
    chips: List<FeedChip>,
    onPickChip: (FeedChip) -> Unit,
    tabPosition: Float? = null,
    tabDragging: Boolean = false,
    chipPagerState: PagerState? = null,
    chipIndex: Int = 0,
    chipTargetPage: Int = 0,
    chipScrolling: Boolean = false,
) {
    FeedList(
        feed,
        contentPadding,
        onOpen,
        header = {
            item(key = "page-title") { LargePageBrandMark() }
            if (chips.size > 1) {
                item(key = "chips") {
                    HomeChips(
                        chips,
                        feed.chip,
                        onPickChip,
                        tabPosition = tabPosition,
                        tabDragging = tabDragging,
                    )
                }
            }
        },
        scrollToTop = scrollToTop,
        indicatorTop = indicatorTop,
        listState = listState,
        chipPagerState = chipPagerState,
        chips = chips,
        chipIndex = chipIndex,
        chipTargetPage = chipTargetPage,
        chipScrolling = chipScrolling,
    )
}

@Composable
fun SubscriptionsScreen(
    lib: LibraryData,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    onSearch: () -> Unit,
    onSignIn: () -> Unit,
    onAccount: () -> Unit,
    scrollToTop: Int = 0,
) {
    val signedIn = Account.signedIn
    val openChannel = LocalChannelOpener.current
    LaunchedEffect(signedIn) {
        if (signedIn) {
            lib.subscriptions.ensureLoaded()
            if (lib.channels.isEmpty()) lib.loadChannels()
        }
    }
    val listState = rememberLazyListState()
    val top = pageContentTop()
    val showStrip = signedIn && lib.channels.isNotEmpty()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
        if (signedIn) {
            FeedList(
                lib.subscriptions,
                contentPadding.withTop(top),
                onOpen,
                header = {
                    item(key = "page-title") { LargePageTitle("Subscriptions", fontSize = LargePageTitleCompactFontSize) }
                    if (showStrip) {
                        item(key = "channels") {
                            SubscriptionsChannelStrip(channels = lib.channels) { ch ->
                                openChannel("https://www.youtube.com/channel/${ch.id}")
                            }
                        }
                    }
                },
                scrollToTop = scrollToTop,
                indicatorTop = topBarHeight(),
                listState = listState,
            )
        } else {
            LazyColumn(state = listState, contentPadding = contentPadding.withTop(top)) {
                item { LargePageTitle("Subscriptions", fontSize = LargePageTitleCompactFontSize) }
                item {
                    EmptyState(
                        icon = SfIcons.Subscriptions,
                        title = "Your subscriptions",
                        body = "Sign in with Google to see new videos from the channels you follow.",
                        action = { PillButton("Sign in with Google", onSignIn) },
                    )
                }
            }
        }
        PageTopFade(listState)
        }
        PageTopBar { HeaderActions(onSearch, onAccount) }
    }
}

/**
 * Words in a row that scrolls, the [selected] one primary and heavier with a short rounded bar
 * springing under it from the last pick; the row fades out at whichever edge has more to scroll
 * to. Home's categories and a channel's tabs.
 */
@Composable
fun UnderlineTabs(
    labels: List<String>,
    selected: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tabPosition: Float? = null,
    tabDragging: Boolean = false,
) {
    val c = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val spots = remember(labels) { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val barLeft = remember { Animatable(0f) }
    val barWidth = remember { Animatable(0f) }
    var barShown by remember { mutableStateOf(false) }
    val position = (tabPosition ?: selected.toFloat()).coerceIn(0f, labels.lastIndex.coerceAtLeast(0).toFloat())
    val highlight = if (tabDragging) position.roundToInt().coerceIn(0, labels.lastIndex) else selected
    fun barAt(pos: Float): Pair<Float, Float>? {
        if (labels.isEmpty()) return null
        val max = labels.lastIndex
        val p = pos.coerceIn(0f, max.toFloat())
        val i = p.toInt().coerceIn(0, max)
        val frac = p - i
        fun one(index: Int): Pair<Float, Float>? {
            val (left, width) = spots[index] ?: return null
            val w = maxOf(width * ChipBarShare, with(density) { ChipBarMin.toPx() })
            val l = left + (width - w) / 2
            return l to w
        }
        val a = one(i) ?: return null
        if (frac <= 0f || i >= max) return a
        val b = one(i + 1) ?: return a
        return a.first + (b.first - a.first) * frac to a.second + (b.second - a.second) * frac
    }
    LaunchedEffect(position, tabDragging, reduced, spots[highlight], spots.get(highlight + 1)) {
        val target = barAt(position) ?: return@LaunchedEffect
        val (l, w) = target
        if (!barShown || reduced || tabDragging) {
            barLeft.snapTo(l); barWidth.snapTo(w); barShown = true
            return@LaunchedEffect
        }
        val spec = spring<Float>(dampingRatio = 0.62f, stiffness = 450f)
        launch { barLeft.animateTo(l, spec) }
        barWidth.animateTo(w, spec)
    }
    LaunchedEffect(highlight) {
        val (left, width) = spots[highlight] ?: return@LaunchedEffect
        val edge = with(density) { ChipRowPadding.toPx() }
        val x = left + edge
        val view = scroll.viewportSize
        if (x - edge < scroll.value || x + width + edge > scroll.value + view) {
            val to = (x + width / 2 - view / 2f).toInt()
            if (reduced) scroll.scrollTo(to) else scroll.animateScrollTo(to)
        }
    }
    val fade = with(density) { ChipRowFade.toPx() }
    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val leftFade = (scroll.value / fade).coerceIn(0f, 1f)
                val rightFade = ((scroll.maxValue - scroll.value) / fade).coerceIn(0f, 1f)
                val w = size.width
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 1f - leftFade),
                        (fade / w) to Color.Black,
                        (1f - fade / w) to Color.Black,
                        1f to Color.Black.copy(alpha = 1f - rightFade),
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        Row(
            Modifier
                .horizontalScroll(scroll)
                .padding(horizontal = ChipRowPadding)
                .drawBehind {
                    if (!barShown) return@drawBehind
                    val h = ChipBarHeight.toPx()
                    drawRoundRect(
                        c.primary,
                        topLeft = Offset(barLeft.value, size.height - h),
                        size = Size(barWidth.value, h),
                        cornerRadius = CornerRadius(h / 2),
                    )
                },
        ) {
            labels.forEachIndexed { i, label ->
                val on = i == highlight
                val pad = with(density) { ChipWordPadding.toPx() }
                val fg by animateColorAsState(if (on) c.primary else c.onSurfaceVariant, tween(if (reduced) 0 else 220), label = "chipFg")
                Box(
                    Modifier
                        .onPlaced { spots[i] = it.positionInParent().x + pad to it.size.width - 2 * pad }
                        .pressable(onClick = { onPick(i) })
                        .height(ChipRowHeight)
                        .padding(horizontal = ChipWordPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = fg,
                        fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

private val ChipRowPadding = 9.dp
private val ChipWordPadding = 11.dp
private val ChipRowHeight = 40.dp
private val ChipRowFade = 28.dp
private const val ChipBarShare = 0.6f
private val ChipBarMin = 18.dp
private val ChipBarHeight = 4.dp

@Composable
private fun HomeChips(
    chips: List<FeedChip>,
    chosen: FeedChip?,
    onPick: (FeedChip) -> Unit,
    tabPosition: Float? = null,
    tabDragging: Boolean = false,
) {
    val selected = feedChipSelectedIndex(chips, chosen)
    UnderlineTabs(
        chips.map { it.title },
        selected,
        { onPick(chips[it]) },
        Modifier.padding(top = 2.dp, bottom = 10.dp),
        tabPosition = tabPosition,
        tabDragging = tabDragging,
    )
}

@Composable
private fun SubscriptionsChannelStrip(
    channels: List<ChannelRef>,
    onOpen: (ChannelRef) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    LazyRow(
        contentPadding = PaddingValues(horizontal = PageGutter),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
    ) {
        items(channels, key = { it.id }) { ch ->
            Column(
                Modifier
                    .width(68.dp)
                    .pressable { onOpen(ch) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ChannelAvatar(ch.avatar, ch.name, size = 56, selected = false, ringSlot = true)
                Spacer(Modifier.height(6.dp))
                FadingLine(
                    text = ch.name,
                    style = TextStyle(fontSize = 12.sp, textAlign = TextAlign.Center),
                    color = c.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun LibraryScreen(
    lib: LibraryData,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    onOpenPlaylist: (PlaylistRef) -> Unit,
    onSearch: () -> Unit,
    onSignIn: () -> Unit,
    onAccount: () -> Unit,
    scrollToTop: Int = 0,
) {
    val signedIn = Account.signedIn
    val localHistory = WatchHistory.videos
    LaunchedEffect(signedIn) {
        if (signedIn) {
            lib.history.ensureLoaded()
            if (lib.playlists.isEmpty()) lib.loadPlaylists()
            if (lib.channels.isEmpty()) lib.loadChannels()
        }
    }
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
        PullToRefresh(
            enabled = signedIn,
            refreshing = lib.history.refreshing,
            onRefresh = { lib.history.refresh(); lib.loadPlaylists() },
            topPadding = topBarHeight(),
        ) {
        LazyColumn(
            state = rememberScrollToTop(scrollToTop, listState),
            contentPadding = contentPadding.withTop(pageContentTop()),
        ) {
            item(key = "page-title") { LargePageTitle("Library", fontSize = LargePageTitleCompactFontSize) }
            item(key = "historyTitle") { SectionTitle("History") }
            item(key = "history") {
                val history = if (signedIn) lib.history.videos else localHistory
                when {
                    history.isNotEmpty() -> LazyRow(
                        contentPadding = PaddingValues(horizontal = PageGutter),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(history.take(20), key = { "h-" + it.id }) { video -> HistoryCard(video) { onOpen(video) } }
                    }
                    signedIn && lib.history.loading -> HistorySkeletons()
                    else -> Text(
                        if (signedIn) lib.history.error ?: "Nothing watched yet."
                        else "Videos you watch on this phone show up here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = PageGutter),
                    )
                }
            }
            if (signedIn) {
                item(key = "playlistsTitle") { SectionTitle("Playlists") }
                val pinned = listOf(
                    PlaylistRef("WL", "Watch later", null, null),
                    PlaylistRef("LL", "Liked videos", null, null),
                )
                items((pinned + lib.playlists).chunked(2), key = { row -> row.joinToString { it.id } }) { row ->
                    Row(Modifier.padding(horizontal = PageGutter, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { pl -> PlaylistTile(pl, Modifier.weight(1f)) { onOpenPlaylist(pl) } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                item(key = "signInNudge") {
                    Column(Modifier.padding(horizontal = PageGutter, vertical = 16.dp)) {
                        Text(
                            "Sign in for Watch later, liked videos and playlists.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(12.dp))
                        PillButton("Sign in with Google", onSignIn)
                    }
                }
            }
        }
        }
        PageTopFade(listState)
        }
        PageTopBar { HeaderActions(onSearch, onAccount) }
    }
}

@Composable
private fun HistoryCard(video: VideoItem, onClick: () -> Unit) {
    val menu = LocalVideoMenu.current
    Column(Modifier.width(168.dp).prefetchOnPress(video.id).pressable(pressedScale = 0.97f, lift = true, onLongClick = { menu(video) }, onClick = onClick)) {
        Box {
            Thumbnail(video.thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 9f), radius = FeedVideoThumbRadius, videoId = video.id)
            DurationPill(video.durationSec, Modifier.align(Alignment.BottomEnd).padding(6.dp), small = true)
        }
        Spacer(Modifier.height(7.dp))
        FadingTitle(
            text = video.title,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp),
        )
    }
}

@Composable
private fun PlaylistTile(playlist: PlaylistRef, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(26.dp)
    val (icon, tone) = when (playlist.id) {
        "WL" -> SfIcons.Clock to c.primaryContainer
        "LL" -> SfIcons.ThumbUpFill to c.tertiaryContainer
        else -> SfIcons.PlaylistPlay to c.secondaryContainer
    }
    Column(
        modifier = modifier
            .height(132.dp)
            .pressable(pressedScale = 0.97f, onClick = onClick)
            .clip(shape)
            .background(c.surfaceContainerLow)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        if (playlist.thumbnail != null) {
            Thumbnail(playlist.thumbnail, Modifier.size(width = 72.dp, height = 44.dp), radius = 12)
        } else {
            Box(Modifier.size(52.dp).clip(CookieShape).background(tone), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = contentColorFor(tone))
            }
        }
        Column {
            Text(playlist.title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            playlist.subtitle?.let { Text(it, color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
    }
}

/** A playlist's videos (Watch later, Liked, or the user's own). */
@Composable
fun PlaylistScreen(
    playlist: PlaylistRef,
    feed: FeedState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (VideoItem) -> Unit,
    channelAvatarSeed: Map<String, String?> = emptyMap(),
    barShown: Boolean = true,
) {
    LaunchedEffect(playlist.id) { feed.ensureLoaded() }
    val avatarSeed = remember(channelAvatarSeed) { channelAvatarSeed }
    val listState = rememberLazyListState()
    val top = pageBodyTop()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize()) {
        if (feed.videos.isEmpty() && !feed.loading && feed.error == null && !feed.hasMore) {
            Column(Modifier.padding(contentPadding.withTop(top))) {
                LargePageTitle(playlist.title, barAligned = false)
                EmptyState(SfIcons.VideoLibrary, "Nothing here yet", "Videos you add to this playlist will show up here.")
            }
        } else {
            FeedList(
                feed,
                contentPadding.withTop(top),
                onOpen,
                header = { item(key = "page-title") { LargePageTitle(playlist.title, barAligned = false) } },
                indicatorTop = topBarHeight(),
                listState = listState,
                channelAvatarSeed = avatarSeed,
            )
        }
        PageTopFade(listState)
        }
        PageTopBar(onBack = onBack, shown = barShown)
    }
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp,
        modifier = Modifier.pressable(onClick = onClick).padding(8.dp),
    )
}

// ── Search ─────────────────────────────────────────────────────────────────────

/** Lives above the tab switcher so results survive switching tabs. */
@Stable
class SearchState(private val scope: CoroutineScope) {
    var query by mutableStateOf("")
        private set
    var submitted by mutableStateOf("")
        private set
    var results by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var suggestionsLoading by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var generation by mutableStateOf(0)
        private set
    private var job: Job? = null
    private var suggestJob: Job? = null

    fun reset() {
        job?.cancel()
        suggestJob?.cancel()
        query = ""
        submitted = ""
        results = emptyList()
        suggestions = emptyList()
        suggestionsLoading = false
        loading = false
        error = null
    }

    fun updateQuery(text: String) {
        query = text
        suggestJob?.cancel()
        if (text.isBlank()) {
            suggestions = emptyList()
            suggestionsLoading = false
            if (submitted.isNotEmpty() || results.isNotEmpty() || error != null || loading) {
                submitted = ""
                results = emptyList()
                error = null
                loading = false
                job?.cancel()
            }
            return
        }
        suggestJob = scope.launch {
            suggestionsLoading = true
            runCatching { YouTube.searchSuggestions(text) }
                .onSuccess { suggestions = it }
                .onFailure { suggestions = emptyList() }
            suggestionsLoading = false
        }
    }

    fun submit() {
        val q = query.trim()
        if (q.isEmpty()) return
        suggestJob?.cancel()
        suggestions = emptyList()
        suggestionsLoading = false
        SearchHistory.add(q)
        submitted = q
        loading = true
        error = null
        job?.cancel()
        job = scope.launch {
            runCatching { YouTube.search(q) }
                .onSuccess { results = it; generation++ }
                .onFailure { error = YouTube.friendlyError(it) }
            loading = false
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    state: SearchState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (VideoItem) -> Unit,
    scrollToTop: Int = 0,
    barShown: Boolean = true,
) {
    val c = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val view = LocalView.current
    val searchFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    fun showSearchKeyboard() {
        scope.launch {
            delay(80)
            runCatching { searchFocus.requestFocus() }
            keyboard?.show()
        }
    }
    LaunchedEffect(Unit) { showSearchKeyboard() }
    // Search and Enter both dismiss the keyboard — every field, every time. A hide issued
    // while the key event is still being dispatched can be undone by the IME, so the
    // window-level hide is posted to run after it.
    fun submit() {
        focus.clearFocus(force = true)
        keyboard?.hide()
        view.postDelayed({
            view.context.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(view.windowToken, 0)
        }, 120)
        state.submit()
    }
    var focused by remember { mutableStateOf(false) }
    // The keyboard closed by Back or its own button leaves the field focused; drop the focus
    // with it so the history gives way to the results again.
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) { if (!imeVisible && focused) focus.clearFocus() }
    // Past searches, filtered by what's typed: while typing, and on the empty page.
    val history = SearchHistory.matching(state.query)
    val draft = state.query.isNotBlank() &&
        (state.submitted.isEmpty() || state.query.trim() != state.submitted.trim())
    // Suggestions win while drafting; history when focused/empty and not mid-suggestion draft.
    val showHistory = !draft && history.isNotEmpty() && (focused || state.submitted.isEmpty())
    val bodyTop = pageBodyTop()
    Box(Modifier.fillMaxSize().background(c.background).overlayTouchBlock()) {
        Box(Modifier.fillMaxSize()) {
        val listShown = showHistory || (draft && state.suggestions.isNotEmpty()) ||
            (state.results.isNotEmpty() && state.error == null)
        Box(Modifier.fillMaxSize()) {
        Box(Modifier.padding(top = if (listShown) 0.dp else bodyTop)) {
        when {
            draft && state.suggestions.isNotEmpty() -> {
                LazyColumn(contentPadding = contentPadding.withTop(bodyTop)) {
                    items(state.suggestions, key = { it }) { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressable {
                                    state.updateQuery(suggestion)
                                    submit()
                                }
                                .padding(horizontal = PageGutter, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                SfIcons.Search,
                                null,
                                tint = c.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                suggestion,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            // Still drafting: wait for suggestions (no "keep typing" placeholder).
            draft -> Unit
            showHistory -> SearchHistoryList(
                history = history,
                contentPadding = contentPadding.withTop(bodyTop),
                onSearch = { state.updateQuery(it); submit() },
            )
            state.loading && state.results.isEmpty() -> Column(
                Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(contentPadding),
            ) { FeedSkeletons() }
            state.error != null -> EmptyState(
                icon = SfIcons.CloudOff,
                title = "Search didn't load",
                body = state.error.orEmpty(),
                action = { PillButton("Try again", { state.submit() }) },
            )
            state.submitted.isEmpty() -> EmptyState(
                icon = SfIcons.Explore,
                title = "Search without the ads",
                body = "Results play straight away, with no ads and no sign-in.",
            )
            state.results.isEmpty() -> EmptyState(
                icon = SfIcons.History,
                title = "Nothing found",
                body = "No videos matched “${state.submitted}”.",
            )
            else -> {
                val list = rememberScrollToTop(scrollToTop)
                LaunchedEffect(state.generation) { list.scrollToItem(0) }
                // scrollToItem(0) pins this item to the top of the viewport, so the
                // clearance has to be an item. Content padding would scroll away and
                // the first thumbnail would sit under the back button.
                LazyColumn(state = list, contentPadding = contentPadding) {
                    item(key = "top-gap") { Spacer(Modifier.height(bodyTop)) }
                    items(state.results, key = { it.id }) { video ->
                        VideoCard(video, onClick = { onOpen(video) })
                    }
                }
            }
        }
        }
        }
        }
        PageTopBar(
            onBack = onBack,
            shown = barShown,
            accessory = {
                SearchBarField(
                    query = state.query,
                    onQueryChange = state::updateQuery,
                    onSubmit = { submit() },
                    onClear = {
                        state.updateQuery("")
                        showSearchKeyboard()
                    },
                    focusRequester = searchFocus,
                    onFocusChanged = { focused = it },
                )
            },
        )
    }
}

/** The search field, drawn inside the top bar's pill on a surface tone. */
@Composable
private fun SearchBarField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val glass = LocalLiquidGlassEnabled.current && isGlassSupported()
    val hint = if (glass) glassContentColor().copy(alpha = 0.6f) else c.onSurfaceVariant
    val typed = if (glass) glassContentColor() else c.onBackground
    val style = OwnDirectionText.merge(
        TextStyle(
            fontFamily = SfProDisplay,
            fontWeight = FontWeight.W400,
            fontSize = 16.sp,
            letterSpacing = 0.2.sp,
        ),
    )
    Row(
        Modifier.fillMaxSize().padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            SfIcons.Search,
            contentDescription = "Search",
            tint = hint,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .pressable(enabled = query.isNotBlank(), onClick = onSubmit)
                .padding(6.dp),
        )
        Spacer(Modifier.width(4.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Search YouTube", style = style, color = hint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = style.merge(TextStyle(color = typed)),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }, onDone = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChanged(it.isFocused) }
                    .onPreviewKeyEvent { e ->
                        if (e.key == Key.Enter || e.key == Key.NumPadEnter) {
                            if (e.type == KeyEventType.KeyDown) onSubmit()
                            true
                        } else false
                    },
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).pressable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SfIcons.Close, "Clear", tint = hint, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SearchHistoryList(history: List<String>, contentPadding: PaddingValues, onSearch: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    LazyColumn(contentPadding = contentPadding) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
            ) {
                SectionTitle("Recent searches", Modifier.weight(1f).alignByBaseline(), top = 0.dp)
                Box(Modifier.alignByBaseline()) { TextLink("Clear all") { SearchHistory.clear() } }
            }
        }
        items(history, key = { it }) { query ->
            Row(
                modifier = Modifier
                    .animateItem()
                    .fillMaxWidth()
                    .pressable { onSearch(query) }
                    .height(52.dp)
                    .padding(start = PageGutter, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SfIcons.History, null, tint = c.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(16.dp))
                Text(
                    query,
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier.size(44.dp).pressable { SearchHistory.remove(query) },
                    contentAlignment = Alignment.Center,
                ) { Icon(SfIcons.Close, "Remove", tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}
