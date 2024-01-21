import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    base
    id("de.undercouch.download") version libs.versions.undercouch.download
}

buildscript {
    dependencies {
        classpath("local.buildsrc:github-api")
    }
}

val kotlincDir: Provider<Directory> = layout.buildDirectory.dir("kotlinc")

val downloadCompiler by tasks.registering(DefaultTask::class) {
    val targetAsset = "kotlin-compiler-${libs.versions.kotlin.target.get()}.zip"
    val destFile = kotlincDir.map { it.file(targetAsset) }

    outputs.file(destFile)

    doLast {
        val kotlinReleasesHandler = GitHubReleasesHandler("JetBrains", "kotlin")

        val release = kotlinReleasesHandler.releases.first { repoRelease ->
            repoRelease.tagName.equals("v${libs.versions.kotlin.target.get()}", ignoreCase = true)
        }

        val asset = release.assets.first { asset ->
            asset.name.equals("kotlin-compiler-${libs.versions.kotlin.target.get()}.zip", ignoreCase = true)
        }

        download.run {
            src(asset.browserDownloadUrl)
            dest(kotlincDir.map { it.file(targetAsset) })
            overwrite(false)
            onlyIfModified(true)
        }
    }
}

val unzipCompiler by tasks.registering(Copy::class) {
    val zip = provider { downloadCompiler.get().outputs.files.singleFile }

    from(zipTree(zip))
    into(kotlincDir.map { it.dir(zip.get().nameWithoutExtension) })
}

val update by tasks.registering(DefaultTask::class) {
    dependsOn(unzipCompiler)

    doLast {
        val extension: String = when {
            Os.isFamily(Os.FAMILY_WINDOWS) -> ".bat"
            else -> ""
        }
        val kotlin = unzipCompiler.get().destinationDir.resolve("kotlinc/bin/kotlin${extension}")

        val workflows: File = rootProject.layout.projectDirectory.dir(".github/workflows").asFile

        (workflows.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.endsWith(".main.kts") && !it.name.startsWith("_") }
            .forEach { script ->
                println("Updating $script")
                println("Making executable...")
                exec {
                    workingDir(rootProject.layout.projectDirectory)
                    executable("git")
                    args("update-index", "--chmod=+x", script.absoluteFile.path)
                    println("Running $commandLine")
                }
                println("Executing...")
                exec {
                    workingDir(workflows)
                    executable(kotlin.absoluteFile.path)
                    args(script.absoluteFile.path)
                    println("Running $commandLine")
                }
                println("Done")
            }
    }
}
