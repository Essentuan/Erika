package net.essentuan.erika.buster.listeners

import com.busted_moments.buster.api.Guild
import com.busted_moments.buster.api.Profile
import com.busted_moments.buster.protocol.serverbound.ContentModifier
import com.busted_moments.buster.protocol.serverbound.ContentStage
import com.busted_moments.buster.protocol.serverbound.ServerboundContentCompletionPacket
import dev.kord.rest.builder.message.EmbedBuilder
import net.essentuan.erika.buster.Listener
import net.essentuan.erika.buster.Socket
import net.essentuan.erika.buster.listeners.GuildListener.guild
import net.essentuan.erika.db.struct.guild.GuildModel
import net.essentuan.erika.db.struct.guild.search.invoke
import net.essentuan.erika.db.struct.player.ProfileModel
import net.essentuan.erika.db.struct.player.search.invoke
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.framework.db.`object`.BsonModel
import net.essentuan.erika.framework.db.`object`.types.Singleton
import net.essentuan.erika.kord.tracks.lanes.IcoEventLane
import net.essentuan.esl.collections.enumMapOf
import net.essentuan.esl.color.Color
import net.essentuan.esl.get
import net.essentuan.esl.isEmpty
import net.essentuan.esl.iteration.extensions.iterate
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.annotations.Ignored
import net.essentuan.esl.orElseThrow
import net.essentuan.esl.orNull
import net.essentuan.esl.rx.filterNotNull
import net.essentuan.esl.rx.first
import net.essentuan.esl.rx.map
import net.essentuan.esl.rx.toList
import net.essentuan.esl.time.TimeUnit
import net.essentuan.esl.time.duration.Duration
import net.essentuan.esl.time.duration.minutes
import net.essentuan.esl.time.extensions.timeSince
import org.reactivestreams.Publisher
import java.lang.IllegalArgumentException
import java.math.BigInteger
import java.util.Date
import java.util.UUID
import kotlin.math.floor

object IcoEvent : Singleton(), Iterable<Pair<UUID, Raid.Store>> {
    @Ignored
    private val LOGGER by Logging

    @Ignored
    val COLOR: Color = Color(132, 0, 255)

    private var completions = mutableMapOf<UUID, Raid.Store>()

    @Ignored
    private var idiotCo: Guild? = null

    private suspend fun ICo(): Guild {
        synchronized(this) {
            if (idiotCo != null)
                return idiotCo!!
        }

        val result = Guild("Idiot Co").first().second

        if (result.isEmpty()) {
            LOGGER.error("COULD NOT FIND IDIOT CO")

            throw IllegalStateException("Could not find ICo???")
        }

        synchronized(this) {
            if (idiotCo == null)
                idiotCo = result.get()
        }

        return idiotCo!!
    }

    suspend fun get(uuid: UUID): Raid.Store? {
        val result = synchronized(completions) { completions[uuid] }

        return when {
            result != null -> result
            uuid !in ICo() -> null

            else -> synchronized(this) { completions.computeIfAbsent(uuid) { Raid.Store() } }
        }
    }


    @Listener
    private suspend fun Socket.on(packet: ServerboundContentCompletionPacket) {
        LOGGER.info(
            "${packet.name} was completed by ${
                buildString {
                    packet.party iterate {
                        if (isNotEmpty()) {
                            append(", ")

                            if (!hasNext())
                                append("and ")
                        }

                        append(it.name)
                    }
                }
            }!")

        val guild = ICo()

        if (uuid !in guild) {
            LOGGER.info("${account.profile.name} is not in ICo!")
            return
        }

        val type = try {
            Raid.Type.valueOf(packet.id)
        } catch (ex: IllegalArgumentException) {
            null
        }

        if (type == null) {
            LOGGER.info("Unknown raid ${packet.id}")
            return
        }

        if (ContentModifier.GUILD_RAID !in packet.modifiers) {
            LOGGER.info("This run was not a guild raid!")
            return
        }

        val party = Profile {
            for (member in packet.party) {
                if (member.hasUUID)
                    +member.uuid
                else
                    +member.name
            }
        }.map { it.second.orNull() }.filterNotNull().toList()

        if (party.size != 4) {
            LOGGER.info("${packet.party.joinToString { it.name }} is not a party of 4!")
            return
        }

        for (member in party) {
            if (member !in guild) {
                LOGGER.info("${member.name} is not a member of ICo!")
                return
            }
        }

        LOGGER.info("Confirmed ${packet.name} was for ICo!")

        val raid = Raid(packet, type, party)

        for (member in party)
            if (!get(member.uuid)!!.process(raid))
                return

        IcoEventLane.Completion(raid).post()
    }

    override fun iterator(): Iterator<Pair<UUID, Raid.Store>> =
        synchronized(completions) { completions.toList() }.iterator()
}

fun EmbedBuilder.room(room: Raid.Room) {
    field {
        name = room.name
        value = room.duration.print(TimeUnit.SECONDS)

        inline = true
    }
}

data class Raid(
    val type: Type,
    val start: Date,
    val rooms: List<Room>,
    val end: Date,
    val partyMembers: List<UUID>
) : Json.Model {
    constructor(packet: ServerboundContentCompletionPacket, type: Raid.Type, party: List<Profile>) : this(
        type,
        packet.start,
        packet.stages.map(::Room),
        packet.end,
        party.map { it.uuid }
    )

    val duration: Duration
        get() = Duration(start, end)

    val party: Publisher<Profile>
        get() = Profile { +partyMembers }.map { it.second.get() }

    data class Room(
        val name: String,
        val duration: Duration
    ) : Json.Model {
        constructor(stage: ContentStage) : this(
            stage.name,
            Duration(stage.start, stage.end)
        )
    }

    enum class Type(val display: String, val short: String, val icon: String) {
        NEST_OF_THE_GROOTSLANGS(
            "Nest of The Grootslangs",
            "NOTG",
            "https://cdn.wynncraft.com/nextgen/leaderboard/icons/grootslang.webp"
        ),
        THE_NEXUS_OF_LIGHT(
            "Orphion's Nexus of Light",
            "NOL",
            "https://cdn.wynncraft.com/nextgen/leaderboard/icons/orphion.webp"
        ),
        THE_CANYON_COLOSSUS(
            "The Canyon Colossus",
            "TCC",
            "https://cdn.wynncraft.com/nextgen/leaderboard/icons/colossus.webp"
        ),
        THE_NAMELESS_ANOMALY(
            "The Nameless Anomaly",
            "TNA",
            "https://cdn.wynncraft.com/nextgen/leaderboard/icons/nameless.webp"
        );
    }

    data class Store(
        private val pbs: MutableMap<Type, Raid>,
        private val counts: MutableMap<Type, Int>,
        var total: Int,
        private var lastCompletion: Date,
    ) : Json.Model {
        constructor() : this(enumMapOf(), enumMapOf(), 0, Date(0))

        val rewards: Double
            get() = total * 0.25 + (floor(total / 10.0) * 5.0)

        fun pb(raid: Type): Raid? =
            pbs[raid]

        fun completions(raid: Type): Int =
            counts[raid] ?: 0

        fun increment(raid: Type, amount: Int): Int {
            total+= amount
            return counts.compute(raid) { _, count -> if (count == null) amount else count + amount }!!
        }

        fun process(raid: Raid): Boolean {
            synchronized(this) {
                if (lastCompletion.timeSince() < 1.minutes)
                    return false

                lastCompletion = Date()
            }

            increment(raid.type, 1)

            pbs.compute(raid.type) { _, pb ->
                if (pb == null || raid.duration < pb.duration)
                    raid
                else
                    pb
            }

            return true
        }
    }
}