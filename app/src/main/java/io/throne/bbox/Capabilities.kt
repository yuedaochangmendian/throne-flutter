package io.throne.bbox

import android.os.Build
import io.nekohasekai.sagernet.BuildConfig

/**
 * The single source of truth for what the Flutter UI may show.
 *
 * Every false that is missing a counterpart in ThroneCore results in a hidden entry rather than a
 * page that is permanently empty, and every true-Throne capability that the B-box UI never had
 * demands a new screen in the same sheet layout.
 */
object Capabilities {

    fun map(): Map<String, Any> = mapOf(

        // --- B-box only: nothing in Throne answers these ------------------------------------------
        "terminal" to false,       // local SSH client
        "capture" to false,        // requests page: libbox private HTTP capture/upstream
        "remote" to false,         // remote control sessions
        "reports" to false,        // crash / oom / power report managers
        "stun" to false,           // STUN page
        "quality" to false,        // network quality sampling page
        "endpoints" to false,      // tailscale / openvpn / openconnect / usbip
        "taildrop" to false,       // taildrop send & receive
        "memory" to false,         // runtime memory panel
        "proxyGroups" to false,    // Clash-mode selector groups; Throne keeps membership in the UI instead
        "clashModes" to false,     // rule / global / direct switch is not provided
        "perAppManaged" to false,  // no Shizuku-managed package list
        "rootQuery" to false,      // packages come from PackageManager only
        "dnsObject" to false,      // raw DNS object editor is intentionally kept out of the UI

        // --- Throne has it ------------------------------------------------------------------------
        "perApp" to true,          // per-app proxy through Key.PROXY_APPS / BYPASS_MODE / INDIVIDUAL
        "logs" to true,            // logcat page
        "groups" to true,          // subscription groups
        "routes" to true,          // route profiles, rule sets
        "backup" to true,          // .thrbackup + WebDAV
        "autoSelector" to true,    // auto selector engine
        "warp" to true,            // Cloudflare WARP / Masque
        "update" to BuildConfig.IN_APP_UPDATER,
        // Only VPN can hand the mixed inbound to apps as the system HTTP proxy, and only from API 29.
        "systemProxy" to (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q),
        // The mixed inbound replaces libbox's LAN proxy: Allow LAN plus the port instead.
        "lanProxy" to true,
    )
}
