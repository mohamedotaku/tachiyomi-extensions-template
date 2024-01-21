plugins {
    id("com.android.library") version libs.versions.android.gradlePlugin
}

android {
    compileSdk = libs.versions.sdk.compile.get().toInt()

    defaultConfig {
        minSdk = tachiyomi.versions.sdk.min.get().toInt()
    }

    namespace = "eu.kanade.tachiyomi.extension"

    sourceSets {
        named("main") {
            manifest.srcFile("AndroidManifest.xml")
            res.setSrcDirs(listOf("res"))
        }
    }

    libraryVariants.all {
        generateBuildConfigProvider?.configure {
            enabled = false
        }
    }
}
