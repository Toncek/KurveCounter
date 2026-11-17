package com.motogeek.telemetry.ui.components

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.motogeek.telemetry.domain.sync.DriveSyncPhase
import com.motogeek.telemetry.domain.sync.DriveSyncScheduler
import com.motogeek.telemetry.ui.theme.tr

@Composable
fun DriveSyncAuthorizationPrompt() {
    val context = LocalContext.current
    val status by DriveSyncScheduler.status.collectAsState()
    var dismissedAccount by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            dismissedAccount = null
            DriveSyncScheduler.enqueue(context, afterCurrent = true)
        }
    }
    if (status.phase == DriveSyncPhase.NEEDS_PERMISSION && status.account != dismissedAccount) {
        AlertDialog(
            onDismissRequest = { dismissedAccount = status.account },
            title = { Text(tr("Sync your devices", "Synchronizovať zariadenia")) },
            text = { Text(tr(
                "Allow Google Drive access for ${status.account} to sync your garage, service history and rides automatically. You can enable it later in JSON & Cloud Backup.",
                "Povoľte prístup k Disku Google pre ${status.account} a automaticky synchronizujte garáž, servisnú históriu a jazdy. Môžete ho povoliť aj neskôr v JSON a Cloud zálohe."
            )) },
            confirmButton = {
                TextButton(onClick = {
                    dismissedAccount = status.account
                    status.authorizationIntent?.let { launcher.launch(it) }
                }) { Text(tr("Allow Drive sync", "Povoliť synchronizáciu")) }
            },
            dismissButton = {
                TextButton(onClick = { dismissedAccount = status.account }) { Text(tr("Later", "Neskôr")) }
            }
        )
    }
}
