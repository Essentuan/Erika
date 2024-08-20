package net.essentuan.erika.db.struct.territories

import com.busted_moments.buster.api.Territory
import net.essentuan.erika.framework.db.api.table.schema.Attribute
import net.essentuan.erika.framework.db.api.table.schema.Index
import net.essentuan.erika.framework.db.commands.find
import net.essentuan.erika.framework.db.`object`.Metadata
import net.essentuan.erika.framework.db.`object`.store
import net.essentuan.erika.framework.db.`object`.types.Struct
import net.essentuan.erika.framework.db.table.StandardTable
import net.essentuan.erika.framework.events.Event
import net.essentuan.erika.db.struct.territories.search.TileSearch
import net.essentuan.erika.fetch.athena.MapPart
import net.essentuan.erika.fetch.athena.map
import net.essentuan.esl.coroutines.blocking
import net.essentuan.esl.fetch.fetch
import net.essentuan.esl.model.annotations.Ignored
import net.essentuan.esl.orNull
import net.essentuan.esl.rx.filterNotNull
import net.essentuan.esl.rx.findFirst
import net.essentuan.esl.rx.map
import net.essentuan.esl.rx.toList
import net.essentuan.esl.scheduling.annotations.Every
import net.essentuan.esl.scheduling.annotations.Lifetime
import org.reactivestreams.Publisher
import java.awt.image.BufferedImage
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow

private const val ZOOM_OFFSET = 3
private const val MIN_ZOOM = 6
private const val TILE_SIZE = 500

data class Tile(
    val name: String,
    val start: IntPair,
    val hash: String,
    private val image: BufferedImage
) : Struct<Tile.Table>() {
    val x: Int
        get() = start.first

    val y: Int
        get() = start.second

    val width: Int
        get() = image.width

    val height: Int
        get() = image.height

    operator fun get(x: Int, y: Int): Int =
        image.getRGB(x - start.first, y - start.second)

    operator fun contains(pos: Territory.Pos): Boolean =
        contains(pos.x, pos.z)

    fun contains(x: Int, y: Int): Boolean =
        x >= start.first && x < (start.first + width) && y >= start.second && y < (start.second + height)

    companion object Table : StandardTable<Tile>() {
        init {
            schema {
                Tile::hash {
                    +Attribute.UNIQUE

                    index {
                        +Index.Type.ASCENDING
                    }
                }
            }

            store {
                +Tile::hash
            }
        }

        operator fun invoke(
            hashes: Iterable<String>,
            parts: Map<String, MapPart> = emptyMap()
        ) = Publisher {
            TileSearch(
                if (hashes is kotlin.collections.Set<String>) hashes else hashes.toSet(),
                parts,
                it
            ).subscribe()
        }
    }

    data class Set(
        val tiles: List<Tile>,
    ) : Struct<Set.Table>(), Iterable<Tile> {
        @Ignored
        val grid = object : Grid<Chunk>(tiles, 50) {
            override fun allocate(size: Int, init: (Int) -> Chunk?): Array<Chunk?> =
                Array(size, init)

            override fun cell(tiles: kotlin.collections.Set<Tile>): Chunk =
                Chunk(tiles.toList())
        }

        @Ignored
        val nativeZoom = max(
            ceil(log2(grid.width.toDouble() / TILE_SIZE)),
            ceil(log2(grid.height.toDouble() / TILE_SIZE))
        ).toInt()

        @Ignored
        val zoom = (nativeZoom + ZOOM_OFFSET).let { maxZoom -> (maxZoom - MIN_ZOOM)..maxZoom }

        private fun real(start: Int, pos: Int, origin: Int, scale: Double): Int =
            start + ((pos + origin) / scale).toInt()

        fun draw(x: Int, y: Int, zoom: Int): BufferedImage? {
            if (zoom !in this.zoom)
                return null

            val scale = 2.0.pow(zoom - nativeZoom)

            val result = BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB)

            val originX = x * TILE_SIZE
            val originY = y * TILE_SIZE

            var tile: Tile? = null

            for (tileY in 0..<500)
                for (tileX in 0..<500) {
                    val realX = real(grid.start.first, tileX, originX, scale)
                    val realY = real(grid.start.second, tileY, originY, scale)

                    if (!grid.contains(realX, realY))
                        continue

                    if (tile?.contains(realX, realY) == false)
                        tile = null

                    if (tile == null)
                        tile = this[realX, realY] ?: continue

                    result.setRGB(tileX, tileY, tile[realX, realY])
                }

            return result
        }

        operator fun get(x: Int, y: Int): Tile? {
            val tiles = grid[x, y]?.get(x, y)

            return when {
                tiles.isNullOrEmpty() -> null
                tiles.size == 1 -> if (tiles[0].contains(x, y)) tiles[0] else null
                else -> tiles.firstOrNull { it.contains(x, y) }
            }
        }

        override fun iterator(): Iterator<Tile> =
            tiles.iterator()

        companion object Table : StandardTable<Set>() {
            init {
                schema {
                    "metadata" {
                        "created" {
                            index {
                                +Index.Type.DESCENDING
                            }
                        }
                    }
                }
            }

            private var latest: Set? = null

            private var queue: MutableList<Continuation<Set>> = mutableListOf()

            suspend fun latest() = latest ?: suspendCoroutine {
                synchronized(this@Table) {
                    if (latest != null)
                        it.resume(latest!!)
                    else
                        queue.add(it)
                }
            }

            @Every(minutes = 10.0)
            @Lifetime(minutes = 1.0)
            private suspend fun update() {
                if (latest == null)
                    latest = blocking {
                        find {
                            select from Set

                            sort {
                                Tile.Set::metadata {
                                    -Metadata::created
                                }
                            }

                            limit to 1
                        }.findFirst().orNull()
                    }

                val map = fetch { map() } ?: return
                val hashes = map.asSequence().map { it.hash }.toSet()

                if (
                    map.size == latest?.tiles?.size &&
                    hashes == latest?.tiles?.asSequence()?.map { it.hash }?.toSet()
                )
                    return

                val tiles = Tile(
                    hashes,
                    parts = map.associateBy { it.hash }
                ).map { (_, result) -> result.orNull() }.filterNotNull().toList()
                Set(tiles).also {
                    it.enqueue()
                }.run {
                    synchronized(this@Table) {
                        latest = this

                        queue.also {
                            queue = mutableListOf()
                        }
                    }.forEach { it.resume(this) }

                    NewTileSetEvent(this).post()
                }
            }
        }
    }
}

class Chunk(tiles: List<Tile>) : Grid<List<Tile>>(tiles, 5) {
    override fun allocate(size: Int, init: (Int) -> List<Tile>?): Array<List<Tile>?> =
        Array(size, init)

    override fun cell(tiles: kotlin.collections.Set<Tile>): List<Tile> =
        tiles.toList()
}

abstract class Grid<CELL : Any>(
    tiles: List<Tile>,
    private val gridSize: Int,
) {
    val start: IntPair
    val end: IntPair
    val stepX: Int
    val stepY: Int

    val size: IntPair
        get() = end - start

    val width: Int
        get() = size.first

    val height: Int
        get() = size.second

    private val cells: Array<CELL?>

    init {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE

        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE

        for (tile in tiles) {
            if (minX > tile.x)
                minX = tile.x

            if (minY > tile.y)
                minY = tile.y

            if (maxX < (tile.x + tile.width))
                maxX = tile.x + tile.width

            if (maxY < (tile.y + tile.height))
                maxY = tile.y + tile.height
        }

        start = IntPair(minX, minY)
        end = IntPair(maxX, maxY)

        stepX = width / (gridSize - 1)
        stepY = height / (gridSize - 1)

        val chunks = arrayOfNulls<MutableSet<Tile>>(gridSize * gridSize)

        for (tile in tiles) {
            val startCellX = cellX(tile.x)
            val startCellY = cellY(tile.y)

            val endCellX = cellX(tile.x + tile.width)
            val endCellY = cellY(tile.y + tile.height)

            if (startCellX == endCellX && startCellY == endCellY) {
                val i = -cellAt(startCellX, startCellY)

                (chunks[i] ?: mutableSetOf<Tile>().also { chunks[i] = it }).run { add(tile) }
            } else
                for (cellY in startCellY..endCellY) {
                    for (cellX in startCellX..endCellX) {
                        val i = cellAt(cellX, cellY)

                        (chunks[i] ?: mutableSetOf<Tile>().also { chunks[i] = it }).run { add(tile) }
                    }
                }
        }

        this.cells = allocate(gridSize * gridSize) {
            chunks[it]?.run { cell(this) }
        }
    }

    protected abstract fun allocate(size: Int, init: (Int) -> CELL?): Array<CELL?>

    protected abstract fun cell(tiles: kotlin.collections.Set<Tile>): CELL

    private fun cellAt(cellX: Int, cellY: Int): Int =
        cellX + (cellY * gridSize)

    private fun cellX(x: Int) =
        (x - start.first) / stepX

    private fun cellY(y: Int) =
        (y - start.second) / stepY

    private fun index(x: Int, y: Int): Int {
        if (!contains(x, y))
            return -1

        return cellAt(cellX(x), cellY(y))
    }

    operator fun get(x: Int, y: Int): CELL? {
        val index = index(x, y)

        return if (index == -1)
            return null
        else
            cells[index]
    }

    operator fun contains(pos: Territory.Pos): Boolean =
        contains(pos.x, pos.z)

    fun contains(x: Int, y: Int): Boolean =
        x >= start.first && x < end.first && y >= start.second && y < end.second
}

class NewTileSetEvent(
    val tiles: Tile.Set
) : Event()