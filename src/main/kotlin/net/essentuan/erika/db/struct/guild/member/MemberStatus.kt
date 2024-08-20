package net.essentuan.erika.db.struct.guild.member

import com.busted_moments.buster.api.Guild
import net.essentuan.esl.json.Json
import net.essentuan.esl.json.json
import net.essentuan.esl.model.annotations.ReadOnly
import net.essentuan.esl.time.duration.Duration
import net.essentuan.esl.time.duration.ms
import net.essentuan.esl.time.span.TimeSpan
import java.util.Date

data class MemberStatus(
    override var rank: Guild.Rank,
    override var start: Date = Date(),
    @property:ReadOnly
    override var end: Date? = null
) : Json.Model, Guild.Member.Status, TimeSpan.Helper {
    override val duration: Duration
        get() = ((end?.time ?: System.currentTimeMillis()) - start.time).ms

    companion object {
        fun Guild.Member.Status.external() = json {
            "rank" to rank.toString()
            "start" to start.time
            "end" to end?.time
        }
    }
}