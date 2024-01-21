package json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RepoRelease(
    @SerialName("assets") val assets: List<Asset>,
    @SerialName("assets_url") val assetsUrl: String,
    @SerialName("author") val author: Author,
    @SerialName("body") val body: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("draft") val draft: Boolean,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("id") val id: Int,
    @SerialName("mentions_count") val mentionsCount: Int = 0,
    @SerialName("name") val name: String,
    @SerialName("node_id") val nodeId: String,
    @SerialName("prerelease") val prerelease: Boolean,
    @SerialName("published_at") val publishedAt: String,
    @SerialName("reactions") val reactions: Reactions,
    @SerialName("tag_name") val tagName: String,
    @SerialName("tarball_url") val tarballUrl: String,
    @SerialName("target_commitish") val targetCommitish: String,
    @SerialName("upload_url") val uploadUrl: String,
    @SerialName("url") val url: String,
    @SerialName("zipball_url") val zipballUrl: String
)
