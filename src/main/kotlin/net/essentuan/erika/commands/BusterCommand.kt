@file:Command("buster")

package net.essentuan.erika.commands

import com.essentuan.acf.core.annotations.Argument
import com.essentuan.acf.core.annotations.Command
import com.essentuan.acf.core.annotations.Subcommand
import com.mojang.brigadier.context.CommandContext
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.effectiveName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import net.essentuan.erika.buster.BusterService
import net.essentuan.erika.buster.BusterService.Constants.releaseManager
import net.essentuan.erika.buster.BusterService.Constants.trustedCutoff
import net.essentuan.erika.buster.releases.repos.FuyRepo
import net.essentuan.erika.buster.releases.repos.Release
import net.essentuan.erika.buster.releases.repos.WynntilsRepo
import net.essentuan.erika.fetch.github.modules.GhRelease
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.framework.events.annotations.Subscribe
import net.essentuan.erika.inline
import net.essentuan.erika.kord.kord
import net.essentuan.esl.coroutines.timeout
import kotlin.time.Duration.Companion.seconds

@Subcommand("trusted")
fun CommandContext<*>.trusted(
    @Argument("cutoff") cutoff: Int
) {
    trustedCutoff = cutoff
    Logging.info("The cutoff for resource updates has been set to $cutoff!")
}

@Subcommand("sessions")
fun CommandContext<*>.sessions() {
    Logging.info("There are currently ${BusterService.sockets.size} sockets connected to Buster!")
}

@Subcommand("manager")
@OptIn(ExperimentalCoroutinesApi::class)
fun CommandContext<*>.master(
    @Argument("manager") manager: Long
) {
    if (manager == -1L) {
        releaseManager = null
        Logging.info("The Buster release manager has been set to none!")
    } else {
        inline {
            val user = kord().getUser(Snowflake(manager))

            if (user == null) {
                Logging.info("Could not find user '$manager'!")
            } else {
                releaseManager = user.id
                Logging.info("The buster release manager has been set to ${user.effectiveName}!")
            }
        }
    }
}

private suspend fun fetch(fuyTag: String, wynntilsTag: String, hash: Boolean = true): Pair<Release, Release>? {
    val fuy = Release(FuyRepo, FuyRepo.getReleases()?.firstOrNull {
        it.tag == fuyTag
    } ?: run {
        Logging.error("Could not find tag $fuyTag in fuy.gg releases!")
        return null
    }, hash)

    val wynntils = Release(WynntilsRepo, WynntilsRepo.getReleases()?.firstOrNull {
        it.tag == wynntilsTag
    } ?: run {
        Logging.error("Could not find tag $wynntilsTag in Wynntils releases!")
        return null
    }, hash)

    if (fuy == null) {
        Logging.info("Could not find release asset fuy.gg [$fuyTag]!")
        return null
    }

    if (wynntils == null) {
        Logging.info("Could not find release asset Wynntils [$wynntilsTag]!")
        return null
    }

    return fuy to wynntils
}

@Subcommand("release add")
fun CommandContext<*>.releaseAdd(
    @Argument("fuy.gg version") fuyTag: String,
    @Argument("Wynntils version") wynntilsTag: String
) {
    inline {
        FuyRepo += fetch(fuyTag, wynntilsTag) ?: return@inline
        Logging.info("Wynntils [$wynntilsTag] has been added to fuy.gg [$fuyTag]!")
    }
}


@Subcommand("release remove")
fun CommandContext<*>.releaseRemove(
    @Argument("fuy.gg version") fuyTag: String,
    @Argument("Wynntils version") wynntilsTag: String
) {
    inline {
        FuyRepo.remove(fetch(fuyTag, wynntilsTag, false) ?: return@inline)
        Logging.info("Wynntils [$wynntilsTag] has been removed from fuy.gg [$fuyTag]!")
    }
}
