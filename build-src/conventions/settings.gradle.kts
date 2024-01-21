pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files(rootProject.projectDir.resolve("../../libs.versions.toml")))
        }
        create("tachiyomi") {
            from(files(rootProject.projectDir.resolve("../../tachiyomi.versions.toml")))
        }
    }
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

plugins {
    id("de.fayard.refreshVersions") version "+"
    id("org.gradle.toolchains.foojay-resolver-convention") version "+"
}

rootProject.apply {
    name = "conventions"
}
