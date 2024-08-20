package net.essentuan.erika.kord.tracks.lanes

import dev.kord.core.behavior.edit
import dev.kord.core.entity.Message
import net.essentuan.erika.framework.events.annotations.Subscribe
import net.essentuan.erika.framework.events.events
import net.essentuan.erika.inline
import net.essentuan.erika.kord.framework.message.ContentBuilder
import net.essentuan.erika.kord.framework.message.Timestamp
import net.essentuan.erika.kord.framework.message.content
import net.essentuan.erika.kord.tracks.Track
import net.essentuan.erika.observers.territories.events.TerritoryCapturedEvent
import net.essentuan.esl.model.annotations.Ignored
import net.essentuan.esl.scheduling.annotations.Every
import net.essentuan.esl.scheduling.annotations.Lifetime
import net.essentuan.esl.scheduling.tasks
import net.essentuan.esl.time.TimeUnit
import net.essentuan.esl.time.duration.FormatFlag
import net.essentuan.esl.time.duration.seconds
import net.essentuan.esl.time.extensions.timeSince
import java.util.LinkedList
import java.util.UUID

class TerritoryLane(
    val guild: UUID?
) : Track.Lane() {
    @Ignored
    private val queue = LinkedList<Pair<Message, TerritoryCapturedEvent>>()

    init {
        events.register()
        tasks.resume()
    }

    @Subscribe
    private fun TerritoryCapturedEvent.on() {
        if (!test(this))
            return

        inline {
            val message = send {
                content {
                    format(this@on, Timestamp.RELATIVE)
                }
            } ?: return@inline

            queue.offer(message to this@on)
        }
    }

    @Every(ms = 250.0)
    @Lifetime(minutes = 1.0)
    private suspend fun reformat() {
        while (queue.isNotEmpty()) {
            val (_, peek) = queue.peek()
            if (peek.after.acquired.timeSince() < 59.5.seconds)
                continue

            val (message, event) = queue.poll()

            message.edit {
                content {
                    format(event, Timestamp.LONG_DATE_WITH_TIME)
                }
            }
        }
    }

    private fun ContentBuilder.format(change: TerritoryCapturedEvent, type: Timestamp) {
        +"**"
        +change.territory
        +" ("
        +(change.before?.defense?.print() ?: "<unknown>")
        +"):"
        +"**"

        newLine()

        +"  *"
        if (change.before == null)
            +"Nobody [NONE]"
        else {
            +change.before.owner.name
            +" ["
            +change.before.owner.tag
            +"]"
        }
        +"*"
        +" ("
        +change.beforeCount.toString()
        +") \u2192 **"
        +change.after.owner.name
        +" ["
        +change.after.owner.tag
        +"]** ("
        +change.afterCount.toString()
        +")"

        newLine()

        +"     Territory held for `"
        +(change.before?.acquired?.timeSince()?.print(FormatFlag.COMPACT, TimeUnit.SECONDS) ?: "<unknown>")
        +"`"

        newLine()
        +"     Acquired: "
        +type(change.after.acquired)
    }

    private fun test(change: TerritoryCapturedEvent): Boolean {
        if (guild == null)
            return true

        return change.before?.owner?.uuid == guild || change.after.owner.uuid == guild
    }

    override fun close() {
        tasks.close()
        events.unregister()
    }
}