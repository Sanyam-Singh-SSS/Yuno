package com.yuno

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page
import com.yuno.icons.SfIcons

/** Opens a channel page from anywhere (player channel row, card avatars). Provided by the shell. */
val LocalChannelOpener = staticCompositionLocalOf<(String) -> Unit> { {} }

@Stable
class ChannelState(val url: String, private val scope: CoroutineScope) {
    var details by mutableStateOf<ChannelDetails?>(null)
        private set
    var videos by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var subscribed by mutableStateOf<Boolean?>(null)
        private set
    private var next: String? = null
    val hasMore get() = next != null

    init { load() }

    fun load() {
        loading = true
        error = null
        scope.launch {
            runCatching { YouTube.channel(url) }
                .onSuccess { base ->
                    var d = base
                    details = d
                    if (Account.signedIn) launch {
                        subscribed = runCatching { InnerTube.channelSubscribed(d.id, d.name, d.avatar) }.getOrNull()
                    }
                    runCatching { YouTube.channelVideos(d, null) }
                        .onSuccess { p ->
                            videos = p.videos
                            next = p.next
                            p.channelHeader?.let { d = d.withHeader(it) }
                            details = d
                        }
                        .onFailure {
                            details = d
                            error = YouTube.friendlyError(it)
                        }
                }
                .onFailure { error = YouTube.friendlyError(it) }
            loading = false
        }
    }

    fun loadMore() {
        val d = details ?: return
        val page = next ?: return
        if (loading) return
        loading = true
        scope.launch {
            runCatching { YouTube.channelVideos(d, page) }
                .onSuccess { p ->
                    val seen = videos.mapTo(HashSet()) { it.id }
                    videos = videos + p.videos.filter { seen.add(it.id) }
                    next = p.next
                }
            loading = false
        }
    }

    fun toggleSubscribe(onError: (String) -> Unit) {
        if (!Account.signedIn) { onError("Sign in to subscribe."); return }
        val id = details?.id ?: return
        val before = subscribed ?: return
        subscribed = !before
        scope.launch {
            runCatching { InnerTube.setSubscribed(id, !before) }
                .onFailure { subscribed = before; onError("Couldn't update your subscription.") }
        }
    }
}

@Composable
fun ChannelScreen(state: ChannelState, contentPadding: PaddingValues, onBack: () -> Unit, onOpen: (VideoItem) -> Unit, onMessage: (String) -> Unit, barShown: Boolean = true) {
    val c = MaterialTheme.colorScheme
    val list = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 4 }
    }
    LaunchedEffect(nearEnd, state.videos.size) { if (nearEnd && state.videos.isNotEmpty()) state.loadMore() }
    // Same clearance as Home before the first feed row: pageContentTop + the in-list title block.
    val top = pageBodyTop()
    Box(Modifier.fillMaxSize().background(c.background)) {
        Box(Modifier.fillMaxSize()) {
        val d = state.details
        when {
            d == null && state.error != null -> Box(Modifier.padding(top = top)) { EmptyState(
                SfIcons.CloudOff, "Couldn't load this channel", state.error.orEmpty(),
                action = { PillButton("Try again", state::load) },
            ) }
            d == null -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding.withTop(top)),
            ) {
                ChannelSkeletons()
            }
            else -> LazyColumn(state = list, contentPadding = contentPadding.withTop(top)) {
                item(key = "header") {
                    Column(Modifier.padding(horizontal = PageGutter)) {
                        if (d.banner != null) {
                            Thumbnail(d.banner, Modifier.fillMaxWidth().aspectRatio(5.6f), radius = FeedVideoThumbRadius)
                            Spacer(Modifier.height(16.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChannelAvatar(d.avatar, d.name, size = 76)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                ChannelNameWithBadge(d.name, verified = d.verified, maxLines = 2)
                                d.handle?.let { handle ->
                                    Spacer(Modifier.height(2.dp))
                                    Text("@$handle", color = c.onSurfaceVariant, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                channelStatLine(d.subscribers, d.videoCount).takeIf { it.isNotBlank() }?.let { stats ->
                                    Text(stats, color = c.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        if (Account.signedIn && state.subscribed != null) {
                            SubscribeWithBell(
                                subscribed = state.subscribed == true,
                                channel = BellChannel(d.id, d.name, d.avatar),
                                onSubscribe = { state.toggleSubscribe(onMessage) },
                                onMessage = onMessage,
                                modifier = Modifier.fillMaxWidth(),
                                wide = true,
                            )
                        } else {
                            ChannelSubscribeButton(state.subscribed == true) { state.toggleSubscribe(onMessage) }
                        }
                        if (d.description.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(d.description, color = c.onSurfaceVariant, fontSize = 13.5.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                item(key = "videosTitle") { SectionTitle("Videos") }
                items(state.videos, key = { it.id }) { video -> VideoCard(video, onClick = { onOpen(video) }) }
                if (state.loading || state.hasMore) item { FeedSkeletons(1) }
            }
        }
        PageTopFade(if (d != null) list else null)
        }
        PageTopBar(onBack = onBack, shown = barShown)
    }
}

@Composable
private fun ChannelSubscribeButton(subscribed: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .squishOn(subscribed)
            .fillMaxWidth()
            .height(46.dp)
            .pressable(onClick = onClick)
            .clip(CircleShape)
            .background(if (subscribed) c.secondaryContainer else c.onSurface),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (subscribed) {
            FlourishIcon(SfIcons.Notifications, null, active = true, kind = Flourish.Ring, tint = c.onSecondaryContainer, size = 18.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(if (subscribed) "Subscribed" else "Subscribe", color = if (subscribed) c.onSecondaryContainer else c.surface, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}
