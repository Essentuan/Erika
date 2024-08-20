package net.essentuan.erika.db.struct.guild.search

import com.busted_moments.buster.api.Guild
import com.busted_moments.buster.api.GuildType
import net.essentuan.erika.framework.db.Search
import net.essentuan.erika.framework.db.api.table.Table
import net.essentuan.erika.framework.db.builders.Filter
import net.essentuan.erika.framework.db.`object`.Memory
import net.essentuan.erika.framework.db.`object`.types.Struct
import net.essentuan.erika.db.struct.guild.GuildModel
import net.essentuan.erika.fetch.wynncraft.guild.guild
import net.essentuan.erika.observers.guilds.GuildService
import net.essentuan.erika.observers.guilds.list.Guilds
import net.essentuan.erika.observers.guilds.list.isDeleted
import net.essentuan.esl.Rating
import net.essentuan.esl.cast
import net.essentuan.esl.comparing.equals
import net.essentuan.esl.fetch.fetch
import net.essentuan.esl.filterNot
import net.essentuan.esl.ifPresent
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.Descriptor
import net.essentuan.esl.model.Model
import net.essentuan.esl.rx.iterator
import net.essentuan.esl.rx.map
import net.essentuan.esl.rx.merge
import net.essentuan.esl.string.extensions.isUUID
import net.essentuan.esl.string.extensions.toUUID
import net.essentuan.esl.time.duration.Duration
import net.essentuan.esl.time.duration.minutes
import net.essentuan.esl.time.extensions.timeSince
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import java.util.UUID

typealias Out = Search<Any, Guild>

data class Term(
    val search: Any,
    val uuid: UUID?
) {
    override fun hashCode(): Int =
        search.hashCode()

    override fun equals(other: Any?): Boolean =
        equals(other) { _, obj -> search == obj.search }
}

private const val UUID_GROUP = 0
private const val STRING_GROUP = 1

class GuildSearch(
    downstream: Subscriber<in Search<Term, Guild>>,
    terms: Set<Term>,
    val api: Boolean
) : Struct.Locator<Term, GuildModel>(downstream, terms, 2) {
    override val GuildModel.children: Array<Struct<*>>
        get() = emptyArray()

    override fun test(term: Term): Boolean {
        if (term.search !is String)
            return true

        return Guild.Names.isValid(term.search) || Guild.Tags.isValid(term.search)
    }

    override fun groupBy(term: Term): Int =
        if (term.uuid != null) UUID_GROUP else STRING_GROUP

    override fun map(term: Term): Term =
        if (term.uuid == null) term else Term(term.uuid, term.uuid)

    override fun Memory.Locate.primary(group: Int, terms: Iterable<*>) {
        if (group == UUID_GROUP)
            GuildModel::uuid in terms.map { (it as Term).uuid!! }.asIterable()
    }

    override fun Filter.primary(group: Int, terms: Iterable<*>) {
        if (group == UUID_GROUP)
            GuildModel::uuid in terms.map { (it as Term).uuid!! }.asIterable()
    }

    override fun Sequence<Struct<*>>.filter(): Sequence<GuildModel> =
        filterIsInstance<GuildModel>()

    override val table: Table<GuildModel>
        get() = GuildModel
    override val descriptor: Descriptor<GuildModel, Json>
        get() = Companion.descriptor

    override fun finish(struct: GuildModel): Array<Term> =
        arrayOf(Term(struct.uuid, struct.uuid))

    override fun Filter.secondary(struct: GuildModel) {
        GuildModel::uuid eq struct.uuid
    }

    override fun Memory.Locate.secondary(struct: GuildModel) {
        GuildModel::uuid eq struct.uuid
    }

    override suspend fun invoke(term: Term): GuildModel? {
        if (!api)
            return null

        return GuildModel.create(
            fetch {
                if (term.search is UUID)
                    return@fetch guild(uuid = term.search)

                if (Guild.Names.isValid(term.search as String)) {
                    val guild = guild(name = term.search)

                    if (guild != null)
                        return@fetch guild
                }

                if (Guild.Tags.isValid(term.search)) {
                    val guild = guild(tag = term.search)

                    if (guild != null)
                        return@fetch guild
                }

                null
            } ?: return null
        )
    }

    private companion object {
        val descriptor = Model.Companion.descriptor(GuildModel::class)
    }

    class Builder(
        internal val terms: MutableSet<Term>,
        private val deleted: Boolean
    ) {
        @Synchronized
        private infix fun GuildType?.to(term: Any) =
            terms.add(Term(term, this?.uuid))

        operator fun String.unaryPlus() {
            if (isUUID())
                +toUUID()

            Guilds.find(
                this,
                deleted
            ) to this
        }

        operator fun UUID.unaryPlus() {
            Guilds[this] to this
        }

        operator fun GuildType.unaryPlus() {
            +uuid
        }

        operator fun Array<*>.unaryPlus() {
            for (e in this) {
                when (e) {
                    is String -> +e
                    is UUID -> +e
                    is GuildType -> +e.uuid
                }
            }
        }

        operator fun Iterable<*>.unaryPlus() {
            for (e in this) {
                when (e) {
                    is String -> +e
                    is UUID -> +e
                    is GuildType -> +e.uuid
                }
            }
        }

        operator fun Sequence<*>.unaryPlus() {
            for (e in this) {
                when (e) {
                    is String -> +e
                    is UUID -> +e
                    is GuildType -> +e.uuid
                }
            }
        }

        suspend operator fun Publisher<*>.unaryPlus() {
            for (e in this) {
                when (e) {
                    is String -> +e
                    is UUID -> +e
                    is GuildType -> +e.uuid
                }
            }
        }

        operator fun Iterator<*>.unaryPlus() {
            for (e in this) {
                when (e) {
                    is String -> +e
                    is UUID -> +e
                    is GuildType -> +e.uuid
                }
            }
        }
    }
}

fun Guild.Companion.search(
    terms: Set<Term>,
    api: Boolean,
    update: Boolean,
    deleted: Boolean,
    priority: Rating,
    expiry: Duration
): Publisher<Search<Any, Guild>> =
    Publisher {
        GuildSearch(
            it,
            terms,
            api
        ).subscribe()
    }.run {
        if (update)
            merge { (term, result) ->
                result.cast<GuildModel>().ifPresent { guild ->
                    if (guild.metadata.modified.timeSince() >= expiry)
                        GuildService.enqueue(guild, priority = priority).await()
                }

                term.search to result
            }
        else
            map { (term, result) -> term.search to result }
    }.run {
        if (!deleted)
            map { (search, result) -> search to result.filterNot { it.isDeleted } }
        else
            this
    }

inline operator fun Guild.Companion.invoke(
    api: Boolean = true,
    update: Boolean = true,
    deleted: Boolean = false,
    priority: Rating = Rating.NORMAL,
    expiry: Duration = 15.minutes,
    block: GuildSearch.Builder.() -> Unit
): Publisher<Search<Any, Guild>> {
    val terms = mutableSetOf<Term>()

    GuildSearch.Builder(terms, deleted).apply(block)

    return search(terms, api, update, deleted, priority, expiry)
}

operator fun Guild.Companion.invoke(
    vararg strings: String,
    api: Boolean = true,
    update: Boolean = true,
    deleted: Boolean = false,
    priority: Rating = Rating.NORMAL,
    expiry: Duration = 15.minutes,
) = Guild(api, update, deleted, priority, expiry) { +strings }

operator fun Guild.Companion.invoke(
    vararg uuids: UUID,
    api: Boolean = true,
    update: Boolean = true,
    deleted: Boolean = false,
    priority: Rating = Rating.NORMAL,
    expiry: Duration = 15.minutes,
) = Guild(api, update, deleted, priority, expiry) { +uuids }