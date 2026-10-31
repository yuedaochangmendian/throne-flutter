package io.throne.bbox

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import io.nekohasekai.sagernet.ktx.app
import java.io.ByteArrayOutputStream

/**
 * The package list behind per-app proxy, which is Throne's own per-app feature reached from
 * `Key.PROXY_APPS` / `BYPASS_MODE` / `INDIVIDUAL` rather than the Shizuku scanner B-box used.
 */
object AppsBridge {

    private val manager: PackageManager get() = app.packageManager

    @Volatile
    private var cache: List<Map<String, Any?>>? = null

    fun list(refresh: Boolean, system: Boolean, nonInternet: Boolean): List<Map<String, Any?>> {
        val cached = cache
        val apps = if (refresh || cached == null) query() else cached
        cache = apps
        return apps.filter { app ->
            (system || app["system"] != true) && (nonInternet || app["internet"] != false)
        }
    }

    private fun query(): List<Map<String, Any?>> = runCatching {
        val installed = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            manager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            manager.getInstalledPackages(0)
        }
        installed.mapNotNull { info ->
            val application = info.applicationInfo ?: return@mapNotNull null
            mapOf(
                "package" to info.packageName,
                "name" to manager.getApplicationLabel(application).toString(),
                "system" to (application.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                "internet" to (manager.checkPermission(
                    android.Manifest.permission.INTERNET, info.packageName
                ) == PackageManager.PERMISSION_GRANTED),
                "time" to info.lastUpdateTime,
            )
        }.sortedBy { app -> (app["name"] as String).lowercase() }
    }.getOrDefault(emptyList())

    /** A 48 dp PNG, the same size the upstream app list requests. */
    fun icon(packageName: String): ByteArray? = runCatching {
        val drawable = manager.getApplicationIcon(packageName)
        val size = (48 * app.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(this)
        }
        ByteArrayOutputStream().apply {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, this)
        }.toByteArray()
    }.getOrNull()
}
