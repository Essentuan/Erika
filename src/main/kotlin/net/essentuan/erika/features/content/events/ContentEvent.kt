package net.essentuan.erika.features.content.events

import net.essentuan.erika.features.content.ContentRecord
import net.essentuan.erika.framework.events.Event

abstract class ContentEvent : Event() {
    data class Completion(val record : ContentRecord) : ContentEvent()
}