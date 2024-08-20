package net.essentuan.erika.observers.territories.events

import com.busted_moments.buster.types.guilds.TerritoryProfile
import net.essentuan.erika.framework.events.Event
import java.util.UUID

data class ResourceUpdateEvent(
    val world: String,
    val by: Set<UUID>,
    val profiles: Map<String, TerritoryProfile>
) : Event()