package com.rork.plcpanelstudio.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.plcpanelstudio.data.ComponentKind
import com.rork.plcpanelstudio.ui.theme.Line
import com.rork.plcpanelstudio.ui.theme.LineBright
import com.rork.plcpanelstudio.ui.theme.NameplateStyle
import com.rork.plcpanelstudio.ui.theme.SignalOrange
import com.rork.plcpanelstudio.ui.theme.SignalTeal
import com.rork.plcpanelstudio.ui.theme.Steel
import com.rork.plcpanelstudio.ui.theme.SteelDark
import com.rork.plcpanelstudio.ui.theme.SteelLight
import com.rork.plcpanelstudio.ui.theme.SurfaceRaised
import com.rork.plcpanelstudio.ui.theme.TextLow
import com.rork.plcpanelstudio.ui.theme.TextMid
import kotlin.math.cos
import kotlin.math.sin

/** Colour a component lights with when its signal is live. */
fun glowColorFor(kind: ComponentKind): Color = when (kind) {
    ComponentKind.BUTTON -> SignalOrange
    ComponentKind.SELECTOR -> SignalOrange
    ComponentKind.LAMP -> SignalTeal
    ComponentKind.GAUGE -> SignalOrange
}

private val DialBlack = Color(0xFF0C1013)
private val ScrewSlot = Color(0xFF10151A)

/**
 * Renders a miniature panel-mount industrial part: a square metal faceplate with
 * corner screws and the hardware mounted in the middle. [active] drives the glow,
 * [analogValue] (0f..1f) drives the gauge needle, [selectorPosition] drives the knob.
 */
@Composable
fun HardwareFace(
    kind: ComponentKind,
    active: Boolean,
    modifier: Modifier = Modifier,
    analogValue: Float = 0f,
    selectorPosition: Int = 0,
    selectorPositions: Int = 2,
    pressed: Boolean = false
) {
    val glow = glowColorFor(kind)
    val intensity by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "glow"
    )
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(durationMillis = 90),
        label = "press"
    )
    val needle by animateFloatAsState(
        targetValue = analogValue.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 420),
        label = "needle"
    )
    val knob by animateFloatAsState(
        targetValue = selectorPosition.toFloat(),
        animationSpec = tween(durationMillis = 200),
        label = "knob"
    )

    Canvas(modifier = modifier) {
        when (kind) {
            ComponentKind.BUTTON -> drawPushButton(glow, intensity, press)
            ComponentKind.LAMP -> drawLamp(glow, intensity)
            ComponentKind.SELECTOR -> drawSelector(glow, intensity, knob, selectorPositions)
            ComponentKind.GAUGE -> drawGauge(glow, intensity, needle)
        }
    }
}

// ---------------------------------------------------------------- module plate

/** Draws the square brushed-metal faceplate with corner screws; returns its centre. */
private fun DrawScope.drawModulePlate(): Offset {
    val s = size.minDimension
    val inset = s * 0.02f
    val plateSize = Size(s - inset * 2f, s - inset * 2f)

    // Soft ambient shadow so the plate sits proud of the panel.
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.5f),
        topLeft = Offset(inset + s * 0.015f, inset + s * 0.025f),
        size = plateSize,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.13f)
    )
    // Brushed metal plate.
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(SteelLight, Steel, SteelDark),
            startY = inset,
            endY = inset + plateSize.height
        ),
        topLeft = Offset(inset, inset),
        size = plateSize,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.13f)
    )
    // Bright top bevel + dark bottom bevel.
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
            startY = inset,
            endY = inset + plateSize.height * 0.3f
        ),
        topLeft = Offset(inset, inset),
        size = plateSize,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.13f)
    )
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.35f),
        topLeft = Offset(inset, inset),
        size = plateSize,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.13f),
        style = Stroke(width = s * 0.012f)
    )
    // Recessed mounting well behind the hardware.
    val wellInset = s * 0.14f
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF12181D), Color(0xFF1A2126)),
            startY = wellInset,
            endY = s - wellInset
        ),
        topLeft = Offset(wellInset, wellInset),
        size = Size(s - wellInset * 2f, s - wellInset * 2f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.07f)
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
            startY = wellInset,
            endY = wellInset + s * 0.08f
        ),
        topLeft = Offset(wellInset, wellInset),
        size = Size(s - wellInset * 2f, s * 0.1f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.05f)
    )

    val screwOffset = s * 0.075f
    val screwRadius = s * 0.038f
    drawScrew(Offset(screwOffset, screwOffset), screwRadius)
    drawScrew(Offset(s - screwOffset, screwOffset), screwRadius)
    drawScrew(Offset(screwOffset, s - screwOffset), screwRadius)
    drawScrew(Offset(s - screwOffset, s - screwOffset), screwRadius)

    return Offset(s / 2f, s / 2f)
}

/** Hex-head panel screw with a slotted drive. */
private fun DrawScope.drawScrew(center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(SteelLight, SteelDark),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
    drawCircle(
        color = ScrewSlot,
        radius = radius * 0.62f,
        center = center
    )
    drawLine(
        color = SteelLight.copy(alpha = 0.8f),
        start = Offset(center.x - radius * 0.42f, center.y - radius * 0.42f),
        end = Offset(center.x + radius * 0.42f, center.y + radius * 0.42f),
        strokeWidth = radius * 0.3f,
        cap = StrokeCap.Round
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.22f),
        radius = radius,
        center = center,
        style = Stroke(width = radius * 0.18f)
    )
}

/** Glow halo spilling past the module when the signal is live. */
private fun DrawScope.drawHalo(center: Offset, glow: Color, intensity: Float, reach: Float) {
    if (intensity <= 0.02f) return
    val s = size.minDimension
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                glow.copy(alpha = 0.5f * intensity),
                glow.copy(alpha = 0.14f * intensity),
                Color.Transparent
            ),
            center = center,
            radius = s * reach
        ),
        radius = s * reach,
        center = center
    )
}

/** Knurled metal collar that grips the cap or lens. */
private fun DrawScope.drawCollar(center: Offset, radius: Float, intensity: Float, glow: Color) {
    drawCircle(
        brush = Brush.verticalGradient(
            colors = listOf(SteelLight, SteelDark),
            startY = center.y - radius,
            endY = center.y + radius
        ),
        radius = radius,
        center = center
    )
    // Knurl ticks around the circumference.
    val ticks = 24
    for (index in 0 until ticks) {
        val angle = (index * 360f / ticks) * (Math.PI / 180.0)
        val cosA = cos(angle).toFloat()
        val sinA = sin(angle).toFloat()
        drawLine(
            color = Color.Black.copy(alpha = 0.4f),
            start = Offset(center.x + radius * 0.86f * cosA, center.y + radius * 0.86f * sinA),
            end = Offset(center.x + radius * 0.97f * cosA, center.y + radius * 0.97f * sinA),
            strokeWidth = radius * 0.055f
        )
    }
    drawCircle(
        color = Color.Black.copy(alpha = 0.45f),
        radius = radius * 0.8f,
        center = center
    )
    if (intensity > 0.02f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glow.copy(alpha = 0.35f * intensity), Color.Transparent),
                center = center,
                radius = radius
            ),
            radius = radius * 0.8f,
            center = center
        )
    }
}

// ---------------------------------------------------------------- parts

private fun DrawScope.drawPushButton(glow: Color, intensity: Float, press: Float) {
    val center = drawModulePlate()
    drawHalo(center, glow, intensity, 0.62f)
    val s = size.minDimension

    drawCollar(center, s * 0.37f, intensity, glow)

    val capRadius = s * (0.28f - 0.02f * press)
    // Cap shadow inside the collar well.
    drawCircle(
        color = Color.Black.copy(alpha = 0.5f),
        radius = capRadius * 1.08f,
        center = Offset(center.x, center.y + s * 0.008f)
    )
    // Chunky cap: lit to signal colour when active, brushed steel when idle.
    val capTop = lerpColor(Color(0xFF5A646D), glow, intensity)
    val capBottom = lerpColor(Color(0xFF2A3238), glow.copy(alpha = 0.65f), intensity)
    drawCircle(
        brush = Brush.verticalGradient(
            colors = listOf(capTop, capBottom),
            startY = center.y - capRadius,
            endY = center.y + capRadius
        ),
        radius = capRadius,
        center = center
    )
    // Hot core when active.
    if (intensity > 0.02f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.55f * intensity),
                    glow.copy(alpha = 0.5f * intensity),
                    Color.Transparent
                ),
                center = Offset(center.x, center.y - capRadius * 0.1f),
                radius = capRadius
            ),
            radius = capRadius,
            center = center
        )
    }
    // Specular highlight arc.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.34f - 0.12f * press),
                Color.Transparent
            ),
            center = Offset(center.x - capRadius * 0.32f, center.y - capRadius * 0.4f),
            radius = capRadius * 0.75f
        ),
        radius = capRadius * 0.75f,
        center = Offset(center.x - capRadius * 0.32f, center.y - capRadius * 0.4f)
    )
    drawCircle(
        color = Color.Black.copy(alpha = 0.4f),
        radius = capRadius,
        center = center,
        style = Stroke(width = s * 0.012f)
    )
}

private fun DrawScope.drawLamp(glow: Color, intensity: Float) {
    val center = drawModulePlate()
    drawHalo(center, glow, intensity, 0.66f)
    val s = size.minDimension

    drawCollar(center, s * 0.37f, intensity, glow)

    val lensRadius = s * 0.29f
    // Deep lens recess.
    drawCircle(color = Color.Black.copy(alpha = 0.6f), radius = lensRadius * 1.05f, center = center)
    // Glass lens: dark tinted when idle, blazing when live.
    val lensCenter = lerpColor(Color(0xFF1E2A24), Color.White, intensity * 0.85f)
    val lensEdge = lerpColor(Color(0xFF141D18), glow, intensity)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(lensCenter, lensEdge),
            center = Offset(center.x, center.y - lensRadius * 0.12f),
            radius = lensRadius * 1.25f
        ),
        radius = lensRadius,
        center = center
    )
    // Hot core.
    if (intensity > 0.02f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.9f * intensity),
                    glow.copy(alpha = 0.65f * intensity),
                    Color.Transparent
                ),
                center = center,
                radius = lensRadius
            ),
            radius = lensRadius,
            center = center
        )
    }
    // Glass reflection.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.3f), Color.Transparent),
            center = Offset(center.x - lensRadius * 0.3f, center.y - lensRadius * 0.34f),
            radius = lensRadius * 0.6f
        ),
        radius = lensRadius * 0.6f,
        center = Offset(center.x - lensRadius * 0.3f, center.y - lensRadius * 0.34f)
    )
    drawCircle(
        color = Color.Black.copy(alpha = 0.4f),
        radius = lensRadius,
        center = center,
        style = Stroke(width = s * 0.01f)
    )
}

private fun DrawScope.drawSelector(glow: Color, intensity: Float, position: Float, positions: Int) {
    val center = drawModulePlate()
    drawHalo(center, glow, intensity, 0.6f)
    val s = size.minDimension

    drawCollar(center, s * 0.37f, intensity, glow)

    // Detent ticks on the dial face.
    val span = 100f
    val steps = (positions - 1).coerceAtLeast(1)
    for (index in 0 until positions) {
        val angleDeg = -90f - span / 2f + span * index / steps
        val rad = Math.toRadians(angleDeg.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        drawLine(
            color = TextLow,
            start = Offset(center.x + s * 0.2f * cosA, center.y + s * 0.2f * sinA),
            end = Offset(center.x + s * 0.25f * cosA, center.y + s * 0.25f * sinA),
            strokeWidth = s * 0.016f,
            cap = StrokeCap.Round
        )
    }

    val knobRadius = s * 0.24f
    drawCircle(
        color = Color.Black.copy(alpha = 0.5f),
        radius = knobRadius * 1.06f,
        center = Offset(center.x, center.y + s * 0.006f)
    )
    drawCircle(
        brush = Brush.verticalGradient(
            colors = listOf(SteelLight, SteelDark),
            startY = center.y - knobRadius,
            endY = center.y + knobRadius
        ),
        radius = knobRadius,
        center = center
    )
    // Pointer flat, tinted when live.
    val pointerDeg = -90f - span / 2f + span * (position / steps)
    val pointerRad = Math.toRadians(pointerDeg.toDouble())
    drawLine(
        color = lerpColor(Color(0xFFD7DEE4), glow, intensity),
        start = center,
        end = Offset(
            center.x + knobRadius * 0.84f * cos(pointerRad).toFloat(),
            center.y + knobRadius * 0.84f * sin(pointerRad).toFloat()
        ),
        strokeWidth = s * 0.032f,
        cap = StrokeCap.Round
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.16f),
        radius = knobRadius * 0.3f,
        center = Offset(center.x - knobRadius * 0.25f, center.y - knobRadius * 0.3f)
    )
    drawCircle(
        color = Color.Black.copy(alpha = 0.4f),
        radius = knobRadius,
        center = center,
        style = Stroke(width = s * 0.012f)
    )
}

private fun DrawScope.drawGauge(glow: Color, intensity: Float, value: Float) {
    val center = drawModulePlate()
    drawHalo(center, glow, intensity, 0.6f)
    val s = size.minDimension

    drawCollar(center, s * 0.37f, intensity, glow)

    // Black instrument dial.
    val dialRadius = s * 0.3f
    drawCircle(color = DialBlack, radius = dialRadius, center = center)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
            center = Offset(center.x, center.y - dialRadius * 0.4f),
            radius = dialRadius
        ),
        radius = dialRadius,
        center = center
    )

    val arcRadius = dialRadius * 0.78f
    val startAngle = 150f
    val sweep = 240f
    val arcTopLeft = Offset(center.x - arcRadius, center.y - arcRadius)
    val arcSize = Size(arcRadius * 2f, arcRadius * 2f)

    // Track + live sweep.
    drawArc(
        color = Color(0xFF2A3238),
        startAngle = startAngle,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcSize,
        style = Stroke(width = s * 0.022f, cap = StrokeCap.Butt)
    )
    if (value > 0f) {
        drawArc(
            color = glow.copy(alpha = 0.4f + 0.6f * intensity),
            startAngle = startAngle,
            sweepAngle = sweep * value,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = s * 0.022f, cap = StrokeCap.Round)
        )
    }

    // Tick marks.
    val tickCount = 9
    for (index in 0 until tickCount) {
        val angleDeg = startAngle + sweep * index / (tickCount - 1)
        val rad = Math.toRadians(angleDeg.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        val isMajor = index % 2 == 0
        drawLine(
            color = Color(0xFFB8C2CA).copy(alpha = if (isMajor) 0.9f else 0.5f),
            start = Offset(center.x + arcRadius * (if (isMajor) 0.82f else 0.88f) * cosA, center.y + arcRadius * (if (isMajor) 0.82f else 0.88f) * sinA),
            end = Offset(center.x + arcRadius * 0.97f * cosA, center.y + arcRadius * 0.97f * sinA),
            strokeWidth = s * if (isMajor) 0.014f else 0.008f
        )
    }

    // Needle.
    val needleRad = Math.toRadians((startAngle + sweep * value).toDouble())
    drawLine(
        color = Color(0xFFE8EEF2),
        start = Offset(center.x - arcRadius * 0.12f * cos(needleRad).toFloat(), center.y - arcRadius * 0.12f * sin(needleRad).toFloat()),
        end = Offset(
            center.x + arcRadius * 0.72f * cos(needleRad).toFloat(),
            center.y + arcRadius * 0.72f * sin(needleRad).toFloat()
        ),
        strokeWidth = s * 0.016f,
        cap = StrokeCap.Round
    )
    drawCircle(color = SteelLight, radius = s * 0.036f, center = center)
    drawCircle(color = SteelDark, radius = s * 0.018f, center = center)
}

private fun lerpColor(from: Color, to: Color, t: Float): Color {
    val clamped = t.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * clamped,
        green = from.green + (to.green - from.green) * clamped,
        blue = from.blue + (to.blue - from.blue) * clamped,
        alpha = from.alpha + (to.alpha - from.alpha) * clamped
    )
}

/** Engraved nameplate strip used above hardware faces. */
@Composable
fun Nameplate(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) SurfaceRaised else SteelDark)
            .border(1.dp, if (active) LineBright else Line, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = NameplateStyle,
            color = if (active) SignalOrange else TextMid,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Small palette tile representing a draggable part in the library rail. */
@Composable
fun PartTile(
    kind: ComponentKind,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) SurfaceRaised else Color(0xFF151B20))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) SignalOrange else Line,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        HardwareFace(
            kind = kind,
            active = selected,
            modifier = Modifier.size(38.dp)
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = kind.displayName,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 10.sp,
            color = if (selected) SignalOrange else TextMid,
            maxLines = 1
        )
    }
}

/** Rendered component with its nameplate and state caption, used on the monitor screen. */
@Composable
fun HardwareUnit(
    kind: ComponentKind,
    label: String,
    caption: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    analogValue: Float = 0f,
    selectorPosition: Int = 0,
    selectorPositions: Int = 2,
    pressed: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Nameplate(text = label, active = active, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            HardwareFace(
                kind = kind,
                active = active,
                analogValue = analogValue,
                selectorPosition = selectorPosition,
                selectorPositions = selectorPositions,
                pressed = pressed,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = if (active) glowColorFor(kind) else TextLow,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Status dot + label row reused across panels and devices. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 8.dp) {
    Canvas(modifier = modifier.size(size)) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.45f), Color.Transparent),
                radius = this.size.minDimension
            ),
            radius = this.size.minDimension
        )
        drawCircle(color = color, radius = this.size.minDimension / 2.6f)
    }
}

@Composable
fun StatusRow(color: Color, text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        StatusDot(color = color)
        Spacer(Modifier.size(6.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
