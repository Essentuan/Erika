package net.essentuan.erika.buster

import com.busted_moments.buster.protocol.Packet
import net.essentuan.esl.reflections.extensions.extends
import net.essentuan.esl.reflections.extensions.get
import net.essentuan.esl.reflections.extensions.instance
import net.essentuan.esl.reflections.extensions.javaClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.extensionReceiverParameter
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.jvm.isAccessible

interface PacketListener {
    val privileged: Boolean
    val packet: Class<*>

    suspend operator fun invoke(socket: Socket, packet: Packet)
}

class MethodPacketListener(
    val function: KFunction<*>,
    val ref: Any?,
    override val packet: Class<*>,
    override val privileged: Boolean
) : PacketListener {
    init {
        function.isAccessible = true
    }

    val size = function.parameters.size

    override suspend operator fun invoke(socket: Socket, packet: Packet) {
        val args = arrayOfNulls<Any?>(size)

        if (size == 3)
            args[0] = ref

        args[size - 2] = socket
        args[size - 1] = packet

        if (function.isSuspend)
            function.callSuspend(*args)
        else
            function.call(*args)
    }

    companion object {
        operator fun invoke(func: KFunction<*>): PacketListener? {
            if (func.parameters.size !in 2..3 || func.extensionReceiverParameter?.type?.javaClass != Socket::class.java)
                return null

            val type = func.parameters.firstOrNull { it.type.javaClass extends Packet::class }?.type?.javaClass ?: return null
            return if (func.parameters.size == 2)
                MethodPacketListener(
                    func,
                    null,
                    type,
                    func[Listener::class]!!.value
                )
            else
                MethodPacketListener(
                    func,
                    func.instanceParameter?.type?.javaClass?.instance ?: return null,
                    type,
                    func[Listener::class]!!.value
                )
        }
    }
}