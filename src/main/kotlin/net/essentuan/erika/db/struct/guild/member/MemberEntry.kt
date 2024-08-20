package net.essentuan.erika.db.struct.guild.member

import com.busted_moments.buster.api.Contribution
import com.busted_moments.buster.api.Guild
import com.busted_moments.buster.api.GuildType
import net.essentuan.erika.framework.events.Event
import net.essentuan.erika.db.struct.guild.ContributionsModel
import net.essentuan.erika.db.struct.guild.ContributionsModel.Companion.external
import net.essentuan.erika.db.struct.guild.member.MemberStatus.Companion.external
import net.essentuan.erika.fetch.wynncraft.guild.BasicMember
import net.essentuan.erika.observers.guilds.events.GuildEvent
import net.essentuan.erika.observers.guilds.list.Guilds
import net.essentuan.esl.collections.builders.list
import net.essentuan.esl.json.Json
import net.essentuan.esl.json.json
import net.essentuan.esl.model.annotations.Ignored
import net.essentuan.esl.model.annotations.ReadOnly
import net.essentuan.esl.time.duration.Duration
import net.essentuan.esl.time.duration.ms
import net.essentuan.esl.time.span.TimeSpan
import java.util.Date
import java.util.UUID


data class MemberEntry(
    override val uuid: UUID,
    override val start: Date,
    override val contributions: ContributionsModel,
    private val status: MutableList<MemberStatus>,
    @property:ReadOnly
    override var end: Date? = null
) : Json.Model, Guild.Member.Entry, TimeSpan.Helper, List<Guild.Member.Status> by status {
    @Ignored
    private val guild by Guilds

    override val name: String
        get() = guild.name
    override val tag: String
        get() = guild.tag

    override val duration: Duration
        get() = ((end?.time ?: System.currentTimeMillis()) - start.time).ms

    constructor(
        guild: UUID,
        joined: Date,
        rank: Guild.Rank,
        contributed: Long
    ) : this(
        guild,
        joined,
        if (contributed == 0L)
            ContributionsModel()
        else
            ContributionsModel(Contribution(joined, contributed)),
        mutableListOf(MemberStatus(rank, joined))
    )

    @Synchronized
    fun MemberModel.update(guild: GuildType, member: BasicMember): List<Event> = list {
        contributions.apply {
            this@update.update(contributed = member.contributed)?.push()
        }

        val entry = status.last()
        if (entry.rank == member.rank)
            return@list

        val before = entry.rank
        val after = member.rank

        entry.end = Date()

        status.add(MemberStatus(after))

        if (before > after)
            +GuildEvent.Member.Demoted(this@update, before, after)
        else if (after > before)
            +GuildEvent.Member.Promoted(this@update, before, after)
    }

    companion object {
        fun Guild.Member.Entry.external() = json {
            "uuid" to uuid.toString()
            "name" to name
            "end" to end?.time
            "tag" to tag
            "start" to start.time
            "contributions" to contributions.external()
            "status" to map { it.external() }
        }
    }
}