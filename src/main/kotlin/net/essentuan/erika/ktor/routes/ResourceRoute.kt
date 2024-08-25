package net.essentuan.erika.ktor.routes

import net.essentuan.erika.Resources
import net.essentuan.erika.ktor.GET
import net.essentuan.erika.ktor.Route
import net.essentuan.erika.ktor.annotations.Wildcard
import java.nio.file.Path
import kotlin.io.path.div

object ResourceRoute : Route.Container() {
    @GET("resources")
    private fun get(@Wildcard resource: String): Path? =
        resource.split("/").fold(Resources / "web", Path::resolve)
}