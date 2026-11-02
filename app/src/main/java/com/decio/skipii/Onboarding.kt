package com.decio.skipii

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.decio.skipii.icons.SfIcons

private const val STEP_COUNT = 4

/** First-launch setup: welcome → appearance → optional sign-in → ready. */
object Onboarding {
    private var prefs: android.content.SharedPreferences? = null
    var completed: Boolean = false
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
        completed = prefs?.getBoolean("completed", false) == true
    }

    fun finish() {
        completed = true
        prefs?.edit()?.putBoolean("completed", true)?.apply()
    }
}

@Composable
fun OnboardingScreen(onFinished: () -> Unit, onSignIn: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    var step by remember { mutableIntStateOf(0) }
    val signedIn = Account.signedIn
    val progress by animateFloatAsState(
        targetValue = step / (STEP_COUNT - 1f),
        animationSpec = if (reduced) snap() else tween(280, easing = Motion.Emphasized),
        label = "onboardingProgress",
    )

    fun tick() { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }

    fun goNext() {
        tick()
        if (step >= STEP_COUNT - 1) {
            Onboarding.finish()
            onFinished()
        } else {
            step++
        }
    }

    fun goBack() {
        if (step > 0) {
            tick()
            step--
        }
    }

    BackHandler(enabled = step > 0) { goBack() }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (reduced) fadeIn(snap()) togetherWith fadeOut(snap())
                else {
                    val forward = targetState > initialState
                    (fadeIn(tween(220, easing = Motion.Emphasized)) +
                        slideInHorizontally(tween(280, easing = Motion.Emphasized)) {
                            if (forward) it / 8 else -it / 8
                        }) togetherWith
                        (fadeOut(tween(140)) +
                            slideOutHorizontally(tween(220, easing = Motion.Emphasized)) {
                                if (forward) -it / 10 else it / 10
                            })
                }
            },
            modifier = Modifier.weight(1f),
            label = "onboardingStep",
        ) { page ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = PageGutter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(28.dp))
                when (page) {
                    0 -> WelcomeStep()
                    1 -> AppearanceStep()
                    2 -> AccountStep(signedIn = signedIn, onSignIn = onSignIn)
                    else -> ReadyStep()
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        // Bottom chrome: progress + back/next (middle steps), or skip + FAB (ends).
        Column(Modifier.fillMaxWidth().padding(horizontal = PageGutter, vertical = TopBarContentGap)) {
            if (step in 1 until STEP_COUNT - 1) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = c.primary,
                    trackColor = c.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.pressable(onClick = ::goBack),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(SfIcons.ArrowBack, null, tint = c.onSurfaceVariant)
                        Text("Back", color = c.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Row(
                        Modifier
                            .pressable(onClick = ::goNext)
                            .clip(CircleShape)
                            .background(c.primary)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Next", color = c.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.width(4.dp))
                        Icon(SfIcons.ArrowForward, null, tint = c.onPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (step == 0) {
                        Text(
                            "Skip",
                            color = c.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.pressable {
                                tick()
                                Onboarding.finish()
                                onFinished()
                            }.padding(horizontal = 8.dp, vertical = 12.dp),
                        )
                    } else {
                        Row(
                            Modifier.pressable(onClick = ::goBack),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(SfIcons.ArrowBack, null, tint = c.onSurfaceVariant)
                            Text("Back", color = c.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    Box(
                        Modifier
                            .size(56.dp)
                            .pressable(onClick = ::goNext)
                            .clip(CircleShape)
                            .background(c.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (step == STEP_COUNT - 1) SfIcons.Check else SfIcons.ArrowForward,
                            if (step == STEP_COUNT - 1) "Get started" else "Next",
                            tint = c.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    val c = MaterialTheme.colorScheme
    Spacer(Modifier.height(24.dp))
    Box(
        Modifier
            .size(88.dp)
            .clip(SunnyShape)
            .background(c.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        BrandMark()
    }
    Spacer(Modifier.height(28.dp))
    Text(
        "Welcome to Skipii",
        fontFamily = RoundedHeavy,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        letterSpacing = (-1.2).sp,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        "Your YouTube, without the ads.",
        color = c.onSurfaceVariant,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        lineHeight = 22.sp,
    )
    Spacer(Modifier.height(36.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        FeatureCard(
            SfIcons.Block,
            "No ads",
            "Play anything from YouTube without commercials or an ad SDK.",
        )
        FeatureCard(
            SfIcons.Headphones,
            "Background audio",
            "Lock the phone or switch apps — the sound keeps going.",
        )
        FeatureCard(
            SfIcons.Sync,
            "Optional full sync",
            "Sign in for Home, subscriptions, likes and history — or stay signed out.",
        )
    }
}

@Composable
private fun AppearanceStep() {
    val c = MaterialTheme.colorScheme
    Icon(SfIcons.Palette, null, tint = c.primary, modifier = Modifier.size(64.dp))
    Spacer(Modifier.height(16.dp))
    Text(
        "Make it yours",
        fontFamily = RoundedHeavy,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        letterSpacing = (-0.8).sp,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Pick a look. You can change this any time in the account sheet.",
        color = c.onSurfaceVariant,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        lineHeight = 21.sp,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
    Spacer(Modifier.height(28.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(c.surfaceContainer)
            .padding(18.dp),
    ) {
        AppearanceSettings()
    }
}

@Composable
private fun AccountStep(signedIn: Boolean, onSignIn: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val profile = Account.profile
    Icon(SfIcons.AccountCircle, null, tint = c.primary, modifier = Modifier.size(64.dp))
    Spacer(Modifier.height(16.dp))
    Text(
        if (signedIn) "You're signed in" else "Connect your account",
        fontFamily = RoundedHeavy,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        letterSpacing = (-0.8).sp,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    if (signedIn && profile != null) {
        Spacer(Modifier.height(12.dp))
        ChannelAvatar(profile.avatar, profile.name, size = 72)
        Spacer(Modifier.height(12.dp))
        Text(profile.name, fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        profile.handle?.let { Text(it, color = c.onSurfaceVariant, fontSize = 14.sp) }
        Spacer(Modifier.height(16.dp))
        Text(
            "Home, subscriptions, history and playlists will sync on this phone.",
            color = c.onSurfaceVariant,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
        )
    } else {
        Text(
            "Sign in to get your recommendations, subscriptions, history and playlists. " +
                "Likes, subscribes, Watch later and watch history sync back to your account.",
            color = c.onSurfaceVariant,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(c.tertiaryContainer)
                .padding(16.dp),
        ) {
            Text(
                "Google treats unofficial apps as against its terms and can suspend accounts that use them. " +
                    "A secondary Google account keeps that risk away from your main one.",
                color = c.onTertiaryContainer,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(24.dp))
        PillButton("Sign in with Google", onSignIn)
        Spacer(Modifier.height(10.dp))
        Text(
            "You can skip this — everything still works signed out.",
            color = c.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReadyStep() {
    val c = MaterialTheme.colorScheme
    Spacer(Modifier.height(48.dp))
    Box(
        Modifier
            .size(88.dp)
            .clip(SunnyShape)
            .background(c.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(SfIcons.Check, null, tint = c.onPrimaryContainer, modifier = Modifier.size(44.dp))
    }
    Spacer(Modifier.height(28.dp))
    Text(
        "You're ready",
        fontFamily = RoundedHeavy,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        letterSpacing = (-1.2).sp,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        "Search, play, and queue videos. Open the account sheet any time for appearance, Shorts and SponsorBlock.",
        color = c.onSurfaceVariant,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        lineHeight = 22.sp,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

@Composable
private fun FeatureCard(icon: ImageVector, title: String, body: String) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.surfaceContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(c.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = c.onPrimaryContainer, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Spacer(Modifier.height(2.dp))
            Text(body, color = c.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
        }
    }
}
