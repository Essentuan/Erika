package net.essentuan.erika.db.struct.territories

import com.busted_moments.buster.api.World
import com.busted_moments.buster.types.guilds.TerritoryProfile
import net.essentuan.erika.fetch.wynncraft.list.BasicTerritoryList
import net.essentuan.erika.framework.db.api.table.schema.Index
import net.essentuan.erika.framework.db.commands.find
import net.essentuan.erika.framework.db.`object`.Metadata
import net.essentuan.erika.framework.db.`object`.types.Struct
import net.essentuan.erika.framework.db.table.StandardTable
import net.essentuan.erika.framework.events.Event
import net.essentuan.erika.framework.events.annotations.Subscribe
import net.essentuan.esl.coroutines.blocking
import net.essentuan.esl.ifPresent
import net.essentuan.esl.rx.findFirst
import net.essentuan.esl.time.extensions.timeSince

data class MapVersion(
    val template: Territory.Template,
    val tiles: Tile.Set
) : Struct<MapVersion.Table>() {
    companion object Table : StandardTable<MapVersion>() {
        lateinit var latest: MapVersion
            private set

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

        suspend operator fun invoke(territories: BasicTerritoryList): MapVersion {
            if (!::latest.isInitialized)
                find {
                    select from MapVersion

                    sort {
                        MapVersion::metadata {
                            -Metadata::created
                        }
                    }

                    limit to 1
                }.findFirst().ifPresent { latest = it }

            val template = Territory.Template(territories)
            if (::latest.isInitialized && template.id == latest.template.id && Tile.Set.latest().id == latest.tiles.id)
                return latest

            return MapVersion(
                template,
                Tile.Set.latest()
            ).also {
                latest = it
                it.enqueue()

                NewMapVersionEvent(it).post()
            }
        }

        operator fun invoke(world: World?, profiles: Map<String, TerritoryProfile>): MapVersion {
            if (world == null || world.age > latest.metadata.created.timeSince())
                return latest

            val template = Territory.Template(profiles)
            if (latest.template.id == template.id)
                return latest

            return MapVersion(
                template,
                blocking { Tile.Set.latest() }
            ).also {
                latest = it
                it.enqueue()

                NewMapVersionEvent(it).post()
            }
        }

        @Subscribe
        private fun NewTileSetEvent.on() {
            MapVersion(
                Territory.Template.latest,
                tiles
            ).also {
                latest = it
                it.enqueue()

                NewMapVersionEvent(it).post()
            }
        }
    }
}

data class NewMapVersionEvent(
    val version: MapVersion
) : Event()