package net.essentuan.erika.db.struct.player.search

import com.busted_moments.buster.api.PlayerType
import com.busted_moments.buster.api.Profile
import com.busted_moments.buster.api.Profile.Companion.isValid
import com.mongodb.client.model.CollationStrength
import net.essentuan.erika.framework.db.Search
import net.essentuan.erika.framework.db.api.table.Table
import net.essentuan.erika.framework.db.builders.Filter
import net.essentuan.erika.framework.db.commands.Find
import net.essentuan.erika.framework.db.`object`.Memory
import net.essentuan.erika.framework.db.`object`.types.Struct
import net.essentuan.erika.db.struct.player.ProfileModel
import net.essentuan.erika.fetch.mojang.username
import net.essentuan.esl.fetch.fetch
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.Descriptor
import net.essentuan.esl.model.Model
import net.essentuan.esl.other.lock
import net.essentuan.esl.rx.iterator
import net.essentuan.esl.string.extensions.isUUID
import net.essentuan.esl.string.extensions.toUUID
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import java.util.UUID

@JvmInline
value class SearchBuilder(
    private val terms: MutableSet<Any>
) {
    operator fun String.unaryPlus() {
        synchronized(terms) {
            if (isUUID())
                terms.add(toUUID())
            else
                terms.add(this.lowercase())
        }
    }

    operator fun UUID.unaryPlus(): Unit =
        terms.lock { add(this@unaryPlus) }

    operator fun PlayerType.unaryPlus(): Unit =
        +uuid

    operator fun Array<*>.unaryPlus() {
        for (e in this) {
            when (e) {
                is String -> +e
                is UUID -> +e
                is PlayerType -> +e.uuid
            }
        }
    }

    operator fun Iterable<*>.unaryPlus() {
        for (e in this) {
            when (e) {
                is String -> +e
                is UUID -> +e
                is PlayerType -> +e.uuid
            }
        }
    }

    operator fun Sequence<*>.unaryPlus() {
        for (e in this) {
            when (e) {
                is String -> +e
                is UUID -> +e
                is PlayerType -> +e.uuid
            }
        }
    }

    suspend operator fun Publisher<*>.unaryPlus() {
        for (e in this) {
            when (e) {
                is String -> +e
                is UUID -> +e
                is PlayerType -> +e.uuid
            }
        }
    }

    operator fun Iterator<*>.unaryPlus() {
        for (e in this) {
            when (e) {
                is String -> +e
                is UUID -> +e
                is PlayerType -> +e.uuid
            }
        }
    }
}

private const val UUID_GROUP = 0
private const val USERNAME_GROUP = 1

private class ProfileSearch(
    val api: Boolean,
    downstream: Subscriber<in Search<Any, ProfileModel>>,
    terms: Set<Any>
) : Struct.Locator<Any, ProfileModel>(downstream, terms, 2) {
    override fun test(term: Any): Boolean =
        term !is String || isValid(term)

    override fun map(term: Any): Any =
        if (term is String) term.lowercase() else term

    override fun groupBy(term: Any): Int =
        if (term is UUID) UUID_GROUP else USERNAME_GROUP

    override fun Memory.Locate.primary(group: Int, terms: Iterable<*>) {
        when (group) {
            UUID_GROUP -> {
                ProfileModel::uuid in terms
            }

            USERNAME_GROUP -> {
                ProfileModel::name in terms
            }
        }
    }

    override fun Filter.primary(group: Int, terms: Iterable<*>) {
        when (group) {
            UUID_GROUP -> {
                ProfileModel::uuid in terms
            }

            USERNAME_GROUP -> {
                ProfileModel::name in terms
            }
        }
    }

    override fun Find<ProfileModel>.config() {
        collation {
            strength { CollationStrength.SECONDARY }
        }

        with {
            diskUse()
        }
    }

    override suspend fun invoke(term: Any): ProfileModel? =
        if (api)
            fetch { username(term.toString()) }?.run(::ProfileModel)
        else
            null

    override fun Memory.Locate.secondary(struct: ProfileModel) {
        ProfileModel::uuid eq struct.uuid
    }

    override fun Filter.secondary(struct: ProfileModel) {
        ProfileModel::uuid eq struct.uuid
    }

    override fun finish(struct: ProfileModel): Array<Any> =
        arrayOf(struct.name, struct.uuid)

    override fun Sequence<Struct<*>>.filter(): Sequence<ProfileModel> =
        filterIsInstance<ProfileModel>()

    override val ProfileModel.children: Array<Struct<*>>
        get() = arrayOf(playtime)

    override val table: Table<ProfileModel>
        get() = ProfileModel
    override val descriptor: Descriptor<ProfileModel, Json>
        get() = Companion.descriptor

    companion object {
        val descriptor = Model.Companion.descriptor(ProfileModel::class)
    }
}

fun Profile.Companion.search(
    terms: MutableSet<out Any>,
    api: Boolean = true
): Publisher<Search<Any, Profile>> = Publisher {
    ProfileSearch(
        api,
        it,
        terms,
    ).subscribe()
}

inline operator fun Profile.Companion.invoke(
    api: Boolean = true,
    block: SearchBuilder.() -> Unit
): Publisher<Search<Any, Profile>> {
    val terms = mutableSetOf<Any>()

    SearchBuilder(terms).block()

    return search(terms, api)
}

operator fun Profile.Companion.invoke(
    vararg usernames: String,
    api: Boolean = true
) = search(usernames.toMutableSet(), api)

operator fun Profile.Companion.invoke(
    vararg uuids: UUID,
    api: Boolean = true
) = search(uuids.toMutableSet(), api)