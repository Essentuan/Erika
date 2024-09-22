package net.essentuan.erika.ktor.routes

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import net.essentuan.erika.db.struct.territories.Tile
import net.essentuan.erika.framework.db.commands.find
import net.essentuan.erika.ktor.GET
import net.essentuan.erika.ktor.Route
import net.essentuan.esl.map
import net.essentuan.esl.orElse
import net.essentuan.esl.rx.findFirst
import org.bson.types.ObjectId
import java.awt.image.BufferedImage
import java.util.concurrent.Executors

object TileRoute : Route.Container() {
    private val THREAD_POOL = Executors.newFixedThreadPool(10).asCoroutineDispatcher()

    @GET("tile")
    private suspend fun get(id: String, x: Int, y: Int, zoom: Int): BufferedImage? {
        return withContext(THREAD_POOL) {
            find<Tile.Set> {
                select from Tile.Set

                where {
                    "_id" eq ObjectId(id)
                }
            }.findFirst()
                .map {
                    it.draw(x, y, zoom)
                }
                .orElse(null)
        }
    }
}