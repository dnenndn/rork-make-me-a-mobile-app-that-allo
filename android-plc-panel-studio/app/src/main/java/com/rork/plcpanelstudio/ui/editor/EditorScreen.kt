package com.rork.plcpanelstudio.ui.editor

import android.content.res.Configuration
import androidx.compose.animation.Crossfade
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
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
import com.rork.plcpanelstudio.data.PanelComponent
import com.rork.plcpanelstudio.ui.components.GridBackdrop
import com.rork.plcpanelstudio.ui.components.HardwareFace
import com.rork.plcpanelstudio.ui.components.HardwareUnit
import com.rork.plcpanelstudio.ui.components.PartTile
import com.rork.plcpanelstudio.ui.theme.Ink
import com.rork.plcpanelstudio.ui.theme.MonoFamily
import com.rork.plcpanelstudio.ui.theme.NameplateStyle
import com.rork.plcpanelstudio.ui.theme.SignalOrange
import com.rork.plcpanelstudio.ui.theme.SignalTeal
import com.rork.plcpanelstudio.ui.theme.Surface1
import com.rork.plcpanelstudio.ui.theme.Surface2
import com.rork.plcpanelstudio.ui.theme.TextLow
import com.rork.plcpanelstudio.ui.theme.TextMid
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.roundToInt

// The old canvas-drawn Button and Gauge are no longer offered in the library. They stay in the
// ComponentKind enum so panels that already contain them keep working.
private val PALETTE_KINDS = listOf(
    ComponentKind.SELECTOR,
    ComponentKind.LAMP,
    ComponentKind.LAMP_RED,
    ComponentKind.LAMP_GREEN,
    ComponentKind.STOP,
    ComponentKind.GREEN,
    ComponentKind.YELLOW
)

/** Transient drag payload: either a new part from the rail or an existing placed part. */
private sealed interface DragPayload {
    data class NewPart(val kind: ComponentKind) : DragPayload
    data class Existing(val component: PanelComponent) : DragPayload
}

/** Footprint of a part on the editor canvas, used to map finger position to 0..1 coordinates. */
private val CANVAS_PART_WIDTH = 84.dp
private val CANVAS_PART_HEIGHT = 116.dp

/** Footprint used in the rotated preview and on the monitor screen. */
private val PREVIEW_PART_WIDTH = 104.dp
private val PREVIEW_PART_HEIGHT = 160.dp

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
    var justSaved by remember { mutableStateOf(false) }

    LaunchedEffect(panelId) { viewModel.load(panelId) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(justSaved) {
        if (justSaved) {
            delay(1600)
            justSaved = false
        }
    }

    val panel = state.panel

    // The device's physical orientation drives the mode: portrait edits with
    // the library rail, landscape previews the finished panel without it.
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Drag state shared between the rail and the canvas.
    var payload by remember { mutableStateOf<DragPayload?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var canvasOrigin by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Offset.Zero) }

    val canvasPartWpx = with(density) { CANVAS_PART_WIDTH.toPx() }
    val canvasPartHpx = with(density) { CANVAS_PART_HEIGHT.toPx() }

    /** Maps a finger position (root coords) to the snapped 0..1 center of a part. */
    fun fractionAt(position: Offset): Pair<Float, Float>? {
        if (canvasSize.x <= 0f || canvasSize.y <= 0f) return null
        val local = position - canvasOrigin
        if (local.x < 0f || local.y < 0f || local.x > canvasSize.x || local.y > canvasSize.y) return null
        val spanX = (canvasSize.x - canvasPartWpx).coerceAtLeast(1f)
        val spanY = (canvasSize.y - canvasPartHpx).coerceAtLeast(1f)
        val x = ((local.x - canvasPartWpx / 2f) / spanX).coerceIn(0f, 1f)
        val y = ((local.y - canvasPartHpx / 2f) / spanY).coerceIn(0f, 1f)
        return snap(x) to snap(y)
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
                            text = if (isLandscape) {
                                "Preview — rotate back to keep editing"
                            } else {
                                state.device?.let { "${it.name} · ${it.endpoint}" } ?: "No device linked"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = MonoFamily,
                            color = TextLow,
                            maxLines = 1
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save(); justSaved = true },
                        enabled = panel != null && !justSaved
                    ) {
                        Text(
                            text = if (justSaved) "Saved" else "Save",
                            color = if (justSaved) SignalTeal else SignalOrange,
                            fontWeight = FontWeight.Bold
                        )
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
            Crossfade(targetState = isLandscape, label = "editorMode") { landscape ->
                if (landscape) {
                    PreviewCanvas(
                        components = panel.components,
                        panelTitle = panel.name,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                            PartRail(
                                activeKind = (payload as? DragPayload.NewPart)?.kind,
                                onDragStart = { kind, position ->
                                    payload = DragPayload.NewPart(kind)
                                    dragPosition = position
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { position -> dragPosition = position },
                                onDragEnd = {
                                    val current = payload
                                    val target = fractionAt(dragPosition)
                                    if (current is DragPayload.NewPart && target != null) {
                                        viewModel.addComponent(current.kind, target.first, target.second)
                                        showProperties = true
                                    }
                                    payload = null
                                },
                                onQuickAdd = { kind ->
                                    val (x, y) = quickAddPosition(panel.components)
                                    viewModel.addComponent(kind, x, y)
                                    showProperties = true
                                },
                                modifier = Modifier
                                    .width(96.dp)
                                    .fillMaxHeight()
                            )

                            FreeCanvas(
                                components = panel.components,
                                panelTitle = panel.name,
                                selectedId = state.selectedId,
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
                                    viewModel.select(component.id)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { position -> dragPosition = position },
                                onDragEnd = {
                                    val current = payload
                                    val target = fractionAt(dragPosition)
                                    if (current is DragPayload.Existing && target != null) {
                                        viewModel.moveComponent(
                                            current.component.id,
                                            target.first,
                                            target.second
                                        )
                                    }
                                    payload = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }

            // Floating ghost that follows the finger (editing side only).
            val ghost = payload
            if (ghost != null && !isLandscape) {
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
            suggestAddress = { direction, exclude -> viewModel.suggestAddress(direction, exclude) },
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
            onApply = { name, description, deviceId, cover ->
                viewModel.renamePanel(name, description)
                viewModel.assignDevice(deviceId)
                viewModel.setCover(cover)
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

/** Picks a comfortable spot for a quick-added part, away from existing ones. */
private fun quickAddPosition(components: List<PanelComponent>): Pair<Float, Float> {
    val candidates = listOf(
        0.5f to 0.5f, 0.25f to 0.25f, 0.75f to 0.25f, 0.25f to 0.75f, 0.75f to 0.75f,
        0.5f to 0.12f, 0.5f to 0.88f, 0.1f to 0.5f, 0.9f to 0.5f,
        0.18f to 0.12f, 0.82f to 0.12f, 0.18f to 0.88f, 0.82f to 0.88f
    )
    candidates.forEach { (x, y) ->
        if (components.none { hypot(it.col - x, it.row - y) < 0.26f }) return x to y
    }
    val n = components.size
    return snap(0.1f + (n * 0.13f) % 0.8f) to snap(0.1f + (n * 0.27f) % 0.8f)
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
            "Hold a part to drag it anywhere, or tap to drop it in a free spot. " +
                "Rotate your phone to preview the panel without the library.",
            style = MaterialTheme.typography.bodySmall,
            fontSize = 10.sp,
            color = TextLow
        )
    }
}

/** Free-placement editing canvas: parts sit anywhere, dragged by long-press. */
@Composable
private fun FreeCanvas(
    components: List<PanelComponent>,
    panelTitle: String,
    selectedId: String?,
    draggingId: String?,
    onPositioned: (Offset, Offset) -> Unit,
    onSelect: (String?) -> Unit,
    onDragStart: (PanelComponent, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val partWpx = with(density) { CANVAS_PART_WIDTH.toPx() }
    val partHpx = with(density) { CANVAS_PART_HEIGHT.toPx() }
    var areaPx by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .background(Ink)
            .pointerInput(Unit) { detectTapGestures(onTap = { onSelect(null) }) }
    ) {
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { areaPx = it }
                    .onGloballyPositioned { coords ->
                        onPositioned(
                            coords.positionInRoot(),
                            Offset(coords.size.width.toFloat(), coords.size.height.toFloat())
                        )
                    }
            ) {
                components.forEach { component ->
                    val isSelected = component.id == selectedId
                    val isDragging = component.id == draggingId
                    var partOrigin by remember(component.id) { mutableStateOf(Offset.Zero) }
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (component.col * (areaPx.width - partWpx)).roundToInt(),
                                    (component.row * (areaPx.height - partHpx)).roundToInt()
                                )
                            }
                            .size(CANVAS_PART_WIDTH, CANVAS_PART_HEIGHT)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Surface2.copy(alpha = 0.9f) else Color.Transparent)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) SignalOrange else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .onGloballyPositioned { partOrigin = it.positionInRoot() }
                            .pointerInput(component.id) {
                                detectTapGestures(onTap = { onSelect(component.id) })
                            }
                            .pointerInput(component.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        onDragStart(component, partOrigin + offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        onDrag(partOrigin + change.position)
                                    },
                                    onDragEnd = { onDragEnd() },
                                    onDragCancel = { onDragEnd() }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.alpha(if (isDragging) 0.25f else 1f)
                        ) {
                            HardwareFace(
                                kind = component.kind,
                                active = isSelected,
                                selectorPositions = component.positions,
                                modifier = Modifier.size(46.dp)
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
                            if (component.isWired) {
                                Text(
                                    text = component.tagSummary,
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
        }
    }
}

/** Non-interactive render of the finished panel, shown when the device is rotated. */
@Composable
private fun PreviewCanvas(
    components: List<PanelComponent>,
    panelTitle: String,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val partWpx = with(density) { PREVIEW_PART_WIDTH.toPx() }
    val partHpx = with(density) { PREVIEW_PART_HEIGHT.toPx() }
    var areaPx by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = modifier.background(Ink)) {
        GridBackdrop(modifier = Modifier.fillMaxSize(), cell = 26.dp)
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { areaPx = it }
            ) {
                if (components.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "This panel is empty — rotate back and drop parts from the library.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMid,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                }
                components.forEach { component ->
                    HardwareUnit(
                        kind = component.kind,
                        label = component.label,
                        caption = component.tagSummary.ifBlank { "NO TAG" },
                        active = false,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (component.col * (areaPx.width - partWpx)).roundToInt(),
                                    (component.row * (areaPx.height - partHpx)).roundToInt()
                                )
                            }
                            .width(PREVIEW_PART_WIDTH)
                    )
                }
            }
        }
    }
}
