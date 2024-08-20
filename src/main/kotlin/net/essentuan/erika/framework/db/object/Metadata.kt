package net.essentuan.erika.framework.db.`object`

import net.essentuan.esl.json.Json
import java.util.Date

data class Metadata(val created: Date = Date()) : Json.Model {
    var modified = Date()
        private set

    override fun prepare() {
        modified = Date()
    }
}