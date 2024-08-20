package net.essentuan.erika.db.struct.player

import com.busted_moments.buster.api.Playtime
import com.busted_moments.buster.api.Session
import net.essentuan.erika.framework.db.`object`.types.Struct
import net.essentuan.erika.framework.db.table.StandardTable
import net.essentuan.erika.db.struct.player.SessionModel.Companion.external
import net.essentuan.esl.json.Json
import net.essentuan.esl.json.json
import net.essentuan.esl.model.annotations.ReadOnly
import net.essentuan.esl.time.duration.Duration
import net.essentuan.esl.time.duration.days
import net.essentuan.esl.time.duration.ms
import net.essentuan.esl.time.span.TimeSpan
import java.util.Date

data class PlaytimeModel(
    val sessions: MutableList<SessionModel> = mutableListOf(),
) : Struct<PlaytimeModel.Table>(), Playtime, Duration.Helper, List<Session> by sessions {
    override val duration: Duration
        get() = Duration(start, end)

    companion object Table : StandardTable<PlaytimeModel>() {
        fun Playtime.external() = past(30.days).map { it.external() }
    }
}

data class SessionModel(
    override val start: Date = Date(),
    @property:ReadOnly
    override var end: Date? = null
) : Json.Model, Session, TimeSpan.Helper {
    override val duration: Duration
        get() = ((end?.time ?: System.currentTimeMillis()) - start.time).ms

    companion object {
        fun Session.external() = json {
            "start" to start.time
            "end" to end?.time
        }
    }
}

