package com.rork.plcpanelstudio.ui.iostatus

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rork.plcpanelstudio.data.DeviceStatus
import com.rork.plcpanelstudio.data.IoGroup
import com.rork.plcpanelstudio.data.IoRow
import com.rork.plcpanelstudio.data.filterTagBody
import com.rork.plcpanelstudio.ui.theme.Ink
import com.rork.plcpanelstudio.ui.theme.Line
import com.rork.plcpanelstudio.ui.theme.LineBright
import com.rork.plcpanelstudio.ui.theme.MonoFamily
import com.rork.plcpanelstudio.ui.theme.NameplateStyle
import com.rork.plcpanelstudio.ui.theme.SignalAmber
import com.rork.plcpanelstudio.ui.theme.SignalOrange
import com.rork.plcpanelstudio.ui.theme.SignalRed
import com.rork.plcpanelstudio.ui.theme.SignalTeal
import com.rork.plcpanelstudio.ui.theme.Surface2
import com.rork.plcpanelstudio.ui.theme.TextHi
import com.rork.plcpanelstudio.ui.theme.TextLow
import com.rork.plcpanelstudio.ui.theme.TextMid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Colour a lit signal of each block shows: inputs orange, outputs teal, memory amber. */
private fun groupColor(group: IoGroup): Color = when (group) {
    IoGroup.INPUTS -> SignalOrange
    IoGroup.OUTPUTS -> SignalTeal
    IoGroup.MEMORY -> SignalAmber
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IoStatusScreen(
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: IoStatusViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    // Read from the PLC only while this screen is showing.
    LaunchedEffect(Unit) { viewModel.start() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.start() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stop() }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = { Text("I/O Status", style = MaterialTheme.typography.headlineMedium) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink)
            )
        }
    ) { inner ->
        if (state.panels.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No panels yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Create a panel first. Its inputs and outputs will be listed here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = inner.calculateTopPadding() + 4.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "panel-chips") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.panels, key = { it.id }) { panel ->
                            FilterChip(
                                selected = panel.id == state.selectedPanelId,
                                onClick = { viewModel.selectPanel(panel.id) },
                                label = { Text(panel.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SignalOrange.copy(alpha = 0.18f),
                                    selectedLabelColor = SignalOrange
                                )
                            )
                        }
                    }
                }
                item(key = "summary") {
                    Spacer(Modifier.height(4.dp))
                    SummaryCard(state)
                }

                for (group in IoGroup.entries) {
                    val rows = state.sections.rowsOf(group)
                    item(key = "header-${group.name}") {
                        Spacer(Modifier.height(10.dp))
                        SectionHeader(
                            group = group,
                            count = rows.size,
                            onAdd = if (group == IoGroup.MEMORY) ({ showAdd = true }) else null
                        )
                    }
                    if (rows.isEmpty()) {
                        item(key = "empty-${group.name}") {
                            Text(
                                text = when (group) {
                                    IoGroup.INPUTS -> "No inputs on this panel yet."
                                    IoGroup.OUTPUTS -> "No outputs on this panel yet."
                                    IoGroup.MEMORY -> "No memory bits yet. Use Add memory to watch one, for example M0.5."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextLow,
                                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                            )
                        }
                    } else {
                        items(rows, key = { it.address }) { row ->
                            IoRowItem(
                                row = row,
                                onRemove = if (row.removable) ({ viewModel.removeMemory(row.address) }) else null
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddMemoryDialog(
            onDismiss = { showAdd = false },
            onAdd = { body, name -> viewModel.addMemory(body, name) }
        )
    }
}

@Composable
private fun SummaryCard(state: IoStatusUiState) {
    val statusColor = when {
        state.live -> SignalTeal
        state.status == DeviceStatus.OFFLINE -> SignalRed
        else -> TextLow
    }
    val statusText = when {
        state.device == null -> state.error ?: "No PLC linked to this panel"
        state.live -> "Connected to ${state.device.name}"
        state.status == DeviceStatus.OFFLINE -> "Offline${state.error?.let { ": $it" }.orEmpty()}"
        else -> "Waiting for ${state.device.name}"
    }
    val updated = state.lastUpdateMillis?.takeIf { state.live }?.let {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.live) TextHi else TextMid,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (updated != null) {
                Text(updated, style = MaterialTheme.typography.bodySmall, fontFamily = MonoFamily, color = TextLow)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${state.sections.inputs.size} inputs  ·  ${state.sections.outputs.size} outputs  ·  " +
                "${state.sections.memory.size} memory",
            style = MaterialTheme.typography.bodySmall,
            color = TextMid
        )
    }
}

@Composable
private fun SectionHeader(group: IoGroup, count: Int, onAdd: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(groupColor(group))
        )
        Spacer(Modifier.width(8.dp))
        Text(group.title.uppercase(), style = NameplateStyle, color = TextMid)
        Spacer(Modifier.width(8.dp))
        Text("$count", style = NameplateStyle, color = TextLow)
        Spacer(Modifier.weight(1f))
        if (onAdd != null) {
            OutlinedButton(
                onClick = onAdd,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add memory", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun IoRowItem(row: IoRow, onRemove: (() -> Unit)?) {
    val known = row.value != null
    val on = (row.value ?: 0) > 0
    val color = groupColor(row.group)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .border(1.dp, if (on) color.copy(alpha = 0.55f) else Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The LED: lit in the block's colour when the bit is 1, dark when 0, hollow when not known.
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(if (on) color else if (known) Ink else Color.Transparent)
                .border(1.5.dp, if (on) color else if (known) LineBright else TextLow.copy(alpha = 0.5f), CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.name.ifBlank { "Memory bit" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = row.partKind?.let { "${row.address}  ·  ${it.displayName}" } ?: row.address,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = MonoFamily,
                color = TextMid,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = when {
                !known -> "—"
                on -> "ON"
                else -> "OFF"
            },
            style = MaterialTheme.typography.labelLarge,
            fontFamily = MonoFamily,
            fontWeight = FontWeight.Bold,
            color = if (on) color else TextLow
        )
        if (onRemove != null) {
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove ${row.address}",
                    tint = TextLow,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onAdd: (body: String, name: String) -> String?
) {
    var body by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text("Add memory bit") },
        text = {
            Column {
                Text(
                    "Watch a memory bit of this panel's PLC. It is listed here with the panel's own inputs and outputs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = {
                        body = filterTagBody(it)
                        error = null
                    },
                    label = { Text("Address (byte.bit)") },
                    prefix = { Text("M", fontFamily = MonoFamily, fontWeight = FontWeight.Bold, color = SignalAmber) },
                    placeholder = { Text("0.5") },
                    singleLine = true,
                    isError = error != null,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(32) },
                    label = { Text("Name (optional)") },
                    placeholder = { Text("Cycle done") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = SignalRed)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val problem = onAdd(body, name)
                    if (problem == null) onDismiss() else error = problem
                },
                colors = ButtonDefaults.buttonColors(containerColor = SignalOrange, contentColor = Ink)
            ) {
                Text("Add", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SignalOrange) }
        }
    )
}
