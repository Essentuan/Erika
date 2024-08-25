package net.essentuan.erika.ktor

import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.fromFilePath
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.pipeline.PipelineContext
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.framework.events.events
import net.essentuan.erika.ktor.annotations.Wildcard
import net.essentuan.esl.collections.maps.expireAfter
import net.essentuan.esl.fetch.annotations.Cache
import net.essentuan.esl.fetch.annotations.duration
import net.essentuan.esl.future.api.Future
import net.essentuan.esl.json.Json
import net.essentuan.esl.json.type.AnyJson
import net.essentuan.esl.model.Model
import net.essentuan.esl.model.Model.Companion.export
import net.essentuan.esl.model.field.Field
import net.essentuan.esl.other.lock
import net.essentuan.esl.reflections.extensions.annotatedWith
import net.essentuan.esl.reflections.extensions.get
import net.essentuan.esl.reflections.extensions.simpleString
import net.essentuan.esl.scheduling.annotations.Every
import net.essentuan.esl.time.duration.Duration
import java.awt.image.BufferedImage
import java.lang.reflect.AnnotatedElement
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.jvm.isAccessible

typealias HttpContext = PipelineContext<Unit, ApplicationCall>

interface Route {
    val name: String
    var isEnabled: Boolean

    fun Application.start()

    abstract class Container : Route {
        override val name: String = javaClass.simpleString().replace(".", "")
        override var isEnabled: Boolean = true

        override fun Application.start() {
            routing {
                this@Container::class.declaredMembers.asSequence()
                    .filterIsInstance<KFunction<*>>()
                    .forEach { func ->
                        val (route, method) = func.method ?: return@forEach

                        var path = StringBuilder()

                        if (!route.startsWith("/"))
                            path.append("/")

                        path.append(route)

                        var instance: KParameter? = null
                        var context: KParameter? = null
                        val params = func.parameters
                            .filter { p ->
                                when (p.kind) {
                                    KParameter.Kind.INSTANCE -> {
                                        instance = p

                                        false
                                    }

                                    KParameter.Kind.EXTENSION_RECEIVER -> {
                                        context = p

                                        false
                                    }

                                    KParameter.Kind.VALUE -> true
                                }
                            }.map { p ->
                                p to Field.Type(p.type, object : AnnotatedElement {
                                    override fun <T : Annotation> getAnnotation(cls: Class<T>): T? =
                                        p[cls]

                                    override fun getAnnotations(): Array<out Annotation> =
                                        p.annotations.toTypedArray()

                                    override fun getDeclaredAnnotations(): Array<out Annotation> =
                                        getAnnotations()
                                })
                            }.onEach { (p, _) ->
                                path.append("/{")
                                path.append(p.name)

                                if (p annotatedWith Wildcard::class)
                                    path.append("...")

                                if (p.isOptional)
                                    path.append("?")

                                path.append('}')
                            }.toList()

                        Router(
                            this,
                            path.toString(),
                            method,
                            func,
                            instance,
                            context,
                            params
                        )
                    }
            }
        }

        private inner class Router(
            routing: Routing,
            path: String,
            method: HttpMethod,
            val func: KFunction<*>,
            val instance: KParameter?,
            val context: KParameter?,
            val params: List<Pair<KParameter, Field.Type>>
        ) : AnnotatedElement {
            private val duration: Duration? = func[Cache::class]?.duration()

            private val cache = mutableMapOf<Int, Future<Any?>>().expireAfter { duration!! }

            init {
                func.isAccessible = true
                routing.route(path, method) { handle { handle() } }

                events.register()
            }

            @Every(seconds = 10.0)
            private fun cleanse() {
                cache.lock { cleanse() }
            }

            private suspend fun PipelineContext<Unit, ApplicationCall>.handle() {
                if (!isEnabled) {
                    call.respond(null)
                    return
                }

                try {
                    val args = mutableMapOf<KParameter, Any?>()

                    for ((param, type) in params) {
                        args[param] = call.parameters.getAll(param.name!!)?.joinToString("/")?.let {
                            type.encoder.valueOf(
                                it,
                                emptySet(),
                                type.cls,
                                this@Router,
                                *type.args
                            )
                        }
                    }

                    if (duration != null) {
                        val hash = args.values.toList().hashCode()

                        call.respond(cache.lock {
                            computeIfAbsent(hash) {
                                Future {
                                    if (instance != null)
                                        args[instance] = this@Container

                                    if (this@Router.context != null)
                                        args[this@Router.context] = call

                                    func.callSuspendBy(args)
                                }
                            }
                        }.await())
                    } else {
                        if (instance != null)
                            args[instance] = this@Container

                        if (this@Router.context != null)
                            args[this@Router.context] = call

                        call.respond(func.callSuspendBy(args))
                    }
                } catch (ex: Exception) {
                    Logging.error("Error in request!", ex)

                    call.respond(data = null)
                }
            }

            override fun <T : Annotation> getAnnotation(cls: Class<T>): T? =
                func[cls]

            override fun getAnnotations(): Array<out Annotation> =
                func.annotations.toTypedArray()

            override fun getDeclaredAnnotations(): Array<out Annotation> =
                getAnnotations()
        }

        companion object {
            private val KFunction<*>.method: Pair<String, HttpMethod>?
                get() = this[GET::class]?.run { value to HttpMethod.Get }
                    ?: this[POST::class]?.run { value to HttpMethod.Post }
        }
    }
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class GET(val value: String)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class POST(val value: String)

suspend fun ApplicationCall.respond(data: Any?, status: HttpStatusCode? = null) {
    when (data) {
        null -> respond(status ?: HttpStatusCode.NotFound, "Not found!")
        is Response -> respond(data.data, data.status)

        is String -> respondText(
            data,
            status = status ?: HttpStatusCode.OK,
            contentType = ContentType.Application.Json
        )

        is Path -> {
            if (!data.exists())
                respond(null)
            else
                respondOutputStream(
                    contentType = ContentType.fromFilePath(data.toString()).firstOrNull() ?: ContentType.Any,
                    status = status
                ) { data.inputStream().copyTo(this) }
        }

        is Unit -> respond(null, status ?: HttpStatusCode.OK)

        is Json -> respondText(
            data.asString(true),
            status = status ?: HttpStatusCode.OK,
            contentType = ContentType.Application.Json
        )

        is AnyJson -> respond(Json(data), status ?: HttpStatusCode.OK)

        is Model<*> -> respond(data.export(), status ?: HttpStatusCode.OK)

        is BufferedImage -> respondOutputStream(
            contentType = ContentType.Image.PNG,
            status = status ?: HttpStatusCode.OK
        ) { ImageIO.write(data, "png", this) }

        else -> throw IllegalArgumentException("Cannot respond with ${data::class.simpleString()}!")
    }
}

data class Response(val status: HttpStatusCode, val data: Any?)

infix fun Any?.status(status: HttpStatusCode): Response =
    Response(status, this)
