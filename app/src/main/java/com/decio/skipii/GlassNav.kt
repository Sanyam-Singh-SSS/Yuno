@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.decio.skipii

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.decio.skipii.floatingtabbar.FloatingTabBar
import com.decio.skipii.floatingtabbar.FloatingTabBarDefaults
import com.decio.skipii.floatingtabbar.FloatingTabBarScrollConnection
import com.decio.skipii.icons.SfIcons

private val PillInset = 4.dp
private val TabVerticalPadding = 6.5.dp
private val TabIconSize = 28.dp
private val TabIconLabelGap = 1.dp
private val AccessoryArt = 30.4.dp
private val AccessoryArtCorner = 5.7.dp

/**
 * The collapsed row's margin at either side of the screen, and its items'
 * spacing — BitChord's INLINE_GUTTER / INLINE_SPACING. The outer gutter is
 * already applied by MainActivity, so only the difference goes on the bar.
 */
private val InlineGutter = 28.dp
private val InlineSpacing = 14.dp

/**
 * Half the difference between the expanded tab row, 62dp, and the collapsed
 * one, 48 — a 28dp glyph in 10dp of padding — so the one folds onto the
 * other's centre line.
 */
private val InlineLift = 7.dp

/**
 * BitChord's liquid-glass nav: now-playing accessory over the tab pill, folding
 * inline on scroll. Used only while Liquid Glass is on and the full player is closed.
 */
@Composable
fun GlassNavBar(
    tabs: List<Tab>,
    selected: Tab,
    onTabSelected: (Tab) -> Unit,
    scrollConnection: FloatingTabBarScrollConnection,
    player: PlayerState,
    onExpandPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillShape = remember { RoundedCornerShape(percent = 50) }
    val contentColor = glassContentColor()
    val selectedColor = contentColor
    val unselectedColor = contentColor.copy(alpha = 0.65f)
    val haptics = rememberBarHaptics()
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    val glassSurface: @Composable () -> Modifier = { Modifier.liquidGlass(shape = pillShape) }
    val song = player.current

    FloatingTabBar(
        selectedTabKey = selected,
        scrollConnection = scrollConnection,
        modifier = modifier.fillMaxWidth(),
        tabBarContentModifier = glassSurface,
        inlineAccessory = song?.let { item ->
            { accessoryModifier, _ ->
                GlassNowPlaying(
                    item = item,
                    player = player,
                    isInline = true,
                    contentColor = contentColor,
                    onExpand = onExpandPlayer,
                    modifier = accessoryModifier.then(glassSurface()),
                )
            }
        },
        expandedAccessory = song?.let { item ->
            { accessoryModifier, _ ->
                GlassNowPlaying(
                    item = item,
                    player = player,
                    isInline = false,
                    contentColor = contentColor,
                    onExpand = onExpandPlayer,
                    modifier = accessoryModifier.fillMaxWidth().then(glassSurface()),
                )
            }
        },
        colors = FloatingTabBarDefaults.colors(
            backgroundColor = Color.Transparent,
            accessoryBackgroundColor = Color.Transparent,
            indicatorColor = glassIndicatorColor(),
        ),
        elevations = FloatingTabBarDefaults.elevations(
            inlineElevation = 0.dp,
            expandedElevation = 0.dp,
        ),
        sizes = FloatingTabBarDefaults.sizes(
            tabBarContentPadding = PaddingValues(PillInset),
            tabExpandedContentPadding = PaddingValues(vertical = TabVerticalPadding),
            inlineComponentSpacing = InlineSpacing,
            inlineBarPadding = PaddingValues(
                start = InlineGutter - NavBarGutter,
                end = InlineGutter - NavBarGutter,
                bottom = InlineLift,
            ),
        ),
        contentKey = remember(selected, tabs, contentColor) { listOf(selected, tabs, contentColor) },
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selected
            val tint = if (isSelected) selectedColor else unselectedColor
            val onClick = {
                if (!isSelected) haptics.select()
                currentOnTabSelected(tab)
            }
            tab(
                key = tab,
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier.size(TabIconSize).graphicsLayer {
                            scaleX = tab.iconScale
                            scaleY = tab.iconScale
                        },
                    )
                },
                title = {
                    Text(
                        text = tab.label,
                        style = tabLabelStyle(),
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = TabIconLabelGap),
                    )
                },
                onClick = onClick,
            )
        }
    }
}

@Composable
private fun GlassNowPlaying(
    item: VideoItem,
    player: PlayerState,
    isInline: Boolean,
    contentColor: Color,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pressSource = remember { MutableInteractionSource() }
    val isPressed by pressSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 1.04f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "accessoryPressScale",
    )
    val glyphSlot = if (isInline) 32.dp else 40.dp
    val glyphSize = if (isInline) 24.dp else 29.dp
    // BitChord's accessory type, measured off Apple's: titleMedium and
    // bodyMedium with the line height left to the font, so the two lines come to
    // the same compact height as its bar rather than Material's default 24sp.
    val titleStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = 13.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 0.15.sp,
        lineHeight = TextUnit.Unspecified,
    )
    val artistStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = 12.sp,
        letterSpacing = 0.35.sp,
        lineHeight = TextUnit.Unspecified,
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .then(modifier)
            .clickable(interactionSource = pressSource, indication = null, onClick = onExpand),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = if (isInline) 8.dp else 12.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(AccessoryArt)
                    .clip(RoundedCornerShape(AccessoryArtCorner))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = item.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(if (isInline) 8.dp else 9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = titleStyle,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.channel,
                    style = artistStyle,
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (player.isBuffering && !player.isPlaying) {
                Box(Modifier.size(glyphSlot), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = contentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(if (isInline) 18.dp else 20.dp),
                    )
                }
            } else {
                IconButton(onClick = player::togglePlay, modifier = Modifier.size(glyphSlot)) {
                    Icon(
                        imageVector = if (player.isPlaying) SfIcons.Pause else SfIcons.Play,
                        contentDescription = if (player.isPlaying) "Pause" else "Play",
                        tint = contentColor,
                        modifier = Modifier.size(glyphSize),
                    )
                }
            }
        }
    }
}
