package com.rork.plcpanelstudio.ui.panels

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rork.plcpanelstudio.data.ComponentKind
import com.rork.plcpanelstudio.data.DeviceStatus
import com.rork.plcpanelstudio.data.PlcDevice
import com.rork.plcpanelstudio.ui.components.CoverTile
import com.rork.plcpanelstudio.ui.components.HardwareFace
import com.rork.plcpanelstudio.ui.components.PANEL_COVERS
import com.rork.plcpanelstudio.ui.components.StatusRow
import com.rork.plcpanelstudio.ui.components.coverRes
import com.rork.plcpanelstudio.ui.theme.Ink
import com.rork.plcpanelstudio.ui.theme.Line
import com.rork.plcpanelstudio.ui.theme.MonoFamily
import com.rork.plcpanelstudio.ui.theme.NameplateStyle
import com.rork.plcpanelstudio.ui.theme.SignalRed
import com.rork.plcpanelstudio.ui.theme.SignalTeal
import com.rork.plcpanelstudio.ui.theme.Surface2
import com.rork.plcpanelstudio.ui.theme.TextLow
import com.rork.plcpanelstudio.ui.theme.TextMid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun statusColor(status: DeviceStatus): Color = when (status) {
    DeviceStatus.CONNECTED -> SignalTeal
    DeviceStatus.OFFLINE -> SignalRed
    DeviceStatus.UNKNOWN -> TextLow
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelsScreen(
    onOpenMonitor: (String) -> Unit,
    onEditPanel: (String) -> Unit,
    contentPadding: PaddingValues,
    viewModel: PanelsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = { Text("Panels", style = MaterialTheme.typography.headlineMedium) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Ink,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Panel", fontWeight = FontWeight.Bold) },
                modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding())
            )
        }
    ) { inner ->
        if (state.rows.isEmpty()) {
            EmptyPanels(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(bottom = contentPadding.calculateBottomPadding())
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = inner.calculateTopPadding() + 4.dp,
                    bottom = contentPadding.calculateBottomPadding() + 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(state.rows, key = { it.panel.id }) { row ->
                    PanelCard(
                        row = row,
                        onOpen = { onOpenMonitor(row.panel.id) },
                        onEdit = {
                            viewModel.openInEditor(row.panel.id)
                            onEditPanel(row.panel.id)
                        },
                        onDuplicate = { viewModel.duplicatePanel(row.panel.id) },
                        onDelete = { viewModel.deletePanel(row.panel.id) }
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreatePanelDialog(
            devices = state.devices,
            onDismiss = { showCreate = false },
            onCreate = { name, description, deviceId, cover ->
                showCreate = false
                val id = viewModel.createPanel(name, description, deviceId, cover)
                onEditPanel(id)
            }
        )
    }
}

@Composable
private fun EmptyPanels(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        HardwareFace(
            kind = ComponentKind.LAMP,
            active = false,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("No panels yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Create a panel, drop in buttons and lamps, then wire them to your PLC tags.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMid
        )
    }
}

@Composable
private fun PanelCard(
    row: PanelRow,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = Surface2),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Line, RoundedCornerShape(14.dp))
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            PanelThumbnail(
                row = row,
                modifier = Modifier
                    .width(180.dp)
                    .aspectRatio(1.5f)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = row.panel.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box {
                        IconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Panel options",
                                tint = TextMid,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Edit panel") },
                                onClick = { menuOpen = false; onEdit() }
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate") },
                                onClick = { menuOpen = false; onDuplicate() }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = SignalRed) },
                                onClick = { menuOpen = false; onDelete() }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${row.tagCount} tags",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMid
                    )
                    Text("  ·  ", style = MaterialTheme.typography.bodySmall, color = TextLow)
                    StatusRow(
                        color = statusColor(row.status),
                        text = row.status.displayName
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = row.panel.description.ifBlank { "No description." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                Text("LAST UPDATED", style = NameplateStyle, color = TextLow, fontSize = 9.sp)
                Text(
                    text = formatUpdated(row.panel.updatedAtMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid
                )
            }
        }
    }
}

/** The panel's cover picture, used as the main thumbnail in the list. */
@Composable
private fun PanelThumbnail(row: PanelRow, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Ink)
            .border(1.dp, Line, RoundedCornerShape(10.dp))
    ) {
        Image(
            painter = painterResource(coverRes(row.panel.cover)),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(statusColor(row.status).copy(alpha = 0.22f))
                .border(1.dp, statusColor(row.status), RoundedCornerShape(3.dp))
        )
    }
}

@Composable
private fun CreatePanelDialog(
    devices: List<PlcDevice>,
    onDismiss: () -> Unit,
    onCreate: (String, String, String?, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var deviceId by remember { mutableStateOf(devices.firstOrNull()?.id) }
    var cover by remember { mutableStateOf(PANEL_COVERS.first().id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text("New Panel") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Panel name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                Text("COVER PICTURE", style = NameplateStyle, color = TextLow)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PANEL_COVERS, key = { it.id }) { item ->
                        CoverTile(
                            item = item,
                            selected = item.id == cover,
                            onClick = { cover = item.id }
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("PLC DEVICE", style = NameplateStyle, color = TextLow)
                Spacer(Modifier.height(6.dp))
                if (devices.isEmpty()) {
                    Text(
                        "Add a device in the Devices tab first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMid
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        devices.forEach { device ->
                            val selected = device.id == deviceId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) Ink else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else Line,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { deviceId = device.id }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(device.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        device.endpoint,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = MonoFamily,
                                        color = TextMid
                                    )
                                }
                                if (device.simulated) {
                                    Text("SIM", style = NameplateStyle, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, description, deviceId, cover) }) {
                Text("Create", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMid) }
        }
    )
}

fun formatUpdated(millis: Long): String {
    val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val isToday = today.format(Date(millis)) == today.format(Date())
    return if (isToday) {
        "Today, " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
    } else {
        formatter.format(Date(millis))
    }
}
