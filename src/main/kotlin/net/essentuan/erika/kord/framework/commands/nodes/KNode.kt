package net.essentuan.erika.kord.framework.commands.nodes

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Guild
import dev.kord.core.entity.application.ChatInputCommandCommand
import dev.kord.rest.builder.interaction.BaseInputChatBuilder
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder
import dev.kord.rest.builder.interaction.OptionsBuilder

interface KNode<T : Any> {
    val id: String
    
    val name: String
    val description: String
    
    val whitelist: Set<Snowflake>
    val blacklist: Set<Snowflake>
    
    fun T.build(guild: Guild?)
}