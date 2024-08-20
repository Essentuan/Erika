package net.essentuan.erika.fetch.wynncraft

import net.essentuan.esl.delegates.lateinit
import net.essentuan.esl.json.Json
import net.essentuan.esl.time.TimeUnit
import net.essentuan.esl.time.Unit
import net.essentuan.esl.time.duration.Duration
import net.essentuan.esl.time.extensions.minus
import java.util.Date


class Metadata(
    val expires: Date?,
    @Unit(TimeUnit.SECONDS)
    @property:Unit(TimeUnit.SECONDS)
    val ttl: Duration?
): Json.Model {
    val cachedAt: Date?
        get() {
            return expires?.minus(ttl ?: return null)
        }

    val remaining: Int by lateinit()
    val limit: Int by lateinit()
    val reset: Int by lateinit()

    lateinit var version: String
        private set
}