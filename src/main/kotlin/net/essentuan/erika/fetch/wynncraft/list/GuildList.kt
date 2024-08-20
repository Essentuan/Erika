package net.essentuan.erika.fetch.wynncraft.list

import com.busted_moments.buster.api.GuildType
import net.essentuan.erika.fetch.WynnModel
import net.essentuan.erika.fetch.wynncraft.Metadata
import net.essentuan.erika.fetch.wynncraft.WynncraftReq
import net.essentuan.esl.Rating
import net.essentuan.esl.collections.builders.mutableMap
import net.essentuan.esl.fetch.Fetch
import net.essentuan.esl.fetch.NoLimit
import net.essentuan.esl.fetch.annotations.At
import net.essentuan.esl.fetch.annotations.Timeout
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.Model.Companion.wrap
import net.essentuan.esl.string.extensions.toUUID
import java.util.UUID

class GuildList(
    private val response: Map<UUID, GuildType>,
    metadata: Metadata
) : WynnModel(), Map<UUID, GuildType> by response {
    init {
        this.metadata = metadata
    }
}

suspend fun Fetch.guildList(
    priority: Rating = Rating.NORMAL
) = GuildListRequest().execute(priority)

@Timeout(seconds = 10.0)
@At("https://beta-api.wynncraft.com/v3/guild/list/guild")
private class GuildListRequest : WynncraftReq<GuildList>(rateLimit = NoLimit) {
    override fun invoke(body: Json): GuildList? {
        return GuildList(
            mutableMap {
                for (entry in body.getJson("response")!!.entries) {
                    val guild = entry.asJson() ?: return null
                    val uuid = guild.getString("uuid")?.toUUID() ?: return null

                    uuid to Entry(
                        uuid,
                        entry.key,
                        guild.getString("prefix") ?: return null
                    )
                }
            },
            body.getJson("metadata")!!.wrap(::Metadata)
        )
    }
}

private data class Entry(
    override val uuid: UUID,
    override val name: String,
    override val tag: String
) : GuildType