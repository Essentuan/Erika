@file:Command("template")

package net.essentuan.erika.commands.template

import com.busted_moments.buster.api.Territory
import com.essentuan.acf.core.annotations.Argument
import com.essentuan.acf.core.annotations.Command
import com.essentuan.acf.core.annotations.Subcommand
import com.essentuan.acf.core.command.arguments.builtin.primitaves.String.StringType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.essentuan.erika.db.struct.territories.editing.TemplateEditor

private var editor: TemplateEditor? = null

@Subcommand("new")
private fun CommandContext<*>.new() {
    if (editor == null)
        editor = TemplateEditor(TemplateEditor.Type.NEW)
}

@Subcommand("modify")
private fun CommandContext<*>.modify() {
    if (editor == null)
        editor = TemplateEditor(TemplateEditor.Type.MODIFY)
}

@Subcommand("resource")
private fun CommandContext<*>.resource(
    @Argument("Territory")
    @StringType(StringArgumentType.StringType.QUOTABLE_PHRASE)
    territory: String,
    @Argument("Resource") resource: Territory.Resource,
    @Argument("Production") production: Int
) {
    editor?.resource(
        territory,
        resource,
        production
    )
}

@Subcommand("connect")
private fun CommandContext<*>.connect(
    @Argument("Territory")
    @StringType(StringArgumentType.StringType.QUOTABLE_PHRASE)
    territory: String,
    @Argument("To")
    @StringType(StringArgumentType.StringType.QUOTABLE_PHRASE)
    to: String,
) {
    editor?.connect(territory, to)
}

@Subcommand("disconnect")
private fun CommandContext<*>.disconnect(
    @Argument("Territory")
    @StringType(StringArgumentType.StringType.QUOTABLE_PHRASE)
    territory: String,
    @Argument("From")
    @StringType(StringArgumentType.StringType.QUOTABLE_PHRASE)
    from: String,
) {
    editor?.disconnect(territory, from)
}

@Subcommand("cancel")
private fun CommandContext<*>.cancel() {
    editor = null
}

@Subcommand("finish")
private fun CommandContext<*>.finish() {
    editor?.also {
        editor = null
        it.finish()
    }
}