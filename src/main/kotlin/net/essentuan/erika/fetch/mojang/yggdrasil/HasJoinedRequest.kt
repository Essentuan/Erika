package net.essentuan.erika.fetch.mojang.yggdrasil

import net.essentuan.erika.fetch.mojang.Username
import net.essentuan.esl.Rating
import net.essentuan.esl.fetch.Fetch
import net.essentuan.esl.fetch.StringRequest
import net.essentuan.esl.fetch.annotations.At
import net.essentuan.esl.fetch.annotations.Cache
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.Model.Companion.wrap

@Cache(seconds = 0.0)
@At("https://sessionserver.mojang.com/session/minecraft/hasJoined?username=%s&serverId=%s")
class HasJoinedRequest(
    username: String,
    server: String
) : StringRequest<Username>(username, server) {
    override fun invoke(body: String): Username? =
        if (body.isNotEmpty())
            Json(body).wrap()
        else
            null
}

suspend fun Fetch.hasJoined(
    username: String,
    server: String,
    priority: Rating = Rating.NORMAL
) = HasJoinedRequest(username, server).execute(priority)
