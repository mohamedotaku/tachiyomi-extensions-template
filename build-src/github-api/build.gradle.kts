plugins {
    kotlin("jvm") version libs.versions.kotlin.gradleCompatible
    kotlin("plugin.serialization") version libs.versions.kotlin.gradleCompatible
}

group = "local.buildsrc"
version = "0.1.0"

dependencies {
    compileOnly(gradleApi())
    compileOnly(gradleKotlinDsl())

    api(libs.kotlin.json)
    compileOnly("de.undercouch.download:de.undercouch.download.gradle.plugin:${libs.versions.undercouch.download.get()}")
}
