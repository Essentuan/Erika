package net.essentuan.erika.fetch.wynncraft.player

import net.essentuan.erika.fetch.WynnModel
import net.essentuan.erika.fetch.wynncraft.WynncraftReq
import net.essentuan.esl.Rating
import net.essentuan.esl.fetch.Fetch
import net.essentuan.esl.fetch.annotations.At
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.Model.Companion.wrap
import net.essentuan.esl.model.annotations.Alias
import net.essentuan.esl.time.TimeUnit
import net.essentuan.esl.time.Unit
import net.essentuan.esl.time.duration.Duration
import java.util.UUID

data class PlaytimeResult(
    @Unit(TimeUnit.HOURS)
    @Alias(["response.playtime"])
    val playtime: Duration
) : WynnModel()

@At("https://api.wynncraft.com/v3/player/%s")
private class PlaytimeRequest(
    uuid: UUID
) : WynncraftReq<PlaytimeResult>(uuid.toString()) {
    override fun invoke(body: Json): PlaytimeResult =
        body.wrap()
}

suspend fun Fetch.playtime(uuid: UUID, priority: Rating = Rating.NORMAL) =
    PlaytimeRequest(uuid).execute(priority)