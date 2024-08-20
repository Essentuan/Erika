package net.essentuan.erika.kord.tracks

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.TextChannel
import net.essentuan.erika.framework.db.`object`.types.Singleton
import net.essentuan.esl.other.lock

object TrackManager : Singleton() {
    private var tracks: MutableMap<Snowflake, Track> = mutableMapOf()

    operator fun get(id: Snowflake): Track? =
        tracks[id]

    operator fun get(channel: TextChannel): Track? =
            tracks[channel.id]

    operator fun contains(id: Snowflake): Boolean =
        id in tracks

    operator fun contains(channel: TextChannel): Boolean =
        channel.id in tracks

    fun getOrCreate(channel: TextChannel, creator: User): Track =
        tracks.lock { computeIfAbsent(channel.id) { Track(channel.id, creator.id) } }

    fun clear(id: Snowflake) {
        tracks.lock{ remove(id) }?.close()
    }
}