package net.essentuan.erika.features.content.type

import net.essentuan.esl.reflections.Reflections
import net.essentuan.esl.reflections.extensions.instance
import java.net.URI

sealed class ContentType(
    val id: String,
    val displayName: String,
    val shortName: String,
    val icon: URI
) {
    companion object : AbstractCollection<ContentType>() {
        private val TYPES by lazy {
            Reflections.types
                .subtypesOf(ContentType::class)
                .mapNotNull { it.instance }
                .associateBy { it.id }
        }
        override val size: Int
            get() = TYPES.size

        fun valueOf(id: String): ContentType? =
            TYPES[id]

        operator fun contains(element: String): Boolean =
            valueOf(element) != null

        override fun contains(element: ContentType): Boolean =
            true

        override fun containsAll(elements: Collection<ContentType>): Boolean =
            true

        override fun iterator(): Iterator<ContentType> =
            TYPES.values.iterator()
    }
}

sealed class Raid(
    id: String,
    displayName: String,
    shortName: String,
    icon: URI,
) : ContentType(id, displayName, shortName, icon) {
    constructor(
        id: String,
        displayName: String,
        shortName: String,
        icon: String
    ) : this(
        id,
        displayName,
        shortName,
        URI.create(icon)
    )
}

data object NestOfTheGrootslangs : Raid(
    "NEST_OF_THE_GROOTSLANGS",
    "Nest of The Grootslangs",
    "NOTG",
    "https://cdn.wynncraft.com/nextgen/leaderboard/icons/grootslang.webp"
)

data object TheNexusOfLight : Raid(
    "THE_NEXUS_OF_LIGHT",
    "Orphion's Nexus of Light",
    "NOL",
    "https://cdn.wynncraft.com/nextgen/leaderboard/icons/orphion.webp"
)

data object TheCanyonColossus : Raid(
    "THE_CANYON_COLOSSUS",
    "The Canyon Colossus",
    "TCC",
    "https://cdn.wynncraft.com/nextgen/leaderboard/icons/colossus.webp"
)

data object TheNamelessAnomaly : Raid(
    "THE_NAMELESS_ANOMALY",
    "The Nameless Anomaly",
    "TNA",
    "https://cdn.wynncraft.com/nextgen/leaderboard/icons/nameless.webp"
)