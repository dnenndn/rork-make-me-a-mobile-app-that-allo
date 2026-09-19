package com.rork.plcpanelstudio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.plcpanelstudio.R
import com.rork.plcpanelstudio.data.ComponentKind
import com.rork.plcpanelstudio.ui.theme.Line
import com.rork.plcpanelstudio.ui.theme.LineBright
import com.rork.plcpanelstudio.ui.theme.NameplateStyle
import com.rork.plcpanelstudio.ui.theme.SignalAmber
import com.rork.plcpanelstudio.ui.theme.SignalGreen
import com.rork.plcpanelstudio.ui.theme.SignalOrange
import com.rork.plcpanelstudio.ui.theme.SignalRed
import com.rork.plcpanelstudio.ui.theme.SignalYellow
import com.rork.plcpanelstudio.ui.theme.Steel
import com.rork.plcpanelstudio.ui.theme.SteelDark
import com.rork.plcpanelstudio.ui.theme.SteelLight
import com.rork.plcpanelstudio.ui.theme.SurfaceRaised
import com.rork.plcpanelstudio.ui.theme.TextLow
import com.rork.plcpanelstudio.ui.theme.TextMid
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

/** Colour a component lights with when its signal is live. */
fun glowColorFor(kind: ComponentKind): Color = when (kind) {
    ComponentKind.BUTTON -> SignalOrange
    ComponentKind.SELECTOR -> SignalOrange
    ComponentKind.LAMP -> SignalAmber
    ComponentKind.LAMP_RED -> SignalRed
    ComponentKind.LAMP_GREEN -> SignalGreen
    ComponentKind.GAUGE -> SignalOrange
    ComponentKind.STOP -> SignalRed
    ComponentKind.GREEN -> SignalGreen
    ComponentKind.YELLOW -> SignalYellow
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
    pressed: Boolean = false,
    /** Text engraved on the plate of image-based parts; ignored by the canvas-drawn parts. */
    label: String = "",
    /** Selector only: called with the new position when the knob is turned. Null = display only. */
    onSelectorChange: ((Int) -> Unit)? = null
) {
    if (kind.isPlateButton) {
        PlateButtonFace(kind = kind, active = active, pressed = pressed, label = label, modifier = modifier)
        return
    }
    if (kind.isLamp) {
        LampFace(kind = kind, active = active, label = label, modifier = modifier)
        return
    }
    if (kind == ComponentKind.SELECTOR) {
        SelectorFace(
            active = active,
            position = selectorPosition,
            positions = selectorPositions,
            label = label,
            onSelect = onSelectorChange,
            modifier = modifier
        )
        return
    }

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

    Canvas(modifier = modifier) {
        when (kind) {
            ComponentKind.BUTTON -> drawPushButton(glow, intensity, press)
            ComponentKind.LAMP, ComponentKind.LAMP_RED, ComponentKind.LAMP_GREEN -> Unit // drawn by LampFace above
            ComponentKind.SELECTOR -> Unit // drawn by SelectorFace above
            ComponentKind.GAUGE -> drawGauge(glow, intensity, needle)
            ComponentKind.STOP, ComponentKind.GREEN, ComponentKind.YELLOW -> Unit // drawn by PlateButtonFace above
        }
    }
}

// ---------------------------------------------------------------- stop button (image based)

private const val STOP_FRAME_ASPECT = 600f / 720f      // stop_button_frame.png is 600 x 720
private const val STOP_WELL_CENTER_Y = 445f / 720f     // centre of the round well inside the frame
private const val STOP_CAP_WIDTH_FRACTION = 0.52f      // cap diameter relative to the frame width
private const val STOP_PLATE_TOP = 50f / 720f          // label plate: top edge
private const val STOP_PLATE_HEIGHT = 130f / 720f      // label plate: height
private const val STOP_PLATE_WIDTH_FRACTION = 0.76f    // usable label width relative to frame width
private val StopLabelColor = Color(0xFFF1F3F5)

/**
 * The image-based push buttons (STOP red, GREEN, YELLOW): a bitmap frame (label plate with
 * the round well) with a coloured bitmap cap sitting in the well and the user's label
 * printed on the plate. The cap sinks while [pressed] and the well glows in the cap's
 * colour while [active]. The frame is letter-boxed so it keeps its proportions inside the
 * square slot used by the other parts.
 */
@Composable
private fun PlateButtonFace(
    kind: ComponentKind,
    active: Boolean,
    pressed: Boolean,
    label: String,
    modifier: Modifier = Modifier
) {
    // Glow while the signal is live OR while the finger is down, so the press is
    // visible instantly even before the PLC echoes the value back.
    val glow = glowColorFor(kind)
    val capRes = when (kind) {
        ComponentKind.GREEN -> R.drawable.green_button_cap
        ComponentKind.YELLOW -> R.drawable.yellow_button_cap
        else -> R.drawable.stop_button_cap
    }
    val intensity by animateFloatAsState(
        targetValue = if (active || pressed) 1f else 0f,
        animationSpec = tween(durationMillis = 80),
        label = "stopGlow"
    )
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(durationMillis = 60),
        label = "stopPress"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            modifier = Modifier.aspectRatio(STOP_FRAME_ASPECT, matchHeightConstraintsFirst = true)
        ) {
            val capSize = maxWidth * STOP_CAP_WIDTH_FRACTION
            val wellOffsetY = maxHeight * (STOP_WELL_CENTER_Y - 0.5f)

            Image(
                painter = painterResource(R.drawable.stop_button_frame),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            // User-defined label printed on the plate at the top of the frame.
            PlateLabel(
                label = label,
                frameWidth = maxWidth,
                frameHeight = maxHeight,
                plateTop = STOP_PLATE_TOP,
                plateHeight = STOP_PLATE_HEIGHT,
                plateWidthFraction = STOP_PLATE_WIDTH_FRACTION,
                maxEm = 0.115f
            )

            // Coloured glow spilling out of the well when the signal is live.
            if (intensity > 0.02f) {
                Canvas(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = wellOffsetY)
                        .size(capSize * 1.5f)
                ) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glow.copy(alpha = 0.55f * intensity),
                                glow.copy(alpha = 0.16f * intensity),
                                Color.Transparent
                            )
                        )
                    )
                }
            }

            Image(
                painter = painterResource(capRes),
                contentDescription = kind.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = wellOffsetY)
                    .size(capSize)
                    .graphicsLayer {
                        // Cap visibly sinks into the well: shrinks and moves down a little.
                        val scale = 1f - 0.16f * press
                        scaleX = scale
                        scaleY = scale
                        translationY = size.height * 0.05f * press
                    },
                colorFilter = if (press > 0.01f) {
                    ColorFilter.tint(Color.Black.copy(alpha = 0.32f * press), BlendMode.SrcAtop)
                } else null
            )
        }
    }
}

// ---------------------------------------------------------------- plate label + lamps (image based)

/**
 * User label engraved on the plate at the top of an image-based part. Capitals, bold, one line;
 * long labels shrink so they always fit. Sizes are passed in explicitly (not read from the
 * enclosing BoxWithConstraints) because a nested scope cannot reach the outer maxWidth.
 */
@Composable
private fun BoxScope.PlateLabel(
    label: String,
    frameWidth: Dp,
    frameHeight: Dp,
    plateTop: Float,
    plateHeight: Float,
    plateWidthFraction: Float,
    maxEm: Float
) {
    val text = label.trim().uppercase()
    if (text.isEmpty()) return
    // Shrink long labels so they always fit on one line of the plate.
    val emFraction = minOf(maxEm, 1.05f / text.length.coerceAtLeast(1))
    val fontSize = (frameWidth * emFraction).value.sp
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = frameHeight * plateTop)
            .width(frameWidth * plateWidthFraction)
            .height(frameHeight * plateHeight),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = StopLabelColor,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private const val LAMP_FRAME_ASPECT = 841f / 1070f          // lamp_*.png proportions
private const val LAMP_LENS_CENTER_Y = 654f / 1070f         // centre of the round lens
private const val LAMP_LENS_RADIUS_FRACTION = 0.268f        // lens radius relative to frame width
private const val LAMP_PLATE_TOP = 40f / 1070f
private const val LAMP_PLATE_HEIGHT = 210f / 1070f
private const val LAMP_PLATE_WIDTH_FRACTION = 0.78f

/**
 * The image-based lamps (amber, red, green). The whole housing is a bitmap; the lens is
 * darkened while the signal is off and lit up with a glow halo while it is on.
 */
@Composable
private fun LampFace(
    kind: ComponentKind,
    active: Boolean,
    label: String,
    modifier: Modifier = Modifier
) {
    val glow = glowColorFor(kind)
    val frameRes = when (kind) {
        ComponentKind.LAMP_RED -> R.drawable.lamp_red
        ComponentKind.LAMP_GREEN -> R.drawable.lamp_green
        else -> R.drawable.lamp_amber
    }
    val intensity by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "lampGlow"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            modifier = Modifier.aspectRatio(LAMP_FRAME_ASPECT, matchHeightConstraintsFirst = true)
        ) {
            val haloScale = 1.6f
            val haloSize = maxWidth * LAMP_LENS_RADIUS_FRACTION * 2f * haloScale
            val lensOffsetY = maxHeight * (LAMP_LENS_CENTER_Y - 0.5f)

            Image(
                painter = painterResource(frameRes),
                contentDescription = kind.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            PlateLabel(
                label = label,
                frameWidth = maxWidth,
                frameHeight = maxHeight,
                plateTop = LAMP_PLATE_TOP,
                plateHeight = LAMP_PLATE_HEIGHT,
                plateWidthFraction = LAMP_PLATE_WIDTH_FRACTION,
                maxEm = 0.10f
            )

            Canvas(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = lensOffsetY)
                    .size(haloSize)
            ) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val lensR = size.minDimension / 2f / haloScale

                // Unlit: dim the lens so an "off" lamp clearly reads as off.
                val dim = 1f - intensity
                if (dim > 0.02f) {
                    drawCircle(color = Color.Black.copy(alpha = 0.55f * dim), radius = lensR, center = c)
                }
                if (intensity > 0.02f) {
                    // Halo spilling onto the frame.
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glow.copy(alpha = 0.5f * intensity),
                                glow.copy(alpha = 0.14f * intensity),
                                Color.Transparent
                            ),
                            center = c,
                            radius = lensR * haloScale
                        ),
                        radius = lensR * haloScale,
                        center = c
                    )
                    // Hot core brightening the lens itself.
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.32f * intensity),
                                glow.copy(alpha = 0.32f * intensity),
                                Color.Transparent
                            ),
                            center = c,
                            radius = lensR
                        ),
                        radius = lensR,
                        center = c,
                        blendMode = BlendMode.Screen
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- selector (image based, turnable)

private const val SELECTOR_FRAME_ASPECT = 856f / 1076f        // selector_frame.jpg proportions
private const val SELECTOR_KNOB_CENTER_X = 425.06f / 856f      // knob centre inside the frame
private const val SELECTOR_KNOB_CENTER_Y = 669.72f / 1076f
private const val SELECTOR_KNOB_RADIUS_FRACTION = 228f / 856f // radius of selector_knob.png vs frame width
private const val SELECTOR_TICK_INNER = 256f / 856f           // detent tick marks on the bezel
private const val SELECTOR_TICK_OUTER = 282f / 856f
private const val SELECTOR_PLATE_TOP = 50f / 1076f
private const val SELECTOR_PLATE_HEIGHT = 204f / 1076f
private const val SELECTOR_PLATE_WIDTH_FRACTION = 0.74f
private const val SELECTOR_SWING_DEG = 45f                    // knob detents sit at +/- this angle

/** Knob angle of every detent, in degrees clockwise from "up". */
private fun selectorAngles(positions: Int): List<Float> =
    if (positions >= 3) listOf(-SELECTOR_SWING_DEG, 0f, SELECTOR_SWING_DEG)
    else listOf(-SELECTOR_SWING_DEG, SELECTOR_SWING_DEG)

/** Angle of [point] around [centre], in degrees clockwise from "up". */
private fun pointerAngleDeg(point: Offset, centre: Offset): Float {
    val dx = point.x - centre.x
    val dy = point.y - centre.y
    return Math.toDegrees(atan2(dx.toDouble(), (-dy).toDouble())).toFloat()
}

/**
 * The 2- or 3-position selector switch. The housing is a bitmap; the round knob is a second
 * bitmap laid on top and rotated to the current detent. When [onSelect] is set the knob can be
 * turned: drag around the knob to rotate it (it follows the finger and snaps to the nearest
 * detent on release) or tap it to step to the next position.
 */
@Composable
private fun SelectorFace(
    active: Boolean,
    position: Int,
    positions: Int,
    label: String,
    onSelect: ((Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val count = if (positions >= 3) 3 else 2
    val angles = selectorAngles(count)
    val current = position.coerceIn(0, count - 1)
    val glow = glowColorFor(ComponentKind.SELECTOR)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // The knob angle is animated, but follows the finger directly while dragging.
    val knobAngle = remember { Animatable(angles[current]) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(current, count) {
        if (!dragging) knobAngle.animateTo(angles[current], tween(durationMillis = 180))
    }

    val latestCurrent by rememberUpdatedState(current)
    val latestCount by rememberUpdatedState(count)
    val latestOnSelect by rememberUpdatedState(onSelect)

    val gestureModifier = if (onSelect != null) {
        Modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        val select = latestOnSelect ?: return@detectTapGestures
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        select((latestCurrent + 1) % latestCount)
                    }
                )
            }
            .pointerInput(Unit) {
                val centre = Offset(
                    size.width * SELECTOR_KNOB_CENTER_X,
                    size.height * SELECTOR_KNOB_CENTER_Y
                )
                var lastAngle = 0f
                fun finishTurn() {
                    dragging = false
                    val list = selectorAngles(latestCount)
                    val nearest = list.indices.minByOrNull { abs(list[it] - lastAngle) } ?: 0
                    scope.launch { knobAngle.animateTo(list[nearest], tween(durationMillis = 150)) }
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    latestOnSelect?.invoke(nearest)
                }
                detectDragGestures(
                    onDragStart = { start ->
                        dragging = true
                        lastAngle = pointerAngleDeg(start, centre)
                            .coerceIn(-SELECTOR_SWING_DEG, SELECTOR_SWING_DEG)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        lastAngle = pointerAngleDeg(change.position, centre)
                            .coerceIn(-SELECTOR_SWING_DEG, SELECTOR_SWING_DEG)
                        val target = lastAngle
                        scope.launch { knobAngle.snapTo(target) }
                    },
                    onDragEnd = { finishTurn() },
                    onDragCancel = { finishTurn() }
                )
            }
    } else Modifier

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            modifier = Modifier
                .aspectRatio(SELECTOR_FRAME_ASPECT, matchHeightConstraintsFirst = true)
                .then(gestureModifier)
        ) {
            val knobSize = maxWidth * SELECTOR_KNOB_RADIUS_FRACTION * 2f
            val knobOffsetX = maxWidth * (SELECTOR_KNOB_CENTER_X - 0.5f)
            val knobOffsetY = maxHeight * (SELECTOR_KNOB_CENTER_Y - 0.5f)

            Image(
                painter = painterResource(R.drawable.selector_frame),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            PlateLabel(
                label = label,
                frameWidth = maxWidth,
                frameHeight = maxHeight,
                plateTop = SELECTOR_PLATE_TOP,
                plateHeight = SELECTOR_PLATE_HEIGHT,
                plateWidthFraction = SELECTOR_PLATE_WIDTH_FRACTION,
                maxEm = 0.10f
            )

            // Detent tick marks on the bezel; the current position is highlighted.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centre = Offset(size.width * SELECTOR_KNOB_CENTER_X, size.height * SELECTOR_KNOB_CENTER_Y)
                val inner = size.width * SELECTOR_TICK_INNER
                val outer = size.width * SELECTOR_TICK_OUTER
                angles.forEachIndexed { index, degrees ->
                    val rad = Math.toRadians(degrees.toDouble())
                    val sinA = sin(rad).toFloat()
                    val cosA = cos(rad).toFloat()
                    val start = Offset(centre.x + inner * sinA, centre.y - inner * cosA)
                    val end = Offset(centre.x + outer * sinA, centre.y - outer * cosA)
                    val isCurrent = index == current
                    if (isCurrent && active) {
                        drawLine(
                            color = glow.copy(alpha = 0.35f),
                            start = start,
                            end = end,
                            strokeWidth = size.width * 0.03f,
                            cap = StrokeCap.Round
                        )
                    }
                    drawLine(
                        color = when {
                            isCurrent && active -> glow
                            isCurrent -> Color(0xFFDDE3E8)
                            else -> Color(0xFF6B7078)
                        },
                        start = start,
                        end = end,
                        strokeWidth = size.width * 0.012f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // The turnable knob.
            Image(
                painter = painterResource(R.drawable.selector_knob),
                contentDescription = "Selector",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = knobOffsetX, y = knobOffsetY)
                    .size(knobSize)
                    .graphicsLayer { rotationZ = knobAngle.value }
            )
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
            lineHeight = 12.sp,
            textAlign = TextAlign.Center,
            color = if (selected) SignalOrange else TextMid,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
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
    pressed: Boolean = false,
    forced: Boolean = false,
    onSelectorChange: ((Int) -> Unit)? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The STOP button carries its label on its own plate, so no separate nameplate.
        if (!kind.hasPlateLabel) {
            Nameplate(text = label, active = active, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(
                    if (forced) {
                        Modifier
                            .border(2.dp, SignalOrange, RoundedCornerShape(10.dp))
                            .padding(3.dp)
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            HardwareFace(
                kind = kind,
                active = active,
                analogValue = analogValue,
                selectorPosition = selectorPosition,
                selectorPositions = selectorPositions,
                pressed = pressed,
                label = label,
                onSelectorChange = onSelectorChange,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (forced) "FORCED · $caption" else caption,
            style = MaterialTheme.typography.bodySmall,
            color = if (forced) SignalOrange else if (active) glowColorFor(kind) else TextLow,
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