package net.essentuan.erika.ktor.routes.api

import net.essentuan.erika.ktor.GET
import net.essentuan.erika.ktor.Route
import net.essentuan.erika.observers.territories.TerritoryList
import net.essentuan.erika.observers.territories.TerritoryList.external

object TerritoryListRoute : Route.Container() {
    @GET("api/territories")
    private fun get() =
        TerritoryList.external()
}