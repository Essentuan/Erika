package net.essentuan.erika.db.struct.player

import com.busted_moments.buster.api.PlayerType
import com.busted_moments.buster.api.Profile
import com.busted_moments.buster.api.World
import net.essentuan.erika.framework.db.api.table.schema.Attribute
import net.essentuan.erika.framework.db.api.table.schema.Index
import net.essentuan.erika.framework.db.`object`.Memory
import net.essentuan.erika.framework.db.`object`.store
import net.essentuan.erika.framework.db.`object`.types.Struct
import net.essentuan.erika.framework.db.`object`.types.Struct.Companion.enqueue
import net.essentuan.erika.framework.db.table.StandardTable
import net.essentuan.erika.framework.events.annotations.Subscribe
import net.essentuan.erika.db.struct.player.PlaytimeModel.Table.external
import net.essentuan.erika.fetch.mojang.Username
import net.essentuan.erika.observers.player.events.PlayerEvent
import net.essentuan.erika.observers.player.worlds.WorldList
import net.essentuan.esl.json.Json
import net.essentuan.esl.json.json
import net.essentuan.esl.model.annotations.ReadOnly
import net.essentuan.esl.other.lock
import net.essentuan.esl.time.duration.Duration
import net.essentuan.esl.time.span.TimeSpan
import java.util.Date
import java.util.UUID

data class ProfileModel(
    @property:ReadOnly
    override var name: String,
    override val uuid: UUID,
    override val playtime: PlaytimeModel = PlaytimeModel(),
    private val history: MutableList<Entry> = mutableListOf()
) : Struct<ProfileModel.Table>(), Profile, List<Profile.Entry> by history {
    constructor(details: Username) : this(details.username, details.uuid) {
        history.add(
            Entry(
                name,
                Date(-1)
            )
        )
    }

    fun update(details: Username) {
        require(details.uuid == uuid)

        PlayerEvent.Update(
            synchronized(this) {
                if (name == details.username)
                    return

                Memory.refresh(this, ProfileModel::name) {
                    history.last().end = Date()

                    val old = name

                    name = details.username
                    history += Entry(name, Date())

                    old
                }
            },
            this
        ).post()

        enqueue()

        return
    }

    companion object Table : StandardTable<ProfileModel>() {
        init {
            schema {
                ProfileModel::name {
                    index {
                        +Index.Type.ASCENDING
                    }
                }

                ProfileModel::uuid {
                    +Attribute.UNIQUE

                    index {
                        +Index.Type.ASCENDING
                    }
                }

                store {
                    +ProfileModel::name { (it as? String)?.lowercase() ?: it }
                    +ProfileModel::uuid
                }
            }
        }

        fun Profile.external() = json {
            "name" to name
            "uuid" to uuid.toString()
            "playtime" to playtime.external()

            "history" to map { it.external() }
        }

        val PlayerType.world: World?
            get() = WorldList[this]

        fun Profile.Entry.external() = json {
            "name" to name
            "start" to start.time
            "end" to end?.time
        }
    }

    override fun contains(name: String): Boolean =
        history.any { it.name == name }
}

data class Entry(
    override val name: String,
    override val start: Date,
    override var end: Date? = null
) : Json.Model, Profile.Entry, TimeSpan.Helper {
    override val duration: Duration
        get() = Duration(start, end ?: Date())
}

@Subscribe
private fun PlayerEvent.Join.listen() {
    val profile = profile as ProfileModel

    profile.playtime.lock {
        if (isEmpty() || lastOrNull()?.end != null)
            sessions.add(SessionModel())
        else
            return
    }

    profile.playtime.enqueue()
}

@Subscribe
private fun PlayerEvent.Leave.listen() {
    val profile = profile as ProfileModel

    profile.playtime.lock {
        sessions.lastOrNull()?.apply {
            if (end == null)
                end = Date()
        } ?: return
    }

    profile.playtime.enqueue()
}