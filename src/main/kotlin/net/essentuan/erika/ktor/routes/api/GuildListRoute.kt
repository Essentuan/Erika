package net.essentuan.erika.ktor.routes.api

import net.essentuan.erika.fetch.athena.athenaGuildList
import net.essentuan.erika.ktor.GET
import net.essentuan.erika.ktor.Route
import net.essentuan.esl.fetch.fetch
import net.essentuan.esl.json.Json
import net.essentuan.esl.json.json
import net.essentuan.esl.time.duration.minutes
import net.essentuan.esl.time.extensions.timeSince
import java.util.*

object GuildListRoute : Route.Container() {
    private var lastFetch = Date(0)
    private var cachedResponse = Json()

    @GET("api/guildList")
    private suspend fun territories(): Json {
        if (lastFetch.timeSince() < 1.minutes)
            return cachedResponse;

        val response = fetch { athenaGuildList() }
        val data = json {
            "guilds" to response.map {
                json {
                    "_id" to it.name
                    "color" to it.color?.asHex()
                }
            }
        }

        this.cachedResponse = data
        this.lastFetch = Date()

        return data
    }
}