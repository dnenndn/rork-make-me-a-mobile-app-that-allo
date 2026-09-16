package com.rork.plcpanelstudio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.plcpanelstudio.data.ComponentKind
import com.rork.plcpanelstudio.data.IoDirection
import com.rork.plcpanelstudio.data.Panel
import com.rork.plcpanelstudio.data.PanelComponent
import com.rork.plcpanelstudio.data.PlcDevice
import com.rork.plcpanelstudio.ui.components.HardwareFace
import com.rork.plcpanelstudio.ui.theme.Ink
import com.rork.plcpanelstudio.ui.theme.Line
import com.rork.plcpanelstudio.ui.theme.MonoFamily
import com.rork.plcpanelstudio.ui.theme.NameplateStyle
import com.rork.plcpanelstudio.ui.theme.SignalOrange
import com.rork.plcpanelstudio.ui.theme.SignalRed
import com.rork.plcpanelstudio.ui.theme.Surface1
import com.rork.plcpanelstudio.ui.theme.TextLow
import com.rork.plcpanelstudio.ui.theme.TextMid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentPropertiesSheet(
    component: PanelComponent,
    suggestAddress: (IoDirection) -> String,
    onDismiss: () -> Unit,
    onApply: (PanelComponent) -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var label by remember(component.id) { mutableStateOf(component.label) }
    var address by remember(component.id) { mutableStateOf(component.tagAddress) }
    var direction by remember(component.id) { mutableStateOf(component.direction) }
    var momentary by remember(component.id) { mutableStateOf(component.momentary) }
    var positions by remember(component.id) { mutableIntStateOf(component.positions.coerceIn(2, 3)) }
    var scaleMax by remember(component.id) { mutableStateOf(component.scaleMax.toString()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface1
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HardwareFace(
                    kind = component.kind,
                    active = true,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Assign Tag", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${component.kind.displayName} · slot R${component.row + 1}C${component.col + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMid
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete component", tint = SignalRed)
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel("Signal direction")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IoDirection.entries.forEach { option ->
                    FilterChip(
                        selected = direction == option,
                        onClick = {
                            direction = option
                            if (address.isBlank()) address = suggestAddress(option)
                        },
                        label = { Text(option.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SignalOrange.copy(alpha = 0.18f),
                            selectedLabelColor = SignalOrange
                        )
                    )
                }
            }
            Text(
                text = if (direction == IoDirection.INPUT) {
                    "Tapping this part writes to the PLC so you can prove the wiring."
                } else {
                    "Read-only: the part mirrors the PLC output state."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextLow,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(16.dp))
            SectionLabel("Tag Address")
            OutlinedTextField(
                value = address,
                onValueChange = { address = it.uppercase() },
                placeholder = { Text("e.g. M0.3, I0.0, Q0.1, MW20") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily),
                trailingIcon = {
                    if (address.isNotEmpty()) {
                        IconButton(onClick = { address = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear address")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextButton(onClick = { address = suggestAddress(direction) }) {
                    Text("Suggest next free", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(10.dp))
            SectionLabel("Label")
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                placeholder = { Text("Start") },
                singleLine = true,
                trailingIcon = {
                    if (label.isNotEmpty()) {
                        IconButton(onClick = { label = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear label")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            when (component.kind) {
                ComponentKind.BUTTON -> {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Momentary", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Releases back to 0 when you lift your finger.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextLow
                            )
                        }
                        Switch(
                            checked = momentary,
                            onCheckedChange = { momentary = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = SignalOrange)
                        )
                    }
                }
                ComponentKind.SELECTOR -> {
                    Spacer(Modifier.height(16.dp))
                    SectionLabel("Positions")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 3).forEach { option ->
                            FilterChip(
                                selected = positions == option,
                                onClick = { positions = option },
                                label = { Text("$option-position") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SignalOrange.copy(alpha = 0.18f),
                                    selectedLabelColor = SignalOrange
                                )
                            )
                        }
                    }
                }
                ComponentKind.GAUGE -> {
                    Spacer(Modifier.height(16.dp))
                    SectionLabel("Full scale value")
                    OutlinedTextField(
                        value = scaleMax,
                        onValueChange = { scaleMax = it.filter(Char::isDigit).take(6) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ComponentKind.LAMP -> Unit
            }

            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = SignalOrange) }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = {
                        onApply(
                            component.copy(
                                label = label.ifBlank { component.kind.displayName },
                                tagAddress = address.trim(),
                                direction = direction,
                                momentary = momentary,
                                positions = positions,
                                scaleMax = scaleMax.toIntOrNull()?.coerceAtLeast(1) ?: 100
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignalOrange,
                        contentColor = Ink
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Apply", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelSettingsSheet(
    panel: Panel,
    devices: List<PlcDevice>,
    onDismiss: () -> Unit,
    onApply: (String, String, String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(panel.id) { mutableStateOf(panel.name) }
    var description by remember(panel.id) { mutableStateOf(panel.description) }
    var deviceId by remember(panel.id) { mutableStateOf(panel.deviceId) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface1
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding()
        ) {
            Text("Panel settings", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
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
            Spacer(Modifier.height(18.dp))
            SectionLabel("Linked PLC")
            if (devices.isEmpty()) {
                Text(
                    "Add a device in the Devices tab to link this panel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    devices.forEach { device ->
                        DeviceOptionRow(
                            device = device,
                            selected = device.id == deviceId,
                            onClick = { deviceId = device.id }
                        )
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = SignalOrange) }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = { onApply(name, description, deviceId) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignalOrange,
                        contentColor = Ink
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Apply", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelSwitcherSheet(
    panels: List<Panel>,
    activeId: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface1
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding()
        ) {
            Text("Switch panel", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))
            if (panels.isEmpty()) {
                Text("No panels yet.", style = MaterialTheme.typography.bodyMedium, color = TextMid)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                panels.forEach { panel ->
                    val selected = panel.id == activeId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Ink else Color.Transparent)
                            .border(
                                1.dp,
                                if (selected) SignalOrange else Line,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelect(panel.id) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                panel.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${panel.components.size} components",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMid
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceOptionRow(
    device: PlcDevice,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Ink else Color.Transparent)
            .border(1.dp, if (selected) SignalOrange else Line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(SignalOrange.copy(alpha = 0.16f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text("SIM", style = NameplateStyle, color = SignalOrange, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = NameplateStyle,
        color = TextLow,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}
