package net.essentuan.erika.buster.listeners

import com.busted_moments.buster.api.Account
import com.busted_moments.buster.protocol.clientbound.*
import com.busted_moments.buster.protocol.requests.AuthRequest
import net.essentuan.erika.buster.BusterService
import net.essentuan.erika.buster.FFAList
import net.essentuan.erika.buster.LOGGER
import net.essentuan.erika.buster.Listener
import net.essentuan.erika.buster.Socket
import net.essentuan.erika.buster.events.BusterEvent
import net.essentuan.erika.db.struct.fuy.BusterAccount
import net.essentuan.erika.db.struct.fuy.search.invoke
import net.essentuan.erika.fetch.mojang.yggdrasil.hasJoined
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.observers.guilds.list.Guilds
import net.essentuan.erika.observers.player.worlds.WorldList
import net.essentuan.erika.observers.territories.TerritoryList
import net.essentuan.erika.observers.territories.TerritoryList.external
import net.essentuan.esl.fetch.fetch
import net.essentuan.esl.flatMap
import net.essentuan.esl.ifPresentOrElse
import net.essentuan.esl.other.lock
import net.essentuan.esl.rx.findFirst

@Listener(false)
private suspend fun Socket.on(request: AuthRequest) {
    if (isOpen)
        return

    val profile = fetch { hasJoined(username, id) }

    if (profile == null || profile.uuid != uuid) {
        if (profile != null && profile.uuid != uuid)
            Logging.error("$username provided an invalid UUID! ($uuid instead of ${profile.uuid})")
        else
            Logging.error("$username provided invalid login credentials!")

        close()
        return
    }

    Account(
        api = true,
        create = true
    ) {
        +uuid
    }.findFirst()
        .flatMap { it.second }
        .ifPresentOrElse(
            {
                BusterService.sockets.lock {
                    put(uuid, this@on.apply {
                        this.account = it as BusterAccount
                    })
                }?.close()

                request.fulfill(this.account.external())

                send(ClientboundSetAttackTimerMarginOfErrorPacket(BusterService.marginOfError))
                send(ClientboundMapPacket(TerritoryList.external()))
                send(ClientboundFFAListPacket(FFAList.lock { toSet() }))
                send(ClientboundGuildListPacket(Guilds.lock { external() }))
                send(ClientboundWorldListPacket(WorldList.lock { external() }))

                LOGGER.info("$username ($uuid) has logged in.")
                BusterEvent.Connect(this).post()
            }
        ) {
            if (it != null)
                Logging.error("Error loading profile for $username!", it)
            else
                Logging.error("Could not find profile for $username!")

            close()
        }
}
