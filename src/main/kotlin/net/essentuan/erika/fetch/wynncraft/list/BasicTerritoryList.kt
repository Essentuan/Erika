package net.essentuan.erika.fetch.wynncraft.list

import com.busted_moments.buster.api.GuildType
import com.busted_moments.buster.api.Territory
import net.essentuan.erika.fetch.WynnModel
import net.essentuan.erika.fetch.wynncraft.WynncraftReq
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.observers.guilds.list.Guilds
import net.essentuan.esl.Rating
import net.essentuan.esl.comparing.equals
import net.essentuan.esl.fetch.Fetch
import net.essentuan.esl.fetch.NoLimit
import net.essentuan.esl.fetch.annotations.At
import net.essentuan.esl.fetch.annotations.Timeout
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.Model.Companion.wrap
import net.essentuan.esl.model.annotations.Alias
import net.essentuan.esl.other.repr
import java.util.Date
import java.util.Objects
import java.util.UUID
import kotlin.math.min

data class BasicTerritoryList(
    @Alias(["response"])
    private val territories: Map<String, BasicTerritory>
) : WynnModel(), Map<String, BasicTerritory> by territories

data class BasicTerritory(
    @Alias(["guild"])
    val owner: BasicOwner,
    val acquired: Date?,
    val location: BasicLocation,
    val hq: Boolean,
    val resources: List<BasicTerritoryProduction>,
    val links: List<String>,
    val defences: Territory.Rating
) : Json.Model

enum class BasicTerritoryResource {
    EMERALD,
    ORE,
    WOOD,
    FISH,
    CROP;

    fun toBuster(): Territory.Resource {
        return when (this) {
            EMERALD -> Territory.Resource.EMERALDS
            ORE -> Territory.Resource.ORE
            WOOD -> Territory.Resource.WOOD
            FISH -> Territory.Resource.FISH
            CROP -> Territory.Resource.CROP
        }
    }
}

data class BasicTerritoryProduction(
    val type: BasicTerritoryResource,
    val generation: Int,
    private val baseGeneration: Int,
    val stored: Int,
    val limit: Int
) : Json.Model {
    val base: Int
        get() = min(generation, baseGeneration)
}

class BasicOwner(
    override val name: String = Guilds.NONE.name,
    @Alias(["prefix"])
    override val tag: String = Guilds.NONE.tag,
    override val uuid: UUID = Guilds.NONE.uuid
) : Json.Model, GuildType

data class BasicLocation(
    @Alias(["start"])
    private val startRaw: List<Int>,
    @Alias(["end"])
    private val endRaw: List<Int>
) : Json.Model, Territory.Location {
    override val start: Territory.Pos
        get() = BasicPos(startRaw[0], startRaw[1])
    override val end: Territory.Pos
        get() = BasicPos(endRaw[0], endRaw[1])

    override fun equals(other: Any?): Boolean =
        equals<Territory.Location>(other) { _, obj ->
            start == obj.start && end == obj.end
        }

    override fun hashCode(): Int {
        var result = start.hashCode()
        result = 31 * result + end.hashCode()
        return result
    }
}

data class BasicPos(
    override val x: Int,
    override val z: Int
) : Territory.Pos {
    override fun hashCode(): Int =
        Objects.hash(x, z)

    override fun toString(): String =
        repr {
            prefix(Territory.Pos::class)

            +Territory.Pos::x
            +Territory.Pos::z
        }

    override fun equals(other: Any?): Boolean =
        equals<Territory.Pos>(other) { _, obj ->
            x == obj.x && z == obj.z
        }
}

@Timeout(seconds = 10.0)
@At("https://api.wynncraft.com/v3/guild/list/territories")
private class TerritoryListRequest : WynncraftReq<BasicTerritoryList>(NoLimit) {
    override fun invoke(body: Json): BasicTerritoryList {
        body
            .getJson("response")
            ?.values
            ?.removeAll {
                it.raw is Json && (it.raw as Json).getJson("location")?.isEmpty() == true
            }

        return body.wrap(BasicTerritoryList::class).apply {
            asSequence().map { (_, it) -> it.owner }.distinctBy { it.uuid }.forEach {
                Guilds.update(it.uuid, it, metadata.cachedAt ?: Date(), true)
            }
        }
    }
}

suspend fun Fetch.territoryList(
    priority: Rating = Rating.NORMAL
) = TerritoryListRequest().execute(priority)