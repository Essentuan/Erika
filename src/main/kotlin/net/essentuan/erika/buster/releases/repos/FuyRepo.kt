package net.essentuan.erika.buster.releases.repos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.essentuan.erika.buster.releases.Repository
import net.essentuan.erika.fetch.github.modules.Asset
import net.essentuan.erika.fetch.github.modules.GhRelease
import net.essentuan.esl.collections.builders.mutableMap
import net.essentuan.esl.delegates.lateinit
import net.essentuan.esl.json.Json
import org.semver4j.Semver
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest
import java.util.regex.Pattern

private const val BUFFER_SIZE = 16384
private val VERSION_PATTERN = Pattern.compile(".*\\+(MC-)?(?<mc>[0-9.]*)\\.jar")

@OptIn(ExperimentalStdlibApi::class)
private suspend fun hash(url: URL): String {
    return withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)

        url.openStream().buffered(BUFFER_SIZE).use { stream ->
            var read: Int

            while (
                stream.read(buffer).also {
                    read = it
                } != -1
            ) {
                digest.update(buffer, 0, read)
            }
        }

        digest.digest().toHexString()
    }
}

data class Release(
    val tag: Semver,
    val mc: String,
    val asset: Asset,
    val hash: String
) : Json.Model {
    companion object {
        suspend operator fun invoke(repo: Repository, release: GhRelease, hash: Boolean = true): Release? {
            for (asset in release) {
                when {
                    repo is FuyRepo && "sources" in asset.name -> continue
                    repo is WynntilsRepo && "fabric" !in asset.name -> continue
                }

                val matcher = VERSION_PATTERN.matcher(asset.name)
                if (!matcher.matches())
                    continue

                return Release(
                    release.tag,
                    matcher.group("mc"),
                    asset,
                    if (hash) hash(asset.download) else ""
                )
            }

            return null
        }
    }
}

data class Candidate(
    val fuy: Release,
    val wynntils: MutableList<Release> = mutableListOf()
) : Json.Model {
    val mc: String
        get() = fuy.mc
}

object FuyRepo : Repository(
    "Essentuan",
    "fuy.gg",
    "https://cdn.modrinth.com/data/EMQzFaJ1/38f37a80299adeb8db8a7ffdb90835a2f751ed60_96.webp"
) {
    private val versions: MutableMap<String, MutableList<Candidate>> by lateinit(::mutableMapOf)

    operator fun get(string: String): Candidate? =
        versions[string.replace('.', '|')]?.lastOrNull()?.takeIf { it.wynntils.isNotEmpty() }

    operator fun plusAssign(releases: Pair<Release, Release>) = synchronized(this.versions) {
        val (fuy, wynntils) = releases

        val candidates = this.versions.computeIfAbsent(fuy.mc.replace('.', '|')) { mutableListOf() }
        when {
            candidates.isEmpty() || candidates.last().fuy.tag != fuy.tag ->
                candidates += Candidate(
                    fuy,
                    mutableListOf(wynntils)
                )

            candidates.last().wynntils.lastOrNull()?.tag != wynntils.tag ->
                candidates.last().wynntils += wynntils

            else -> return@synchronized
        }

        for (candidate in candidates)
            candidate.wynntils.sortBy { it.tag }

        candidates.sortBy { it.fuy.tag }
    }

    fun remove(releases: Pair<Release, Release>) = synchronized(this.versions) {
        val (fuy, wynntils) = releases

        this.versions[fuy.mc.replace('.', '|')]
            ?.firstOrNull {
                it.fuy.tag == fuy.tag
            }
            ?.wynntils
            ?.removeIf {
                it.tag == wynntils.tag
            }
    }
}