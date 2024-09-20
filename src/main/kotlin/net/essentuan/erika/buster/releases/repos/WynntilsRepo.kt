package net.essentuan.erika.buster.releases.repos

import net.essentuan.erika.buster.releases.Repository
import net.essentuan.erika.fetch.github.modules.GhRelease

object WynntilsRepo : Repository(
    "Wynntils",
    "Wynntils",
    "https://cdn.modrinth.com/data/dU5Gb9Ab/119292d11c610552b2167d2ceda5755de89cdde6_96.webp"
) {
    val latest: GhRelease
        get() = last()
}