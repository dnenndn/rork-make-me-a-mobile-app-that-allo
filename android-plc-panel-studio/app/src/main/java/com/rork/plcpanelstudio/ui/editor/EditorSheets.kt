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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.rork.plcpanelstudio.data.Panel
import com.rork.plcpanelstudio.data.PanelComponent
import com.rork.plcpanelstudio.data.PlcDevice
import com.rork.plcpanelstudio.data.TagArea
import com.rork.plcpanelstudio.data.fixedAreaFor
import com.rork.plcpanelstudio.data.fixedDirectionFor
import com.rork.plcpanelstudio.data.filterTagBody
import com.rork.plcpanelstudio.data.isValidTagBody
import com.rork.plcpanelstudio.data.tagBodyIgnoringArea
import com.rork.plcpanelstudio.data.tagAddressOf
import com.rork.plcpanelstudio.ui.components.CoverTile
import com.rork.plcpanelstudio.ui.components.HardwareFace
import com.rork.plcpanelstudio.ui.components.PANEL_COVERS
import com.rork.plcpanelstudio.ui.theme.Ink
import com.rork.plcpanelstudio.ui.theme.Line
import com.rork.plcpanelstudio.ui.theme.MonoFamily
import com.rork.plcpanelstudio.ui.theme.NameplateStyle
import com.rork.plcpanelstudio.ui.theme.SignalOrange
import com.rork.plcpanelstudio.ui.theme.SignalRed
import com.rork.plcpanelstudio.ui.theme.Surface1
import com.rork.plcpanelstudio.ui.theme.TextLow
import com.rork.plcpanelstudio.ui.theme.TextMid
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentPropertiesSheet(
    component: PanelComponent,
    suggestAddress: (TagArea, Set<String>) -> String,
    onDismiss: () -> Unit,
    onApply: (PanelComponent) -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isSelector = component.kind == ComponentKind.SELECTOR

    // Every part has exactly one possible area: buttons and the selector are always inputs,
    // lamps are always outputs. There is nothing to pick, so this is a plain value, not state.
    val area = fixedAreaFor(component.kind)

    var label by remember(component.id) { mutableStateOf(component.label) }
    var momentary by remember(component.id) { mutableStateOf(component.momentary) }
    var positions by remember(component.id) { mutableIntStateOf(component.positions.coerceIn(2, 3)) }

    // The "byte.bit" part the user types; the area letter above is fixed and shown as a prefix.
    var body by remember(component.id) { mutableStateOf(tagBodyIgnoringArea(component.tagAddress)) }
    var positionBodies by remember(component.id) {
        mutableStateOf(List(3) { tagBodyIgnoringArea(component.positionAddress(it)) })
    }

    val bodyInvalid = body.isNotBlank() && !isValidTagBody(body)
    val typedPositions = positionBodies.take(positions).map { it.trim() }.filter { it.isNotBlank() }
    val positionsInvalid = typedPositions.any { !isValidTagBody(it) }
    val hasDuplicateInputs = isSelector && typedPositions.size != typedPositions.toSet().size
    val canApply = if (isSelector) !hasDuplicateInputs && !positionsInvalid else !bodyInvalid

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
                        "${component.kind.displayName} · position ${(component.col * 100).roundToInt()}% / ${(component.row * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMid
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete component", tint = SignalRed)
                }
            }

            // Direction and area are both fixed by what the part is (see fixedAreaFor):
            // buttons and the selector always write to input I, lamps always mirror output Q.
            Spacer(Modifier.height(18.dp))
            Text(
                text = if (component.kind.isLamp) {
                    "Output (Q): this part only mirrors what the PLC reports, it never writes to it."
                } else if (isSelector) {
                    "Input (I): turning this selector writes to the PLC."
                } else {
                    "Input (I): pressing this button writes to the PLC."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextMid
            )

            if (!isSelector) {
                Spacer(Modifier.height(16.dp))
                SectionLabel("Tag Address")
                TagBodyField(
                    area = area,
                    body = body,
                    onBodyChange = { body = it },
                    isError = bodyInvalid,
                    label = null
                )
                TextButton(
                    onClick = { body = suggestAddress(area, emptySet()).removePrefix(area.prefix) },
                    modifier = Modifier.padding(top = 2.dp)
                ) {
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
                ComponentKind.STOP, ComponentKind.GREEN, ComponentKind.YELLOW -> {
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

                    Spacer(Modifier.height(16.dp))
                    SectionLabel("Input for each position")
                    Text(
                        "Turning the selector to a position sets that position's input to 1 and " +
                            "the other inputs to 0. Leave a position empty if it has no input.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLow
                    )
                    for (index in 0 until positions) {
                        Spacer(Modifier.height(8.dp))
                        val positionBody = positionBodies[index]
                        TagBodyField(
                            area = area,
                            body = positionBody,
                            onBodyChange = { value ->
                                positionBodies = positionBodies.toMutableList().also { it[index] = value }
                            },
                            isError = (positionBody.isNotBlank() && !isValidTagBody(positionBody)) ||
                                (hasDuplicateInputs && positionBody.isNotBlank() &&
                                    typedPositions.count { it == positionBody.trim() } > 1),
                            label = "Position ${index + 1}"
                        )
                    }
                    TextButton(
                        onClick = {
                            val next = positionBodies.toMutableList()
                            val taken = next.take(positions)
                                .filter { it.isNotBlank() }
                                .map { tagAddressOf(area, it) }
                                .toMutableSet()
                            for (index in 0 until positions) {
                                if (next[index].isBlank()) {
                                    val suggestion = suggestAddress(area, taken)
                                    next[index] = suggestion.removePrefix(area.prefix)
                                    taken += suggestion
                                }
                            }
                            positionBodies = next
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text("Suggest free addresses", fontSize = 12.sp)
                    }
                    if (hasDuplicateInputs) {
                        Text(
                            "Each position needs a different input.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SignalRed
                        )
                    }
                    if (component.tagAddress.isNotBlank() && !component.hasPositionTags) {
                        Text(
                            "Currently one tag (${component.tagAddress}) holds the position number. " +
                                "Fill in the inputs above to switch to one input per position.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextLow,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                ComponentKind.LAMP, ComponentKind.LAMP_RED, ComponentKind.LAMP_GREEN -> Unit
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
                                tagAddress = when {
                                    isSelector && typedPositions.isNotEmpty() -> ""
                                    isSelector -> component.tagAddress
                                    else -> tagAddressOf(area, body)
                                },
                                direction = fixedDirectionFor(component.kind),
                                momentary = momentary,
                                positions = positions,
                                positionTags = if (isSelector && typedPositions.isNotEmpty()) {
                                    positionBodies.take(positions).map { tagAddressOf(area, it) }
                                } else emptyList()
                            )
                        )
                    },
                    enabled = canApply,
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

/**
 * Field for the "byte.bit" part of an address. The area letter is fixed by the part's kind and
 * shown as a prefix; the typed text is filtered down to digits and a single dot, so the result
 * can only ever be something like 3.4 — giving I3.4, Q3.2 or M5.4 once the area is put in front.
 */
@Composable
private fun TagBodyField(
    area: TagArea,
    body: String,
    onBodyChange: (String) -> Unit,
    isError: Boolean,
    label: String?
) {
    val fieldLabel: (@Composable () -> Unit)? = label?.let { text -> { Text(text) } }
    OutlinedTextField(
        value = body,
        onValueChange = { onBodyChange(filterTagBody(it)) },
        label = fieldLabel,
        prefix = {
            Text(
                area.prefix,
                fontFamily = MonoFamily,
                color = if (isError) SignalRed else SignalOrange,
                fontWeight = FontWeight.Bold
            )
        },
        placeholder = { Text("3.4") },
        singleLine = true,
        isError = isError,
        supportingText = {
            Text(
                text = if (body.isBlank()) {
                    "Byte and bit separated by a dot, e.g. ${area.prefix}3.4"
                } else if (isValidTagBody(body)) {
                    "Address: ${tagAddressOf(area, body)}"
                } else {
                    "Incomplete: use byte.bit, e.g. ${area.prefix}3.4 (bit 0-7)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) SignalRed else TextLow
            )
        },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily),
        trailingIcon = {
            if (body.isNotEmpty()) {
                IconButton(onClick = { onBodyChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear address")
                }
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelSettingsSheet(
    panel: Panel,
    devices: List<PlcDevice>,
    onDismiss: () -> Unit,
    onApply: (String, String, String?, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(panel.id) { mutableStateOf(panel.name) }
    var description by remember(panel.id) { mutableStateOf(panel.description) }
    var deviceId by remember(panel.id) { mutableStateOf(panel.deviceId) }
    var cover by remember(panel.id) { mutableStateOf(panel.cover) }

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
            Spacer(Modifier.height(14.dp))
            SectionLabel("Cover picture")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PANEL_COVERS, key = { it.id }) { item ->
                    CoverTile(
                        item = item,
                        selected = item.id == cover,
                        onClick = { cover = item.id }
                    )
                }
            }
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
                    onClick = { onApply(name, description, deviceId, cover) },
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
