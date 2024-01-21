import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun Project.setupTachiyomiLibConfiguration(
    @Suppress("UNUSED_PARAMETER") vararg useParameterNames: Unit = emptyArray(),
    libsCatalog: VersionCatalog = extensions.catalog("libs"),
    tachiyomiCatalog: VersionCatalog = extensions.catalog("tachiyomi"),
    compileSdk: Int = fromAny(tachiyomiCatalog, libsCatalog) { getIntVersionOrNull("sdk_compile") },
    minSdk: Int = fromAny(tachiyomiCatalog, libsCatalog) { getIntVersionOrNull("sdk_min") },
    libVersion: LibVersion = LibVersion.V4,
    lib: TachiyomiLibrary,
    kotlinFreeCompilerArgs: List<String> = listOf("-opt-in=kotlinx.serialization.ExperimentalSerializationApi"),
) {
    extensions.getByName<LibraryExtension>("android").apply {
        this.compileSdk = compileSdk

        defaultConfig {
            this.minSdk = minSdk
        }

        namespace = "eu.kanade.tachiyomi.lib.${lib.identifier}"
    }

    dependencies {
        @OptIn(LibVersionNotReadyYet::class) when (libVersion) {
            LibVersion.V4 -> "compileOnly"(fromAny(tachiyomiCatalog, libsCatalog) { findLibrary("tachiyomi_lib_v4").get() })
            LibVersion.V5 -> "compileOnly"(fromAny(tachiyomiCatalog, libsCatalog) { findBundle("tachiyomi_lib_v5").get() })
        }
        "compileOnly"(fromAny(tachiyomiCatalog, libsCatalog) { findBundle("extension_compile").get() })
    }

    project(":").tasks.named("compileLibsKotlin") {
        it.dependsOn(project.tasks.named("compileDebugKotlin"), project.tasks.named("compileReleaseKotlin"))
    }

    tasks.withType<KotlinCompile>().configureEach {
        it.kotlinOptions {
            jvmTarget = JavaVersion.VERSION_1_8.toString()
            apiVersion = fromAny(tachiyomiCatalog, libsCatalog) { getStringVersionOrNull("kotlin_api") }
            languageVersion = fromAny(tachiyomiCatalog, libsCatalog) { getStringVersionOrNull("kotlin_language") }
            freeCompilerArgs = freeCompilerArgs + kotlinFreeCompilerArgs
        }
    }
}
