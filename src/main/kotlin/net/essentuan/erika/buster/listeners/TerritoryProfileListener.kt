package net.essentuan.erika.buster.listeners

import com.busted_moments.buster.api.Territory
import com.busted_moments.buster.protocol.serverbound.ServerboundTerritoryProfileUpdatePacket
import com.busted_moments.buster.types.guilds.TerritoryProfile
import net.essentuan.erika.buster.BusterService.Constants.trustedCutoff
import net.essentuan.erika.buster.Listener
import net.essentuan.erika.buster.Socket
import net.essentuan.erika.observers.player.worlds.WorldList
import net.essentuan.erika.observers.territories.TerritoryList
import net.essentuan.esl.collections.maps.expireAfter
import net.essentuan.esl.other.lock
import net.essentuan.esl.scheduling.annotations.Every
import net.essentuan.esl.scheduling.api.schedule
import net.essentuan.esl.time.duration.minutes
import net.essentuan.esl.time.duration.seconds
import java.util.UUID

private const val MAX_RESOURCE = 149_760
private const val MAX_EMERALDS = 374_400

private val worlds = mutableMapOf<String, Submission>().expireAfter { 9.minutes }

@Every(seconds = 10.0)
private fun cleanse() {
    worlds.lock { cleanse() }
}

@Listener
private suspend fun Socket.on(packet: ServerboundTerritoryProfileUpdatePacket) {
    if (!account.isTrusted() || packet.profiles.keys != TerritoryList.keys || packet.world !in WorldList)
        return

    for ((_, profile) in packet.profiles) {
        for ((resource, storage) in profile.resources) {
            if (storage.production < 0)
                return

            when (resource) {
                Territory.Resource.EMERALDS -> if (storage.production > MAX_EMERALDS) return
                else -> if (storage.production > MAX_RESOURCE) return
            }
        }
    }

    worlds.lock { computeIfAbsent(packet.world) { Submission(it, packet.profiles) } }.submit(uuid, packet.profiles)
}

class Submission(
    val world: String,
    val profiles: Map<String, TerritoryProfile>
) {
    val by: MutableSet<UUID> = mutableSetOf()

    init {
        schedule {
            if (by.size < trustedCutoff)
                worlds.lock { remove(world) }
        } after 10.seconds
    }

    fun submit(uuid: UUID, profiles: Map<String, TerritoryProfile>) {
        lock {
            if (by.size >= trustedCutoff || profiles != this.profiles)
                return

            by += uuid
        }

        if (by.size >= trustedCutoff)
            TerritoryList.submit(this)
    }
}