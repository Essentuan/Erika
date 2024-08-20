package net.essentuan.erika.framework.db.`object`.types

import com.mongodb.client.model.ReplaceOptions
import net.essentuan.erika.ReadyEvent
import net.essentuan.erika.ShutdownEvent
import net.essentuan.erika.framework.annotation.Priority
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.framework.db.bson
import net.essentuan.erika.framework.db.builders.filter
import net.essentuan.erika.framework.db.commands.find
import net.essentuan.erika.framework.db.`object`.BsonModel
import net.essentuan.erika.framework.db.`object`.Memory
import net.essentuan.erika.framework.db.table.StandardTable
import net.essentuan.erika.framework.events.annotations.Subscribe
import net.essentuan.erika.framework.events.events
import net.essentuan.esl.Rating
import net.essentuan.esl.coroutines.await
import net.essentuan.esl.coroutines.blocking
import net.essentuan.esl.delegates.lateinit
import net.essentuan.esl.future.api.Future
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.Model.Companion.load
import net.essentuan.esl.model.annotations.Override
import net.essentuan.esl.orNull
import net.essentuan.esl.reflections.Reflections
import net.essentuan.esl.reflections.Types.Companion.concreteTypes
import net.essentuan.esl.reflections.Types.Companion.objects
import net.essentuan.esl.reflections.extensions.get
import net.essentuan.esl.reflections.extensions.instance
import net.essentuan.esl.reflections.extensions.simpleString
import net.essentuan.esl.rx.discard
import net.essentuan.esl.rx.findFirst
import net.essentuan.esl.scheduling.annotations.Auto
import net.essentuan.esl.scheduling.annotations.Every
import net.essentuan.esl.scheduling.tasks

private val LOGGER by Logging
private const val TYPE = "metadata.type"

@Auto(false)
abstract class Singleton : BsonModel() {
    @Override(TYPE)
    val javaClass by lateinit { this::class.java }

    override val table: net.essentuan.erika.framework.db.api.table.Table<*>
        get() = Table

    override suspend fun save() {
        table.mongo.replaceOne(
            filter { "_id" eq id },
            export(false).bson(),
            ReplaceOptions().upsert(true)
        ).discard()
    }

    object Table : StandardTable<Json>() {
        private val loaded: Map<Class<*>, Singleton> by lazy {
            Reflections.types
                .subtypesOf(Singleton::class)
                .concreteTypes()
                .objects()
                .associate {
                    it.java to (it.instance ?: error("Failed to load singleton ${it.simpleString()}"))
                }
        }

        private var ready: Boolean = false

        @Subscribe
        private fun ReadyEvent.listen() {
            LOGGER.info("Loading singletons")

            blocking {
                val entries = loaded.entries.sortedByDescending { (cls, _) ->
                    cls.kotlin[Priority::class]?.value ?: Rating.NORMAL
                }

                for ((type, instance) in entries) {
                    LOGGER.info("Loading ${type.simpleString()}")

                    val json = find {
                        select from Table

                        where {
                            "metadata.type" eq type
                        }

                        limit to 1
                    }.findFirst().orNull() ?: continue

                    try {
                        instance.load(json)
                        Memory.register(instance)
                    } catch (ex: Throwable) {
                        LOGGER.error("Failed to load singleton ${type.simpleString()}!", ex)
                    }
                }
            }

            loaded.values.forEach {
                it.events.register()
                it.tasks.resume()
            }

            LOGGER.info("Finished loading singletons")

            ready = true
        }

        @Subscribe
        private fun onShutdown(event: ShutdownEvent) {
            blocking {
                await {
                    loaded.values.forEach { +Future { it.save() } }
                }
            }
        }

        @Every(seconds = 10.0)
        private suspend fun doAutoSave() {
            if (ready)
                await {
                    for (instance in loaded.values)
                        +Future {
                            try {
                                instance.save()
                            } catch (ex: Exception) {
                                Logging.error("Error saving singleton ${instance.javaClass.simpleString()}", ex)
                            }
                        }

                }
        }
    }
}