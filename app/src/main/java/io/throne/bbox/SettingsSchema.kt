package io.throne.bbox

import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.database.DataStore
import kotlin.math.absoluteValue

/**
 * What has to happen after a write.
 *
 * Unlike the native Throne screens this runs head-less, so the three native levels are split here:
 * None          — takes effect immediately.
 * Reload        — the running core rebuilds its config (`Action.RELOAD`).
 * AppRestart    — only the process restart picks it up (log level, log buffer).
 */
enum class Apply { None, Reload, AppRestart }

class Entry(
    val bridgeKey: String,
    val apply: Apply = Apply.None,
    /** Restartable while running through `settings.set {"restart":true}`, like the three automatic ones upstream. */
    val autoRestartable: Boolean = false,
    val get: () -> Any?,
    val set: (Any?) -> Unit,
)

private fun bool(
    bridgeKey: String,
    apply: Apply = Apply.None,
    autoRestartable: Boolean = false,
    get: () -> Boolean,
    set: (Boolean) -> Unit,
) = Entry(bridgeKey, apply, autoRestartable, { get() }, { set(it == true) })

private fun intEntry(
    bridgeKey: String,
    apply: Apply = Apply.None,
    get: () -> Int,
    set: (Int) -> Unit,
) = Entry(bridgeKey, apply, false, { get() }, {
    val value = when (it) {
        is Int -> it
        is Long -> it.toInt()
        is Double -> it.toInt()
        is String -> it.toIntOrNull() ?: return@Entry
        else -> return@Entry
    }
    set(value)
})

private fun textEntry(
    bridgeKey: String,
    apply: Apply = Apply.None,
    get: () -> String,
    set: (String) -> Unit,
) = Entry(bridgeKey, apply, false, { get() }, { set(it?.toString() ?: "") })

/** signed-coded minutes: negative = off. Exposed as an enabled flag plus a positive minutes value. */
private val Int.hoursPair get() = Pair(this > 0, this.absoluteValue)

object Settings {

    /** LAN / loopback are the two states of `inbound_address`; there is no separate boolean upstream. */
    private const val LAN_ADDRESS = "::"
    private const val LOOPBACK_ADDRESS = "127.0.0.1"

    val entries: List<Entry> = listOf(

        // --- General / service -------------------------------------------------------------------
        Entry(
            bridgeKey = "serviceMode",
            apply = Apply.Reload,
            autoRestartable = true,
            get = { DataStore.serviceMode },
            set = { DataStore.serviceMode = it?.toString() ?: Key.MODE_VPN },
        ),
        bool("rememberEnable", get = { DataStore.rememberEnable }, set = { DataStore.rememberEnable = it }),
        bool("hideFromRecentApps", get = { DataStore.hideFromRecentApps }, set = { DataStore.hideFromRecentApps = it }),
        bool("meteredNetwork", Apply.Reload, true, { DataStore.meteredNetwork }, { DataStore.meteredNetwork = it }),
        bool(
            "networkChangeResetConnections",
            get = { DataStore.networkChangeResetConnections },
            set = { DataStore.networkChangeResetConnections = it },
        ),
        bool("wakeResetConnections", get = { DataStore.wakeResetConnections }, set = { DataStore.wakeResetConnections = it }),
        bool("skipDeleteConfirmation", get = { DataStore.skipDeleteConfirmation }, set = { DataStore.skipDeleteConfirmation = it }),
        bool("showDirectSpeed", get = { DataStore.showDirectSpeed }, set = { DataStore.showDirectSpeed = it }),
        bool("alwaysShowAddress", get = { DataStore.alwaysShowAddress }, set = { DataStore.alwaysShowAddress = it }),
        bool(
            "showGroupInNotification",
            get = { DataStore.showGroupInNotification },
            set = { DataStore.showGroupInNotification = it },
        ),
        bool("appendHttpProxy", Apply.Reload, true, { DataStore.appendHttpProxy }, { DataStore.appendHttpProxy = it }),
        textEntry(
            "httpProxyBypass",
            Apply.Reload,
            { DataStore.httpProxyBypass },
            { DataStore.httpProxyBypass = it },
        ),
        bool("allowBetaUpdate", get = { DataStore.allowBetaUpdate }, set = { DataStore.allowBetaUpdate = it }),

        // --- Inbound ------------------------------------------------------------------------------
        intEntry("inboundSocksPort", Apply.Reload, { DataStore.inboundSocksPort }, { DataStore.inboundSocksPort = it }),
        bool("disableMixedInbound", Apply.Reload, true, { DataStore.disableMixedInbound }, { DataStore.disableMixedInbound = it }),
        bool("randomInboundPort", Apply.Reload, { DataStore.randomInboundPort }, { DataStore.randomInboundPort = it }),
        bool("inboundAuth", Apply.Reload, true, { DataStore.inboundAuth }, { DataStore.inboundAuth = it }),
        textEntry("inboundUser", Apply.Reload, { DataStore.inboundUser }, { DataStore.inboundUser = it }),
        textEntry("inboundPass", Apply.Reload, { DataStore.inboundPass }, { DataStore.inboundPass = it }),
        intEntry("coreDnsInPort", Apply.Reload, { DataStore.coreDnsInPort }, { DataStore.coreDnsInPort = it }),
        Entry(
            bridgeKey = "allowLan",
            apply = Apply.Reload,
            autoRestartable = true,
            get = { DataStore.allowLanAccess },
            set = { DataStore.inboundAddress = if (it == true) LAN_ADDRESS else LOOPBACK_ADDRESS },
        ),

        // --- TUN ----------------------------------------------------------------------------------
        intEntry("vpnMtu", Apply.Reload, { DataStore.vpnMtu }, { DataStore.vpnMtu = it }),
        bool("vpnIpv6", Apply.Reload, true, { DataStore.vpnIpv6 }, { DataStore.vpnIpv6 = it }),
        textEntry("vpnTunIpv4Cidr", Apply.Reload, { DataStore.vpnTunIpv4Cidr }, { DataStore.vpnTunIpv4Cidr = it }),
        textEntry("vpnTunIpv6Cidr", Apply.Reload, { DataStore.vpnTunIpv6Cidr }, { DataStore.vpnTunIpv6Cidr = it }),
        bool(
            "disablePrivateRangeBypass",
            Apply.Reload,
            true,
            { DataStore.disablePrivateRangeBypass },
            { DataStore.disablePrivateRangeBypass = it },
        ),
        bool("enableTunRouting", Apply.Reload, { DataStore.enableTunRouting }, { DataStore.enableTunRouting = it }),

        // --- Per-app proxy ------------------------------------------------------------------------

        // --- Routing ------------------------------------------------------------------------------
        textEntry(
            "outboundDomainStrategy",
            Apply.Reload,
            { DataStore.outboundDomainStrategy },
            { DataStore.outboundDomainStrategy = it },
        ),
        textEntry("domainStrategy", Apply.Reload, { DataStore.domainStrategy }, { DataStore.domainStrategy = it }),
        intEntry("rulesetMirror", Apply.Reload, { DataStore.rulesetMirror }, { DataStore.rulesetMirror = it }),
        bool("adblockEnable", Apply.Reload, true, { DataStore.adblockEnable }, { DataStore.adblockEnable = it }),
        bool("enableWarp", Apply.Reload, { DataStore.enableWarp }, { DataStore.enableWarp = it }),

        // --- DNS ----------------------------------------------------------------------------------
        textEntry("remoteDns", Apply.Reload, { DataStore.remoteDns }, { DataStore.remoteDns = it }),
        bool("remoteDnsDisableIpv6", Apply.Reload, { DataStore.remoteDnsDisableIpv6 }, { DataStore.remoteDnsDisableIpv6 = it }),
        textEntry("directDns", Apply.Reload, { DataStore.directDns }, { DataStore.directDns = it }),
        bool("directDnsDisableIpv6", Apply.Reload, { DataStore.directDnsDisableIpv6 }, { DataStore.directDnsDisableIpv6 = it }),
        textEntry("dnsFinalOut", Apply.Reload, { DataStore.dnsFinalOut }, { DataStore.dnsFinalOut = it }),
        bool("enableDnsRouting", Apply.Reload, { DataStore.enableDnsRouting }, { DataStore.enableDnsRouting = it }),
        bool("fakeDns", Apply.Reload, true, { DataStore.fakedns }, { DataStore.fakedns = it }),
        bool("fakeipDisableIpv6", Apply.Reload, { DataStore.fakeipDisableIpv6 }, { DataStore.fakeipDisableIpv6 = it }),
        bool("dnsUseHosts", Apply.Reload, { DataStore.dnsUseHosts }, { DataStore.dnsUseHosts = it }),
        bool("dnsPredefinedEnable", Apply.Reload, { DataStore.dnsPredefinedEnable }, { DataStore.dnsPredefinedEnable = it }),
        textEntry(
            "coreBoxUnderlyingDns",
            Apply.Reload,
            { DataStore.coreBoxUnderlyingDns },
            { DataStore.coreBoxUnderlyingDns = it },
        ),
        bool("dnsDisableCache", Apply.Reload, { DataStore.dnsDisableCache }, { DataStore.dnsDisableCache = it }),
        bool("dnsDisableExpire", Apply.Reload, { DataStore.dnsDisableExpire }, { DataStore.dnsDisableExpire = it }),
        intEntry("dnsCacheCapacity", Apply.Reload, { DataStore.dnsCacheCapacity }, { DataStore.dnsCacheCapacity = it }),

        // --- Core ---------------------------------------------------------------------------------
        textEntry("logLevel", Apply.AppRestart, { DataStore.logLevel }, { DataStore.logLevel = it }),
        bool("enableStats", Apply.Reload, true, { DataStore.enableStats }, { DataStore.enableStats = it }),
        bool("disableTrafficStats", Apply.Reload, true, { DataStore.disableTrafficStats }, { DataStore.disableTrafficStats = it }),
        intEntry("logBufSize", Apply.AppRestart, { DataStore.logBufSize }, { DataStore.logBufSize = it }),
        bool("skipCert", Apply.Reload, { DataStore.skipCert }, { DataStore.skipCert = it }),
        bool("enableNtp", Apply.Reload, true, { DataStore.enableNtp }, { DataStore.enableNtp = it }),
        textEntry("ntpServerAddress", Apply.Reload, { DataStore.ntpServerAddress }, { DataStore.ntpServerAddress = it }),
        intEntry("ntpServerPort", Apply.Reload, { DataStore.ntpServerPort }, { DataStore.ntpServerPort = it }),

        // Clash API / sing-box API: one signed port each, split into a flag and a port for the UI.
        Entry(
            bridgeKey = "clashApiEnabled",
            apply = Apply.Reload,
            get = { DataStore.clashApiEnabled },
            set = {
                val port = DataStore.coreBoxClashApi.absoluteValue.takeIf { value -> value > 0 } ?: 9090
                DataStore.coreBoxClashApi = if (it == true) port else -port
            },
        ),
        intEntry("clashApiPort", Apply.Reload, {
            DataStore.coreBoxClashApi.absoluteValue.takeIf { value -> value > 0 } ?: 9090
        }, { port -> DataStore.coreBoxClashApi = if (DataStore.clashApiEnabled) port else -port }),
        textEntry(
            "clashApiListenAddr",
            Apply.Reload,
            { DataStore.coreBoxClashListenAddr },
            { DataStore.coreBoxClashListenAddr = it },
        ),
        textEntry(
            "clashApiSecret",
            Apply.Reload,
            { DataStore.coreBoxClashApiSecret },
            { DataStore.coreBoxClashApiSecret = it },
        ),
        Entry(
            bridgeKey = "boxApiEnabled",
            apply = Apply.Reload,
            get = { DataStore.apiDashboardEnabled },
            set = {
                val port = DataStore.coreBoxApiPort.absoluteValue.takeIf { value -> value > 0 } ?: 9091
                DataStore.coreBoxApiPort = if (it == true) port else -port
            },
        ),
        intEntry("boxApiPort", Apply.Reload, {
            DataStore.coreBoxApiPort.absoluteValue.takeIf { value -> value > 0 } ?: 9091
        }, { port -> DataStore.coreBoxApiPort = if (DataStore.apiDashboardEnabled) port else -port }),

        // --- Testing ------------------------------------------------------------------------------
        textEntry("testUrl", { DataStore.testUrl }, { DataStore.testUrl = it }),
        intEntry("urlTestTimeoutMs", { DataStore.urlTestTimeoutMs }, { DataStore.urlTestTimeoutMs = it }),
        intEntry("testConcurrent", { DataStore.testConcurrent }, { DataStore.testConcurrent = it }),
        textEntry("directTestUrl", { DataStore.directTestUrl }, { DataStore.directTestUrl = it }),
        bool("exitCountryLookup", { DataStore.exitCountryLookup }, { DataStore.exitCountryLookup = it }),
        textEntry("exitLookupProvider", { DataStore.exitLookupProvider }, { DataStore.exitLookupProvider = it }),
        intEntry("speedTestMode", { DataStore.speedTestMode }, { DataStore.speedTestMode = it }),
        intEntry("speedTestTimeoutMs", { DataStore.speedTestTimeoutMs }, { DataStore.speedTestTimeoutMs = it }),

        // --- Subscriptions ------------------------------------------------------------------------
        textEntry("userAgent", { DataStore.userAgent2 }, { DataStore.userAgent2 = it }),
        bool("netUseProxy", { DataStore.netUseProxy }, { DataStore.netUseProxy = it }),
        bool("netInsecure", { DataStore.netInsecure }, { DataStore.netInsecure = it }),
        bool("subClear", { DataStore.subClear }, { DataStore.subClear = it }),
        bool("subShowChangePopup", { DataStore.subShowChangePopup }, { DataStore.subShowChangePopup = it }),
        bool("subSendHwid", { DataStore.subSendHwid }, { DataStore.subSendHwid = it }),
        intEntry("subTlsVersion", { DataStore.subTlsVersion }, { DataStore.subTlsVersion = it }),
        intEntry("subHttpVersion", { DataStore.subHttpVersion }, { DataStore.subHttpVersion = it }),
        bool("subRespectServerInterval", { DataStore.subRespectServerInterval }, { DataStore.subRespectServerInterval = it }),
        bool("allowStoppingActiveProfile", { DataStore.allowStoppingActiveProfile }, { DataStore.allowStoppingActiveProfile = it }),
        bool("subAutoUpdateEnabled", get = { DataStore.subAutoUpdate.hoursPair.first }, set = { enabled ->
            DataStore.subAutoUpdate = if (enabled) DataStore.subAutoUpdate.hoursPair.second.coerceAtLeast(30) else -1
        }),
        intEntry("subAutoUpdateMinutes", get = { DataStore.subAutoUpdate.hoursPair.second.takeIf { it >= 30 } ?: 30 }, set = { minutes ->
            DataStore.subAutoUpdate = if (DataStore.subAutoUpdate > 0) minutes.coerceIn(30, 525600) else -minutes.coerceIn(30, 525600)
        }),
        bool("routeAutoUpdateEnabled", get = { DataStore.routeAutoUpdate.hoursPair.first }, set = { enabled ->
            DataStore.routeAutoUpdate = if (enabled) DataStore.routeAutoUpdate.hoursPair.second.coerceAtLeast(30) else -1
        }),
        intEntry("routeAutoUpdateMinutes", get = { DataStore.routeAutoUpdate.hoursPair.second.takeIf { it >= 30 } ?: 1440 }, set = { minutes ->
            DataStore.routeAutoUpdate = if (DataStore.routeAutoUpdate > 0) minutes.coerceIn(30, 525600) else -minutes.coerceIn(30, 525600)
        }),

        // --- Legacy B-box keys, kept so the existing screens keep working without duplication ----
        bool("perAppEnabled", Apply.Reload, true, { DataStore.proxyApps }, { DataStore.proxyApps = it }),
        bool("perAppManaged", get = { false }, set = { /* Throne has no managed list */ }),
        Entry(bridgeKey = "perAppManagedList", get = { emptyList<String>() }, set = { /* read only */ }),
        Entry(
            bridgeKey = "perAppMode",
            apply = Apply.Reload,
            get = { if (DataStore.bypass) 1 else 2 },
            set = { DataStore.bypass = (it as? Int ?: it?.toString()?.toIntOrNull() ?: 1) == 1 },
        ),
        Entry(
            bridgeKey = "perAppList",
            apply = Apply.Reload,
            get = { DataStore.individual.split("\n").filter { line -> line.isNotBlank() } },
            set = {
                val packages = (it as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                DataStore.individual = packages.joinToString("\n")
            },
        ),
        // The upstream switch is inverted: Throne ships "bypass private ranges" on by default.
        bool(
            "allowBypass",
            Apply.Reload,
            true,
            { !DataStore.disablePrivateRangeBypass },
            { DataStore.disablePrivateRangeBypass = !it },
        ),
        Entry(bridgeKey = "queryMode", get = { "ROOT" }, set = { /* PackageManager query only */ }),
        Entry(bridgeKey = "batteryExempt", get = { batteryUnrestricted() }, set = { /* read only */ }),
    )

    private val index: Map<String, Entry> by lazy(LazyThreadSafetyMode.NONE) {
        entries.associateBy { entry -> entry.bridgeKey }
    }

    fun has(key: String) = index.containsKey(key)

    fun get(key: String) = index[key]?.get?.invoke()

    /** Returns the follow-up action; null when the key is unknown. */
    fun set(key: String, value: Any?): Apply? {
        val entry = index[key] ?: return null
        entry.set(value)
        return entry.apply
    }

    fun read(): Map<String, Any?> = entries.associate { entry -> entry.bridgeKey to entry.get() }
}
