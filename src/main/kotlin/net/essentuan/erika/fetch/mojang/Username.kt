package net.essentuan.erika.fetch.mojang

import com.google.gson.stream.MalformedJsonException
import net.essentuan.erika.framework.console.Logging
import net.essentuan.esl.Rating
import net.essentuan.esl.fetch.Fetch
import net.essentuan.esl.fetch.HttpScheduler
import net.essentuan.esl.fetch.JsonRequest
import net.essentuan.esl.fetch.annotations.At
import net.essentuan.esl.fetch.annotations.Cache
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.Model.Companion.wrap
import net.essentuan.esl.model.annotations.Alias
import net.essentuan.esl.other.causedBy
import net.essentuan.esl.string.extensions.isUUID
import net.essentuan.esl.time.extensions.timeUntil
import java.io.EOFException
import java.io.IOException
import java.net.http.HttpResponse
import java.util.Date
import java.util.UUID

data class Username(
    @Alias(["name"])
    val username: String,
    @Alias(["id"])
    val uuid: UUID
) : Json.Model

suspend fun Fetch.username(
    query: String,
    priority: Rating = Rating.NORMAL
): Username? =
    try {
        Minetools(query).execute(priority)
    } catch (ex: Exception) {
        if (ex.causedBy<MalformedJsonException>() || ex.causedBy<IOException> { it.message?.contains("GOAWAY received") == true })
            null
        else {
            Logging.error("Failed to execute minetools request for $query", ex)

            throw ex
        }
    } ?: try {
        Mojang(query).execute(priority)
    } catch (ex: Exception) {
        if (ex.causedBy<EOFException>())
            null
        else {
            Logging.error("Failed to execute mojang request for $query", ex)

            throw ex
        }
    }

suspend fun Fetch.username(
    uuid: UUID,
    priority: Rating = Rating.NORMAL
) = username(uuid.toString(), priority)

@At("https://api.minetools.eu/uuid/%s")
private class Minetools(query: String) : JsonRequest<Username>(query) {
    override fun invoke(body: Json): Username = body.wrap()

    override fun validate(response: HttpResponse<Json>): Boolean {
        return super.validate(response) && response.body()["status"]?.raw != "ERR"
    }

    override fun after(response: HttpResponse<Json>) {
        cache = Date((response.body().getDouble("cache.cached_until")!! * 1000).toLong()).timeUntil()
    }

    companion object {
        init {
            HttpScheduler.Outbound.max["api.minetools.eu"] = 20
            HttpScheduler.Outbound.base["api.minetools.eu"] = 10
        }
    }
}

@Cache(minutes = 5.0)
@At("https://api.ashcon.app/mojang/v2/user/%s")
private class Ashcon(query: String) : JsonRequest<Username>(query) {
    override fun invoke(body: Json): Username = body.wrap()
}


private const val UUID_URL = "https://sessionserver.mojang.com/session/minecraft/profile/"
private const val USERNAME_URL = "https://api.mojang.com/users/profiles/minecraft/"

@At("%s%s")
@Cache(minutes = 5.0)
private class Mojang(query: String) : JsonRequest<Username>(
    (if (query.isUUID()) UUID_URL else USERNAME_URL), query
) {
    override fun invoke(body: Json): Username = body.wrap()

    override fun validate(response: HttpResponse<Json>): Boolean {
        return super.validate(response) && response.body().isNotEmpty()
    }
}