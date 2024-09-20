package net.essentuan.erika.ktor

import com.busted_moments.buster.Buster
import io.ktor.server.application.install
import io.ktor.server.engine.*
import io.ktor.server.netty.Netty
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.websocket.*
import net.essentuan.erika.arg
import net.essentuan.erika.framework.Service
import net.essentuan.esl.reflections.Reflections
import net.essentuan.esl.reflections.Types.Companion.objects
import net.essentuan.esl.reflections.extensions.instance

object KtorService : Service(), ApplicationEngine {
    private val engine: ApplicationEngine by this {
        embeddedServer(Netty, port = arg("port", 25569, String::toInt).value) {
            install(IgnoreTrailingSlash)

            install(WebSockets) {
                extensions {
                    install(Buster)
                }
            }

            Reflections.types
                .subtypesOf(Route::class)
                .objects()
                .map { it.instance }
                .filterNotNull()
                .forEach {
                    it.apply { this@embeddedServer.start() }
                }
        }.start(wait = false)
    } finally {
        stop()
    }
    override val environment: ApplicationEngineEnvironment
        get() = engine.environment

    override suspend fun resolvedConnectors(): List<EngineConnectorConfig> =
        engine.resolvedConnectors()

    override fun start(wait: Boolean): ApplicationEngine =
        engine.start(wait)

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) =
        engine.stop(gracePeriodMillis, timeoutMillis)
}