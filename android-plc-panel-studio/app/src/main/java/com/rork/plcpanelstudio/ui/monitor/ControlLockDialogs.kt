package com.rork.plcpanelstudio.ui.monitor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.rork.plcpanelstudio.ui.theme.Ink
import com.rork.plcpanelstudio.ui.theme.SignalOrange
import com.rork.plcpanelstudio.ui.theme.SignalRed
import com.rork.plcpanelstudio.ui.theme.Surface2
import com.rork.plcpanelstudio.ui.theme.TextLow
import com.rork.plcpanelstudio.ui.theme.TextMid
import kotlinx.coroutines.launch

/** What the password dialog is asking for. */
enum class PasswordDialogMode { UNLOCK, CREATE, CHANGE }

/**
 * One dialog for the three password tasks: unlock the controls, create the first password,
 * or change it. [onSubmit] receives (current, new, confirm) and returns an error message to
 * show, or null when it worked (the dialog then closes).
 */
@Composable
fun PasswordDialog(
    mode: PasswordDialogMode,
    onDismiss: () -> Unit,
    onSubmit: suspend (current: String, new: String, confirm: String) -> String?,
    onWantChange: (() -> Unit)? = null
) {
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val title = when (mode) {
        PasswordDialogMode.UNLOCK -> "Unlock controls"
        PasswordDialogMode.CREATE -> "Set a control password"
        PasswordDialogMode.CHANGE -> "Change password"
    }
    val hint = when (mode) {
        PasswordDialogMode.UNLOCK -> "Enter the password to operate buttons and selectors."
        PasswordDialogMode.CREATE -> "Buttons and selectors are protected. Choose a password (at least 4 characters) — you will need it to operate this panel."
        PasswordDialogMode.CHANGE -> "Enter the current password, then the new one."
    }
    val confirmLabel = when (mode) {
        PasswordDialogMode.UNLOCK -> "Unlock"
        PasswordDialogMode.CREATE -> "Set password"
        PasswordDialogMode.CHANGE -> "Change"
    }

    fun submit() {
        if (busy) return
        scope.launch {
            busy = true
            val result = onSubmit(current, newPassword, confirm)
            busy = false
            if (result == null) {
                onDismiss()
            } else {
                error = result
                // Never keep a rejected password in the field.
                current = if (mode == PasswordDialogMode.CREATE) current else ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Surface2,
        title = { Text(title) },
        text = {
            Column {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = TextMid)
                if (mode != PasswordDialogMode.CREATE) {
                    Spacer(Modifier.height(14.dp))
                    PasswordField(
                        value = current,
                        onValueChange = { current = it; error = null },
                        label = if (mode == PasswordDialogMode.UNLOCK) "Password" else "Current password",
                        visible = visible,
                        onToggleVisible = { visible = !visible },
                        last = mode == PasswordDialogMode.UNLOCK,
                        onDone = ::submit
                    )
                }
                if (mode != PasswordDialogMode.UNLOCK) {
                    Spacer(Modifier.height(10.dp))
                    PasswordField(
                        value = newPassword,
                        onValueChange = { newPassword = it; error = null },
                        label = "New password",
                        visible = visible,
                        onToggleVisible = { visible = !visible },
                        last = false,
                        onDone = ::submit
                    )
                    Spacer(Modifier.height(10.dp))
                    PasswordField(
                        value = confirm,
                        onValueChange = { confirm = it; error = null },
                        label = "Confirm new password",
                        visible = visible,
                        onToggleVisible = { visible = !visible },
                        last = true,
                        onDone = ::submit
                    )
                }
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = SignalRed)
                }
                if (mode == PasswordDialogMode.UNLOCK && onWantChange != null) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onWantChange) {
                        Text("Change password", color = TextLow)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = ::submit,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = SignalOrange, contentColor = Ink)
            ) {
                Text(if (busy) "Checking…" else confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!busy) onDismiss() }) {
                Text("Cancel", color = SignalOrange)
            }
        }
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    last: Boolean,
    onDone: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = if (last) ImeAction.Done else ImeAction.Next
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password"
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}
