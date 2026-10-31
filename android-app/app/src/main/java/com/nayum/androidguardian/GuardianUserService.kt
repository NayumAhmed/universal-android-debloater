package com.nayum.androidguardian

import android.os.RemoteException

/**
 * Runs inside Shizuku's user-service process with the privileges Shizuku
 * was started with (normally ADB shell, not root).
 */
class GuardianUserService : IGuardianService.Stub() {
    override fun destroy() {
        System.exit(0)
    }

    override fun uninstallForUser(packageName: String): String {
        if (!isValidPackage(packageName)) return "ERROR: Invalid package name."
        if (isProtectedCorePackage(packageName)) {
            return "BLOCKED: This package belongs to a protected Android, Google, Motorola or chipset service."
        }
        return runPmCommand(arrayOf("pm", "uninstall", "--user", "0", packageName))
    }

    override fun restoreForUser(packageName: String): String {
        if (!isValidPackage(packageName)) return "ERROR: Invalid package name."
        if (isProtectedCorePackage(packageName)) {
            return "BLOCKED: Protected core packages cannot be changed by Android Guardian."
        }
        return runPmCommand(arrayOf("cmd", "package", "install-existing", "--user", "0", packageName))
    }

    private fun isValidPackage(value: String): Boolean =
        value.length in 3..255 &&
            value.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) &&
            value != "com.nayum.androidguardian"

    private fun isProtectedCorePackage(pkg: String): Boolean {
        val protectedPrefixes = listOf(
            "android", "com.android.", "com.google.android.",
            "com.motorola.", "com.qualcomm.", "com.mediatek."
        )
        return pkg == "android" || protectedPrefixes.any { pkg.startsWith(it) }
    }

    private fun runPmCommand(command: Array<String>): String {
        return try {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val exit = process.waitFor()
            if (exit == 0) "SUCCESS: ${output.ifBlank { "Package manager completed the operation." }}"
            else "FAILED (exit $exit): ${output.ifBlank { "Package manager rejected the operation." }}"
        } catch (e: Exception) {
            "ERROR: ${e.message ?: "Could not execute package-manager command."}"
        }
    }
}
