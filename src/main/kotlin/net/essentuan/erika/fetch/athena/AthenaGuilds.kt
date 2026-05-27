package net.essentuan.erika.fetch.athena

import net.essentuan.erika.framework.annotation.Priority
import net.essentuan.esl.Rating
import net.essentuan.esl.color.Color
import net.essentuan.esl.fetch.Fetch
import net.essentuan.esl.fetch.JsonRequest
import net.essentuan.esl.fetch.annotations.At
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.annotations.Alias

data class AthenaGuild(
    val name: String,
    val color: Color?
)

@At("https://athena.wynntils.com/cache/get/guildList")
private class AthenaGuildListRequest : JsonRequest<List<AthenaGuild>>() {
    override fun invoke(body: Json): List<AthenaGuild>? =
        body.getList("array", Json::class)?.map {
               AthenaGuild(
                   it.getString("_id") ?: "<unknown>",
                   it.getString("color")?.let { str ->
                       if (str.isEmpty()) null else Color(str.substring(1).toInt(16))
                   }
               )
        }
}

suspend fun Fetch.athenaGuildList(priority: Rating = Rating.NORMAL) =
    AthenaGuildListRequest().execute(priority) ?: emptyList()