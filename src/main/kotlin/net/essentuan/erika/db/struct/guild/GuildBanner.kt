package net.essentuan.erika.db.struct.guild

import com.busted_moments.buster.api.Guild
import net.essentuan.esl.json.Json
import net.essentuan.esl.json.json
import net.essentuan.esl.model.annotations.Alias

data class GuildBanner(
    override val color: Guild.Banner.Color = Guild.Banner.Color.WHITE,
    override val tier: Int = 0,
    override val structure: Guild.Banner.Structure = Guild.Banner.Structure.DEFAULT,
    private val layers: List<Layer> = emptyList()
) : Json.Model, Guild.Banner, List<Guild.Banner.Layer> by layers {
    data class Layer(
        @Alias(["colour"])
        override val color: Guild.Banner.Color,
        override val pattern: Guild.Banner.Pattern
    ) : Json.Model, Guild.Banner.Layer

    companion object {
        fun Guild.Banner.external() = json {
            "color" to color.toString()
            "tier" to tier
            "structure" to structure.toString()
            "layers" to map { it.external() }
        }

        fun Guild.Banner.Layer.external() = json {
            "color" to color.toString()
            "pattern" to pattern.toString()
        }
    }
}