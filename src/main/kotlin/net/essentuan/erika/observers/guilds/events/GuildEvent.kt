package net.essentuan.erika.observers.guilds.events

import com.busted_moments.buster.api.Guild
import com.busted_moments.buster.api.GuildType
import net.essentuan.erika.framework.events.Event

abstract class GuildEvent(
    val guild: GuildType
) : Event() {
    class Created(guild: GuildType) : GuildEvent(guild)
    class Deleted(guild: GuildType) : GuildEvent(guild)

    class NameChange(val old: String, guild: GuildType) : GuildEvent(guild)
    class TagChange(val old: String, guild: GuildType): GuildEvent(guild)

    class XpGained(val xp: Long, guild: GuildType): GuildEvent(guild)
    class LevelUp(val before: Int, val after: Int, guild: GuildType): GuildEvent(guild)

    abstract class Member(val member: Guild.Member) : GuildEvent(member.last()) {
        class Join(member: Guild.Member) : Member(member)

        class Contributed(member: Guild.Member, val xp: Long) : Member(member)

        class Promoted(
            member: Guild.Member,
            val before: Guild.Rank,
            val after: Guild.Rank
        ) : Member(member)

        class Demoted(
            member: Guild.Member,
            val before: Guild.Rank,
            val after: Guild.Rank
        ) : Member(member)

        class Leave(member: Guild.Member, val before: GuildType) : Member(member)
    }
}