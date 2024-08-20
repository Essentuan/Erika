package net.essentuan.erika.buster.events

import net.essentuan.erika.buster.Socket
import net.essentuan.erika.framework.events.Event

abstract class BusterEvent(
    val socket: Socket
) : Event() {
    class Connect(socket: Socket) : BusterEvent(socket)
    class Disconnect(socket: Socket) : BusterEvent(socket)
}