package net.essentuan.erika.fetch

import net.essentuan.erika.fetch.wynncraft.Metadata
import net.essentuan.esl.json.Json

abstract class WynnModel : Json.Model {
    lateinit var metadata: Metadata
        protected set
}