package com.nayum.androidguardian

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

data class AppEntry(val name: String, val packageName: String, val system: Boolean, val enabled: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GuardianScreen() }
    }

    @Suppress("DEPRECATION")
    private fun installedApps(): List<AppEntry> =
        packageManager.getInstalledApplications(0)
            .filter { it.packageName != packageName }
            .map {
                AppEntry(
                    name = packageManager.getApplicationLabel(it).toString(),
                    packageName = it.packageName,
                    system = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    enabled = it.enabled
                )
            }.sortedBy { it.name.lowercase() }

    private fun clearGuardianCache(): Long {
        fun sizeOf(file: File): Long =
            if (file.isDirectory) file.listFiles()?.sumOf { sizeOf(it) } ?: 0L else file.length()
        val before = cacheDir.listFiles()?.sumOf { sizeOf(it) } ?: 0L
        cacheDir.listFiles()?.forEach { child ->
            if (child.isDirectory) child.deleteRecursively() else child.delete()
        }
        return before
    }

    @Composable
    private fun GuardianScreen() {
        var apps by remember { mutableStateOf(installedApps()) }
        var status by remember { mutableStateOf("Ready. No apps or personal files have been changed.") }
        val protected = apps.count { it.system }
        val userApps = apps.count { !it.system }
        MaterialTheme {
            Scaffold { padding ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text("Android Guardian", style = MaterialTheme.typography.headlineMedium)
                        Text("Your phone. Your control. Safe recommendations first.")
                    }
                    item {
                        Card {
                            Column(Modifier.padding(16.dp)) {
                                Text("${Build.MANUFACTURER} ${Build.MODEL}", style = MaterialTheme.typography.titleLarge)
                                Text("Android ${Build.VERSION.RELEASE} • SDK ${Build.VERSION.SDK_INT}")
                                Text("Build: ${Build.DISPLAY}")
                                Text("Security patch: ${Build.VERSION.SECURITY_PATCH}")
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("Apps found", apps.size.toString(), Modifier.weight(1f))
                            MetricCard("System apps", protected.toString(), Modifier.weight(1f))
                            MetricCard("User apps", userApps.toString(), Modifier.weight(1f))
                        }
                    }
                    item {
                        Card {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Storage & cache", style = MaterialTheme.typography.titleLarge)
                                Text("Clear Android Guardian's temporary cache, or open Android storage settings to review other apps. Android blocks ordinary apps from silently clearing every app's cache.")
                                Button(onClick = {
                                    val bytes = clearGuardianCache()
                                    status = "Cleared Android Guardian cache (${bytes / 1024} KB). Other apps' caches were not touched."
                                }) { Text("Clear Guardian cache") }
                                OutlinedButton(onClick = {
                                    try {
                                        startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
                                    } catch (_: Exception) {
                                        try { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                                        catch (e: Exception) { status = "Could not open storage settings: ${e.message ?: "unsupported"}" }
                                    }
                                }) { Text("Open device storage settings") }
                            }
                        }
                    }
                    item {
                        Text("App inventory", style = MaterialTheme.typography.titleLarge)
                        Text("System apps are marked protected. Removing them requires an authorised Shizuku/ADB mode; this build does not attempt unsafe system-package removal.")
                    }
                    items(apps, key = { it.packageName }) { app ->
                        Card {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(app.name, style = MaterialTheme.typography.titleMedium)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    when {
                                        app.system -> "PROTECTED • preinstalled/system package"
                                        !app.enabled -> "REVIEW • currently disabled"
                                        else -> "REVIEW • installed user app; usage and safety not yet assessed"
                                    },
                                    color = if (app.system) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(onClick = {
                                    try {
                                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${app.packageName}")
                                        })
                                        status = "Opened Android settings for ${app.name}."
                                    } catch (e: Exception) {
                                        status = "Could not open app settings: ${e.message ?: "unsupported"}"
                                    }
                                }) { Text(if (app.system) "Open protected app settings" else "Open app details / storage") }
                                if (!app.system) {
                                    Button(onClick = {
                                        try {
                                            val uninstall = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                                                data = Uri.parse("package:${app.packageName}")
                                                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                                            }
                                            startActivity(uninstall)
                                            status = "Android's uninstall confirmation opened for ${app.name}. Confirm there to continue."
                                        } catch (e: Exception) {
                                            status = "Uninstall screen could not be opened for ${app.name}: ${e.message ?: "no compatible installer"}"
                                        }
                                    }) { Text("Uninstall user app") }
                                }
                            }
                        }
                    }
                    item {
                        Text("Status", style = MaterialTheme.typography.titleMedium)
                        Text(status)
                        OutlinedButton(onClick = {
                            apps = installedApps()
                            status = "App inventory refreshed."
                        }) { Text("Refresh app inventory") }
                        Text("Safety: Android Guardian does not scan private app data, certify files as malware-free, kill background services, or change firmware. System apps remain protected unless a separate privileged workflow is explicitly authorised.")
                    }
                }
            }
        }
    }

    @Composable
    private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
        Card(modifier) {
            Column(Modifier.padding(10.dp)) {
                Text(value, style = MaterialTheme.typography.headlineSmall)
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
