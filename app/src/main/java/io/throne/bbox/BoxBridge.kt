package io.throne.bbox

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.ktx.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * The MethodChannel the B-box Flutter UI talks to.
 *
 * Anything Throne cannot answer fails with a readable message, which the UI surfaces as a snackbar,
 * and everything it can answer is translated into Throne's own storage:
 * [DataStore] / SettingsRegistry for settings, Room through ProfileManager and GroupRepo for
 * profiles, and the two services for lifecycle.
 */
/** Missing arguments fail with a readable message, exactly as the upstream bridge did. */
private fun MethodCall.string(key: String): String =
    argument<String>(key) ?: throw IllegalArgumentException("Missing argument: $key")

private fun MethodCall.long(key: String = "id"): Long =
    argument<Number>(key)?.toLong() ?: throw IllegalArgumentException("Missing argument: $key")

class BoxBridge(
    private val messenger: BinaryMessenger,
    private val context: Context,
) : MethodChannel.MethodCallHandler, EventChannel.StreamHandler, ServiceBridge.Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val channel = MethodChannel(messenger, "io.ericlee.bfa/box")
    private val events = EventChannel(messenger, "io.ericlee.bfa/events")
    private var sink: EventChannel.EventSink? = null
    private val main = Handler(Looper.getMainLooper())

    private var logJob: Job? = null
    private var logId = 0L

    fun attach() {
        channel.setMethodCallHandler(this)
        events.setStreamHandler(this)
        ServiceBridge.addListener(this)
        ServiceBridge.attach(context)
    }

    fun detach() {
        channel.setMethodCallHandler(null)
        events.setStreamHandler(null)
        ServiceBridge.removeListener(this)
        ServiceBridge.detach(context)
        stopLogs()
        scope.coroutineContext.cancelChildren()
    }

    // --------------------------------------------------------------------------------------------- events

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        sink = events
        emit("dashboard", ServiceBridge.dashboard())
        emit("settings", Settings.read())
        emit("lan", ProfilesBridge.lan())
        scope.launch {
            emit("profiles", ProfilesBridge.list())
        }
    }

    override fun onCancel(arguments: Any?) {
        sink = null
    }

    private fun emit(type: String, value: Any?) {
        val sink = sink ?: return
        main.post { runCatching { sink.success(mapOf("type" to type, "value" to value)) } }
    }

    override fun onState(state: BaseService.State, profileName: String?) {
        emit("dashboard", ServiceBridge.dashboard())
        emit("lan", ProfilesBridge.lan())
    }

    override fun onTotals(tx: Long, rx: Long) {
        emit("dashboard", ServiceBridge.dashboard())
    }

    override fun onRates(tx: Long, rx: Long) {
        emit("rates", mapOf("upload" to tx, "download" to rx, "memoryBytes" to 0L))
    }

    // --------------------------------------------------------------------------------------------- ready

    private fun ready(): Map<String, Any?> = mapOf(
        "version" to ServiceBridge.version(),
        "coreVersion" to BuildConfig.THRONE_CORE_REF,
        "systemAccentColor" to systemAccentColor(),
        "dashboard" to ServiceBridge.dashboard(),
        "profiles" to emptyList<Map<String, Any?>>(), // filled asynchronously below
        "settings" to Settings.read(),
        "preferences" to UiPreferences.read(),
        "lan" to ProfilesBridge.lan(),
        "tools" to emptyMap<String, Any?>(),
        "diagnostics" to emptyMap<String, Any?>(),
        "capabilities" to Capabilities.map(),
    )

    private fun systemAccentColor(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0xff007aff.toInt()
        return runCatching {
            @Suppress("InlinedApi")
            context.resources.getColor(android.R.color.system_accent1_500, context.theme)
        }.getOrDefault(0xff007aff.toInt())
    }

    // --------------------------------------------------------------------------------------------- methods

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        scope.launch {
            try {
                val reply = withContext(Dispatchers.IO) { handle(call) }
                result.success(reply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                result.error("box_error", e.message, null)
            }
        }
    }

    /** Runs off the main thread: Room and file IO live here. */
    private suspend fun handle(call: MethodCall): Any? = when (call.method) {

        "ready" -> ready().let { map ->
            map + ("profiles" to ProfilesBridge.list())
        }

        // -- service ---------------------------------------------------------------------------------
        "service.start" -> {
            ServiceBridge.start()
            null
        }

        "service.stop" -> {
            ServiceBridge.stop()
            null
        }

        "service.reload" -> {
            ServiceBridge.reload(call.argument<Boolean>("restart") == true)
            null
        }

        "service.select" -> {
            ProfilesBridge.select(call.long("id"))
            emit("dashboard", ServiceBridge.dashboard())
            null
        }

        "service.urlTest" -> ServiceBridge.urlTest()

        "service.resetTraffic" -> {
            val ids = call.argument<List<Number>>("ids")?.map { number -> number.toLong() }?.toLongArray()
                ?: longArrayOf(DataStore.selectedProxy)
            ServiceBridge.resetTraffic(ids)
            null
        }

        // -- settings --------------------------------------------------------------------------------
        "settings.get" -> Settings.read()

        "settings.set" -> {
            val key = call.string("key")
            val value = call.argument<Any?>("value")
            val restart = call.argument<Boolean>("restart") == true
            val entry = Settings.entries.firstOrNull { candidate -> candidate.bridgeKey == key }
                ?: throw IllegalArgumentException("Unknown setting: $key")
            if (restart && !entry.autoRestartable) {
                throw IllegalArgumentException("Invalid automatic service restart setting: $key")
            }
            Settings.set(key, value)
            afterChange(entry.apply, restart)
            Settings.read()
        }

        "settings.patch" -> {
            val values = call.argument<Map<String, Any?>>("value").orEmpty()
            var apply: Apply = Apply.None
            for ((key, value) in values) {
                val entry = Settings.entries.firstOrNull { candidate -> candidate.bridgeKey == key }
                    ?: throw IllegalArgumentException("Unknown setting: $key")
                Settings.set(key, value)
                apply = strongest(apply, entry.apply)
            }
            afterChange(apply, false)
            Settings.read()
        }

        "preferences.set" -> {
            val values = call.argument<Map<String, Any?>>("value").orEmpty()
            UiPreferences.write(values)
            UiPreferences.read()
        }

        // -- profiles --------------------------------------------------------------------------------
        "profiles.list" -> ProfilesBridge.list()

        "profiles.select" -> {
            ProfilesBridge.select(call.long("id"))
            emit("dashboard", ServiceBridge.dashboard())
            null
        }

        "profiles.save" -> ProfilesBridge.save(
            id = call.argument<Number>("id")?.toLong(),
            name = call.argument<String>("name").orEmpty(),
            url = call.argument<String>("url").orEmpty(),
            autoUpdate = call.argument<Boolean>("autoUpdate") == true,
            interval = call.argument<Number>("interval")?.toInt() ?: 0,
        ).also { emit("profiles", ProfilesBridge.list()) }

        "profiles.update" -> {
            ProfilesBridge.update(call.long("id"))
            emit("profiles", ProfilesBridge.list())
            null
        }

        "profiles.delete" -> {
            ProfilesBridge.delete(call.long("id"))
            emit("profiles", ProfilesBridge.list())
            null
        }

        "profiles.config" -> profileConfig()

        "profiles.read" -> profileConfig()["content"]

        "profiles.write" -> {
            val content = call.string("content")
            writeProfile(content)
            emit("profiles", ProfilesBridge.list())
            profileConfig()
        }

        "profiles.stats" -> mapOf(
            "groups" to 0,
            "outbounds" to 1,
            "rules" to 0,
        )

        "config.check" -> {
            val content = call.string("content")
            org.json.JSONObject(content)
            true
        }

        "config.format" -> {
            val content = call.string("content")
            org.json.JSONObject(content).toString(2)
        }

        // -- inbound / LAN ----------------------------------------------------------------------------
        "dashboard.lan.refresh" -> ProfilesBridge.lan()

        "dashboard.lan.set" -> ProfilesBridge.setLan(
            enabled = call.argument<Boolean>("enabled") == true,
            port = call.argument<Number>("port")?.toInt(),
        ).also { emit("lan", it) }

        // -- system proxy: the VPN hands the mixed inbound to apps, which needs API 29 -----------------
        "systemProxy.set" -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || DataStore.serviceMode != Key.MODE_VPN) {
                throw IllegalStateException("System HTTP proxy is unavailable")
            }
            DataStore.appendHttpProxy = call.argument<Boolean>("enabled") == true
            afterChange(Apply.Reload, false)
            emit("dashboard", ServiceBridge.dashboard())
            null
        }

        // -- logs: Throne's core logs ride logcat, the same source the native logcat screen reads -----
        "logs.visible" -> {
            if (call.argument<Boolean>("visible") == true) startLogs() else stopLogs()
            null
        }

        "logs.clear" -> {
            runCatching { Runtime.getRuntime().exec("/system/bin/logcat -c") }
            logId = 0
            emit(
                "logs", mapOf(
                    "reset" to true,
                    "firstId" to null,
                    "defaultLogLevel" to DataStore.logLevel,
                    "entries" to emptyList<Map<String, Any?>>(),
                )
            )
            null
        }

        // -- per-app proxy: PackageManager instead of the Shizuku scanner ---------------------------
        "apps.list" -> AppsBridge.list(
            refresh = call.argument<Boolean>("refresh") == true,
            system = call.argument<Boolean>("system") == true,
            nonInternet = call.argument<Boolean>("nonInternet") == true,
        )

        "apps.icon" -> AppsBridge.icon(call.string("package"))

        // There is no managed list to rescan; the selection is Throne's own.
        "apps.rescan" -> Settings.read()

        // -- host extras ------------------------------------------------------------------------------
        "battery.open" -> openBatterySettings()

        "url.open" -> {
            val url = call.string("url")
            val uri = Uri.parse(url)
            if (uri.scheme != "http" && uri.scheme != "https") {
                throw IllegalArgumentException("Unsupported URL: $url")
            }
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            null
        }

        "document.save" -> {
            val name = call.string("name")
            val content = call.string("content")
            val file = File(context.cacheDir, name)
            file.writeText(content)
            true
        }

        "share.text" -> {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, call.string("content"))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            null
        }

        "intent.take" -> null

        "updates.check" -> if (!BuildConfig.IN_APP_UPDATER) {
            throw IllegalStateException("Updates are not available in this build")
        } else {
            mapOf(
                "downloadUrl" to null,
                "url" to "https://github.com/throneproj/ThroneForAndroid/releases/latest",
            )
        }

        // -- everything the pruned pages used ----------------------------------------------------------
        else -> throw UnsupportedOperationException("$method is not supported by Throne")
    }

    private fun strongest(current: Apply, next: Apply): Apply = when {
        next == Apply.AppRestart -> next
        current == Apply.AppRestart -> current
        next == Apply.Reload -> next
        else -> current
    }

    private fun afterChange(apply: Apply, restartRequested: Boolean) {
        when (apply) {
            Apply.None -> Unit
            Apply.Reload -> if (ServiceBridge.running) SagerNet.reloadService()
            Apply.AppRestart -> emit("applyRequired", "Restart")
        }
        if (restartRequested && ServiceBridge.running) {
            ServiceBridge.reload(true)
        }
        emit("settings", Settings.read())
        emit("dashboard", ServiceBridge.dashboard())
        emit("lan", ProfilesBridge.lan())
    }

    // ----------------------------------------------------------------------------------------------- logs

    private fun startLogs() {
        if (logJob?.isActive == true) return
        logJob = scope.launch(Dispatchers.IO) {
            val level = DataStore.logLevel
            val process = runCatching {
                ProcessBuilder("/system/bin/logcat", "-v", "time", "-T", "300").start()
            }.getOrNull() ?: return@launch
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (!isActive) break
                        val entry = parseLogEntry(line) ?: continue
                        logId++
                        emit(
                            "logs", mapOf(
                                "reset" to false,
                                "firstId" to null,
                                "defaultLogLevel" to level,
                                "entries" to listOf(entry + ("id" to logId)),
                            )
                        )
                        // Coalesce bursts so the UI gets a few entries per frame instead of thousands.
                        delay(50)
                    }
                }
            } finally {
                process.destroy()
            }
        }
    }

    private fun stopLogs() {
        logJob?.cancel()
        logJob = null
    }

    /** `03-01 12:00:00.000  1234  1234 D tag: message` -> `{level, message, receivedAt}`. */
    private fun parseLogEntry(line: String): Map<String, Any?>? {
        if (line.isBlank()) return null
        val body = line.substringAfter(": ", line)
        val level = when {
            line.contains(" E ") -> "error"
            line.contains(" W ") -> "warn"
            line.contains(" I ") -> "info"
            line.contains(" D ") -> "debug"
            line.contains(" V ") -> "trace"
            line.contains(" F ") || line.contains(" A ") -> "fatal"
            else -> "info"
        }
        if (body.isBlank()) return null
        return mapOf(
            "level" to level,
            "message" to body,
            "receivedAt" to System.currentTimeMillis(),
        )
    }

    // ------------------------------------------------------------------------------------------- profiles

    private fun profileConfig(): Map<String, Any?> {
        val profile = ProfileManager.getProfile(DataStore.selectedProxy)
        val content = profile?.outboundJson?.takeIf { json -> json.isNotBlank() } ?: "{}"
        val formatted = runCatching { org.json.JSONObject(content).toString(2) }.getOrDefault(content)
        return mapOf(
            "content" to content,
            "formatted" to formatted,
            "revision" to ProfilesBridge.REVISION,
            "stats" to mapOf("groups" to 0, "outbounds" to 1, "rules" to 0),
        )
    }

    private suspend fun writeProfile(content: String) {
        org.json.JSONObject(content) // validate before anything touches the record
        val profile = ProfileManager.getProfile(DataStore.selectedProxy)
            ?: throw IllegalStateException("Select an outbound before editing its configuration")
        profile.outboundJson = content
        ProfileManager.updateProfile(profile)
    }
