plugins {
    id("com.android.library") version libs.versions.android.gradlePlugin
    kotlin("android") version libs.versions.kotlin.target
    kotlin("plugin.serialization") version libs.versions.kotlin.target
}

buildscript {
    dependencies {
        classpath("local.buildsrc:conventions")
    }
}

setupTachiyomiMultiSrcConfiguration(
    multiSrc = MultiSrc.Grouple,
)
