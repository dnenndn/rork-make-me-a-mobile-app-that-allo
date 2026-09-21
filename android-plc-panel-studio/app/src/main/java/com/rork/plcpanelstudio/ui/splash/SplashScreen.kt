package com.rork.plcpanelstudio.ui.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.plcpanelstudio.R
import com.rork.plcpanelstudio.ui.theme.SignalOrange
import com.rork.plcpanelstudio.ui.theme.TextHi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Deep blueprint navy, the same colour as the panel pictures. */
private val SplashNavy = Color(0xFF00263F)
private val SplashGrid = Color(0xFF053A57)

/** How long the loading page stays before it fades out to the app. */
private const val SPLASH_HOLD_MS = 2000L

/**
 * Shows the loading page over [content] when the app opens, then fades it out.
 * The app is already composed underneath, so the hand-over is instant. Turning the phone
 * does not replay the loading page.
 */
@Composable
fun AppLaunchGate(content: @Composable () -> Unit) {
    var finished by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!finished) {
            delay(SPLASH_HOLD_MS)
            finished = true
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(
            visible = !finished,
            exit = fadeOut(animationSpec = tween(durationMillis = 450))
        ) {
            SplashScreen()
        }
    }
}

/** The loading page: logo, factory name underneath, and a slim progress bar. */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.82f) }
    val nameAlpha = remember { Animatable(0f) }
    val nameShift = remember { Animatable(14f) }

    LaunchedEffect(Unit) {
        launch { logoAlpha.animateTo(1f, tween(durationMillis = 600)) }
        launch {
            logoScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
        }
        delay(350)
        launch { nameAlpha.animateTo(1f, tween(durationMillis = 600)) }
        launch { nameShift.animateTo(0f, tween(durationMillis = 600, easing = FastOutSlowInEasing)) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SplashNavy)
            // Swallow touches so nothing underneath can be tapped while loading.
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        BlueprintGrid()
        // Soft warm glow behind the logo.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(SignalOrange.copy(alpha = 0.14f), Color.Transparent)
                    )
                )
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.splash_logo),
                contentDescription = "Ideal Brique",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(210.dp)
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value)
            )
            Spacer(Modifier.height(26.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = TextHi)) { append("IDEAL ") }
                    withStyle(SpanStyle(color = SignalOrange)) { append("BRIQUE") }
                },
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(y = nameShift.value.dp)
                    .alpha(nameAlpha.value)
            )
            Spacer(Modifier.height(34.dp))
            LinearProgressIndicator(
                color = SignalOrange,
                trackColor = Color.White.copy(alpha = 0.12f),
                modifier = Modifier
                    .width(150.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .alpha(nameAlpha.value)
            )
        }
    }
}

/** Faint blueprint grid, like the background of the panel pictures. */
@Composable
private fun BlueprintGrid() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val step = 18.dp.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(SplashGrid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(SplashGrid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
    }
}
