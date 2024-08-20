package net.essentuan.erika.db.struct.guild.member.search

import com.busted_moments.buster.api.Guild
import com.busted_moments.buster.api.Profile
import net.essentuan.erika.framework.db.Database.via
import net.essentuan.erika.framework.db.Search
import net.essentuan.erika.framework.db.api.table.Table
import net.essentuan.erika.framework.db.builders.Filter
import net.essentuan.erika.framework.db.commands.Find
import net.essentuan.erika.framework.db.`object`.Memory
import net.essentuan.erika.framework.db.`object`.types.Struct
import net.essentuan.erika.db.struct.guild.member.MemberModel
import net.essentuan.erika.db.struct.player.ProfileModel
import net.essentuan.erika.db.struct.player.search.SearchBuilder
import net.essentuan.erika.db.struct.player.search.search
import net.essentuan.esl.Result
import net.essentuan.esl.cast
import net.essentuan.esl.collections.multimap.Multimaps
import net.essentuan.esl.collections.multimap.hashSetValues
import net.essentuan.esl.iteration.extensions.iterate
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.Descriptor
import net.essentuan.esl.model.Model
import net.essentuan.esl.orNull
import net.essentuan.esl.other.lock
import net.essentuan.esl.rx.associateBy
import net.essentuan.esl.rx.filterNotNull
import net.essentuan.esl.rx.flatMap
import net.essentuan.esl.rx.iterate
import net.essentuan.esl.rx.map
import net.essentuan.esl.rx.publish
import net.essentuan.esl.rx.publisher
import org.bson.types.ObjectId
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import java.util.UUID

private class MemberSearch(
    downstream: Subscriber<in Search<ObjectId, MemberModel>>,
    val create: Boolean,
    val players: Map<ObjectId, ProfileModel>
) : Struct.Locator<ObjectId, MemberModel>(downstream, players.keys, 1) {
    override fun test(term: ObjectId): Boolean =
        true

    override fun groupBy(term: ObjectId): Int = 0

    override fun Memory.Locate.primary(group: Int, terms: Iterable<*>) {
        MemberModel::profile in terms
    }

    override fun Filter.primary(group: Int, terms: Iterable<*>) {
        MemberModel::profile in terms.map { (it as ObjectId) via ProfileModel }.asIterable()
    }

    override fun Sequence<Struct<*>>.filter(): Sequence<MemberModel> =
        filterIsInstance<MemberModel>()

    override val table: Table<MemberModel>
        get() = MemberModel
    override val descriptor: Descriptor<MemberModel, Json>
        get() = Companion.descriptor
    override val MemberModel.children: Array<Struct<*>>
        get() = emptyArray()

    override fun finish(struct: MemberModel): Array<ObjectId> =
        arrayOf(struct.profile.id)

    override fun Filter.secondary(struct: MemberModel) {
        MemberModel::profile eq (struct.profile.id via ProfileModel)
    }

    override fun Memory.Locate.secondary(struct: MemberModel) {
        MemberModel::profile eq struct.profile.id
    }

    override suspend fun invoke(term: ObjectId): MemberModel? {
        return if (create) MemberModel(players[term] ?: return null) else null
    }

    override fun Find<MemberModel>.config() {
        with {
            diskUse()
        }
    }

    companion object {
        val descriptor = Model.Companion.descriptor(MemberModel::class)
    }
}

fun Guild.Member.Companion.search(
    terms: MutableSet<out Any>,
    api: Boolean = true,
    create: Boolean = false
): Publisher<Search<Any, Guild.Member>> = publisher outer@{
    val search = Multimaps.hashKeys().hashSetValues<ObjectId, Any>()

    Profile.search(terms, api = api)
        .map { (term, result) ->
            result.cast<ProfileModel>().orNull()?.also {
                search.put(it.id, term)
            }
        }
        .filterNotNull()
        .associateBy { it.id }
        .run {
            Publisher {
                MemberSearch(
                    it,
                    create,
                    this@run
                ).subscribe()
            }
        }.flatMap { (id, member) ->
            search.lock { removeAll(id) }
                .asSequence()
                .map { it to member }
                .publish()
        } iterate { yield(it) }

    search.values() iterate { yield(it to Result.empty()) }
}

inline operator fun Guild.Member.Companion.invoke(
    api: Boolean = true,
    create: Boolean = false,
    block: SearchBuilder.() -> Unit
): Publisher<Search<Any, Guild.Member>> {
    val terms = mutableSetOf<Any>()

    SearchBuilder(terms).block()

    return search(terms, api, create)
}

operator fun Guild.Member.Companion.invoke(
    vararg usernames: String,
    api: Boolean = true,
    create: Boolean = false
) = search(
    usernames.toMutableSet(),
    api,
    create
)

operator fun Guild.Member.Companion.invoke(
    vararg uuids: UUID,
    api: Boolean = true,
    create: Boolean = false
) = search(
    uuids.toMutableSet(),
    api,
    create
)