package net.essentuan.erika.fetch.wynncraft.list

import net.essentuan.erika.fetch.WynnModel
import net.essentuan.erika.fetch.wynncraft.Identifier
import net.essentuan.erika.fetch.wynncraft.Metadata
import net.essentuan.erika.fetch.wynncraft.WynncraftReq
import net.essentuan.esl.Rating
import net.essentuan.esl.collections.builders.mutableMap
import net.essentuan.esl.fetch.Fetch
import net.essentuan.esl.fetch.annotations.At
import net.essentuan.esl.fetch.annotations.Timeout
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.Model.Companion.wrap
import net.essentuan.esl.string.extensions.toUUID

class PlayerList<T>(
    private val response: Map<T, String>,
    metadata: Metadata
) : WynnModel(), Map<T, String> by response {
    init {
        this.metadata = metadata
    }
}

suspend fun <T> Fetch.playerList(
    identifier: Identifier<T>,
    priority: Rating = Rating.NORMAL
) = PlayerListRequest(identifier).execute(priority)

@Timeout(seconds = 10.0)
@At("https://beta-api.wynncraft.com/v3/player?identifier=%s")
private class PlayerListRequest<T>(
    val identifier: Identifier<T>
) : WynncraftReq<PlayerList<T>>(identifier.type()) {
    @Suppress("UNCHECKED_CAST")
    override fun invoke(body: Json): PlayerList<T>? {
        val players = body.getJson("response.players") ?: return null

        return PlayerList(
            mutableMap {
                for (entry in players.entries) {
                    if (identifier == Identifier.UUID)
                        entry.key.toUUID() as T to (entry.asString() ?: return null)
                    else
                        entry.key as T to (entry.asString() ?: return null)
                }
            },
            body.getJson("metadata")!!.wrap(Metadata::class)
        )
    }
}