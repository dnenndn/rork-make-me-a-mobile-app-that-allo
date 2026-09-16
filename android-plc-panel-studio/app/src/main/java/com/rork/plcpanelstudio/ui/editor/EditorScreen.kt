package com.rork.plcpanelstudio.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rork.plcpanelstudio.data.ComponentKind
import com.rork.plcpanelstudio.data.PanelComponent
import com.rork.plcpanelstudio.ui.components.GridBackdrop
import com.rork.plcpanelstudio.ui.components.HardwareFace
import com.rork.plcpanelstudio.ui.components.PartTile
import com.rork.plcpanelstudio.ui.theme.Ink
import com.rork.plcpanelstudio.ui.theme.Line
import com.rork.plcpanelstudio.ui.theme.MonoFamily
import com.rork.plcpanelstudio.ui.theme.NameplateStyle
import com.rork.plcpanelstudio.ui.theme.SignalOrange
import com.rork.plcpanelstudio.ui.theme.Surface1
import com.rork.plcpanelstudio.ui.theme.Surface2
import com.rork.plcpanelstudio.ui.theme.TextLow
import com.rork.plcpanelstudio.ui.theme.TextMid
import kotlin.math.floor

private val PALETTE_KINDS = listOf(
    ComponentKind.BUTTON,
    ComponentKind.SELECTOR,
    ComponentKind.LAMP,
    ComponentKind.GAUGE
)

/** Transient drag payload: either a new part from the rail or an existing placed part. */
private sealed interface DragPayload {
    data class NewPart(val kind: ComponentKind) : DragPayload
    data class Existing(val component: PanelComponent) : DragPayload
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    panelId: String?,
    onBack: (() -> Unit)?,
    contentPadding: PaddingValues,
    viewModel: EditorViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var showPanelSwitcher by remember { mutableStateOf(false) }
    var showProperties by remember { mutableStateOf(false) }
    var showPanelSettings by remember { mutableStateOf(false) }

    LaunchedEffect(panelId) { viewModel.load(panelId) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val panel = state.panel

    val haptics = LocalHapticFeedback.current

    // Drag state shared between the rail and the canvas.
    var payload by remember { mutableStateOf<DragPayload?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var canvasOrigin by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Offset.Zero) }
    var hoverCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    fun cellAt(position: Offset): Pair<Int, Int>? {
        if (canvasSize.x <= 0f || canvasSize.y <= 0f) return null
        val local = position - canvasOrigin
        if (local.x < 0f || local.y < 0f || local.x > canvasSize.x || local.y > canvasSize.y) return null
        val col = floor(local.x / (canvasSize.x / GRID_COLUMNS)).toInt().coerceIn(0, GRID_COLUMNS - 1)
        val row = floor(local.y / (canvasSize.y / GRID_ROWS)).toInt().coerceIn(0, GRID_ROWS - 1)
        return col to row
    }

    Scaffold(
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            text = panel?.name ?: "Editor",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = state.device?.let { "${it.name} · ${it.endpoint}" } ?: "No device linked",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = MonoFamily,
                            color = TextLow,
                            maxLines = 1
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = panel != null
                    ) {
                        Text("Save", color = SignalOrange, fontWeight = FontWeight.Bold)
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Editor options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Panel settings") },
                                onClick = { menuOpen = false; showPanelSettings = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Switch panel") },
                                onClick = { menuOpen = false; showPanelSwitcher = true }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink)
            )
        }
    ) { inner ->
        if (panel == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Create a panel from the Panels tab to start building.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid,
                    modifier = Modifier.padding(32.dp)
                )
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = inner.calculateTopPadding())
                .padding(bottom = contentPadding.calculateBottomPadding())
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                PartRail(
                    activeKind = (payload as? DragPayload.NewPart)?.kind,
                    onDragStart = { kind, position ->
                        payload = DragPayload.NewPart(kind)
                        dragPosition = position
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { position ->
                        dragPosition = position
                        hoverCell = cellAt(position)
                    },
                    onDragEnd = {
                        val target = hoverCell
                        val current = payload
                        if (target != null && current is DragPayload.NewPart) {
                            viewModel.addComponent(current.kind, target.first, target.second)
                            showProperties = true
                        }
                        payload = null
                        hoverCell = null
                    },
                    onQuickAdd = { kind ->
                        val free = firstFreeCell(panel.components)
                        if (free == null) {
                            viewModel.select(null)
                        } else {
                            viewModel.addComponent(kind, free.first, free.second)
                            showProperties = true
                        }
                    },
                    modifier = Modifier
                        .width(96.dp)
                        .fillMaxHeight()
                )

                PanelCanvas(
                    components = panel.components,
                    panelTitle = panel.name,
                    selectedId = state.selectedId,
                    hoverCell = hoverCell,
                    draggingId = (payload as? DragPayload.Existing)?.component?.id,
                    onPositioned = { origin, size ->
                        canvasOrigin = origin
                        canvasSize = size
                    },
                    onSelect = { id ->
                        viewModel.select(id)
                        showProperties = id != null
                    },
                    onDragStart = { component, position ->
                        payload = DragPayload.Existing(component)
                        dragPosition = position
                        hoverCell = component.col to component.row
                        viewModel.select(component.id)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { position ->
                        dragPosition = position
                        val cell = cellAt(position)
                        if (cell != hoverCell) {
                            hoverCell = cell
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        // Live rearrange: the part reflows as the finger crosses cells,
                        // swapping with any occupant instead of waiting for the drop.
                        val current = payload
                        if (cell != null && current is DragPayload.Existing) {
                            viewModel.moveComponent(current.component.id, cell.first, cell.second)
                        }
                    },
                    onDragEnd = {
                        payload = null
                        hoverCell = null
                    },
                    onEmptyCellTap = { col, row ->
                        viewModel.addComponent(ComponentKind.BUTTON, col, row)
                        showProperties = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            // Floating ghost that follows the finger.
            val ghost = payload
            if (ghost != null) {
                val density = LocalDensity.current
                val ghostSize = 56.dp
                val half = with(density) { ghostSize.toPx() / 2f }
                val kind = when (ghost) {
                    is DragPayload.NewPart -> ghost.kind
                    is DragPayload.Existing -> ghost.component.kind
                }
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (dragPosition.x - half).toInt(),
                                (dragPosition.y - half - inner.calculateTopPadding().value * density.density).toInt()
                            )
                        }
                        .size(ghostSize)
                        .graphicsLayer {
                            scaleX = 1.15f
                            scaleY = 1.15f
                            shape = RoundedCornerShape(10.dp)
                            shadowElevation = 16f
                        }
                        .alpha(0.96f)
                ) {
                    HardwareFace(kind = kind, active = true, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    val selected = panel?.components?.firstOrNull { it.id == state.selectedId }
    if (showProperties && selected != null) {
        ComponentPropertiesSheet(
            component = selected,
            suggestAddress = { direction -> viewModel.suggestAddress(direction) },
            onDismiss = { showProperties = false },
            onApply = { updated ->
                viewModel.updateComponent(updated)
                showProperties = false
            },
            onDelete = {
                viewModel.deleteComponent(selected.id)
                showProperties = false
            }
        )
    }

    if (showPanelSettings && panel != null) {
        PanelSettingsSheet(
            panel = panel,
            devices = state.devices,
            onDismiss = { showPanelSettings = false },
            onApply = { name, description, deviceId ->
                viewModel.renamePanel(name, description)
                viewModel.assignDevice(deviceId)
                showPanelSettings = false
            }
        )
    }

    if (showPanelSwitcher) {
        PanelSwitcherSheet(
            panels = state.panels,
            activeId = panel?.id,
            onDismiss = { showPanelSwitcher = false },
            onSelect = { id ->
                viewModel.switchPanel(id)
                showPanelSwitcher = false
            }
        )
    }
}

private fun firstFreeCell(components: List<PanelComponent>): Pair<Int, Int>? {
    for (row in 0 until GRID_ROWS) {
        for (col in 0 until GRID_COLUMNS) {
            if (components.none { it.col == col && it.row == row }) return col to row
        }
    }
    return null
}

@Composable
private fun PartRail(
    activeKind: ComponentKind?,
    onDragStart: (ComponentKind, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onQuickAdd: (ComponentKind) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Surface1)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("LIBRARY", style = NameplateStyle, color = TextLow, fontSize = 9.sp)
        PALETTE_KINDS.forEach { kind ->
            var tileOrigin by remember(kind) { mutableStateOf(Offset.Zero) }
            PartTile(
                kind = kind,
                selected = activeKind == kind,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { tileOrigin = it.positionInRoot() }
                    .pointerInput(kind) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset -> onDragStart(kind, tileOrigin + offset) },
                            onDrag = { change, _ ->
                                change.consume()
                                onDrag(tileOrigin + change.position)
                            },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd
                        )
                    }
                    .clickable { onQuickAdd(kind) }
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Hold a part to drag it in, or tap to drop it into the next free slot. " +
                "Rearrange placed parts by dragging them around the grid.",
            style = MaterialTheme.typography.bodySmall,
            fontSize = 10.sp,
            color = TextLow
        )
    }
}

@Composable
private fun PanelCanvas(
    components: List<PanelComponent>,
    panelTitle: String,
    selectedId: String?,
    hoverCell: Pair<Int, Int>?,
    draggingId: String?,
    onPositioned: (Offset, Offset) -> Unit,
    onSelect: (String?) -> Unit,
    onDragStart: (PanelComponent, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onEmptyCellTap: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(Ink)) {
        GridBackdrop(modifier = Modifier.fillMaxSize(), cell = 22.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            Text(
                text = panelTitle.uppercase(),
                style = NameplateStyle,
                color = TextLow,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords ->
                        onPositioned(
                            coords.positionInRoot(),
                            Offset(coords.size.width.toFloat(), coords.size.height.toFloat())
                        )
                    }
            ) {
                for (row in 0 until GRID_ROWS) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        for (col in 0 until GRID_COLUMNS) {
                            val component = components.firstOrNull { it.col == col && it.row == row }
                            val hovered = hoverCell?.first == col && hoverCell.second == row
                            CanvasCell(
                                component = component,
                                selected = component != null && component.id == selectedId,
                                hovered = hovered,
                                dragging = component != null && component.id == draggingId,
                                onSelect = { onSelect(component?.id) },
                                onEmptyTap = { onEmptyCellTap(col, row) },
                                onDragStart = { position ->
                                    component?.let { onDragStart(it, position) }
                                },
                                onDrag = onDrag,
                                onDragEnd = onDragEnd,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CanvasCell(
    component: PanelComponent?,
    selected: Boolean,
    hovered: Boolean,
    dragging: Boolean,
    onSelect: () -> Unit,
    onEmptyTap: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var cellOrigin by remember { mutableStateOf(Offset.Zero) }
    // Fresh reads inside the gesture handlers: cells recompose while a drag is
    // live (parts swap under the finger) and the gesture must survive that,
    // so the pointer inputs are keyed on Unit, never on the component.
    val currentComponent by rememberUpdatedState(component)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnEmptyTap by rememberUpdatedState(onEmptyTap)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val borderColor = when {
        hovered -> SignalOrange
        selected -> SignalOrange.copy(alpha = 0.7f)
        component != null -> Line
        else -> Line.copy(alpha = 0.6f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    hovered -> SignalOrange.copy(alpha = 0.10f)
                    component != null -> Surface2.copy(alpha = 0.85f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (hovered || selected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp)
            )
            .onGloballyPositioned { cellOrigin = it.positionInRoot() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (currentComponent != null) currentOnSelect() else currentOnEmptyTap()
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        currentComponent?.let { currentOnDragStart(cellOrigin + offset) }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentOnDrag(cellOrigin + change.position)
                    },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragEnd() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (component == null) {
            Text("+", color = TextLow, fontSize = 18.sp)
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(if (dragging) 0.25f else 1f)
            ) {
                HardwareFace(
                    kind = component.kind,
                    active = selected,
                    modifier = Modifier.size(42.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = component.label,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = TextMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AnimatedVisibility(visible = component.tagAddress.isNotBlank()) {
                    Text(
                        text = component.tagAddress,
                        fontFamily = MonoFamily,
                        fontSize = 10.sp,
                        color = SignalOrange,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
