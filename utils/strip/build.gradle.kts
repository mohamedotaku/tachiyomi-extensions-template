plugins {
    base
}

val template by tasks.registering(Zip::class) {
    from(fileTree(rootProject.layout.projectDirectory))

    val includedExtensions = setOf("kt", "kts", "java", "png", "xml", "yaml")

    val excludeTopLevel = listOf(listOf(".idea"), listOf(".git"), listOf("extensions"), listOf(".github", "ISSUE_TEMPLATE"))
    val excludeRecursive = setOf("build", ".gradle", ".idea")

    include { it.isDirectory }
    include { it.file.extension in includedExtensions }
    include { arrayOf("tachiyomi.versions.toml").contentEquals(it.relativePath.segments) }

    exclude { it.relativePath.lastName in excludeRecursive }
    exclude { element ->
        val segments: Array<String> = element.relativePath.segments
        for (exclude in excludeTopLevel) {
            when {
                segments.size >= exclude.size -> {
                    if (segments.zip(exclude) { l, r -> l.equals(r, ignoreCase = true) }.all { it }) {
                        return@exclude true
                    }
                }
                else -> continue
            }
        }

        false
    }

    includeEmptyDirs = false

    destinationDirectory.set(rootProject.layout.buildDirectory)
    archiveBaseName.set("stripped-template")
}
