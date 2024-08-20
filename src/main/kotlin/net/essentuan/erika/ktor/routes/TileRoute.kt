package net.essentuan.erika.ktor.routes

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import net.essentuan.erika.db.struct.territories.Tile
import net.essentuan.erika.ktor.GET
import net.essentuan.erika.ktor.Route
import java.awt.image.BufferedImage
import java.util.concurrent.Executors

object TileRoute : Route.Container() {
    private val THREAD_POOL = Executors.newFixedThreadPool(10).asCoroutineDispatcher()

    @GET("tile")
    private suspend fun get(id: String, x: Int, y: Int, zoom: Int): BufferedImage? {
        return withContext(THREAD_POOL) {
            Tile.Set.latest().draw(x, y, zoom)
        }
    }
}