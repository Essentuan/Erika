package net.essentuan.erika.buster

import com.busted_moments.buster.protocol.clientbound.ClientboundFFAListPacket
import net.essentuan.erika.framework.db.`object`.types.Singleton
import net.essentuan.esl.other.lock

object FFAList : Singleton(), MutableSet<String> {
    private var ffas = mutableSetOf<String>()

    override fun add(element: String): Boolean =
        ffas.add(element)

    override fun addAll(elements: Collection<String>): Boolean =
        ffas.addAll(elements)

    override fun clear() =
        ffas.clear()

    override fun iterator(): MutableIterator<String> =
        ffas.iterator()

    override fun remove(element: String): Boolean =
        ffas.remove(element)

    override fun removeAll(elements: Collection<String>): Boolean =
        ffas.removeAll(elements)

    override fun retainAll(elements: Collection<String>): Boolean =
        ffas.retainAll(elements)

    override val size: Int
        get() = ffas.size

    override fun contains(element: String): Boolean =
        ffas.contains(element)

    override fun containsAll(elements: Collection<String>): Boolean =
        ffas.containsAll(elements)

    override fun isEmpty(): Boolean =
        ffas.isEmpty()

    fun update() {
        BusterService.broadcast(ClientboundFFAListPacket(lock { toSet() }))
    }
}