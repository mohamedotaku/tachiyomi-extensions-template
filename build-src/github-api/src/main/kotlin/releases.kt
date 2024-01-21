import json.RepoRelease
import json.RepoReleaseData
import kotlinx.serialization.json.Json
import java.net.URL

@Suppress("MemberVisibilityCanBePrivate", "CanBeParameter", "unused")
class GitHubReleasesHandler(val owner: String, val repo: String) {
    val releasesURL = URL("""https://api.github.com/repos/${owner}/${repo}/releases""")
    val latestURL = URL("""https://api.github.com/repos/${owner}/${repo}/releases/latest""")

    val latestRelease: RepoRelease by lazy {
        Json.decodeFromString(String(latestURL.readBytes()))
    }
    val latestReleaseData: RepoReleaseData by lazy {
        Json.decodeFromString(String(URL(latestRelease.url).readBytes()))
    }

    val releases: List<RepoRelease> by lazy {
        Json.decodeFromString(String(releasesURL.readBytes()))
    }
    val releasesData: Map<RepoRelease, Lazy<RepoReleaseData>> by lazy {
        releases.associateWith { release ->
            lazy {
                Json.decodeFromString(String(URL(release.url).readBytes()))
            }
        }
    }
}
