package net.essentuan.erika.db.struct.territories.search

import net.essentuan.erika.framework.db.Search
import net.essentuan.erika.framework.db.api.table.Table
import net.essentuan.erika.framework.db.builders.Filter
import net.essentuan.erika.framework.db.`object`.Memory
import net.essentuan.erika.framework.db.`object`.types.Struct
import net.essentuan.erika.db.struct.territories.Tile
import net.essentuan.erika.fetch.athena.MapPart
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.Descriptor
import net.essentuan.esl.model.Model
import org.reactivestreams.Subscriber

class TileSearch(
    terms: Set<String>,
    private val parts: Map<String, MapPart>,
    downstream: Subscriber<in Search<String, Tile>>
) : Struct.Locator<String, Tile>(downstream, terms, 1) {
    override val Tile.children: Array<Struct<*>>
        get() = emptyArray()

    override fun test(term: String): Boolean =
        true

    override fun groupBy(term: String): Int =
        0

    override fun Memory.Locate.primary(group: Int, terms: Iterable<*>) {
        Tile::hash in terms
    }

    override fun Filter.primary(group: Int, terms: Iterable<*>) {
        Tile::hash in terms
    }

    override fun Sequence<Struct<*>>.filter(): Sequence<Tile> =
        filterIsInstance<Tile>()

    override val table: Table<Tile>
        get() = Tile
    override val descriptor: Descriptor<Tile, Json>
        get() = Companion.descriptor

    override fun finish(struct: Tile): Array<String> =
        arrayOf(struct.hash)

    override fun Filter.secondary(struct: Tile) {
        Tile::hash eq struct.hash
    }

    override fun Memory.Locate.secondary(struct: Tile) {
        Tile::hash eq struct.hash
    }

    override suspend fun invoke(term: String): Tile? =
        parts[term]?.download()

    companion object {
        private val descriptor = Model.descriptor(Tile::class)
    }
}