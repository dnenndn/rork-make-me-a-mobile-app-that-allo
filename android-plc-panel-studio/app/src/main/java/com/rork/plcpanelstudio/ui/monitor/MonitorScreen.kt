package com.rork.plcpanelstudio.ui.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rork.plcpanelstudio.data.ComponentKind
import com.rork.plcpanelstudio.data.DeviceStatus
import com.rork.plcpanelstudio.data.IoDirection
import com.rork.plcpanelstudio.data.PanelComponent
import com.rork.plcpanelstudio.ui.components.GridBackdrop
import com.rork.plcpanelstudio.ui.components.HardwareUnit
import com.rork.plcpanelstudio.ui.components.StatusRow
import com.rork.plcpanelstudio.ui.panels.statusColor
import com.rork.plcpanelstudio.ui.theme.Ink
import com.rork.plcpanelstudio.ui.theme.Line
import com.rork.plcpanelstudio.ui.theme.MonoFamily
import com.rork.plcpanelstudio.ui.theme.NameplateStyle
import com.rork.plcpanelstudio.ui.theme.SignalOrange
import com.rork.plcpanelstudio.ui.theme.SignalRed
import com.rork.plcpanelstudio.ui.theme.SignalTeal
import com.rork.plcpanelstudio.ui.theme.Surface1
import com.rork.plcpanelstudio.ui.theme.Surface2
import com.rork.plcpanelstudio.ui.theme.TextHi
import com.rork.plcpanelstudio.ui.theme.TextLow
import com.rork.plcpanelstudio.ui.theme.TextMid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Footprint used to place live parts at their edited 0..1 canvas positions. */
private val MONITOR_PART_WIDTH = 104.dp
private val MONITOR_PART_HEIGHT = 160.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    panelId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: MonitorViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(panelId) { viewModel.start(panelId) }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = state.panel?.name ?: "Monitor",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        StatusRow(
                            color = statusColor(state.status),
                            text = state.error ?: state.status.displayName
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(panelId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit panel")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink)
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = inner.calculateTopPadding())
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                GridBackdrop(modifier = Modifier.fillMaxSize(), cell = 26.dp)
                val components = state.panel?.components.orEmpty()
                if (components.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "This panel has no parts yet. Open the editor to add buttons and lamps.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMid,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                } else {
                    val density = LocalDensity.current
                    val partWpx = with(density) { MONITOR_PART_WIDTH.toPx() }
                    val partHpx = with(density) { MONITOR_PART_HEIGHT.toPx() }
                    var areaPx by remember { mutableStateOf(IntSize.Zero) }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { areaPx = it }
                    ) {
                        components.forEach { component ->
                            LiveComponent(
                                component = component,
                                value = state.values[component.tagAddress] ?: 0,
                                pressed = component.id in state.pressedIds,
                                interactive = component.direction == IoDirection.INPUT &&
                                    component.tagAddress.isNotBlank(),
                                onPressDown = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.onPressDown(component)
                                },
                                onPressUp = { viewModel.onPressUp(component) },
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (component.col * (areaPx.width - partWpx)).roundToInt(),
                                            (component.row * (areaPx.height - partHpx)).roundToInt()
                                        )
                                    }
                                    .width(MONITOR_PART_WIDTH)
                            )
                        }
                    }
                }
            }

            IoStatesPanel(
                state = state,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LiveComponent(
    component: PanelComponent,
    value: Int,
    pressed: Boolean,
    interactive: Boolean,
    onPressDown: () -> Unit,
    onPressUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = when (component.kind) {
        ComponentKind.GAUGE -> value > 0
        ComponentKind.SELECTOR -> value > 0
        else -> value > 0
    }
    val caption = when {
        component.tagAddress.isBlank() -> "NO TAG"
        component.kind == ComponentKind.GAUGE -> "$value / ${component.scaleMax}"
        component.kind == ComponentKind.SELECTOR -> if (value > 0) "AUTO" else "MANUAL"
        component.direction == IoDirection.INPUT -> if (active) "ACTIVE" else "INACTIVE"
        else -> if (active) "ON" else "OFF"
    }

    // Press-and-hold semantics: write on touch down, release on lift.
    val interactionModifier = if (interactive) {
        Modifier.pointerInput(component.id) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                onPressDown()
                waitForUpOrCancellation()
                onPressUp()
            }
        }
    } else {
        Modifier
    }

    HardwareUnit(
        kind = component.kind,
        label = component.label,
        caption = caption,
        active = active,
        analogValue = if (component.kind == ComponentKind.GAUGE) {
            value.toFloat() / component.scaleMax.coerceAtLeast(1).toFloat()
        } else 0f,
        selectorPosition = value.coerceIn(0, (component.positions - 1).coerceAtLeast(1)),
        selectorPositions = component.positions.coerceAtLeast(2),
        pressed = pressed,
        modifier = modifier.then(interactionModifier)
    )
}

@Composable
private fun IoStatesPanel(
    state: MonitorUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(Surface1)
            .border(
                1.dp,
                Line,
                RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Line)
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("I/O States", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = state.device?.let { "Live values from ${it.name}" } ?: "No PLC linked",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = state.lastUpdateMillis?.let { formatClock(it) } ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid,
                    fontFamily = MonoFamily
                )
                StatusRow(
                    color = if (state.live) SignalTeal else statusColor(DeviceStatus.OFFLINE),
                    text = if (state.live) "Live · ${state.pingMs ?: 0}ms" else "Paused"
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        if (state.readings.isEmpty()) {
            Text(
                state.error ?: "Assign tag addresses in the editor to see live values.",
                style = MaterialTheme.typography.bodySmall,
                color = TextLow,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                state.readings.take(6).forEach { reading ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Surface2)
                            .border(1.dp, Line, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = reading.address,
                            fontFamily = MonoFamily,
                            fontSize = 14.sp,
                            color = TextHi,
                            modifier = Modifier.width(64.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = reading.label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = reading.direction.displayName.uppercase(),
                                style = NameplateStyle,
                                fontSize = 9.sp,
                                color = TextLow
                            )
                        }
                        Text(
                            text = reading.value.toString(),
                            fontFamily = MonoFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (reading.value > 0) SignalOrange else TextLow
                        )
                    }
                }
                if (state.readings.size > 6) {
                    Text(
                        "+${state.readings.size - 6} more tags",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLow
                    )
                }
            }
        }
        if (state.error != null && state.readings.isNotEmpty()) {
            Text(
                text = state.error,
                style = MaterialTheme.typography.bodySmall,
                color = SignalRed,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

private fun formatClock(millis: Long): String =
    "Today, " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
