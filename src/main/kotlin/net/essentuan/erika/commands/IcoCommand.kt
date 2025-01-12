@file:Command("ico")

package net.essentuan.erika.commands

import com.busted_moments.buster.api.Profile
import com.essentuan.acf.core.annotations.Argument
import com.essentuan.acf.core.annotations.Command
import com.essentuan.acf.core.annotations.Subcommand
import com.mojang.brigadier.context.CommandContext
import net.essentuan.erika.buster.listeners.IcoEvent
import net.essentuan.erika.buster.listeners.Raid
import net.essentuan.erika.db.struct.player.search.invoke
import net.essentuan.erika.framework.console.SystemSource
import net.essentuan.erika.inline
import net.essentuan.esl.orNull
import net.essentuan.esl.other.lock
import net.essentuan.esl.rx.first

@Subcommand("update")
fun CommandContext<SystemSource>.update(
    @Argument("player") player: String,
    @Argument("raid") type: Raid.Type,
    @Argument("amount") amount: Int
) {
    inline {
        val profile = Profile(player)
            .first()
            .second
            .orNull()

        if (profile == null) {
            source.message("Could not find '$player'!")
            return@inline
        }

        val store = IcoEvent.get(profile.uuid)

        if (store == null) {
            source.message("${profile.name} is not in ICo!")
            return@inline
        }

        source.message("${profile.name}'s completions for ${type.display} has been updated to ${store.lock { increment(type, amount) }}")
    }
}