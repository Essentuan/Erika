package net.essentuan.erika.ktor.routes

import net.essentuan.erika.Resources
import net.essentuan.erika.ktor.GET
import net.essentuan.erika.ktor.Route
import net.essentuan.erika.ktor.annotations.Wildcard
import java.nio.file.Path
import kotlin.io.path.div

object AssetRoute : Route.Container() {
    @GET("assets")
    private fun get(@Wildcard resource: String): Path =
        resource.split("/").fold(Resources / "assets", Path::resolve)
}