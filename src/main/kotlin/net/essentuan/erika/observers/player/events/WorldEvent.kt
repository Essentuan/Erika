package net.essentuan.erika.observers.player.events

import com.busted_moments.buster.api.World
import net.essentuan.erika.framework.events.Event


abstract class WorldEvent(
    val world: World
) : Event() {
    class Start(world: World) : WorldEvent(world)
    class Stop(world: World) : WorldEvent(world)
}