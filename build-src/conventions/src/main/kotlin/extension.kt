import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import de.undercouch.gradle.tasks.download.Download
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.archivesName
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream
import java.io.File

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
@RequiresOptIn(message = "extensions-lib 1.5 hasn't been released as the writing of this message. Please check availability on github.")
annotation class LibVersionNotReadyYet

@Suppress("unused")
sealed class LibVersion(val versionName: String) {
    data object V4 : LibVersion("1.4")

    @LibVersionNotReadyYet
    data object V5 : LibVersion("1.5")
}

sealed class TachiyomiLibrary(val identifier: String) {
    data object CryptoAES : TachiyomiLibrary("cryptoaes")
    data object DataImage : TachiyomiLibrary("dataimage")
    data object I18n : TachiyomiLibrary("i18n")
    data object RandomUA : TachiyomiLibrary("randomua")
    data object Synchrony : TachiyomiLibrary("synchrony")
    data object TextInterceptor : TachiyomiLibrary("textinterceptor")
    data object Unpacker : TachiyomiLibrary("unpacker")
}

sealed class MultiSrc(val identifier: String) {
    data object A3Manga : MultiSrc("a3manga")
    data object Bakamanga : MultiSrc("bakamanga")
    data object Bakkin : MultiSrc("bakkin")
    data object Bilibili : MultiSrc("bilibili")
    data object ComicGamma : MultiSrc("comicgamma")
    data object EroMuse : MultiSrc("eromuse")
    data object FansubsCat : MultiSrc("fansubscat")
    data object FlixScans : MultiSrc("flixscans")
    data object FMReader : MultiSrc("fmreader")
    data object FoolSlide : MultiSrc("foolslide")
    data object Gattsu : MultiSrc("gattsu")
    data object GigaViewer : MultiSrc("gigaviewer")
    data object Grouple : MultiSrc("grouple")
    data object Guya : MultiSrc("guya")
    data object HeanCms : MultiSrc("heancms")
    data object HentaiHand : MultiSrc("hentaihand")
    data object Kemono : MultiSrc("kemono")
    data object LibGroup : MultiSrc("libgroup")
    data object Madara : MultiSrc("madara")
    data object MadTheme : MultiSrc("madtheme")
    data object MangaBox : MultiSrc("mangabox")
    data object MangaCatalog : MultiSrc("mangacatalog")
    data object Mangadventure : MultiSrc("mangadventure")
    data object MangaHub : MultiSrc("mangahub")
    data object MangaMainac : MultiSrc("mangamainac")
    data object MangaRaw : MultiSrc("mangaraw")
    data object MangaReader : MultiSrc("mangareader")
    data object MangaThemesia : MultiSrc("mangathemesia")
    data object MangaWorld : MultiSrc("mangaworld")
    data object MCCMS : MultiSrc("mccms")
    data object MMRCMS : MultiSrc("mmrcms")
    data object Monochrome : MultiSrc("monochrome")
    data object MultiChan : MultiSrc("multichan")
    data object MyMangaCMS : MultiSrc("mymangacms")
    data object NepNep : MultiSrc("nepnep")
    data object OtakuSanctuary : MultiSrc("otakusanctuary")
    data object Paprika : MultiSrc("paprika")
    data object PizzaReader : MultiSrc("pizzareader")
    data object ReadAllComics : MultiSrc("readallcomics")
    data object ReaderFront : MultiSrc("readerfront")
    data object Senkuro : MultiSrc("senkuro")
    data object SinMH : MultiSrc("sinmh")
    data object Webtoons : MultiSrc("webtoons")
    data object WPComics : MultiSrc("wpcomics")
    data object ZBulu : MultiSrc("zbulu")
    data object ZeistManga : MultiSrc("zeistmanga")
    data object ZManga : MultiSrc("zmanga")
}

/**
 * Tries to obtain and load a valid aapt2 cli tool by hijacking the `androidComponents`
 * extension on the current project, asking for the `aidl` tool (located in a versioned subdirectory of build-tools)
 * and just uses the `aapt2` executable located in the same directory.
 *
 * `androidComponents` should download and configure anything necessary automatically.
 *
 * The process is lazily executed when the provider string is queried.
 *
 * @return the absolute path to an extension-less aapt2 executable under a valid Android SDK build-tool directory (works in both linux and Windows).
 */
@Suppress("UnstableApiUsage")
private fun Project.getAAPT2Command(): Provider<String> {
    return extensions.getByName<ApplicationAndroidComponentsExtension>("androidComponents")
        .sdkComponents
        .aidl
        .map { it.executable.get() }
        .map { it.asFile.parentFile }
        .map { it.resolve("aapt2") }
        .map { it.absoluteFile.path }
}

private fun Boolean.to1or0() = if (this) 1 else 0

/**
 * @param libsCatalog (default: `libs`) -> represents the `libs.versions.toml` catalog, you can replace it for testing purposes
 * @param tachiyomiCatalog (default: `tachiyomi`) -> represents the `tachiyomi.versions.toml` catalog, you can replace it for testing purposes
 * @param compileSdk (default: `sdk_compile` in `libs`) -> sdk version number against which the extension will be compiled. Should ideally be the latest available
 * @param minSdk (default: `sdk_min` in `tachiyomi`) -> minimum supported sdk version for your extension. Should be kept as low as possible. (min recommended is 21)
 * @param namespaceIdentifier represents the suffix for the android namespace => `eu.kanade.tachiyomi.extension.${namespaceIdentifier}`
 * @param extName user-visible name of the extension. Full name will be `Tachiyomi: $extName` for release and `[Debug] Tachiyomi: $extName` for debug variants
 * @param pkgNameSuffix suffix that will be appended to the namespace to find the main extension class
 * @param extClass dot-initial name of the main class (e.g. `.ProjectSuki`)
 * @param extFactory (default: `""`) -> unused, possibly deprecated
 * @param extVersionCode main version of the extension, should be bumped any time you make changes to the extension.
 * @param isNsfw whether your extension handles NSFW content
 * @param libVersion (default: `1.4`) -> whether to use extensions-lib [1.4][LibVersion.V4] or [1.5][LibVersion.V5]
 * @param readmeFile (default: `<extension root>/README.md`) -> location where to check for the README file
 * @param changelogFile (default: `<extension root>/CHANGELOG.md`) ->  location where to check for the CHANGELOG file
 * @param kotlinApiVersion (default: `kotlin_api` in `tachiyomi`) -> APIs of the kotlin stdlib to make available for use
 * @param kotlinLanguageVersion (default: `kotlin_language` in `libs`) -> Kotlin language version (features)
 * @param libs (default: `[TachiyomiLibrary.RandomUA]`) -> Libraries the extension makes use of
 * @param multisrc (default: `[]`) -> MultiSrc(s) the extension makes use of
 * @param useDefaultManifest (default: `true`) -> whether the extension should inherit the configuration from `:default`
 * @param includeStdLibInApk (default: `true`) -> whether the extension should package the kotlin stdlib (Mostly useful for url activity creators)
 * @param aapt2Command (default: see [getAAPT2Command]) -> [AAPT2](https://developer.android.com/tools/aapt2) executable used to dump the `badging` apk information. (Prints information extracted from the APK's manifest)
 * @param versionName (default: `{libVersion}.{extVersionCode}`) -> the full extension version. Changing this to have a different logic could have unforeseen consequences.
 * @param archivesNameProvider (default: `{pkgNameSuffix: String, versionName: String -> "tachiyomi-${pkgNameSuffix}-${versionCode}"}`) -> the base name for the apk archive. Changing this could have unforeseen consequences.
 * @param includeInBatchDebug (default: `true`) -> Should this extension be included in the debug repo?
 * @param includeInBatchRelease (default: `includeInBatchDebug`) -> Should this extension be included in the release repo?
 * @param releaseSigningConfiguration (default: `null`) -> The release signing configuration. Changing this will most likely break the workflow.
 * @param kotlinFreeCompilerArgs (default: `[-opt-in=kotlinx.serialization.ExperimentalSerializationApi]`) -> args to pass onto the kotlin compiler.
 */
fun Project.setupTachiyomiExtensionConfiguration(
    @Suppress("UNUSED_PARAMETER") vararg useParameterNames: Unit = emptyArray(),
    libsCatalog: VersionCatalog = extensions.catalog("libs"),
    tachiyomiCatalog: VersionCatalog = extensions.catalog("tachiyomi"),
    compileSdk: Int = fromAny(tachiyomiCatalog, libsCatalog) { getIntVersionOrNull("sdk_compile") },
    minSdk: Int = fromAny(tachiyomiCatalog, libsCatalog) { getIntVersionOrNull("sdk_min") },
    namespaceIdentifier: String,
    extName: String,
    pkgNameSuffix: String,
    extClass: String,
    extFactory: String = "",
    extVersionCode: Int,
    isNsfw: Boolean,
    libVersion: LibVersion = LibVersion.V4,
    readmeFile: RegularFile = project.layout.projectDirectory.file("README.md"),
    changelogFile: RegularFile = project.layout.projectDirectory.file("CHANGELOG.md"),
    kotlinApiVersion: String = fromAny(tachiyomiCatalog, libsCatalog) { getStringVersionOrNull("kotlin_api") },
    kotlinLanguageVersion: String = fromAny(tachiyomiCatalog, libsCatalog) { getStringVersionOrNull("kotlin_language") },
    libs: Set<TachiyomiLibrary> = setOf(TachiyomiLibrary.RandomUA),
    multisrc: Set<MultiSrc> = setOf(),
    useDefaultManifest: Boolean = true,
    includeStdLibInApk: Boolean = true,
    aapt2Command: Provider<String> = getAAPT2Command(),
    versionName: String = "${libVersion.versionName}.${extVersionCode}",
    archivesNameProvider: (pkgNameSuffix: String, versionName: String) -> String = { suffix, vn -> "tachiyomi-$suffix-v${vn}" },
    includeInBatchDebug: Boolean = true,
    includeInBatchRelease: Boolean = includeInBatchDebug,
    releaseSigningConfiguration: (ApkSigningConfig.() -> Unit)? = null,
    kotlinFreeCompilerArgs: List<String> = listOf("-opt-in=kotlinx.serialization.ExperimentalSerializationApi"),
) {

    archivesName.set(archivesNameProvider(pkgNameSuffix, versionName))

    extensions.getByName<BaseAppModuleExtension>("android").apply {
        namespace = "eu.kanade.tachiyomi.extension.${namespaceIdentifier}"
        this.compileSdk = compileSdk

        sourceSets {
            named("main") {
                it.manifest.srcFile(project.layout.projectDirectory.file("AndroidManifest.xml"))
                it.java.setSrcDirs(listOf(project.layout.projectDirectory.dir("src")))
                it.kotlin.setSrcDirs(listOf(project.layout.projectDirectory.dir("src")))
                it.assets.setSrcDirs(listOf(project.layout.projectDirectory.dir("assets")))
                it.res.setSrcDirs(listOf(project.layout.projectDirectory.dir("res")))
            }

            named("release") {
                it.manifest.srcFile(project.layout.projectDirectory.file("AndroidManifest.xml"))
            }

            named("debug") {
                it.manifest.srcFile(project.layout.projectDirectory.file("AndroidManifest.xml"))
            }
        }

        val signing = releaseSigningConfiguration ?: {
            storeFile = System.getenv("KEY_FILE_NAME")?.let { rootProject.rootDir.resolve(it) }
            storePassword = System.getenv("KEY_STORE_PASSWORD")
            keyAlias = System.getenv("KEY_STORE_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }

        signingConfigs {
            create("release", signing)
        }

        defaultConfig {
            this.minSdk = minSdk
            this.targetSdk = compileSdk
            this.applicationIdSuffix = pkgNameSuffix
            this.versionCode = extVersionCode
            this.versionName = versionName

            addManifestPlaceholders(buildMap {
                // put("appName", "Tachiyomi: $extName") // look into buildTypes
                put("extClass", extClass)
                put("extFactory", extFactory)
                put("nsfw", isNsfw.to1or0())
                put("hasReadme", readmeFile.asFile.exists().to1or0())
                put("hasChangelog", changelogFile.asFile.exists().to1or0())
            })
        }

        buildTypes {
            release {
                signingConfig = signingConfigs.findByName("release")
                isMinifyEnabled = false
                isDebuggable = false

                addManifestPlaceholders(mapOf("appName" to "Tachiyomi: $extName"))
            }

            debug {
                // AGP generates a debug signing config
                isMinifyEnabled = false
                isDebuggable = true

                addManifestPlaceholders(mapOf("appName" to "[Debug] Tachiyomi: $extName"))
            }
        }

        dependenciesInfo {
            includeInApk = false
            includeInBundle = false
        }

        buildFeatures {
            aidl = false
            renderScript = false
            resValues = false
            shaders = false
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        it.kotlinOptions {
            jvmTarget = JavaVersion.VERSION_1_8.toString()
            apiVersion = kotlinApiVersion
            languageVersion = kotlinLanguageVersion
            freeCompilerArgs = freeCompilerArgs + kotlinFreeCompilerArgs
        }
    }

    dependencies {
        @OptIn(LibVersionNotReadyYet::class) when (libVersion) {
            LibVersion.V4 -> "compileOnly"(fromAny(tachiyomiCatalog, libsCatalog) { findLibrary("tachiyomi_lib_v4").get() })
            LibVersion.V5 -> "compileOnly"(fromAny(tachiyomiCatalog, libsCatalog) { findBundle("tachiyomi_lib_v5").get() })
        }
        "compileOnly"(fromAny(tachiyomiCatalog, libsCatalog) { findBundle("extension_compile").get() })

        if (useDefaultManifest) {
            "implementation"(project(":default"))
        }

        if (includeStdLibInApk) {
            "implementation"(fromAny(tachiyomiCatalog, libsCatalog) { findLibrary("kotlin_stdlib").get() })
        } else {
            "compileOnly"(fromAny(tachiyomiCatalog, libsCatalog) { findLibrary("kotlin_stdlib").get() })
        }

        libs.forEach { lib ->
            "implementation"(project(":lib-${lib.identifier}"))
        }

        multisrc.forEach { multi ->
            "implementation"(project(":multisrc-${multi.identifier}"))
        }
    }

    afterEvaluate { _ ->

        val variants = when {
            includeInBatchDebug && includeInBatchRelease -> listOf("debug", "release")
            includeInBatchDebug -> listOf("debug")
            includeInBatchRelease -> listOf("release")
            else -> emptyList()
        }

        // jitpack doesn't work
        val downloadInspectorTask: TaskProvider<Download> = tasks.register<Download>("downloadInspector") {
            val inspectorHandler = GitHubReleasesHandler("tachiyomiorg", "tachiyomi-extensions-inspector")
            src(project.provider { inspectorHandler.latestRelease.assets.single { it.name.endsWith(".jar") } })
            dest(layout.buildDirectory.file("inspector.jar"))
            overwrite(false)
            onlyIfModified(true)
        }

        fun File.resolveSiblingWithExtension(ext: String) = resolveSibling("${nameWithoutExtension}.${ext}")

        variants.forEach { variant ->
            val capitalVariant = variant.replaceFirstChar { it.uppercase() }

            val assembleTask = tasks.named("assemble${capitalVariant}")
            val assembleOutputDir = layout.buildDirectory.dir("outputs/apk/$variant").get()

            val constructRepoTask = project(":").tasks.named<DefaultTask>("construct${capitalVariant}Repo")

            val apkFile = assembleOutputDir.file("${archivesNameProvider(pkgNameSuffix, versionName)}-${variant}.apk").asFile
            constructRepoTask.configure { it.inputs.file(apkFile) }

            val inspectorOutputFile = apkFile.resolveSiblingWithExtension("inspector.json")
            val inspectTask = tasks.register<Exec>("inspect${capitalVariant}") {
                // JavaExec was being very difficult
                dependsOn(assembleTask)
                dependsOn(downloadInspectorTask)

                outputs.file(inspectorOutputFile)

                workingDir(assembleOutputDir)

                executable("java")
                args(
                    "-jar",
                    downloadInspectorTask.get().outputs.files.singleFile.absolutePath,
                    apkFile.name,
                    inspectorOutputFile.name,
                    "${apkFile.nameWithoutExtension}-inspector",
                )

                doFirst {
                    println("Running: $commandLine")
                    println("In: $workingDir")
                    println("Contents: ${workingDir.listFiles()?.map { it.name }}")
                }
            }

            val jsonDataFile = apkFile.resolveSiblingWithExtension("json")
            constructRepoTask.configure { it.inputs.file(jsonDataFile) }

            val pngFile = apkFile.resolveSiblingWithExtension("png")
            constructRepoTask.configure { it.inputs.file(pngFile) }

            val createRepoDataTask = tasks.register<Exec>("create${capitalVariant}RepoData") {
                dependsOn(assembleTask)
                dependsOn(inspectTask)

                val aapt2BAOS = ByteArrayOutputStream()
                standardOutput = aapt2BAOS

                workingDir(assembleOutputDir)
                executable(aapt2Command.get())
                args(
                    "dump",
                    "badging",
                    "--include-meta-data",
                    apkFile.name,
                )

                doFirst {
                    println("Running: $commandLine")
                    println("In: $workingDir")
                    println("Contents: ${workingDir.listFiles()?.map { it.name }}")
                }

                doLast {
                    val (repoData: RepositoryJsonExtension, aapt2Output: AAPT2Output) = createRepoData(
                        aapt2Output = String(aapt2BAOS.toByteArray()),
                        inspectorOutput = inspectTask.get().outputs.files.singleFile.readText(),
                        baseName = apkFile.nameWithoutExtension,
                    )

                    jsonDataFile.apply { createNewFile() }.writeText(Json.encodeToString(repoData))

                    val extractionDir = apkFile.resolveSibling(apkFile.nameWithoutExtension).apply { mkdir() }
                    copy { cp ->
                        cp.duplicatesStrategy = DuplicatesStrategy.INCLUDE
                        cp.from(zipTree(apkFile))
                        cp.into(extractionDir)
                    }

                    val icLauncherRelativePath: String = aapt2Output.icons.icon320
                    val icLauncherFile = extractionDir.resolve(icLauncherRelativePath)
                    require(icLauncherFile.exists()) {
                        "$icLauncherRelativePath not found:\n${
                            fileTree(extractionDir).files.joinToString("\n") {
                                it.relativeTo(extractionDir).invariantSeparatorsPath
                            }
                        }"
                    }

                    copy { cp ->
                        cp.duplicatesStrategy = DuplicatesStrategy.INCLUDE
                        cp.from(icLauncherFile)
                        cp.rename { pngFile.name }
                        cp.into(pngFile.parentFile)
                    }

                    val packageIconFile = pngFile.resolveSibling("${aapt2Output.pack.name}.png")
                    constructRepoTask.configure { it.inputs.file(packageIconFile) }

                    copy { cp ->
                        cp.from(pngFile)
                        cp.rename { packageIconFile.name }
                        cp.into(pngFile.parentFile)
                    }

                    println("Contents After: ${workingDir.listFiles()?.map { it.name }}")
                }
            }

            constructRepoTask.configure {
                it.dependsOn(assembleTask)
                it.dependsOn(inspectTask)
                it.dependsOn(createRepoDataTask)
            }
        }
    }
}
