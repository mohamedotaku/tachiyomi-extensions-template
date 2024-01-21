plugins {
    kotlin("jvm") version libs.versions.kotlin.gradleCompatible
    kotlin("plugin.serialization") version libs.versions.kotlin.gradleCompatible
}

group = "local.buildsrc"
version = "0.1.0"

dependencies {
    compileOnly(gradleApi())
    compileOnly(gradleKotlinDsl())

    implementation("local.buildsrc:github-api")

    api(libs.kotlin.json)
    api("de.undercouch.download:de.undercouch.download.gradle.plugin:${libs.versions.undercouch.download.get()}")

    compileOnly("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:${libs.versions.kotlin.target.get()}")
    compileOnly("com.android.library:com.android.library.gradle.plugin:${libs.versions.android.gradlePlugin.get()}")
}
