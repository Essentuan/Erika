package net.essentuan.erika.fetch.wynncraft

import java.util.UUID

fun interface Identifier<T> {
    fun type(): String

    companion object {
        val UUID = Identifier<UUID> { "uuid" }
        val USERNAME = Identifier<String> { "username" }
    }
}