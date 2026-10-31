package io.throne.bbox

import android.content.Context
import android.os.Build
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore

/**
 * Lifecycle of the two Throne services (VPN and proxy-only) behind `SagerNet.startService` / `stopService`
 * / `reloadService`, plus the dashboard snapshot the Flutter dashboard reads.
 * The start/stop entry points are static, not part of ISagerNetService: the service only exposes state,
 * the speed and traffic callbacks and `urlTest`.
 */
object ServiceBridge {

    /** A private id so the connection is never confused with the MainActivity's. */
    private const val CONNECTION_ID = 6

    interface Listener {
        fun onState(state: BaseService.State, profileName: String?)
        fun onRates(tx: Long, rx: Long)
        fun onTotals(tx: Long, rx: Long)
    }

    private val listeners = mutableListOf<Listener>()
    private var attached = false

    @Volatile
    private var startedAt = 0L
    @Volatile
    private var uploadTotal = 0L
    @Volatile
    private var downloadTotal = 0L

    private val connection = SagerConnection(CONNECTION_ID, true)

    private val callback = object : SagerConnection.Callback {
        override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {
            if (state == BaseService.State.Connected && startedAt == 0L) startedAt = System.currentTimeMillis()
            if (state == BaseService.State.Stopped) startedAt = 0L
            for (listener in listeners) listener.onState(state, profileName)
        }

        override fun cbSpeedUpdate(stats: SpeedDisplayData) {
            uploadTotal = stats.txTotal
            downloadTotal = stats.rxTotal
            for (listener in listeners) {
                listener.onRates(stats.txRateProxy, stats.rxRateProxy)
                listener.onTotals(stats.txTotal, stats.rxTotal)
            }
        }

        override fun onServiceConnected(service: ISagerNetService) {
            for (listener in listeners) listener.onState(DataStore.serviceState, null)
        }

        override fun onServiceDisconnected() {
            for (listener in listeners) listener.onState(BaseService.State.Idle, null)
        }

        override fun onBinderDied() {
            for (listener in listeners) listener.onState(BaseService.State.Idle, null)
        }
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun attach(context: Context) {
        if (attached) return
        attached = true
        connection.connect(context, callback)
    }

    fun detach(context: Context) {
        if (!attached) return
        attached = false
        connection.disconnect(context)
    }

    /** The service class depends on DataStore.serviceMode, so a mode change needs a rebind. */
    fun rebind(context: Context) {
        if (!attached) return
        attached = false
        connection.disconnect(context)
        attached = true
        connection.connect(context, callback)
    }

    private val service get() = connection.service

    val running: Boolean get() = DataStore.serviceState.started

    fun start() {
        if (running) return
        SagerNet.startService()
    }

    fun stop() {
        if (!running) return
        SagerNet.stopService()
    }

    fun reload(restart: Boolean) {
        SagerNet.reloadService()
        if (!restart) return
        // A full restart of the service, as `service.reload {"restart":true}` expects.
        stop()
        start()
    }

    /** Milliseconds of a URL test of the running connection; -2 when it cannot be probed. */
    fun urlTest(): Int = runCatching { service?.urlTest() ?: -2 }.getOrDefault(-2)

    fun resetTraffic(profileIds: LongArray) {
        runCatching { service?.resetTraffic(profileIds) }
    }

    /** Dashboard wire: field names are fixed by the Flutter side. */
    fun dashboard(): Map<String, Any?> {
        val state = DataStore.serviceState
        val status = when (state) {
            BaseService.State.Connected -> "Started"
            BaseService.State.Connecting -> "Starting"
            BaseService.State.Stopping -> "Stopping"
            else -> "Stopped"
        }
        val vpnMode = DataStore.serviceMode == Key.MODE_VPN
        return mapOf(
            "status" to status,
            "selectedProfileId" to DataStore.selectedProxy,
            "startedAt" to startedAt,
            "memory" to 0L,
            "goroutines" to 0,
            "uploadTotal" to uploadTotal,
            "downloadTotal" to downloadTotal,
            "trafficAvailable" to DataStore.enableStats,
            "connections" to 0,
            // Throne routes through route profiles in the UI, there is no rule/global/direct switch.
            "modes" to emptyList<String>(),
            "mode" to "",
            "systemProxyAvailable" to (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vpnMode),
            "systemProxy" to DataStore.appendHttpProxy,
            "remote" to null,
            "remoteConnected" to false,
            "deprecated" to emptyList<Map<String, Any?>>(),
        )
    }

    fun version(): String = BuildConfig.VERSION_NAME
}
