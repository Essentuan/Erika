package net.essentuan.erika.ktor.routes.api

import net.essentuan.erika.ktor.GET
import net.essentuan.erika.ktor.Route
import net.essentuan.erika.observers.territories.TerritoryList
import net.essentuan.erika.observers.territories.TerritoryList.external
import net.essentuan.esl.json.Json
import net.essentuan.esl.json.json

object TerritoryListRoute : Route.Container() {
    @GET("api/territories")
    private fun territories() =
        TerritoryList.api()

    @GET("api/territories/id")
    private fun id() =
        json {
            "timestamp" to TerritoryList.timestamp.time
        }

    fun TerritoryList.api(): Json =
        json(TerritoryList.external()) {
            val tiles = TerritoryList.version.tiles

            "tiles" {
                "id" to tiles.id.toString()

                "start" {
                    "x" to tiles.grid.start.first
                    "z" to tiles.grid.start.second
                }

                "end" {
                    "x" to tiles.grid.end.first
                    "z" to tiles.grid.end.second
                }

                "nativeZoom" to tiles.nativeZoom
                "zoom" {
                    "min" to tiles.zoom.first
                    "max" to tiles.zoom.last
                }
            }
        }
}