package net.essentuan.erika.features.content

import com.busted_moments.buster.api.Guild
import com.busted_moments.buster.api.Party
import com.busted_moments.buster.api.Profile
import com.busted_moments.buster.protocol.serverbound.ContentModifier as Modifier
import com.busted_moments.buster.protocol.serverbound.ServerboundContentCompletionPacket
import net.essentuan.erika.buster.Listener
import net.essentuan.erika.buster.Socket
import net.essentuan.erika.db.struct.guild.member.search.invoke
import net.essentuan.erika.db.struct.guild.search.invoke
import net.essentuan.erika.db.struct.player.search.invoke
import net.essentuan.erika.features.content.events.ContentEvent
import net.essentuan.erika.features.content.modifiers.ContentModifier
import net.essentuan.erika.features.content.modifiers.EmptyContentModifier
import net.essentuan.erika.features.content.modifiers.GuildRaidModifier
import net.essentuan.erika.features.content.type.ContentType
import net.essentuan.erika.features.content.type.Raid
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.observers.guilds.list.Guilds
import net.essentuan.esl.collections.maps.TempMap
import net.essentuan.esl.collections.maps.expireAfter
import net.essentuan.esl.iteration.extensions.iterate
import net.essentuan.esl.orNull
import net.essentuan.esl.orThrow
import net.essentuan.esl.rx.*
import net.essentuan.esl.scheduling.annotations.Every
import net.essentuan.esl.time.TimeUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.essentuan.esl.time.duration.Duration

private val LOGGER by Logging

object ContentCompletionListener {
    /** Players that have recently completed content. */
    private val recentlyCompleted = ConcurrentHashMap<String, Unit>()
        .expireAfter(Duration(30, TimeUnit.SECONDS))

    @Every(minutes = 1.0)
    private fun clearRecentlyCompleted() =
        recentlyCompleted.cleanse()

    @Listener
    private suspend fun Socket.on(packet: ServerboundContentCompletionPacket) {
        val type: ContentType = ContentType.valueOf(packet.id) ?: run<Nothing> {
            LOGGER.warn("Recieved content completion of unknown type (${packet.id})")
            return
        }


        // Basic validation
        if (packet.isMalformed) {
            logMalformed(packet)
            return
        }
        if (packet.party in recentlyCompleted) {
            LOGGER.info("Duplicate completion")
            return
        }

        val party = Guild.Member(create = true) {
            for (member in packet.party) {
                if (member.hasUUID)
                    +member.uuid
                else
                    +member.name
            }
        }.map { it.second.orNull() }.filterNotNull().toList()

        // Party validation
        if (party.size != packet.party.size) {
            val names = party.associateBy { it.name.lowercase() }

            LOGGER.warn(
                "Could not find " +
                        "${packet.party.count { it.name.lowercase() !in names }} in content party"
            )
            return
        }
        if (type is Raid && party.size != 4) {
            LOGGER.warn("Recieved content completion for raid with party of ${party.size}")
            return
        }

        var modifier: ContentModifier = EmptyContentModifier

        //Modifier verification
        if (Modifier.GUILD_RAID in packet.modifiers) {
            if (type !is Raid) {
                LOGGER.warn("Recieved content completion with modifier GUILD_RAID outside of raid")
                return
            }

            val guildType = party.firstNotNullOfOrNull { it.guild }

            if (guildType == null) {
                LOGGER.info("Recieved content completion for guild raid with no guild")
                return
            }

            val guild = Guild(guildType.uuid)
                .first()
                .second
                .orThrow()

            if (party.any { it.uuid !in guild }) {
                LOGGER.warn("Recieved content completion for guild raid while its players were not in the same guild (${guild.name} [${guild.tag}])")
                return
            }

            modifier = modifier then GuildRaidModifier(guildType.uuid)
        }

        ContentRecord(
            type,
            packet.start,
            packet.end,
            buildSet { for (e in party) add(e.uuid) },
            packet.stages,
            modifier
        ).apply {
            save()
            LOGGER.info(
                "${type.displayName} was completed by ${
                buildString {
                    party iterate {
                        if (isNotEmpty()) {
                            append(", ")

                            if (!hasNext())
                                append("and ")
                        }

                        append(it.name)
                    }
                }
            }")

            ContentEvent.Completion(this).post()
        }
    }

    private operator fun TempMap<String, Unit>.contains(party: Party): Boolean {
        var contained = false

        for (member in party)
            if (recentlyCompleted.putIfAbsent(member.name, Unit) != null)
                contained = true

        return contained
    }
}

private val ServerboundContentCompletionPacket.isMalformed: Boolean
    get() = party.any { it.name.isBlank() }

private fun logMalformed(packet: ServerboundContentCompletionPacket) {
    LOGGER.warn(
        "Recieved malformed completion " +
                "for ${packet.id}"
    )
}

