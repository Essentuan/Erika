package net.essentuan.erika.db.struct.guild.member

import com.busted_moments.buster.api.Guild
import com.busted_moments.buster.api.GuildType
import net.essentuan.erika.framework.db.api.table.schema.Attribute
import net.essentuan.erika.framework.db.api.table.schema.Index
import net.essentuan.erika.framework.db.`object`.store
import net.essentuan.erika.framework.db.`object`.types.Struct
import net.essentuan.erika.framework.db.table.StandardTable
import net.essentuan.erika.framework.events.Event
import net.essentuan.erika.db.struct.guild.member.MemberEntry.Companion.external
import net.essentuan.erika.db.struct.player.ProfileModel
import net.essentuan.erika.db.struct.player.ProfileModel.Table.external
import net.essentuan.erika.fetch.wynncraft.guild.BasicMember
import net.essentuan.erika.observers.guilds.events.GuildEvent
import net.essentuan.esl.collections.builders.list
import net.essentuan.esl.json.json
import net.essentuan.esl.time.duration.ms
import net.essentuan.esl.time.extensions.minus
import java.util.Date
import java.util.UUID

data class MemberModel(
    override val profile: ProfileModel,
    private val history: MutableList<MemberEntry> = mutableListOf()
) : Struct<MemberModel.Table>(), Guild.Member, List<Guild.Member.Entry> by history {
    override val name: String
        get() = profile.name
    override val uuid: UUID
        get() = profile.uuid

    private fun first(guild: GuildType, member: BasicMember) {
        history.add(MemberEntry(guild.uuid, member.joined, member.rank, member.contributed))
    }

    @Synchronized
    fun compare(guild: GuildType, member: BasicMember?, postFirstJoin: Boolean = true): List<Event> = list {
        val current = history.lastOrNull()

        when {
            current == null -> {
                first(guild, member ?: return@list)

                if (postFirstJoin)
                    +GuildEvent.Member.Join(this@MemberModel)

                enqueue()
            }

            member == null -> {
                if (current.end == null && guild.uuid == current.uuid) {
                    current.end = Date()

                    +GuildEvent.Member.Leave(this@MemberModel, current)
                }
            }

            member.joined < current.start -> return@list
            member.joined != current.start -> {
                current.end = member.joined - 1.ms

                history.add(MemberEntry(guild.uuid, member.joined, member.rank, member.contributed))

                +GuildEvent.Member.Leave(this@MemberModel, current)
                +GuildEvent.Member.Join(this@MemberModel)

                if (current.uuid != guild.uuid)
                    return@list

                if (member.contributed > 0)
                    +GuildEvent.Member.Contributed(this@MemberModel, member.contributed)

                val before = current.last().rank
                val after = member.rank

                if (before > after)
                    +GuildEvent.Member.Demoted(this@MemberModel, before, after)
                else if (after > before)
                    +GuildEvent.Member.Promoted(this@MemberModel, before, after)
            }

            else -> {
                current.apply {
                    +this@MemberModel.update(guild, member)
                }
            }
        }
    }

    private val current: MemberEntry?
        get() {
            val entry = history.lastOrNull() ?: return null

            if (entry.end != null)
                return null

            return entry
        }

    override val guild: GuildType?
        get() = current

    override val rank: Guild.Rank?
        get() = current?.lastOrNull()?.rank
    override val joinedAt: Date?
        get() = current?.start
    override val contributed: Long?
        get() = current?.contributions?.total

    companion object Table : StandardTable<MemberModel>() {
        init {
            schema {
                MemberModel::profile {
                    +Attribute.UNIQUE

                    index {
                        +Index.Type.ASCENDING
                    }
                }
            }

            store {
                +MemberModel::profile { (it as? ProfileModel)?.id ?: it }
            }
        }

        fun Guild.Member.external() = json {
            "profile" to profile.external()
            "history" to map { it.external() }
        }
    }
}