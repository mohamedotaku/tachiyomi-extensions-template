import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.properties.PropertyDelegateProvider

@Serializable
data class RepositoryJsonExtension(
    @SerialName("name") val extensionName: String,
    @SerialName("pkg") val extensionPackage: String,
    @SerialName("apk") val apkFileName: String,
    @SerialName("lang") val lang: String,
    @SerialName("code") val versionCode: Int,
    @SerialName("version") val version: String,
    @SerialName("nsfw") val isNsfw: Int,
    @SerialName("hasReadme") val hasReadme: Int,
    @SerialName("hasChangelog") val hasChangelog: Int,
    @SerialName("sources") val sources: List<ExtensionSource>,
)

@Serializable
data class ExtensionSource(
    @SerialName("name") val name: String,
    @SerialName("lang") val lang: String,
    @SerialName("id") val id: String,
    @SerialName("baseUrl") val baseUrl: String,
    @SerialName("versionId") val versionId: Int,
    @SerialName("hasCloudflare") val hasCloudflare: Int,
)

sealed class AAPT2DataExtractor {
    abstract val data: List<String>

    protected fun startingWith(start: Regex): List<String> = data.filter { str -> start.matchesAt(str, 0) }

    class Package(override val data: List<String>) : AAPT2DataExtractor() {
        private val pack = startingWith(packageRegex).first()

        val name by attrValue(nameRegex, pack)
        val versionCode by attrValue(versionCodeRegex, pack)
        val versionName by attrValue(versionNameRegex, pack)
    }

    class Application(override val data: List<String>) : AAPT2DataExtractor() {
        private val app = startingWith(applicationRegex).first()

        val label by attrValue(labelRegex, app)
        val icon by attrValue(iconRegex, app)
    }

    class MetaData(override val data: List<String>) : AAPT2DataExtractor() {
        private val all = startingWith(metaDataRegex)

        val meta: Map<String, String> by lazy {
            buildMap {
                all.forEach { line ->
                    val name by attrValue(nameRegex, line)
                    val value by attrValue(valueRegex, line, "")
                    put(name, value)
                }
            }
        }

        val `class` by lazy { meta["tachiyomi.extension.class"] }
        val factory by lazy { meta["tachiyomi.extension.factory"] }
        val nsfw by lazy { meta["tachiyomi.extension.nsfw"] }
        val hasReadme by lazy { meta["tachiyomi.extension.hasReadme"] }
        val hasChangelog by lazy { meta["tachiyomi.extension.hasChangelog"] }
    }

    class AppIcons(override val data: List<String>) : AAPT2DataExtractor() {
        val icon160 by singleton("application-icon-160")
        val icon240 by singleton("application-icon-240")
        val icon320 by singleton("application-icon-320")
        val icon480 by singleton("application-icon-480")
        val icon640 by singleton("application-icon-640")
    }

    private companion object {
        private fun attrValue(use: Regex, `in`: String, default: String? = null): PropertyDelegateProvider<Any?, Lazy<String>> = PropertyDelegateProvider { cls, property ->
            lazy { use.find(`in`)?.groupValues?.get(1) ?: default ?: error("Could not find ${property.name} in ${(cls ?: Unit)::class.simpleName}") }
        }

        private fun singleton(startOverride: String? = null): PropertyDelegateProvider<AAPT2DataExtractor, Lazy<String>> = PropertyDelegateProvider { extractor, property ->
            val singleton = startOverride ?: property.name.substringBeforeLast("Regex", "").takeIf { it.isNotBlank() } ?: error("Invalid name: ${property.name}")
            val regex = """${singleton}:'([^']+)'""".toRegex(RegexOption.IGNORE_CASE)
            lazy {
                val match = extractor.data.firstNotNullOfOrNull { regex.matchEntire(it) } ?: error("Could not find ${property.name} in ${extractor::class.simpleName}")
                match.groupValues[1]
            }
        }

        private fun attr(labelOverride: String? = null): PropertyDelegateProvider<Any?, Lazy<Regex>> = PropertyDelegateProvider { _, property ->
            val label = labelOverride ?: property.name.substringBeforeLast("Regex", "").takeIf { it.isNotBlank() } ?: error("Invalid name: ${property.name}")
            lazy { """${label}='([^']+)'""".toRegex(RegexOption.IGNORE_CASE) }
        }

        private fun start(startOverride: String? = null): PropertyDelegateProvider<Any?, Lazy<Regex>> = PropertyDelegateProvider { _, property ->
            val start = startOverride ?: property.name.substringBeforeLast("Regex", "").takeIf { it.isNotBlank() } ?: error("Invalid name: ${property.name}")
            lazy { """${start}:""".toRegex(RegexOption.IGNORE_CASE) }
        }

        val packageRegex by start()
        val applicationRegex by start()
        val metaDataRegex by start("meta-data")

        val nameRegex by attr()
        val versionCodeRegex by attr()
        val versionNameRegex by attr()
        val labelRegex by attr()
        val iconRegex by attr()
        val valueRegex by attr()
    }
}

data class AAPT2Output(
    val pack: AAPT2DataExtractor.Package,
    val app: AAPT2DataExtractor.Application,
    val meta: AAPT2DataExtractor.MetaData,
    val icons: AAPT2DataExtractor.AppIcons,
)

private typealias ExtensionPackage = String

fun createRepoData(aapt2Output: String, inspectorOutput: String, baseName: String): Pair<RepositoryJsonExtension, AAPT2Output> {
    val lines = aapt2Output.lines()
    val packageData = AAPT2DataExtractor.Package(lines)
    val applicationData = AAPT2DataExtractor.Application(lines)
    val metaData = AAPT2DataExtractor.MetaData(lines)
    val appIcons = AAPT2DataExtractor.AppIcons(lines)
    val aapt2OutputData = AAPT2Output(packageData, applicationData, metaData, appIcons)

    val inspectionReport: Map<ExtensionPackage, List<ExtensionSource>> = Json.decodeFromString(inspectorOutput)
    val (_, inspectionData) = inspectionReport.entries.singleOrNull() ?: error("inspector report should contain only a single entry!")

    val globalLang = when (inspectionData.size) {
        0 -> error("No sources found!")
        1 -> inspectionData.single().lang
        else -> {
            val distinct = inspectionData.mapTo(HashSet()) { it.lang }
            when {
                "all" in distinct -> "all"
                "other" in distinct -> "other"
                else -> distinct.singleOrNull() ?: "all"
            }
        }
    }

    return RepositoryJsonExtension(
        extensionName = applicationData.label,
        extensionPackage = packageData.name,
        apkFileName = "${baseName}.apk",
        lang = globalLang,
        versionCode = packageData.versionCode.toInt(),
        version = packageData.versionName,
        isNsfw = metaData.nsfw?.toIntOrNull() ?: -1,
        hasReadme = metaData.hasReadme?.toIntOrNull() ?: -1,
        hasChangelog = metaData.hasChangelog?.toIntOrNull() ?: -1,
        sources = inspectionData.map { element ->
            ExtensionSource(
                name = element.name,
                lang = element.lang,
                id = element.id,
                baseUrl = element.baseUrl,
                versionId = element.versionId,
                hasCloudflare = element.hasCloudflare,
            )
        }
    ) to aapt2OutputData
}
