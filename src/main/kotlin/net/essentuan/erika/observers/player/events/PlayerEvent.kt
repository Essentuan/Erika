package net.essentuan.erika.observers.player.events

import com.busted_moments.buster.api.Profile
import com.busted_moments.buster.api.World
import net.essentuan.erika.framework.events.Event

abstract class PlayerEvent(
    val profile: Profile
) : Event() {
    class Join(val world: World, profile: Profile) : PlayerEvent(profile)
    class Swap(val old: World, val world: World, profile: Profile) : PlayerEvent(profile)
    class Leave(val world: World, profile: Profile) : PlayerEvent(profile)

    class Update(val old: String, profile: Profile): PlayerEvent(profile)
}