package com.rork.plcpanelstudio.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rork.plcpanelstudio.data.DeviceStatus
import com.rork.plcpanelstudio.data.EndpointParser
import com.rork.plcpanelstudio.data.PlcDevice
import com.rork.plcpanelstudio.data.connectionHint
import com.rork.plcpanelstudio.ui.components.StatusDot
import com.rork.plcpanelstudio.ui.panels.statusColor
import com.rork.plcpanelstudio.ui.theme.Ink
import com.rork.plcpanelstudio.ui.theme.Line
import com.rork.plcpanelstudio.ui.theme.MonoFamily
import com.rork.plcpanelstudio.ui.theme.NameplateStyle
import com.rork.plcpanelstudio.ui.theme.SignalOrange
import com.rork.plcpanelstudio.ui.theme.SignalRed
import com.rork.plcpanelstudio.ui.theme.SignalTeal
import com.rork.plcpanelstudio.ui.theme.Steel
import com.rork.plcpanelstudio.ui.theme.SteelDark
import com.rork.plcpanelstudio.ui.theme.SteelLight
import com.rork.plcpanelstudio.ui.theme.Surface2
import com.rork.plcpanelstudio.ui.theme.TextHi
import com.rork.plcpanelstudio.ui.theme.TextLow
import com.rork.plcpanelstudio.ui.theme.TextMid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    contentPadding: PaddingValues,
    viewModel: DevicesViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PlcDevice?>(null) }

    editing?.let { device ->
        EditAddressesDialog(
            device = device,
            onDismiss = { editing = null },
            onSave = { host, port, fallbackHost, fallbackPort ->
                viewModel.updateAddresses(device.id, host, port, fallbackHost, fallbackPort)
                editing = null
            }
        )
    }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = { Text("Devices", style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    IconButton(onClick = { viewModel.refreshAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh all")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink)
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding() + 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.devices, key = { it.id }) { device ->
                DeviceCard(
                    device = device,
                    testing = device.id in state.testingIds,
                    onTest = { viewModel.testConnection(device.id) },
                    onEdit = { editing = device },
                    onDelete = { viewModel.deleteDevice(device.id) },
                    onToggleSimulated = {
                        viewModel.updateDevice(
                            device.copy(simulated = !device.simulated, status = DeviceStatus.UNKNOWN)
                        )
                        viewModel.testConnection(device.id)
                    }
                )
            }
            item {
                AddDeviceCard(
                    expanded = showAdd || state.devices.isEmpty(),
                    onToggle = { showAdd = !showAdd },
                    onAdd = { name, host, port, simulated, backupHost, backupPort ->
                        viewModel.addDevice(name, host, port, simulated, backupHost, backupPort)
                        showAdd = false
                    }
                )
            }
            item {
                BridgeHelpCard()
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: PlcDevice,
    testing: Boolean,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleSimulated: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Surface2),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Line, RoundedCornerShape(14.dp))
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            RackGraphic(
                status = device.status,
                modifier = Modifier
                    .width(56.dp)
                    .height(96.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    StatusChip(status = device.status, testing = testing)
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Device options",
                                tint = TextMid,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Test connection") },
                                onClick = { menuOpen = false; onTest() }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit addresses") },
                                onClick = { menuOpen = false; onEdit() }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(if (device.simulated) "Use real bridge" else "Use built-in simulator")
                                },
                                onClick = { menuOpen = false; onToggleSimulated() }
                            )
                            DropdownMenuItem(
                                text = { Text("Remove", color = SignalRed) },
                                onClick = { menuOpen = false; onDelete() }
                            )
                        }
                    }
                }
                Text(
                    text = device.endpoint,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = MonoFamily,
                    color = TextMid
                )
                if (device.hasBackup) {
                    Text(
                        text = "Backup ${device.backupEndpoint}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = MonoFamily,
                        color = TextLow
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(
                            when {
                                device.status == DeviceStatus.CONNECTED && device.pingMs != null ->
                                    "Ping ${device.pingMs}ms" +
                                        if (device.usingBackup) " · via backup address" else ""
                                device.lastError != null -> device.lastError
                                else -> "Not tested"
                            }
                        )
                        device.lastSeenMillis?.let {
                            append(" · Last seen ")
                            append(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it)))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (device.status == DeviceStatus.OFFLINE) SignalRed else TextMid,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (device.status == DeviceStatus.OFFLINE && !device.simulated) {
                    connectionHint(device.lastError)?.let { hint ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = TextMid
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Line)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricCell("CPU", device.cpuPercent?.let { "$it%" } ?: "—", SignalOrange, Modifier.weight(1f))
                    MetricCell(
                        "I/O OK",
                        if (device.ioOk != null && device.ioTotal != null) "${device.ioOk} / ${device.ioTotal}" else "—",
                        SignalTeal,
                        Modifier.weight(1.2f)
                    )
                    MetricCell("UPTIME", device.uptime ?: "—", TextHi, Modifier.weight(1f))
                }
                if (device.simulated) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Simulated PLC — answered by the built-in demo driver.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = SignalOrange
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCell(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(label, style = NameplateStyle, fontSize = 9.sp, color = TextLow)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            fontFamily = MonoFamily,
            fontSize = 13.sp,
            color = if (value == "—") TextLow else valueColor,
            maxLines = 1
        )
    }
}

@Composable
private fun StatusChip(status: DeviceStatus, testing: Boolean) {
    val color = statusColor(status)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (testing) {
            CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
                strokeWidth = 1.5.dp,
                color = color
            )
        } else {
            StatusDot(color = color)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (testing) "Testing" else status.displayName,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            color = color
        )
    }
}

/** Simple PLC rack silhouette with a status LED strip. */
@Composable
private fun RackGraphic(status: DeviceStatus, modifier: Modifier = Modifier) {
    val ledColor = statusColor(status)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SteelDark)
            .border(1.dp, Line, RoundedCornerShape(8.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(ledColor)
        )
        repeat(3) { index ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (index == 0) Steel else SteelLight.copy(alpha = 0.35f))
                    .padding(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (status == DeviceStatus.CONNECTED) ledColor else TextLow)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddDeviceCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    onAdd: (String, String, Int, Boolean, String, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("192.168.1.40") }
    // The bridge server listens on 8080 unless it was started with --port.
    var port by remember { mutableStateOf(EndpointParser.DEFAULT_PORT.toString()) }
    var hostError by remember { mutableStateOf<String?>(null) }
    var backup by remember { mutableStateOf("") }
    var simulated by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Surface2),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Line, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Add Device", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Point at your LAN bridge server or a Modbus gateway.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMid
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(Icons.Default.Add, contentDescription = "Toggle add form", tint = SignalOrange)
                }
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Line 3 PLC") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = host,
                        // Allow ":" and "/" so an address such as 192.168.1.129:8081 can be pasted whole.
                        onValueChange = {
                            host = it.filter { ch -> ch.isLetterOrDigit() || ch in ".-_:/" }
                            hostError = null
                        },
                        label = { Text("IP Address") },
                        singleLine = true,
                        isError = hostError != null,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
                        modifier = Modifier.weight(2f)
                    )
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(5) },
                        label = { Text("Port") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    hostError ?: "Type the bridge's address and port, or paste it as printed, e.g. 192.168.1.129:8081.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hostError != null) SignalRed else TextLow
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = backup,
                    onValueChange = {
                        backup = it.filter { ch -> ch.isLetterOrDigit() || ch in ".-_:/" }
                        hostError = null
                    },
                    label = { Text("Backup address (optional)") },
                    placeholder = { Text("e.g. your Tailscale address") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily),
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Used automatically when the main address doesn't answer. Leave the port out to use the same port.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLow
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Simulate this PLC", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Use the demo driver when the bridge isn't reachable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextLow
                        )
                    }
                    Switch(
                        checked = simulated,
                        onCheckedChange = { simulated = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = SignalOrange)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        val endpoint = EndpointParser.parse(host, port)
                        val backupEndpoint = if (backup.isBlank()) null
                        else EndpointParser.parse(backup, "", defaultPort = endpoint?.port ?: EndpointParser.DEFAULT_PORT)
                        if (endpoint == null) {
                            hostError = "That address isn't valid. Use something like 192.168.1.129 or 192.168.1.129:8081."
                        } else if (backup.isNotBlank() && backupEndpoint == null) {
                            hostError = "The backup address isn't valid. Use something like 100.67.101.121 or 100.67.101.121:8081."
                        } else {
                            onAdd(
                                name, endpoint.host, endpoint.port, simulated,
                                backupEndpoint?.host.orEmpty(), backupEndpoint?.port ?: 0
                            )
                        }
                    },
                    enabled = host.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignalOrange,
                        contentColor = Ink
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BridgeHelpCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Ink),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Line, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("BRIDGE SERVER", style = NameplateStyle, color = TextLow)
            Spacer(Modifier.height(8.dp))
            Text(
                "Run the bridge on a machine in the same LAN as your PLCs. " +
                    "The app talks HTTP to it:",
                style = MaterialTheme.typography.bodySmall,
                color = TextMid
            )
            Spacer(Modifier.height(8.dp))
            listOf(
                "GET  /api/health",
                "POST /api/read   { addresses: [] }",
                "POST /api/write  { address, value }"
            ).forEach { line ->
                Text(
                    text = line,
                    fontFamily = MonoFamily,
                    fontSize = 12.sp,
                    color = SignalTeal,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}

/** Changes a device's main and backup addresses without removing it, so panels stay linked. */
@Composable
private fun EditAddressesDialog(
    device: PlcDevice,
    onDismiss: () -> Unit,
    onSave: (host: String, port: Int, fallbackHost: String, fallbackPort: Int) -> Unit
) {
    var main by remember(device.id) { mutableStateOf(device.endpoint) }
    var backup by remember(device.id) { mutableStateOf(if (device.hasBackup) device.backupEndpoint else "") }
    var error by remember(device.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text("Edit addresses") },
        text = {
            Column {
                Text(
                    "The main address is tried first. If it doesn't answer, the backup address is used, " +
                        "for example your Tailscale address.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = main,
                    onValueChange = {
                        main = it.filter { ch -> ch.isLetterOrDigit() || ch in ".-_:/" }
                        error = null
                    },
                    label = { Text("Main address") },
                    placeholder = { Text("192.168.137.1:8081") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily),
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = backup,
                    onValueChange = {
                        backup = it.filter { ch -> ch.isLetterOrDigit() || ch in ".-_:/" }
                        error = null
                    },
                    label = { Text("Backup address (optional)") },
                    placeholder = { Text("100.67.101.121:8081") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily),
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
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
                    val mainEndpoint = EndpointParser.parse(main, "")
                    val backupEndpoint = if (backup.isBlank()) null
                    else EndpointParser.parse(backup, "", defaultPort = mainEndpoint?.port ?: EndpointParser.DEFAULT_PORT)
                    if (mainEndpoint == null) {
                        error = "The main address isn't valid. Use something like 192.168.137.1:8081."
                    } else if (backup.isNotBlank() && backupEndpoint == null) {
                        error = "The backup address isn't valid. Use something like 100.67.101.121:8081."
                    } else {
                        onSave(
                            mainEndpoint.host, mainEndpoint.port,
                            backupEndpoint?.host.orEmpty(), backupEndpoint?.port ?: 0
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SignalOrange, contentColor = Ink)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SignalOrange) }
        }
    )
}
