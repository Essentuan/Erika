package net.essentuan.erika.db.struct.guild

import com.busted_moments.buster.api.Contribution
import com.busted_moments.buster.api.Guild
import com.busted_moments.buster.api.GuildType
import com.busted_moments.buster.api.Season
import net.essentuan.erika.framework.db.api.table.schema.Attribute
import net.essentuan.erika.framework.db.api.table.schema.Index
import net.essentuan.erika.framework.db.`object`.Memory
import net.essentuan.erika.framework.db.`object`.store
import net.essentuan.erika.framework.db.`object`.types.Struct
import net.essentuan.erika.framework.db.table.StandardTable
import net.essentuan.erika.framework.events.Event
import net.essentuan.erika.db.struct.guild.ContributionsModel.Companion.external
import net.essentuan.erika.db.struct.guild.GuildBanner.Companion.external
import net.essentuan.erika.db.struct.guild.member.MemberModel
import net.essentuan.erika.db.struct.guild.member.MemberModel.Table.external
import net.essentuan.erika.db.struct.guild.member.search.invoke
import net.essentuan.erika.db.struct.guild.member.search.search
import net.essentuan.erika.fetch.wynncraft.guild.BasicGuild
import net.essentuan.erika.observers.guilds.events.GuildEvent
import net.essentuan.erika.observers.guilds.list.Guilds
import net.essentuan.esl.cast
import net.essentuan.esl.collections.builders.list
import net.essentuan.esl.get
import net.essentuan.esl.isPresent
import net.essentuan.esl.iteration.extensions.filter
import net.essentuan.esl.iteration.extensions.immutable
import net.essentuan.esl.json.json
import net.essentuan.esl.model.annotations.ReadOnly
import net.essentuan.esl.orNull
import net.essentuan.esl.other.lock
import net.essentuan.esl.rx.filter
import net.essentuan.esl.rx.forEach
import net.essentuan.esl.rx.iterate
import net.essentuan.esl.rx.map
import java.util.Date
import java.util.UUID

data class GuildModel(
    override val uuid: UUID,
    override val createdAt: Date,
    val members: MutableMap<UUID, MemberModel> = mutableMapOf(),
    @property:ReadOnly
    override var level: Int = 0,
    @property:ReadOnly
    override var progress: Int = 0,
    override val contributions: ContributionsModel = ContributionsModel(),
    @property:ReadOnly
    override var banner: GuildBanner = GuildBanner(),
    @property:ReadOnly
    override var wars: Int = 0,
    @property:ReadOnly
    override var results: Season.Results = Season.Results()
) : Struct<GuildModel.Table>(), Guild, GuildType by Guilds[uuid] ?: Guilds.UNKOWN {
    override val xp: Long
        get() = (Guild.required(level) * (progress / 100.0)).toLong()
    override val required: Long
        get() = Guild.required(level).toLong()

    init {
        members.entries.removeIf { entry -> entry.value.guild?.uuid != uuid }
    }

    private inline fun <T> T.change(new: T, block: (T) -> Unit): Boolean {
        if (this == new)
            return false

        block(new)

        return true
    }

    suspend fun update(guild: BasicGuild?): Boolean {
        var changed = false

        changed = changed or banner.change(guild?.banner ?: banner) { banner = it }
        changed = changed or wars.change(guild?.wars ?: wars) { wars = it }
        changed = changed or results.change(guild?.results ?: results) { results = it }

        list<Event> {
            val members = (guild?.members?.keys ?: emptySet())
                .asSequence()
                .plus(
                    this@GuildModel.lock {
                        members.keys.toList()
                    }
                ).distinct()
                .filter { uuid ->
                    val before = this@GuildModel[uuid] as MemberModel?
                    val after = guild?.members?.get(uuid)

                    when {
                        before != null && after != null -> {
                            before.compare(this@GuildModel, after).also {
                                if (it.isNotEmpty()) {
                                    before.enqueue()
                                    +it
                                }
                            }

                            false
                        }

                        before != null && after == null -> {
                            synchronized(this@GuildModel) {
                                members.remove(uuid)
                            }

                            before.compare(this@GuildModel, null).also {
                                if (it.isNotEmpty()) {
                                    before.enqueue()
                                    +it
                                }
                            }

                            false
                        }

                        before == null && after != null -> true
                        else -> false
                    }
                }.toMutableSet()

            if (members.isEmpty())
                return@list

            Guild.Member.search(members, create = true)
                .filter { it.second.isPresent() }
                .map { it.second.get() as MemberModel }
                .iterate {
                    it.compare(this@GuildModel, guild?.members?.get(it.uuid)).also { events ->
                        it.enqueue()
                        +events
                    }

                    this@GuildModel.lock { this.members[it.uuid] = it }
                }
        }.also {
            if (it.isNotEmpty()) {
                changed = true

                it.forEach(Event::post)
            }
        }

        if (guild == null || level == guild.level && progress == guild.progress)
            return changed

        var contributed = 0.0

        val before = this.level

        var progress: Int = this.progress
        for (i in this.level..guild.level) {
            if (progress < 100)
                contributed += Guild.required(i) * ((100 - progress) / 100.0)

            progress = 0
        }

        this.progress = guild.progress
        this.level = guild.level

        GuildEvent.XpGained(contributed.toLong(), this).post()

        if (before != level)
            GuildEvent.LevelUp(before, this.level, this).post()

        return true
    }

    override val size: Int
        get() = members.size

    override fun isEmpty(): Boolean = members.isEmpty()

    @Synchronized
    override fun get(uuid: UUID): Guild.Member? {
        val member = members[uuid] ?: return null

        if (member.guild?.uuid != this.uuid)
            return null

        return member
    }

    override fun iterator(): Iterator<Guild.Member> =
        members
            .values
            .iterator()
            .filter { it.guild?.uuid == uuid }
            .immutable()

    companion object Table : StandardTable<GuildModel>() {
        init {
            schema {
                GuildModel::uuid {
                    +Attribute.UNIQUE

                    index {
                        +Index.Type.ASCENDING
                    }
                }

                store {
                    +GuildModel::uuid
                }
            }
        }

        suspend fun create(guild: BasicGuild): GuildModel {
            var xp = 0L

            for (level in 1..<guild.level)
                xp += Guild.required(level).toLong()

            xp += guild.xp

            val model = GuildModel(
                guild.uuid,
                guild.createdAt,
                level = guild.level,
                progress = guild.progress,
                contributions = ContributionsModel(Contribution(guild.createdAt, xp)),
                banner = guild.banner,
                wars = guild.wars,
                results = guild.results
            )

            Guild.Member(
                create = true
            ) {
                +guild.members.keys
            }.map { (_, result) ->
                val member = result.cast<MemberModel>().orNull() ?: return@map null

                member
                    .compare(guild, guild.members[member.uuid] ?: return@map null, false)
                    .forEach(Event::post)

                member
            }
                .filter { it != null && it.guild?.uuid == model.uuid }
                .forEach {
                    model.lock {
                        members[it!!.uuid] = it
                    }
                }

            return Memory.create(
                { GuildModel::uuid eq model.uuid },
                { emptyArray() },
            ) { model }
        }

        fun Guild.external() = json {
            "uuid" to uuid.toString()
            "name" to name
            "tag" to tag
            "createdAt" to createdAt.time
            "level" to level
            "xp" to xp
            "required" to required
            "progress" to progress
            "members" to json {
                for (member in this@external)
                    member.uuid.toString() to member.external()
            }
            "contributions" to contributions.external()
            "banner" to banner.external()
            "wars" to wars
            "results" to results.map {
                json {
                    "rating" to it.rating
                    "finalTerritories" to it.territories
                }
            }
        }
    }
}