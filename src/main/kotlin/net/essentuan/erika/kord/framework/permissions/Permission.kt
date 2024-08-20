package net.essentuan.erika.kord.framework.permissions

import net.essentuan.erika.kord.framework.permissions.Permission.Tree.Node
import net.essentuan.esl.encoding.Encoder
import net.essentuan.esl.encoding.JsonBasedEncoder
import net.essentuan.esl.json.Json
import net.essentuan.esl.json.json
import net.essentuan.esl.json.type.AnyJson
import net.essentuan.esl.reflections.Annotations
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Type
import java.util.LinkedList

const val ALLOW = true
const val DENY = false

@JvmInline
value class Permission private constructor(
    private val parts: Array<String>
) {
    init {
        val i = parts.indexOf("*")

        require(i == -1 || i == parts.lastIndex)
    }

    constructor(node: String) : this(node.split('.').toTypedArray())

    val node: String
        get() = parts.joinToString(".")

    override fun toString(): String {
        return "Permission($node)"
    }

    class Tree(
        private val nodes: MutableMap<String, Node> = mutableMapOf()
    ) : Json.Model {
        operator fun set(permission: Permission, state: Boolean) {
            val parts = permission.parts

            var map = nodes

            for (i in parts.indices) {
                val part = parts[i]

                val node = map.computeIfAbsent(part) { Node() }

                if (i == parts.lastIndex)
                    node.state = state
                else
                    map = node.nodes
            }
        }

        operator fun get(permission: Permission): Boolean {
            val parts = permission.parts

            var state: Boolean = false
            var map = nodes

            for (i in parts.indices) {
                val part = parts[i]

                val all = map["*"]
                if (all != null)
                    all.state?.let {
                        state = it
                    }

                val next = map[part]

                when {
                    next == null -> return state
                    i == parts.lastIndex -> return next.state ?: state
                    else -> map = next.nodes
                }
            }

            return state
        }

        operator fun plus(other: Tree): Tree =
            combine(this, other)

        fun delete(permission: Permission) {
            val parts = permission.parts
            val queue = LinkedList<Pair<String, MutableMap<String, Node>>>()

            var map = nodes

            for (i in parts.indices) {
                val part = parts[i]

                queue.offerFirst(part to map)

                val node = map[part] ?: return

                when {
                    i != parts.lastIndex ->
                        map = node.nodes

                    else ->
                        node.state = null
                }
            }

            for ((key, container) in queue) {
                val node = container[key]!!

                if (node.state == null && node.nodes.isEmpty())
                    container.remove(key)
            }
        }

        class Node(
            var state: Boolean? = null,
            val nodes: MutableMap<String, Node> = mutableMapOf<String, Node>()
        ) {
            fun copy(): Node {
                val out = LinkedHashMap<String, Node>(nodes.size)

                for ((name, node) in nodes)
                    out[name] = node.copy()

                return Node(state, out)
            }

            companion object : JsonBasedEncoder<Node>() {
                private val encoder: Encoder<MutableMap<String, Node>, AnyJson> by lazy {
                    @Suppress("UNCHECKED_CAST")
                    Encoder(
                        MutableMap::class.java,
                        Annotations.empty(),
                        String::class.java,
                        Node::class.java
                    ) as Encoder<MutableMap<String, Node>, AnyJson>
                }

                override fun encode(
                    obj: Node,
                    flags: Set<Any>,
                    type: Class<*>,
                    element: AnnotatedElement,
                    vararg typeArgs: Type
                ): AnyJson? = json {
                    if (obj.state != null)
                        "state" to obj.state

                    if (obj.nodes.isNotEmpty())
                        "nodes" to encoder.encode(obj.nodes, flags, type, element)
                }

                override fun decode(
                    obj: AnyJson,
                    flags: Set<Any>,
                    type: Class<*>,
                    element: AnnotatedElement,
                    vararg typeArgs: Type
                ): Node? {
                    return Node(
                        obj["state"]?.raw as Boolean?,
                        encoder.decode(obj["nodes"]?.`as`(AnyJson::class) ?: Json(), flags, type, element) ?: mutableMapOf()
                    )
                }
            }
        }

        companion object {
            private fun Node.store(out: Node) {
                if (out.state == null)
                    out.state = state

                for ((key, node) in nodes)
                    node.store(out.nodes.computeIfAbsent(key) { Node() })
            }

            fun combine(vararg trees: Tree): Tree =
                combine(trees.asIterable())

            fun combine(trees: Iterable<Tree>): Tree {
                val out = mutableMapOf<String, Node>()

                for (tree in trees)
                    for ((key, node) in tree.nodes)
                        node.store(out.computeIfAbsent(key) { Node() })

                return Tree(out)
            }
        }
    }
}