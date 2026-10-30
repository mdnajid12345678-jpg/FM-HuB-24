package com.fmhub24.android.ui.screens.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fmhub24.android.R
import com.fmhub24.android.ui.components.stringRes

/**
 * Update screens built now with placeholder data (spec 9): update available, the forced update
 * that cannot be dismissed, and the install-permission explainer. The values come from the
 * app_config table later, so nothing here claims a version that does not exist.
 */
@Composable
fun UpdateAvailableDialog(
    onLater: () -> Unit,
    onUpdateNow: () -> Unit,
) {
    var progress by remember { mutableIntStateOf(-1) }

    AlertDialog(
        onDismissRequest = { if (progress < 0) onLater() },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringRes(R.string.update_available_title)) },
        text = {
            Column {
                Text(stringRes(R.string.update_available_message), fontSize = 13.sp)
                if (progress >= 0) {
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringRes(R.string.update_downloading, progress),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdateNow) {
                Text(stringRes(R.string.update_now))
            }
        },
        dismissButton = {
            TextButton(onClick = onLater, enabled = progress < 0) {
                Text(stringRes(R.string.update_later))
            }
        },
    )
}

/** Forced update: it cannot be dismissed (spec 9). */
@Composable
fun ForcedUpdateDialog(onUpdateNow: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringRes(R.string.update_forced_title)) },
        text = { Text(stringRes(R.string.update_forced_message), fontSize = 13.sp) },
        confirmButton = {
            TextButton(onClick = onUpdateNow) { Text(stringRes(R.string.update_now)) }
        },
        dismissButton = null,
    )
}

/** Three steps and "Open settings" before the system install prompt (spec 9). */
@Composable
fun InstallPermissionDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringRes(R.string.install_permission_title)) },
        text = {
            Column {
                listOf(
                    R.string.install_permission_step1,
                    R.string.install_permission_step2,
                    R.string.install_permission_step3,
                ).forEachIndexed { index, step ->
                    Text(
                        text = "${index + 1}. ${stringRes(step)}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringRes(R.string.open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.action_cancel)) }
        },
    )
}


/** Which update screen the About page is showing (placeholder data, spec 9). */
enum class UpdateFlow { AVAILABLE, INSTALL_PERMISSION, TERMS }

/**
 * Entry point for the install-permission explainer: three steps and "Open settings" before the
 * system install prompt of the downloaded update.
 */
@Composable
fun ForceUpdateEntryPoint(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    InstallPermissionDialog(
        onOpenSettings = {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_SETTINGS),
                )
            }
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}
