package net.essentuan.erika.fetch.wynncraft.guild

import com.busted_moments.buster.api.Guild
import com.busted_moments.buster.api.GuildType
import com.busted_moments.buster.api.Season
import net.essentuan.erika.db.struct.guild.GuildBanner
import net.essentuan.erika.fetch.WynnModel
import net.essentuan.erika.fetch.wynncraft.DATE_HEADER
import net.essentuan.erika.fetch.wynncraft.WynncraftReq
import net.essentuan.erika.observers.guilds.list.Guilds
import net.essentuan.esl.Rating
import net.essentuan.esl.fetch.Fetch
import net.essentuan.esl.fetch.NOTHING
import net.essentuan.esl.fetch.annotations.At
import net.essentuan.esl.fetch.annotations.Timeout
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.Model.Companion.wrap
import net.essentuan.esl.model.annotations.Alias
import net.essentuan.esl.model.annotations.Ignored
import net.essentuan.esl.string.extensions.isUUID
import net.essentuan.esl.string.extensions.toUUID
import net.essentuan.esl.time.extensions.toDate
import java.net.http.HttpResponse
import java.util.Date
import java.util.UUID

data class BasicGuild(
    override val uuid: UUID,
    override val name: String,
    @Alias(["prefix"])
    override val tag: String,
    @Alias(["created"])
    val createdAt: Date,
    val level: Int,
    @Alias(["xpPercent"])
    val progress: Int,
    val wars: Int = 0,
    val banner: GuildBanner = GuildBanner(),
    @Alias(["seasonRanks"])
    val results: Season.Results = Season.Results()
) : WynnModel(), GuildType {
    val xp: Long
        get() = (Guild.required(level) * (progress / 100.0)).toLong()
    val required: Long
        get() = Guild.required(level).toLong()

    @Ignored
    val members: MutableMap<UUID, BasicMember> = mutableMapOf()

    override fun init(data: Json) {
        val members = data.getJson("members") ?: return

        for (rank in Guild.Rank.entries)
            for (value in members.getJson(rank.name.lowercase())?.values ?: continue) {
                val json = value.asJson() ?: continue

                val member = BasicMember(
                    json.getString("uuid")?.toUUID() ?: continue,
                    json.getLong("contributed") ?: continue,
                    rank,
                    json.getDate("joined") ?: continue
                )

                this.members[member.uuid] = member
            }
    }
}

data class BasicMember(
    val uuid: UUID,
    val contributed: Long,
    val rank: Guild.Rank,
    val joined: Date
) : Json.Model

@Timeout(seconds = 90.0)
@At("https://beta-api.wynncraft.com/v3/guild/%s%s")
private class Request(
    val query: String,
    type: String
) : WynncraftReq<BasicGuild>(type, query) {
    override fun invoke(body: Json): BasicGuild {
        return body.getJson("response")!!.also {
            it["metadata"] = body.getJson("metadata")
        }.wrap()
    }

    override fun handle(response: HttpResponse<Json>): BasicGuild? {
        val out = super.handle(response)

        if (out == null && !query.isUUID())
            return null

        Guilds.update(
            out?.uuid ?: query.toUUID(),
            out,
            out?.createdAt ?: response.headers().allValues(DATE_HEADER).first().toDate(),
            true
        )

        return out
    }
}

val NOTHING_UUID = UUID(0L, 0L)

suspend fun Fetch.guild(
    name: String = NOTHING,
    tag: String = NOTHING,
    uuid: UUID = NOTHING_UUID,
    priority: Rating = Rating.NORMAL
): BasicGuild? = when {
    name !== NOTHING -> Request(name, "").execute(priority)
    tag !== NOTHING -> Request(tag, "prefix/").execute(priority)
    uuid !== NOTHING_UUID -> Request(uuid.toString(), "uuid/").execute(priority)
    else -> throw IllegalArgumentException("Must specify a name, tag, or uuid!")
}