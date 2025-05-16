package net.essentuan.erika.observers.guilds.list

import com.busted_moments.buster.api.Guild
import com.busted_moments.buster.api.GuildType
import com.google.common.collect.Multimap
import net.essentuan.erika.framework.db.`object`.types.Singleton
import net.essentuan.erika.fetch.wynncraft.list.GuildList
import net.essentuan.erika.fetch.wynncraft.list.guildList
import net.essentuan.erika.observers.guilds.events.GuildEvent
import net.essentuan.esl.collections.multimap.Multimaps
import net.essentuan.esl.collections.multimap.hashSetValues
import net.essentuan.esl.delegates.lateinit
import net.essentuan.esl.fetch.fetch
import net.essentuan.esl.json.Json
import net.essentuan.esl.json.json
import net.essentuan.esl.model.annotations.Ignored
import net.essentuan.esl.model.annotations.ReadOnly
import net.essentuan.esl.other.lock
import net.essentuan.esl.scheduling.annotations.Every
import net.essentuan.esl.scheduling.annotations.Lifetime
import net.essentuan.esl.string.extensions.bestMatch
import net.essentuan.esl.string.extensions.isUUID
import net.essentuan.esl.string.extensions.toUUID
import java.util.Date
import java.util.UUID
import kotlin.reflect.KProperty

object Guilds : Singleton(), Guild.List {
    private val guilds: MutableMap<UUID, Entry> by lateinit(::mutableMapOf)

    @Ignored
    private val cache: Multimap<String, Entry> = Multimaps.hashKeys().hashSetValues()

    override val size: Int
        get() = guilds.size

    @Ignored
    val NONE: GuildType = object : GuildType {
        override val uuid: UUID = UUID(0L, 0L)
        override val name: String
            get() = "Nobody"
        override val tag: String
            get() = "NONE"
    }

    @Ignored
    val UNKOWN: GuildType = object : GuildType {
        override val uuid: UUID = UUID(0L, 0L)
        override val name: String
            get() = "Unknown"
        override val tag: String
            get() = "UKWN"
    }

    override fun isEmpty(): Boolean = guilds.isEmpty()

    override operator fun get(uuid: UUID): GuildType? {
        return if (uuid == NONE.uuid)
            NONE
        else
            guilds[uuid]
    }

    override operator fun get(type: GuildType): GuildType? {
        return if (type.uuid == NONE.uuid)
            NONE
        else
            guilds[type.uuid]
    }

    fun find(string: String, deleted: Boolean = false): GuildType? {
        if (string.isUUID())
            return guilds[string.toUUID()]

        val isName = Guild.Names.isValid(string)
        val isTag = Guild.Tags.isValid(string)

        if (!isTag && !isName)
            return null

        return string.bestMatch(
            guilds.lock { values.toList() }
                .asSequence()
                .filter { it.isDeleted == deleted },
            {
                when {
                    isName && isTag -> arrayOf(it.name, it.tag)
                    isName -> arrayOf(it.name)
                    else -> arrayOf(it.tag)
                }
            }
        )
    }

    override fun containsAll(elements: Collection<GuildType>): Boolean {
        for (e in elements)
            if (e !in this)
                return false

        return true
    }

    operator fun contains(uuid: UUID): Boolean =
        this[uuid] != null

    override fun contains(element: GuildType): Boolean =
        element.uuid in this

    override fun iterator(): Iterator<GuildType> =
        guilds.values.iterator()

    operator fun provideDelegate(thisRef: GuildType, prop: KProperty<*>): Lazy<GuildType> {
        return lazy { this@Guilds[thisRef.uuid] ?: Guilds.UNKOWN }
    }

    @Ignored
    private var previous: GuildList? = null

    @Synchronized
    fun update(uuid: UUID, after: GuildType?, at: Date, api: Boolean) {
        if (uuid == NONE.uuid)
            return

        val before = this[uuid] as Entry?

        when {
            before != null && at < before.updatedAt || (before == null && after == null) -> return
            before == null -> {
                var created = false

                val entry = lock {
                    guilds.computeIfAbsent(after!!.uuid) { _ ->
                        val entry = Entry(after)
                        entry.updatedAt = at

                        created = true

                        entry
                    }
                }

                if (created)
                    GuildEvent.Created(entry).post()
            }

            after == null && api -> Unit 

            after == null -> {
                before.isDeleted = true
                GuildEvent.Deleted(before).post()
            }

            else -> {
                if (before.name != after.name) {
                    val previous = before.name
                    before.name = after.name

                    GuildEvent.NameChange(previous, before).post()
                }

                if (before.tag != after.tag) {
                    val previous = before.tag
                    before.tag = after.tag

                    GuildEvent.TagChange(previous, before).post()
                }
            }
        }
    }

    @Every(seconds = 45.0)
    @Lifetime(minutes = 1.0)
    suspend fun update() {
        val guildList = fetch { guildList() } ?: return

        if (guildList === previous)
            return
        else
            previous = guildList

        guilds.values.lock { toList() }
            .asSequence()
            .filterNot { it.isDeleted }
            .map { it.uuid }
            .plus(guildList.keys)
            .distinct()
            .forEach { update(it, guildList[it], guildList.metadata.cachedAt ?: return@forEach, false) }
    }

    fun Guild.List.external() = json {
        for (guild in this@external)
            guild.uuid.toString() to json {
                "uuid" to guild.uuid.toString()
                "name" to guild.name
                "tag" to guild.tag
            }
    }
}

private data class Entry(
    override val uuid: UUID,
    @property:ReadOnly
    override var name: String,
    @property:ReadOnly
    override var tag: String
) : Json.Model, GuildType {
    var isDeleted: Boolean = false
    var updatedAt: Date = Date(0)

    constructor(type: GuildType) : this(
        type.uuid,
        type.name,
        type.tag
    )
}

val GuildType.isDeleted: Boolean
    get() {
        if (this is Entry)
            return this.isDeleted

        return (Guilds[this] as Entry).isDeleted
    }