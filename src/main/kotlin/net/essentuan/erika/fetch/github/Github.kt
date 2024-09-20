package net.essentuan.erika.fetch.github

import net.essentuan.esl.fetch.Fetch

@JvmInline
value class Github private constructor(private val fetch: Fetch) {
    companion object {
        val Fetch.github: Github
            get() = Github(this)
    }
}
