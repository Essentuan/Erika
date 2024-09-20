package net.essentuan.erika.buster.releases

import dev.kord.common.entity.ButtonStyle
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.reply
import dev.kord.core.entity.effectiveName
import dev.kord.core.event.interaction.GlobalButtonInteractionCreateEvent
import dev.kord.rest.builder.message.MessageBuilder
import dev.kord.rest.builder.message.actionRow
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import net.essentuan.erika.buster.BusterService.Constants.releaseManager
import net.essentuan.erika.buster.releases.events.RepoEvent
import net.essentuan.erika.buster.releases.repos.FuyRepo
import net.essentuan.erika.buster.releases.repos.Release
import net.essentuan.erika.buster.releases.repos.WynntilsRepo
import net.essentuan.erika.fetch.github.Github.Companion.github
import net.essentuan.erika.fetch.github.modules.GhRelease
import net.essentuan.erika.fetch.github.modules.releases
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.framework.db.`object`.types.Singleton
import net.essentuan.erika.framework.events.annotations.Subscribe
import net.essentuan.erika.inline
import net.essentuan.erika.kord.framework.message.*
import net.essentuan.erika.kord.kord
import net.essentuan.esl.color.Color
import net.essentuan.esl.color.McColor
import net.essentuan.esl.coroutines.delay
import net.essentuan.esl.fetch.fetch
import net.essentuan.esl.model.annotations.Ignored
import net.essentuan.esl.scheduling.annotations.Every
import net.essentuan.esl.time.duration.seconds
import java.net.URI
import java.net.URL

abstract class Repository(
    val author: String,
    val repo: String,
    val icon: URL? = null
) : Singleton(), List<GhRelease> {
    constructor(
        author: String,
        repo: String,
        icon: String?
    ) : this(author, repo, icon?.let { URI.create(it).toURL() })

    @Ignored
    private val logger by Logging

    private var releases = mutableListOf<GhRelease>()

    suspend fun getReleases(): List<GhRelease>? =
        fetch {
            github.releases(author, repo)
        }

    @Every(minutes = 1.0)
    private suspend fun update() {
        val latest = getReleases()?.firstOrNull() ?: run {
            logger.error("Couldn't fetch releases for $author/$repo")

            return
        }

        if (firstOrNull()?.tag == latest.tag)
            return

        RepoEvent.Release(this, latest).post()

        releases.add(latest)
    }

    override val size: Int
        get() = releases.size

    override fun get(index: Int): GhRelease =
        releases[index]

    override fun isEmpty(): Boolean =
        releases.isEmpty()

    override fun iterator(): Iterator<GhRelease> =
        releases.iterator()

    override fun listIterator(): ListIterator<GhRelease> =
        releases.listIterator()

    override fun listIterator(index: Int): ListIterator<GhRelease> =
        releases.listIterator(index)

    override fun subList(fromIndex: Int, toIndex: Int): List<GhRelease> =
        releases.subList(fromIndex, toIndex)

    override fun lastIndexOf(element: GhRelease): Int =
        releases.lastIndexOf(element)

    override fun indexOf(element: GhRelease): Int =
        releases.indexOf(element)

    override fun containsAll(elements: Collection<GhRelease>): Boolean =
        releases.containsAll(elements)

    override fun contains(element: GhRelease): Boolean =
        releases.contains(element)

    companion object Manager {
        private inline fun <T : RepoEvent<*>> release(
            obj: T,
            crossinline block: suspend MessageBuilder.(event: T) -> Unit
        ) {
            inline {
                val user = kord().getUser(releaseManager ?: return@inline) ?: run {
                    Logging.error("Could not find selected release manager!")
                    return@inline
                }

                val channel = user.getDmChannelOrNull() ?: run {
                    Logging.error("${user.effectiveName} does not have DM channel!")
                    return@inline
                }

                channel.createMessage {
                    block(obj)
                }
            }
        }

        @Subscribe
        private fun onFuyUpdate(event: RepoEvent.Release<FuyRepo>) {
            if (FuyRepo.isEmpty())
                return

            release(event) {
                val fuy = it.release
                val wynntils = WynntilsRepo.latest

                embed(Color(255, 238, 143)) {
                    thumbnail {
                        url = FuyRepo.icon!!.toString()
                    }

                    author {
                        name {
                            +"fuy.gg "

                            +"["
                            +(it.previous?.tag ?: "none")
                            +"]"

                            +" -> "
                            +"["
                            +fuy.tag
                            +"]"
                        }
                    }

                    description = fuy.body.replace("\\n", "\n")

                    footer {
                        text {
                            +"Is this compatible with Wynntils [${wynntils.tag}]?"
                        }
                    }

                    timestamp = fuy.createdAt.toInstant().toKotlinInstant()
                }

                actionRow {
                    interactionButton(
                        ButtonStyle.Primary,
                        "br|${fuy.tag}|${wynntils.tag}"
                    ) {
                        label = "Yes"
                    }

                    interactionButton(
                        ButtonStyle.Danger,
                        "br_cancel"
                    ) {
                        label = "No"
                    }
                }
            }
        }

        @Subscribe
        private fun onWynntilsUpdate(event: RepoEvent.Release<WynntilsRepo>) {
            release(event) {
                delay(1.seconds)

                val fuy = FuyRepo.lastOrNull() ?: return@release
                val wynntils = it.release

                embed(Color(143, 230, 143)) {
                    thumbnail {
                        url = WynntilsRepo.icon!!.toString()
                    }

                    author {
                        name {
                            +"Wynntils "

                            +"["
                            +(it.previous?.tag ?: "none")
                            +"]"

                            +" -> "
                            +"["
                            +wynntils.tag
                            +"]"
                        }
                    }

                    description = wynntils.body.replace("\\n", "\n")

                    footer {
                        text {
                            +"Is this compatible with fuy.gg [${fuy.tag}]?"
                        }
                    }

                    timestamp = wynntils.createdAt.toInstant().toKotlinInstant()
                }

                actionRow {
                    interactionButton(
                        ButtonStyle.Primary,
                        "br|${fuy.tag}|${wynntils.tag}"
                    ) {
                        label = "Yes"
                    }

                    interactionButton(
                        ButtonStyle.Danger,
                        "br_cancel"
                    ) {
                        label = "No"
                    }
                }
            }
        }

        @Subscribe
        private suspend fun GlobalButtonInteractionCreateEvent.on() {
            when {
                interaction.componentId == "br_cancel" -> {
                    interaction.deferPublicMessageUpdate()

                    interaction.message.edit {
                        components = mutableListOf()
                    }
                }

                interaction.componentId.startsWith("br|") -> {
                    val (fuyTag, wynntilsTag) = interaction.componentId.removePrefix("br|").split("|")

                    val fuy = FuyRepo.firstOrNull {
                        it.tag == fuyTag
                    } ?: run {
                        interaction.respondEphemeral {
                            embed(McColor.DARK_RED) {
                                description = "Could not find fuy.gg version `${fuyTag}`!"
                            }
                        }

                        return
                    }

                    val wynntils = WynntilsRepo.firstOrNull {
                        it.tag == wynntilsTag
                    } ?: run {
                        interaction.respondEphemeral {
                            embed(McColor.DARK_RED) {
                                description = "Could not find Wynntils version `${wynntilsTag}`!"
                            }
                        }

                        return
                    }

                    interaction.deferPublicMessageUpdate()

                    FuyRepo += (Release(FuyRepo, fuy) ?: return) to (Release(WynntilsRepo, wynntils) ?: return)

                    interaction.message.edit {
                        components = mutableListOf()
                    }
                }
            }
        }
    }
}