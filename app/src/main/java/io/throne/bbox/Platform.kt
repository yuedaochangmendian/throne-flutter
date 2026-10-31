package io.throne.bbox

import android.os.PowerManager
import androidx.core.content.getSystemService
import io.nekohasekai.sagernet.ktx.app

/** Host-only bits the bridge reports through the settings map. */
fun batteryUnrestricted(): Boolean {
    val power = app.getSystemService<PowerManager>() ?: return false
    return runCatching { power.isIgnoringBatteryOptimizations(app.packageName) }.getOrDefault(false)
}

fun openBatterySettings(): Boolean = runCatching {
    val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    app.startActivity(intent)
    true
}.getOrDefault(false)

fun localAddresses(): List<String> {
    val addresses = mutableListOf<String>()
    runCatching {
        for (networkInterface in java.net.NetworkInterface.getNetworkInterfaces()) {
            for (address in networkInterface.inetAddresses) {
                val text = address.hostAddress ?: continue
                if (address.isLoopbackAddress || address is java.net.Inet6Address) continue
                addresses.add(text)
            }
        }
    }
    return addresses
}
