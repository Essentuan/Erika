package net.essentuan.erika.kord.framework.events

import dev.kord.common.annotation.KordPreview
import dev.kord.core.Kord
import net.essentuan.erika.framework.events.KordEventType

abstract class KordEvent(override val kord: Kord) : KordEventType {
    @KordPreview
    override val customContext: Any?
        get() = null

    override val shard: Int
        get() = 0
}