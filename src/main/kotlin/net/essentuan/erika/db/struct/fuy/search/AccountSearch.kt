package net.essentuan.erika.db.struct.fuy.search

import com.busted_moments.buster.api.Account
import com.busted_moments.buster.api.Guild
import net.essentuan.erika.framework.db.Database.via
import net.essentuan.erika.framework.db.Search
import net.essentuan.erika.framework.db.api.table.Table
import net.essentuan.erika.framework.db.builders.Filter
import net.essentuan.erika.framework.db.commands.Find
import net.essentuan.erika.framework.db.`object`.Memory
import net.essentuan.erika.framework.db.`object`.types.Struct
import net.essentuan.erika.db.struct.fuy.BusterAccount
import net.essentuan.erika.db.struct.guild.member.MemberModel
import net.essentuan.erika.db.struct.guild.member.search.search
import net.essentuan.erika.db.struct.player.search.SearchBuilder
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

private class AccountSearch(
    downstream: Subscriber<in Search<ObjectId, BusterAccount>>,
    val create: Boolean,
    val members: Map<ObjectId, MemberModel>
) : Struct.Locator<ObjectId, BusterAccount>(downstream, members.keys, 1) {
    override fun test(term: ObjectId): Boolean =
        true

    override fun groupBy(term: ObjectId): Int = 0

    override fun Memory.Locate.primary(group: Int, terms: Iterable<*>) {
        BusterAccount::member in terms
    }

    override fun Filter.primary(group: Int, terms: Iterable<*>) {
        BusterAccount::member in terms.map { (it as ObjectId) via MemberModel }.asIterable()
    }

    override fun Sequence<Struct<*>>.filter(): Sequence<BusterAccount> =
        filterIsInstance<BusterAccount>()

    override val table: Table<BusterAccount>
        get() = BusterAccount
    override val descriptor: Descriptor<BusterAccount, Json>
        get() = Companion.descriptor
    override val BusterAccount.children: Array<Struct<*>>
        get() = emptyArray()

    override fun finish(struct: BusterAccount): Array<ObjectId> =
        arrayOf(struct.member.id)

    override fun Filter.secondary(struct: BusterAccount) {
        BusterAccount::member eq (struct.member.id via MemberModel)
    }

    override fun Memory.Locate.secondary(struct: BusterAccount) {
        BusterAccount::member eq struct.member.id
    }

    override suspend fun invoke(term: ObjectId): BusterAccount? {
        if (!create)
            return null

        val member = members[term] ?: return null
        return BusterAccount(
            member.profile,
            member
        )
    }

    override fun Find<BusterAccount>.config() {
        with {
            diskUse()
        }
    }

    companion object {
        val descriptor = Model.Companion.descriptor(BusterAccount::class)
    }
}

fun Account.Companion.search(
    terms: MutableSet<out Any>,
    api: Boolean = true,
    create: Boolean = false
): Publisher<Search<Any, Account>> = publisher outer@{
    val search = Multimaps.hashKeys().hashSetValues<ObjectId, Any>()

    Guild.Member.search(terms, api = api, create = create)
        .map { (term, result) ->
            result.cast<MemberModel>().orNull()?.also {
                search.put(it.id, term)
            }
        }
        .filterNotNull()
        .associateBy { it.id }
        .run {
            Publisher {
                AccountSearch(
                    it,
                    create,
                    this@run
                ).subscribe()
            }
        }.flatMap { (id, account) ->
            search.lock { removeAll(id) }
                .asSequence()
                .map { it to account }
                .publish()
        } iterate { yield(it) }

    search.values() iterate { yield(it to Result.empty()) }
}

inline operator fun Account.Companion.invoke(
    api: Boolean = true,
    create: Boolean = false,
    block: SearchBuilder.() -> Unit
): Publisher<Search<Any, Account>> {
    val terms = mutableSetOf<Any>()

    SearchBuilder(terms).block()

    return search(terms, api, create)
}

operator fun Account.Companion.invoke(
    vararg usernames: String,
    api: Boolean = true,
    create: Boolean = false
) = search(
    usernames.toMutableSet(),
    api,
    create
)

operator fun Account.Companion.invoke(
    vararg uuids: UUID,
    api: Boolean = true,
    create: Boolean = false
) = search(
    uuids.toMutableSet(),
    api,
    create
)