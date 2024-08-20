package net.essentuan.erika.observers.territories.events

import com.busted_moments.buster.api.Territory
import net.essentuan.erika.framework.events.Event

data class TerritoryCapturedEvent(
    val before: Territory?,
    val beforeCount: Int,
    val after: Territory,
    val afterCount: Int
) : Event() {
    val territory: String
        get() = after.name
}