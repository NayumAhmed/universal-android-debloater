package com.nayum.androidguardian

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
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
import rikka.shizuku.Shizuku
import java.io.File

data class AppEntry(val name: String, val packageName: String, val system: Boolean, val enabled: Boolean)

class MainActivity : ComponentActivity() {
    private var guardianService: IGuardianService? = null
    private var shizukuStatus by mutableStateOf("Not connected")
    private var onShizukuPermission: Shizuku.OnRequestPermissionResultListener? = null

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(packageName, GuardianUserService::class.java.name)
        ).daemon(false)
            .processNameSuffix("guardian")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            guardianService = if (binder?.pingBinder() == true) IGuardianService.Stub.asInterface(binder) else null
            shizukuStatus = if (guardianService != null) "Connected and authorised" else "Service connection failed"
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            guardianService = null
            shizukuStatus = "Shizuku service disconnected. Start it again and reconnect."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onShizukuPermission = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 7401) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) bindGuardianService()
                else shizukuStatus = "Permission denied in Shizuku. No packages were changed."
            }
        }
        Shizuku.addRequestPermissionResultListener(onShizukuPermission!!)
        setContent { GuardianScreen() }
    }

    override fun onDestroy() {
        onShizukuPermission?.let { Shizuku.removeRequestPermissionResultListener(it) }
        if (guardianService != null) {
            try { Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true) } catch (_: Exception) {}
        }
        guardianService = null
        super.onDestroy()
    }

    private fun connectShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                shizukuStatus = "Shizuku is not running. Install and start Shizuku first."
                return
            }
            if (Shizuku.isPreV11()) {
                shizukuStatus = "Your Shizuku version is too old. Update Shizuku."
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                bindGuardianService()
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                shizukuStatus = "Permission was denied. Open Shizuku and grant Android Guardian access."
            } else {
                shizukuStatus = "Waiting for Shizuku authorisation…"
                Shizuku.requestPermission(7401)
            }
        } catch (e: Exception) {
            shizukuStatus = "Shizuku error: ${e.message ?: "Unable to connect"}"
        }
    }

    private fun bindGuardianService() {
        try {
            shizukuStatus = "Connecting to Shizuku…"
            Shizuku.bindUserService(userServiceArgs, userServiceConnection)
        } catch (e: Exception) {
            shizukuStatus = "Could not connect: ${e.message ?: "unknown error"}"
        }
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
        var pendingSystemApp by remember { mutableStateOf<AppEntry?>(null) }
        val protected = apps.count { it.system }
        val userApps = apps.count { !it.system }

        if (pendingSystemApp != null) {
            val target = pendingSystemApp!!
            AlertDialog(
                onDismissRequest = { pendingSystemApp = null },
                title = { Text("Remove preinstalled app?") },
                text = { Text("Android Guardian will run 'pm uninstall --user 0' for ${target.name} (${target.packageName}). This removes it for the current user only; it does not erase the factory APK. Some OEM features may stop working. You can restore it later with package-manager install-existing. Continue only if you recognise this app.") },
                confirmButton = {
                    TextButton(onClick = {
                        pendingSystemApp = null
                        val service = guardianService
                        if (service == null) {
                            status = "Connect and authorise Shizuku before removing system apps."
                        } else {
                            status = "Requesting removal of ${target.name}…"
                            Thread {
                                val result = try { service.uninstallForUser(target.packageName) }
                                catch (e: Exception) { "ERROR: ${e.message ?: "Shizuku call failed"}" }
                                runOnUiThread {
                                    status = "${target.name}: $result"
                                    if (result.startsWith("SUCCESS:")) apps = installedApps()
                                }
                            }.start()
                        }
                    }) { Text("Remove for this user") }
                },
                dismissButton = { TextButton(onClick = { pendingSystemApp = null }) { Text("Cancel") } }
            )
        }

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
                                Text("Advanced removal • Shizuku", style = MaterialTheme.typography.titleLarge)
                                Text("Shizuku lets Android Guardian request ADB-level package operations after you explicitly authorise it. On non-rooted Android 11+, start Shizuku using Wireless debugging; it usually needs restarting after a reboot.")
                                Text("Connection: $shizukuStatus")
                                Button(onClick = { connectShizuku() }) { Text("Connect / authorise Shizuku") }
                                OutlinedButton(onClick = {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
                                }) { Text("Install Shizuku") }
                                Text("System-app removal is per-user only, not a firmware deletion. Core Android, Google, Motorola and chipset package namespaces are blocked in this build.")
                            }
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
                                    try { startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
                                    catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                                }) { Text("Open device storage settings") }
                            }
                        }
                    }
                    item {
                        Text("App inventory", style = MaterialTheme.typography.titleLarge)
                        Text("Review app names and package IDs. A system label alone does not mean an app is essential or safe to remove.")
                    }
                    items(apps, key = { it.packageName }) { app ->
                        Card {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(app.name, style = MaterialTheme.typography.titleMedium)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    when {
                                        app.system -> "PREINSTALLED • requires Shizuku for user-level removal"
                                        !app.enabled -> "REVIEW • currently disabled"
                                        else -> "REVIEW • installed user app"
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                }) { Text("App details / storage") }
                                if (app.system) {
                                    Button(onClick = { pendingSystemApp = app }) { Text("Remove preinstalled app (Shizuku)") }
                                } else {
                                    Button(onClick = {
                                        try {
                                            startActivity(Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                                                data = Uri.parse("package:${app.packageName}")
                                                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                                            })
                                            status = "Android uninstall confirmation opened for ${app.name}."
                                        } catch (e: Exception) {
                                            status = "Uninstall screen could not be opened: ${e.message ?: "unsupported"}"
                                        }
                                    }) { Text("Uninstall app") }
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
                        Text("Safety: no bulk removal, no firmware partition changes, and no access to private app data. Each preinstalled-app removal requires a separate confirmation.")
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
