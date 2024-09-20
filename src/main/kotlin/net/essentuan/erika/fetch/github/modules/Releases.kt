package net.essentuan.erika.fetch.github.modules

import net.essentuan.erika.fetch.github.Github
import net.essentuan.erika.framework.annotation.Priority
import net.essentuan.esl.Rating
import net.essentuan.esl.fetch.JsonRequest
import net.essentuan.esl.fetch.annotations.At
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.Model.Companion.wrap
import net.essentuan.esl.model.annotations.Alias
import net.essentuan.esl.model.annotations.Override
import java.net.URL
import java.util.Date

typealias GhRelease = Release
typealias GhAsset = Asset

data class Release(
    val id: Long,
    val name: String,
    @Alias(["tag_name"])
    val tag: String,
    @Alias(["created_at"])
    val createdAt: Date,
    private val assets: List<Asset>,
    val body: String
) : Json.Model, List<Asset> by assets

data class Asset(
    val id: Long,
    val name: String,
    val size: Long,
    @Alias(["download_count"])
    val downloads: Int,
    @Alias(["created_at"])
    val createdAt: Date,
    @Alias(["browser_download_url"])
    val download: URL
) : Json.Model

@At("https://api.github.com/repos/%s/%s/releases")
private class ReleasesRequest(
    author: String,
    repo: String
) : JsonRequest<List<Release>>(author, repo) {
    override fun invoke(body: Json): List<Release>? =
        body.getList("array", Json::class)?.map { it.wrap() }
}

suspend fun Github.releases(author: String, repo: String, priority: Rating = Rating.NORMAL) =
    ReleasesRequest(author, repo).execute(priority)