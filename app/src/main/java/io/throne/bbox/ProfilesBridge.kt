package io.throne.bbox

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupRepo
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.group.SubscriptionQueue

/**
 * Throne keeps a subscription group tree plus single outbound records, where B-box keeps config
 * files. The mapping here is deliberate:
 *
 *  - a subscription group is the remote profile (`remote: true`, url, update interval);
 *  - the selected outbound ([DataStore.selectedProxy]) is what the dashboard shows as current.
 *
 * There is no config file behind a profile, so `profiles.config` / `profiles.write` operate on the
 * raw outbound JSON of the selected record, which is the same thing Throne edits natively.
 *
 * Everything touching Room is suspend and must be called off the main thread.
 */
object ProfilesBridge {

    /** Wire shape fixed by the Flutter side; extra keys are ignored there. */
    private fun ProxyGroup.toWire(members: Int): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "remote" to isSubscription,
        "url" to url,
        "updatedAt" to if (subLastUpdate > 0) subLastUpdate * 1000 else 0L,
        "autoUpdate" to !skipAutoUpdate,
        "interval" to subOptions.groupSubUpdateInterval.takeIf { minutes -> minutes >= 30 } ?: 0,
        "type" to if (isSubscription) "subscription" else "basic",
        "members" to members,
    )

    suspend fun list(): List<Map<String, Any?>> = GroupRepo.all().map { group ->
        group.toWire(ProfileManager.memberIds(group.id).size)
    }

    suspend fun select(id: Long) {
        if (id <= 0) return
        GroupRepo.setCurrent(id)
        val members = ProfileManager.memberIds(id)
        if (members.isNotEmpty()) DataStore.selectedProxy = members.first()
    }

    /** Creates or updates a subscription group and lets the queue fire the first fetch. */
    suspend fun save(id: Long?, name: String, url: String, autoUpdate: Boolean, interval: Int): Long {
        val group = id?.takeIf { value -> value > 0 }?.let { groupId -> GroupRepo.get(groupId) } ?: ProxyGroup()
        group.name = name
        group.url = url
        group.skipAutoUpdate = !autoUpdate
        if (interval >= 30) group.subOptions.groupSubUpdateInterval = interval
        val isNew = group.id == 0L
        if (isNew) GroupRepo.add(group) else GroupRepo.save(group)
        if (isNew && url.isNotBlank()) SubscriptionQueue.subscribeUrl(url, name, autoUpdate)
        return group.id
    }

    suspend fun delete(id: Long) {
        if (GroupRepo.delete(id)) return
        // Fall back to a single outbound record when the id belongs to one.
        val profile = ProfileManager.getProfile(id) ?: return
        ProfileManager.deleteProfile(profile.groupId, profile.id)
    }

    fun update(id: Long) {
        val group = GroupRepo.get(id) ?: return
        if (group.isSubscription) SubscriptionQueue.refreshGroup(id, false)
    }

    /** The mixed inbound is what replaces libbox's LAN proxy on the Intranet IP card. */
    fun lan(): Map<String, Any?> {
        val port = DataStore.inboundSocksPort
        val enabled = DataStore.allowLanAccess
        val running = ServiceBridge.running
        val addresses = localAddresses()
        val binding = mapOf(
            "lan" to true,
            "port" to port,
            "endpoints" to addresses.map { address -> "$address:$port" },
        )
        val bindings = listOf(binding)
        return mapOf(
            "addresses" to addresses,
            "bindings" to bindings,
            "activeBindings" to if (running && enabled) bindings else emptyList<Map<String, Any?>>(),
            "enabled" to enabled,
            "running" to running,
            "activeProfileId" to DataStore.selectedProxy,
            "profileId" to DataStore.selectedProxy,
            "revision" to REVISION,
            "canToggle" to true,
            "error" to null,
            "stamp" to System.currentTimeMillis(),
        )
    }

    fun setLan(enabled: Boolean, port: Int?): Map<String, Any?> {
        if (port != null && port in 1..65535) DataStore.inboundSocksPort = port
        DataStore.inboundAddress = if (enabled) LAN_ADDRESS else LOOPBACK_ADDRESS
        return lan()
    }

    const val REVISION = "throne-inbound"
    private const val LAN_ADDRESS = "::"
    private const val LOOPBACK_ADDRESS = "127.0.0.1"
}
