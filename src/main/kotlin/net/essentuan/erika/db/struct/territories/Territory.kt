package net.essentuan.erika.db.struct.territories

import com.busted_moments.buster.api.GuildType
import com.busted_moments.buster.types.guilds.TerritoryProfile
import net.essentuan.erika.fetch.wynncraft.list.BasicTerritoryList
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.framework.db.api.table.schema.Index
import net.essentuan.erika.framework.db.commands.find
import net.essentuan.erika.framework.db.`object`.Metadata
import net.essentuan.erika.framework.db.`object`.types.Struct
import net.essentuan.erika.framework.db.table.StandardTable
import net.essentuan.erika.framework.events.Event
import net.essentuan.erika.observers.guilds.list.Guilds
import net.essentuan.erika.observers.territories.TerritoryRating
import net.essentuan.esl.comparing.equals
import net.essentuan.esl.ifPresent
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.annotations.Ignored
import net.essentuan.esl.other.repr
import net.essentuan.esl.rx.findFirst
import net.essentuan.esl.time.duration.days
import net.essentuan.esl.time.duration.hours
import net.essentuan.esl.time.extensions.timeSince
import java.util.Date
import java.util.EnumMap
import java.util.UUID

typealias ITerritory = com.busted_moments.buster.api.Territory
typealias ILocation = com.busted_moments.buster.api.Territory.Location
typealias IStorage = com.busted_moments.buster.api.Territory.Storage
typealias IPos = com.busted_moments.buster.api.Territory.Pos
typealias ResourceType = com.busted_moments.buster.api.Territory.Resource
typealias TerritoryRating = com.busted_moments.buster.api.Territory.Rating

private const val TEMPLATE_UNCHANGED = 0
private const val TEMPLATE_UPDATED = 1
private const val TEMPLATE_CREATED = 2

private const val DOUBLE_EMERALD_CUTOFF = 200_000
private const val DOUBLE_RESOURCE_CUTOFF = 74_880

private const val DOUBLE_EMERALD = 18_000
private const val NORMAL_EMERALD = 9_000
private const val RAINBOW_EMERALD = 1_800
private const val DOUBLE_RESOURCE = 7_200
private const val NORMAL_RESOURCE = 3_600
private const val RAINBOW_RESOURCE = 900

data class Territory(
    val name: String,
    val base: MutableMap<ResourceType, Int>,
    val location: Location,
    val connections: MutableSet<String>
) : Json.Model {
    data class State(
        override val name: String,
        private val uuid: UUID,
        override val acquired: Date,
        private val storages: Map<ResourceType, Resource>,
        override val hq: Boolean,
        override val defense: TerritoryRating,
        val version: MapVersion
    ) : Struct<State.Table>(), ITerritory {
        @Ignored
        private val template = version.template[name]!!

        override val location: com.busted_moments.buster.api.Territory.Location
            get() = template.location

        override val connections: Set<String>
            get() = template.connections

        @Ignored
        override val owner: GuildType = Guilds[uuid] ?: Guilds.UNKOWN

        @Ignored
        override val resources: Map<ResourceType, IStorage> =
            EnumMap<ResourceType, IStorage>(ResourceType::class.java).also {
                for (resource in ResourceType.entries) {
                    val storages = storages[resource]

                    it[resource] = Storage(
                        template.base[resource] ?: 0,
                        storages?.capacity ?: -1,
                        storages?.production ?: template.base[resource] ?: 0,
                        storages?.stored ?: -1
                    )
                }
            }

        override val treasury: com.busted_moments.buster.api.Territory.Rating
            get() {
                val held = acquired.timeSince()

                return when {
                    held >= 12.days -> TerritoryRating.VERY_HIGH
                    held >= 5.days -> TerritoryRating.HIGH
                    held >= 1.days -> TerritoryRating.MEDIUM
                    held >= 1.hours -> TerritoryRating.LOW
                    else -> TerritoryRating.VERY_LOW
                }
            }


        data class Resource(
            val production: Int,
            val stored: Int,
            val capacity: Int,
        ) : Json.Model

        private data class Storage(
            override val base: Int,
            override val capacity: Int,
            override val production: Int,
            override val stored: Int
        ) : IStorage

        companion object Table : StandardTable<State>() {
            init {
                schema {
                    State::name {
                        index {
                            +Index.Type.ASCENDING
                        }
                    }

                    State::acquired {
                        index {
                            +Index.Type.ASCENDING
                        }
                    }

                    State::uuid {
                        index {
                            +Index.Type.ASCENDING
                        }
                    }

                    "metadata" {
                        "created" {
                            index {
                                +Index.Type.DESCENDING
                            }
                        }
                    }
                }
            }
        }
    }

    data class Location(
        override val start: Pos,
        override val end: Pos
    ) : Json.Model, ILocation {
        override fun equals(other: Any?): Boolean =
            equals<ILocation>(other) { _, obj ->
                start == obj.start && end == obj.end
            }

        override fun hashCode(): Int {
            var result = end.hashCode()
            result = 31 * result + start.hashCode()
            return result
        }
    }

    data class Pos(
        override val x: Int,
        override val z: Int
    ) : Json.Model, IPos {
        override fun toString(): String =
            repr {
                prefix(IPos::class)

                +IPos::x
                +IPos::z
            }

        override fun equals(other: Any?): Boolean =
            equals<IPos>(other) { _, obj ->
                x == obj.x && z == obj.z
            }

        override fun hashCode(): Int {
            var result = x
            result = 31 * result + z
            return result
        }
    }

    data class Template(
        private val territories: Map<String, Territory>
    ) : Struct<Template.Table>(), Map<String, Territory> by territories {
        init {
            mirrorCons()
        }

        private fun mirrorCons() {
            for ((name, territory) in territories)
                for (connection in territory.connections)
                    territories[connection]?.connections?.add(name)
        }

        private fun changed(territories: BasicTerritoryList): Boolean {
            if (territories.keys != this.territories.keys)
                return true

            for ((name, territory) in territories) {
                val before = this[name] ?: return true

                val newLocation = territory.location
                val oldLocation = before.location

                if (
                    newLocation.start.x != oldLocation.start.x ||
                    newLocation.start.z != oldLocation.start.z ||
                    newLocation.end.x != oldLocation.end.x ||
                    newLocation.end.z != oldLocation.end.z
                ) return true;

                if (territory.links != before.connections.toList()) return true

                val oldProduction = before.base
                val newProduction = territory.resources.associateBy { it.type.toBuster() }

                for (resource in ResourceType.entries) {
                    val beforeRes = oldProduction[resource] ?: 0
                    val newRes = newProduction[resource]?.base ?: 0

                    if (beforeRes != newRes) return true
                }
            }

            return false
        }

        companion object Table : StandardTable<Template>() {
            lateinit var latest: Template

            init {
                schema {
                    "metadata" {
                        "created" {
                            index {
                                +Index.Type.DESCENDING
                            }
                        }
                    }
                }
            }

            suspend operator fun invoke(territories: BasicTerritoryList): Template {
                if (!::latest.isInitialized)
                    find {
                        select from Template

                        sort {
                            Template::metadata {
                                -Metadata::created
                            }
                        }

                        limit to 1
                    }.findFirst().ifPresent { latest = it }

                if (::latest.isInitialized && !latest.changed(territories))
                    return latest

                return Template(
                    territories.mapValues { (name, territory) ->
                        Territory(
                            name,
                            EnumMap<ResourceType, Int>(ResourceType::class.java).also {
                                territory.resources.forEach { res -> it[res.type.toBuster()] = res.base }
                            },
                            Location(
                                Pos(
                                    territory.location.start.x,
                                    territory.location.start.z
                                ),
                                Pos(
                                    territory.location.end.x,
                                    territory.location.end.z
                                )
                            ),
                            territory.links.toMutableSet()
                        )
                    }
                ).also {
                    latest = it
                    it.enqueue()

                    NewTemplateEvent(it).post()
                }
            }
        }
    }
}

class NewTemplateEvent(
    val template: Territory.Template
) : Event()