package net.essentuan.erika.db.struct.territories.editing

import com.google.common.collect.Multimap
import net.essentuan.erika.framework.db.`object`.types.Struct.Companion.enqueue
import net.essentuan.erika.db.struct.territories.NewTemplateEvent
import net.essentuan.erika.db.struct.territories.ResourceType
import net.essentuan.erika.db.struct.territories.Territory
import net.essentuan.erika.db.struct.territories.Territory.Template.Table.latest
import net.essentuan.erika.db.struct.territories.editing.TemplateEditor.Edit
import net.essentuan.esl.collections.multimap.Multimaps
import net.essentuan.esl.collections.multimap.hashSetValues
import java.util.EnumMap

data class TemplateEditor(
    private val type: Type,
    private val edits: Multimap<String, Edit> = Multimaps.hashKeys().hashSetValues()
) {
    fun resource(territory: String, type: ResourceType, value: Int) {
        edits[territory]+= Edit {
            it.base[type] = value
        }
    }

    fun connect(territory: String, to: String) {
        edits[territory]+= Edit {
            it.connections.add(to)
        }

        edits[to]+= Edit {
            it.connections.add(territory)
        }
    }

    fun disconnect(territory: String, from: String) {
        edits[territory]+= Edit {
            it.connections.remove(from)
        }

        edits[from]+= Edit {
            it.connections.remove(toString())
        }
    }

    fun finish() {
        type.start().also {
             for ((name, edits) in edits.asMap().entries) {
                 val territory = it[name] ?: continue

                 for (edit in edits)
                     edit.applyTo(territory)
             }

            latest = it
            it.enqueue()

            NewTemplateEvent(it).post()
        }
    }

    fun interface Edit {
        fun applyTo(territory: Territory)
    }

    enum class Type {
        MODIFY {
            override fun start(): Territory.Template =
                latest
        },
        NEW {
            override fun start(): Territory.Template =
                Territory.Template(
                    latest.mapValues { (_, it) ->
                        Territory(
                            it.name,
                            EnumMap<ResourceType, Int>(
                                ResourceType::class.java
                            ).apply {
                                putAll(it.base)
                            },
                            Territory.Location(
                                it.location.start.copy(),
                                it.location.end.copy()
                            ),
                            it.connections.toMutableSet()
                        )
                    }
                )

        };

        abstract fun start(): Territory.Template
    }
}