package net.essentuan.erika.fetch.athena

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.essentuan.erika.db.struct.territories.IntPair
import net.essentuan.erika.db.struct.territories.Tile
import net.essentuan.esl.Rating
import net.essentuan.esl.fetch.Fetch
import net.essentuan.esl.fetch.JsonRequest
import net.essentuan.esl.fetch.annotations.At
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.Model.Companion.wrap
import net.essentuan.esl.model.annotations.Alias
import java.net.URL
import javax.imageio.ImageIO

data class MapPart(
    val name: String,
    val url: URL,
    val x1: Int,
    val z1: Int,
    val x2: Int,
    val z2: Int,
    @Alias(["md5"])
    val hash: String
) : Json.Model {
    suspend fun download(): Tile {
        return Tile(
            name,
            IntPair(x1, z1),
            hash,
            withContext(Dispatchers.IO) {
                ImageIO.read(url)
            }
        )
    }
}

@At("https://raw.githubusercontent.com/Wynntils/Static-Storage/main/Reference/maps.json")
private class MapRequest : JsonRequest<List<MapPart>>() {
    override fun invoke(body: Json): List<MapPart>? =
        body.getList("array", Json::class)?.map { it.wrap(MapPart::class) }
}

suspend fun Fetch.map(priority: Rating = Rating.NORMAL) =
    MapRequest().execute(priority)