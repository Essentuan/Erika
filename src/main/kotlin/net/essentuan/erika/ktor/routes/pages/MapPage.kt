package net.essentuan.erika.ktor.routes.pages

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import net.essentuan.erika.Resources
import net.essentuan.erika.framework.events.Event
import net.essentuan.erika.framework.events.annotations.Subscribe
import net.essentuan.erika.framework.events.listen
import net.essentuan.erika.inline
import net.essentuan.erika.ktor.GET
import net.essentuan.erika.ktor.Route
import net.essentuan.erika.ktor.respond
import net.essentuan.erika.ktor.routes.api.TerritoryListRoute.api
import net.essentuan.erika.observers.territories.TerritoryList
import net.essentuan.erika.observers.territories.events.MapUpdateEvent
import net.essentuan.esl.coroutines.launch
import java.nio.file.Path
import kotlin.io.path.div

object MapPage : Route.Container() {
    @GET("map")
    private fun get() =
        Resources / "pages" / "map" / "index.html"
}