package net.essentuan.erika.kord.framework.permissions

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Entity
import dev.kord.core.entity.Member
import dev.kord.core.entity.User
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toCollection
import net.essentuan.erika.framework.db.`object`.types.Singleton
import net.essentuan.esl.other.lock
import java.util.Collections
import java.util.LinkedList

private val EMPTY_TREE = Permission.Tree(Collections.emptyMap<String, Permission.Tree.Node>())
private const val ESSENTUAN_ID = 299319853389578240

object Permissions : Singleton() {
    private var entities = mutableMapOf<Snowflake, Permission.Tree>()

    operator fun get(entity: Entity): Permission.Tree? =
        entities[entity.id]

    val Entity.permissions: Permission.Tree
        get() =
            entities.lock { computeIfAbsent(id) { Permission.Tree() } }

    suspend infix fun User.has(permission: Permission): Boolean {
        if (id.value.toLong() == ESSENTUAN_ID)
            return true

        val tree = if (this is Member)
            Permission.Tree.combine(
                roles
                    .map { Permissions[it] }
                    .filterNotNull()
                    .toCollection(LinkedList())
                    .also {
                        it.offerFirst(Permissions[this] ?: return@also)
                    }
            )
        else
            Permissions[this] ?: EMPTY_TREE

        return tree[permission]
    }

    suspend infix fun User.missing(permission: Permission): Boolean =
        !(this has permission)
}