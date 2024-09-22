package net.essentuan.erika.buster.releases.events

import net.essentuan.erika.buster.releases.Repository
import net.essentuan.erika.fetch.github.modules.GhRelease
import net.essentuan.erika.fetch.github.modules.Release
import net.essentuan.erika.framework.events.GenericEvent

abstract class RepoEvent<T : Repository>(
    val repo: T
) : GenericEvent<T>(repo) {
    class Release<T : Repository>(
        repo: T,
        val release: GhRelease
    ) : RepoEvent<T>(repo) {
        val previous: GhRelease? = repo.lastOrNull()
    }
}