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

data class AppEntry(val name: String, val packageName: String, val system: Boolean, val enabled: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GuardianScreen() }
    }

    @Suppress("DEPRECATION")
    private fun installedApps(): List<AppEntry> {
        return packageManager.getInstalledApplications(0)
            .filter { it.packageName != packageName }
            .map {
                AppEntry(
                    name = packageManager.getApplicationLabel(it).toString(),
                    packageName = it.packageName,
                    system = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    enabled = it.enabled
                )
            }.sortedBy { it.name.lowercase() }
    }

    @Composable
    private fun GuardianScreen() {
        var apps by remember { mutableStateOf(installedApps()) }
        var selectedTab by remember { mutableStateOf(0) }
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
                            MetricCard("Protected system", protected.toString(), Modifier.weight(1f))
                            MetricCard("User apps", userApps.toString(), Modifier.weight(1f))
                        }
                    }
                    item {
                        Text("App inventory", style = MaterialTheme.typography.titleLarge)
                        Text("System apps are protected from removal recommendations. 'Review' is not a malware verdict.")
                    }
                    items(apps, key = { it.packageName }) { app ->
                        Card {
                            Column(Modifier.padding(12.dp)) {
                                Text(app.name, style = MaterialTheme.typography.titleMedium)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    when {
                                        app.system -> "KEEP • Android/system component"
                                        !app.enabled -> "REVIEW • currently disabled"
                                        else -> "REVIEW • installed user app; usage and safety not yet assessed"
                                    },
                                    color = if (app.system) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!app.system) {
                                    TextButton(onClick = {
                                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:${app.packageName}")))
                                    }) { Text("Open app details") }
                                    TextButton(onClick = {
                                        startActivity(Intent(Intent.ACTION_DELETE,
                                            Uri.parse("package:${app.packageName}")))
                                    }) { Text("Request uninstall") }
                                }
                            }
                        }
                    }
                    item {
                        Text("Safety limits", style = MaterialTheme.typography.titleLarge)
                        Text("This first build does not scan private app data, certify files as malware-free, kill background services, or change firmware. Android controls uninstall confirmation. Keep system apps unless you know exactly what they do.")
                        OutlinedButton(onClick = { apps = installedApps() }) { Text("Refresh app inventory") }
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
