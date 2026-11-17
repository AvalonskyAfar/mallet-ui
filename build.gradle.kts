import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}

// ---------------------------------------------------------------------------------------------
// Release artifacts:  ./gradlew dist
//
//   dist/sancai-<version>.aar                 the library
//   dist/sancai-<version>-sources.jar         its sources, for IDEs
//   dist/sancai-<version>-maven-repo.zip      a Maven repository with AAR, POM, module metadata, sources
//   dist/sancai-showcase-<version>.apk        the demo app (minified)
//   dist/sancai-ui-<version>-source.zip       this project, without build output
//   dist/SHA256SUMS.txt
// ---------------------------------------------------------------------------------------------

val releaseVersion: String = providers.gradleProperty("VERSION_NAME").get()
val staging = layout.buildDirectory.dir("dist-staging")

val sourceZip = tasks.register<Zip>("sourceZip") {
    group = "distribution"
    description = "Zips the project sources, without build output or machine-local files."
    archiveFileName.set("sancai-ui-$releaseVersion-source.zip")
    destinationDirectory.set(staging)
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from(rootDir) {
        into("sancai-ui-$releaseVersion")
        exclude(
            "**/build/**", ".gradle/**", ".git/**", ".kotlin/**", ".idea/**", ".claude/**",
            "dist/**", "local.properties", "**/*.iml",
        )
        filesMatching("gradlew") { permissions { unix("rwxr-xr-x") } }
    }
}

val mavenRepoZip = tasks.register<Zip>("mavenRepoZip") {
    group = "distribution"
    description = "Zips a Maven repository that contains the library."
    dependsOn(":sancai:publishReleasePublicationToDistRepository")
    archiveFileName.set("sancai-$releaseVersion-maven-repo.zip")
    destinationDirectory.set(staging)
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from(layout.buildDirectory.dir("dist-repo"))
}

// A Sync, not a Copy: whatever an earlier run left in dist/ and this run does not produce is removed.
tasks.register<Sync>("dist") {
    group = "distribution"
    description = "Builds every release artifact into dist/, which then holds nothing else."
    dependsOn(":sancai:assembleRelease", ":showcase:assembleRelease", sourceZip, mavenRepoZip)
    val target = layout.projectDirectory.dir("dist")
    into(target)
    from(project(":sancai").layout.buildDirectory.file("outputs/aar/sancai-release.aar")) {
        rename { "sancai-$releaseVersion.aar" }
    }
    from(layout.buildDirectory.dir("dist-repo")) {
        include("**/sancai-$releaseVersion-sources.jar")
        eachFile { path = name }
        includeEmptyDirs = false
    }
    from(project(":showcase").layout.buildDirectory.file("outputs/apk/release/showcase-release.apk")) {
        rename { "sancai-showcase-$releaseVersion.apk" }
    }
    from(sourceZip)
    from(mavenRepoZip)
    doLast {
        val files = target.asFile.listFiles { file -> file.isFile && file.name != "SHA256SUMS.txt" }
            .orEmpty().sortedBy { it.name }
        val lines = files.map { file ->
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) } + "  " + file.name
        }
        target.file("SHA256SUMS.txt").asFile.writeText(lines.joinToString("\n", postfix = "\n"))
        logger.lifecycle("dist/:\n" + files.joinToString("\n") { "  %-44s %8d KB".format(it.name, it.length() / 1024) })
    }
}

tasks.register<Delete>("cleanDist") {
    group = "distribution"
    description = "Removes dist/."
    delete(layout.projectDirectory.dir("dist"))
}
