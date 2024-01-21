import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


plugins {
    base
}

tasks {
    wrapper {
        distributionType = Wrapper.DistributionType.ALL
        gradleVersion = """8.6-rc-2"""
        distributionSha256Sum = """4a0d149f9edee932df9ae67f38e31878141c2aa54a93c59652beceeb46d5cfd3"""
    }
}

buildscript {
    dependencies {
        classpath(libs.kotlin.json)
    }
}

val repo: Directory = rootProject.layout.buildDirectory.dir("repo").get()
val prettyJson = Json { prettyPrint = true }

val cleanseRootBuildDir by tasks.registering(Delete::class) {
    delete(project.layout.buildDirectory)
}

val cleanseAll by tasks.registering(Delete::class)

gradle.projectsEvaluated {
    allprojects {
        cleanseAll.configure {
            delete(layout.buildDirectory)
        }
    }
}

val compileLibsKotlin: TaskProvider<DefaultTask> by tasks.registering(DefaultTask::class)
val compileMultiSrcKotlin: TaskProvider<DefaultTask> by tasks.registering(DefaultTask::class)

listOf("debug", "release").forEach { variant ->
    val capitalVariant = variant.replaceFirstChar { it.uppercase() }

    tasks.register<DefaultTask>("construct${capitalVariant}Repo") {
        dependsOn(cleanseRootBuildDir)

        val repo = repo.dir(variant)
        outputs.dir(repo)

        doLast {
            val allJson = inputs.files
                .filter { it.extension == "json" }
                .joinToString(",", "[", "]") { json -> json.readText() }

            inputs.files
                .filter { it.extension == "apk" }
                .forEach { apk ->
                    copy {
                        duplicatesStrategy = DuplicatesStrategy.FAIL
                        from(apk)
                        into(repo.dir("apk"))
                    }
                }

            inputs.files
                .filter { it.extension == "png" }
                .forEach { apk ->
                    copy {
                        duplicatesStrategy = DuplicatesStrategy.FAIL
                        from(apk)
                        into(repo.dir("icon"))
                    }
                }

            val index = Json.parseToJsonElement(allJson)

            repo.file("index.min.json").asFile
                .apply { createNewFile() }
                .writeText(Json.encodeToString(index))

            repo.file("index.json").asFile
                .apply { createNewFile() }
                .writeText(prettyJson.encodeToString(index))

            println("$capitalVariant repo (${repo.asFile.invariantSeparatorsPath}) contents:")
            fileTree(repo).forEach { file ->
                println(file.relativeTo(repo.asFile).invariantSeparatorsPath)
            }
        }
    }
}
