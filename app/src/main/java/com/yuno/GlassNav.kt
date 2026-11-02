@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.yuno

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuno.floatingtabbar.FloatingTabBar
import com.yuno.floatingtabbar.FloatingTabBarDefaults
import com.yuno.floatingtabbar.FloatingTabBarScrollConnection

private val PillInset = 4.dp
private val TabVerticalPadding = 6.5.dp
private val TabIconSize = 28.dp
private val TabIconLabelGap = 1.dp

/** Spacing between the collapsed row's items. */
private val InlineSpacing = 14.dp

/**
 * Half the difference between the expanded tab row, 62dp, and the collapsed
 * one, 48 — a 28dp glyph in 10dp of padding — so the one folds onto the
 * other's centre line.
 */
private val InlineLift = 7.dp

/**
 * Liquid-glass nav: now-playing accessory over the tab pill, folding
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
    dock: PlayerDock? = null,
    pull: MiniPlayerPull? = null,
    modifier: Modifier = Modifier,
) {
    val pillShape = remember { RoundedCornerShape(percent = 50) }
    val contentColor = glassContentColor()
    val selectedColor = contentColor
    val unselectedColor = contentColor.copy(alpha = 0.65f)
    val haptics = rememberBarHaptics()
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    val glassSurface: @Composable () -> Modifier = { Modifier.liquidGlass(shape = pillShape) }
    val song = if (selected == Tab.Shorts) null else player.current

    FloatingTabBar(
        selectedTabKey = selected,
        scrollConnection = scrollConnection,
        modifier = modifier.fillMaxWidth(),
        tabBarContentModifier = glassSurface,
        inlineAccessory = if (song != null) {
            { accessoryModifier, scope ->
                val visible = scope.transition.targetState == EnterExitState.Visible
                // Settled only. While the shared element is moving, a TextureView
                // is a hole in the overlay: the fold remeasures it every frame
                // and the scroll drops. Morph a still. The frame was
                // captured just before this flip (see [FloatingTabBarScrollConnection.onBeforeToggle]).
                val live = visible &&
                    scope.transition.currentState == EnterExitState.Visible &&
                    dock?.coversMiniArt != true
                MiniPlayer(
                    state = player,
                    onExpand = onExpandPlayer,
                    // The pull lifts the pill. It has to sit outside the accessory's clip,
                    // or the top of the bar is cut off as the finger rises — gesture on the
                    // outside of the glass.
                    modifier = Modifier.miniPlayerPullGestures(pull, onDismiss = player::close).then(accessoryModifier),
                    shape = pillShape,
                    showVideo = live,
                    compact = true,
                    // Only the row the fold is settling on reports where the artwork lands.
                    dock = if (visible) dock else null,
                    pull = null,
                    gestures = false,
                )
            }
        } else null,
        expandedAccessory = if (song != null) {
            { accessoryModifier, scope ->
                val visible = scope.transition.targetState == EnterExitState.Visible
                val live = visible &&
                    scope.transition.currentState == EnterExitState.Visible &&
                    dock?.coversMiniArt != true
                MiniPlayer(
                    state = player,
                    onExpand = onExpandPlayer,
                    modifier = Modifier.miniPlayerPullGestures(pull, onDismiss = player::close).fillMaxWidth().then(accessoryModifier),
                    shape = pillShape,
                    showVideo = live,
                    dock = if (visible) dock else null,
                    pull = null,
                    gestures = false,
                )
            }
        } else null,
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
            // No extra side inset. The bar already sits at the page gutter, and
            // pulling the collapsed row in made it narrower than the lists.
            inlineBarPadding = PaddingValues(bottom = InlineLift),
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

