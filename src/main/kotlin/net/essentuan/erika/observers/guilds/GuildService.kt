package net.essentuan.erika.observers.guilds

import com.busted_moments.buster.api.Guild
import com.busted_moments.buster.api.GuildType
import com.google.gson.stream.MalformedJsonException
import net.essentuan.erika.awaitReady
import net.essentuan.erika.db.struct.guild.GuildModel
import net.essentuan.erika.db.struct.guild.search.invoke
import net.essentuan.erika.fetch.wynncraft.guild.guild
import net.essentuan.erika.framework.Service
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.framework.db.commands.find
import net.essentuan.erika.framework.db.`object`.touched
import net.essentuan.erika.framework.db.`object`.types.Struct.Companion.enqueue
import net.essentuan.erika.inline
import net.essentuan.erika.observers.guilds.list.Guilds
import net.essentuan.erika.observers.guilds.list.isDeleted
import net.essentuan.esl.Rating
import net.essentuan.esl.collections.synchronized
import net.essentuan.esl.coroutines.delay
import net.essentuan.esl.fetch.fetch
import net.essentuan.esl.flatMap
import net.essentuan.esl.future.api.Future
import net.essentuan.esl.json.Json
import net.essentuan.esl.map
import net.essentuan.esl.model.annotations.Alias
import net.essentuan.esl.other.causedBy
import net.essentuan.esl.rx.discard
import net.essentuan.esl.rx.distinct
import net.essentuan.esl.rx.filter
import net.essentuan.esl.rx.findFirst
import net.essentuan.esl.rx.iterator
import net.essentuan.esl.rx.limit
import net.essentuan.esl.rx.plus
import net.essentuan.esl.rx.sortedBy
import net.essentuan.esl.scheduling.annotations.Every
import net.essentuan.esl.scheduling.annotations.Lifetime
import net.essentuan.esl.time.duration.minutes
import net.essentuan.esl.time.duration.seconds
import net.essentuan.esl.time.extensions.timeSince
import java.util.Date
import java.util.UUID

private const val MAX_GUILDS_PER_UPDATE = 300L

private val LOGGER by Logging

object GuildService : Service() {
    private val active = mutableMapOf<UUID, Future<Unit>>().synchronized()

    private suspend fun update(guild: Guild, priority: Rating) {
        if (guild.isDeleted || guild !is GuildModel)
            return

        val basic = fetch { guild(uuid = guild.uuid, priority = priority) }

        if (basic != null && guild.update(basic)) {
            guild.touched()
            guild.enqueue()
        }
    }

    fun enqueue(guild: GuildType, priority: Rating = Rating.NORMAL): Future<Unit> {
        return active.computeIfAbsent(guild.uuid) {
            inline {
                Guild(guild.uuid, update = false)
                    .findFirst()
                    .flatMap { (_, result) -> result }
                    .map { update(it, priority) }
            }
        }.also {
            it.then {
                active.remove(guild.uuid)
            }
        }
    }

    fun enqueue(guild: Guild, priority: Rating = Rating.NORMAL): Future<Unit> {
        return active.computeIfAbsent(guild.uuid) {
            inline {
                update(guild, priority)
            }
        }.also {
            it.then {
                active.remove(guild.uuid)
            }
        }
    }

    @Every(minutes = 15.0)
    @Lifetime(minutes = 15.0)
    private suspend fun update() {
        awaitReady()

        val guilds = (find {
            select from (GuildModel `as` GuildInfo::class)

            project {
                +GuildModel::uuid
                +"metadata.modified"
            }
        } + Guilds.asSequence().map {
            GuildInfo(it.uuid, Date(-1))
        })
            .distinct { it.uuid }
            .sortedBy { it.modified.time }
            .filter {
                it.modified.time == 1L || it.modified.timeSince() >= 15.minutes
            }
            .limit(MAX_GUILDS_PER_UPDATE)

        for (guild in guilds) {
            val start = Date()

            try {
                Guild(guild.uuid, priority = Rating.LOWEST).discard()
            } catch (ex: Exception) {
                val type = Guilds[guild.uuid]

                if (!ex.causedBy<MalformedJsonException>()) {
                    if (type == null)
                        LOGGER.info("Failed to update ${guild.uuid}!", ex)
                    else
                        LOGGER.info("Failed to update ${type.name} [${type.tag}]!", ex)
                }
            }

            val wait = 2.seconds - start.timeSince()
            if (wait > 0.seconds)
                delay(wait)
        }
    }
}

private class GuildInfo(
    override val uuid: UUID,
    @Alias(["metadata.modified"])
    val modified: Date
) : Json.Model, GuildType by Guilds[uuid]!!