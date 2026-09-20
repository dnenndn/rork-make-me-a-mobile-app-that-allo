package com.rork.plcpanelstudio.ui.monitor

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
    var forceTarget by remember { mutableStateOf<PanelComponent?>(null) }
    var passwordDialog by remember { mutableStateOf<PasswordDialogMode?>(null) }
    var lockMenuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    /** Asks for the password (or to create one on first use). */
    fun requestUnlock() {
        passwordDialog = if (state.hasControlPassword) PasswordDialogMode.UNLOCK else PasswordDialogMode.CREATE
    }

    LaunchedEffect(panelId) { viewModel.start(panelId) }
    // Leaving the screen locks the controls again (but not when the screen only rotates).
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stop()
            if (context.findActivity()?.isChangingConfigurations != true) viewModel.lockControl()
        }
    }
    // Sending the app to the background locks them too.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (context.findActivity()?.isChangingConfigurations != true) viewModel.lockControl()
    }

    passwordDialog?.let { mode ->
        key(mode) {
            PasswordDialog(
                mode = mode,
                onDismiss = { passwordDialog = null },
                onSubmit = { current, new, confirm ->
                    when (mode) {
                        PasswordDialogMode.UNLOCK -> viewModel.unlockControl(current)
                        PasswordDialogMode.CREATE -> viewModel.createControlPassword(new, confirm)
                        PasswordDialogMode.CHANGE -> viewModel.changeControlPassword(current, new, confirm)
                    }
                },
                onWantChange = if (mode == PasswordDialogMode.UNLOCK) {
                    ({ passwordDialog = PasswordDialogMode.CHANGE })
                } else null
            )
        }
    }

    forceTarget?.let { component ->
        ForceOutputDialog(
            component = component,
            currentValue = state.values[component.tagAddress] ?: 0,
            onDismiss = { forceTarget = null },
            onForce = { value ->
                forceTarget = null
                if (state.controlUnlocked) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.forceWrite(component, value)
                } else {
                    // The lock timed out while the dialog was open.
                    requestUnlock()
                }
            },
            onRelease = {
                viewModel.releaseForce(component)
                forceTarget = null
            }
        )
    }

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
                    Box {
                        IconButton(
                            onClick = {
                                if (state.controlUnlocked) lockMenuOpen = true else requestUnlock()
                            }
                        ) {
                            Icon(
                                imageVector = if (state.controlUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = if (state.controlUnlocked) "Controls unlocked" else "Controls locked",
                                tint = if (state.controlUnlocked) SignalTeal else TextMid
                            )
                        }
                        DropdownMenu(
                            expanded = lockMenuOpen,
                            onDismissRequest = { lockMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Lock controls") },
                                onClick = {
                                    lockMenuOpen = false
                                    viewModel.lockControl()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Change password") },
                                onClick = {
                                    lockMenuOpen = false
                                    passwordDialog = PasswordDialogMode.CHANGE
                                }
                            )
                        }
                    }
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
                                positionValues = if (component.hasPositionTags) {
                                    (0 until component.positionCount).map { index ->
                                        state.values[component.positionAddress(index)] ?: 0
                                    }
                                } else emptyList(),
                                pressed = component.id in state.pressedIds,
                                forced = component.id in state.forcedIds,
                                interactive = component.direction == IoDirection.INPUT &&
                                    component.isWired,
                                forceable = component.direction == IoDirection.OUTPUT &&
                                    component.isWired,
                                onPressDown = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.onPressDown(component)
                                },
                                onPressUp = { viewModel.onPressUp(component) },
                                onSelectorChange = { position -> viewModel.setSelector(component, position) },
                                onForceRequest = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (state.controlUnlocked) forceTarget = component else requestUnlock()
                                },
                                controlEnabled = state.controlUnlocked,
                                onLockedTouch = { requestUnlock() },
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
    positionValues: List<Int> = emptyList(),
    onPressDown: () -> Unit,
    onPressUp: () -> Unit,
    forced: Boolean = false,
    forceable: Boolean = false,
    onForceRequest: () -> Unit = {},
    onSelectorChange: (Int) -> Unit = {},
    controlEnabled: Boolean = true,
    onLockedTouch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Selector state. A selector wired with one input per position reports which contact is
    // closed; a legacy single-tag selector reports one value (0..positions-1).
    val multiSelector = component.hasPositionTags
    val wired = component.isWired
    val positionCount = component.positionCount
    var localSelector by remember(component.id) { mutableStateOf(0) }
    val activePositionIndex = if (multiSelector) positionValues.indexOfFirst { it > 0 } else -1
    val shownSelectorPosition = when {
        !wired -> localSelector.coerceIn(0, positionCount - 1)
        // No contact closed (e.g. a centre position without an input): show the last commanded one.
        multiSelector -> if (activePositionIndex >= 0) activePositionIndex else localSelector.coerceIn(0, positionCount - 1)
        else -> value.coerceIn(0, positionCount - 1)
    }
    val active = if (multiSelector) activePositionIndex >= 0 else value > 0
    val caption = when {
        !wired -> "NO TAG"
        component.kind == ComponentKind.GAUGE -> "$value / ${component.scaleMax}"
        multiSelector -> {
            val address = component.positionAddress(shownSelectorPosition)
            "POS ${shownSelectorPosition + 1}" + if (address.isNotBlank()) " · $address" else ""
        }
        component.kind == ComponentKind.SELECTOR && component.positions >= 3 -> "POS ${value + 1}"
        component.kind == ComponentKind.SELECTOR -> if (value > 0) "AUTO" else "MANUAL"
        component.direction == IoDirection.INPUT -> if (active) "ACTIVE" else "INACTIVE"
        else -> if (active) "ON" else "OFF"
    }

    // The knob turns on touch (drag to rotate, tap to step). Without a tag it just turns
    // locally; with tags the new position is written to the PLC.
    val onSelectorTurn: (Int) -> Unit = { position ->
        localSelector = position
        if (wired) onSelectorChange(position)
    }
    // Every button and selector is locked until the password is entered, tag or no tag:
    // a locked part does not move, does not light up and does not turn.
    val locked = component.isLocked(controlEnabled)
    val selectorCallback: ((Int) -> Unit)? =
        if (component.kind == ComponentKind.SELECTOR && component.direction == IoDirection.INPUT && !locked) {
            onSelectorTurn
        } else null

    // Local press state so the button always reacts to touch, even if the tag is not
    // assigned yet (in which case nothing is written to the PLC).
    var localPressed by remember(component.id) { mutableStateOf(false) }
    val isPushInput = component.kind.isPushButton && component.direction == IoDirection.INPUT

    // Press-and-hold semantics: write on touch down, release on lift.
    // Each branch has its own key so the handler is replaced when the lock state changes.
    val interactionModifier = when {
        // Locked: touching the part only asks for the password.
        locked -> Modifier.pointerInput(component.id, "locked") {
            detectTapGestures(onTap = { onLockedTouch() })
        }
        interactive && component.kind != ComponentKind.SELECTOR -> Modifier.pointerInput(component.id, "control") {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                localPressed = true
                onPressDown()
                waitForUpOrCancellation()
                localPressed = false
                onPressUp()
            }
        }
        isPushInput -> Modifier.pointerInput(component.id, "local-press") {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                localPressed = true
                waitForUpOrCancellation()
                localPressed = false
            }
        }
        // Outputs (lamps/gauges) aren't pressed like a control — long-press instead opens
        // a "Force" dialog, mirroring the force-table concept from real PLC HMIs.
        forceable -> Modifier.pointerInput(component.id, "force") {
            detectTapGestures(onLongPress = { onForceRequest() })
        }
        else -> Modifier
    }

    Box(modifier = modifier.then(interactionModifier)) {
        HardwareUnit(
            kind = component.kind,
            label = component.label,
            caption = caption,
            // A button without a tag has no input to show, so light it while it is touched.
            active = active || (!wired && localPressed && component.kind.isPushButton),
            analogValue = if (component.kind == ComponentKind.GAUGE) {
                value.toFloat() / component.scaleMax.coerceAtLeast(1).toFloat()
            } else 0f,
            selectorPosition = shownSelectorPosition,
            selectorPositions = component.positionCount,
            pressed = pressed || localPressed,
            forced = forced,
            onSelectorChange = selectorCallback,
            modifier = Modifier.fillMaxWidth()
        )
        // Small padlock so it is obvious why the part does not react.
        if (locked) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                tint = TextMid,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(14.dp)
            )
        }
    }
}

/** Bottom-sheet-style dialog to force a value onto an output tag for bench testing. */
@Composable
private fun ForceOutputDialog(
    component: PanelComponent,
    currentValue: Int,
    onDismiss: () -> Unit,
    onForce: (Int) -> Unit,
    onRelease: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text("Force \"${component.label}\"") },
        text = {
            Column {
                Text(
                    "Writes directly to ${component.tagAddress}. If the PLC's own program " +
                        "keeps driving this address, it may overwrite the forced value on " +
                        "its next scan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid
                )
                Spacer(Modifier.height(14.dp))
                if (component.kind == ComponentKind.GAUGE) {
                    var text by remember { mutableStateOf(currentValue.toString()) }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.filter(Char::isDigit).take(6) },
                        label = { Text("Forced value") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = onRelease) { Text("Release", color = TextMid) }
                        Button(
                            onClick = { onForce(text.toIntOrNull() ?: 0) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SignalOrange,
                                contentColor = Ink
                            )
                        ) { Text("Force", fontWeight = FontWeight.Bold) }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onForce(1) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SignalOrange,
                                contentColor = Ink
                            )
                        ) { Text("Force ON", fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = { onForce(0) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Surface1,
                                contentColor = TextHi
                            )
                        ) { Text("Force OFF") }
                    }
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onRelease) { Text("Release forced value", color = TextMid) }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMid) }
        }
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

/** The Activity behind a Compose [Context], or null. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
