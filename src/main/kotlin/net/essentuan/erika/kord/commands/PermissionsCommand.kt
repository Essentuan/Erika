package net.essentuan.erika.kord.commands

import com.essentuan.acf.core.annotations.Command
import com.essentuan.acf.core.annotations.Subcommand
import dev.kord.core.entity.Entity
import dev.kord.core.entity.User
import dev.kord.rest.builder.message.embed
import net.essentuan.erika.kord.framework.commands.CommandInteraction
import net.essentuan.erika.kord.framework.commands.annotations.Requires
import net.essentuan.erika.kord.framework.message.description
import net.essentuan.erika.kord.framework.message.embed
import net.essentuan.erika.kord.framework.permissions.Permission
import net.essentuan.erika.kord.framework.permissions.Permissions.has
import net.essentuan.erika.kord.framework.permissions.Permissions.permissions
import net.essentuan.erika.kord.kord
import net.essentuan.esl.color.McColor
import net.essentuan.esl.string.extensions.camelCase

@Command("permissions")
@Requires("command.permissions")
object PermissionsCommand {
    @Subcommand("set")
    private suspend fun CommandInteraction.set(
        target: Entity,
        permission: String,
        state: State = State.ALLOW
    ) {
        target.permissions[Permission(permission)] = state.boolean

        ephemeral {
            embed(McColor.GREEN) {
                description {
                    +"'"
                    +permission
                    +"' for "
                    +target
                    +" has been set to "
                    +state.name.camelCase(separator = " ")
                }
            }
        }
    }

    @Subcommand("delete")
    private suspend fun CommandInteraction.delete(
        target: Entity,
        permission: String,
    ) {
        target.permissions.delete(Permission(permission))

        ephemeral {
            embed(McColor.GREEN) {
                description {
                    +"'"
                    +permission
                    +"' has been removed from"
                    +target
                }
            }
        }
    }

    @Subcommand("test")
    private suspend fun CommandInteraction.test(
        target: User,
        permission: String,
    ) {
        ephemeral {
            embed {
                if (target has Permission(permission)) {
                    description = "${target.mention} has the permission '$permission'!"
                    color = McColor.GREEN.kord
                } else {
                    description = "${target.mention} does not have the permission '$permission'."
                    color = McColor.RED.kord
                }
            }
        }
    }
}

private enum class State(val boolean: Boolean) {
    ALLOW(true),
    DENY(false)
}