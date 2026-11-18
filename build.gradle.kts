import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}

// ---------------------------------------------------------------------------------------------
// Release artifacts:  ./gradlew dist
//
//   dist/mallet-<version>.aar                 the library
//   dist/mallet-<version>-sources.jar         its sources, for IDEs
//   dist/mallet-<version>-maven-repo.zip      a Maven repository with AAR, POM, module metadata, sources
//   dist/mallet-showcase-<version>.apk        the demo app (minified)
//   dist/mallet-ui-<version>-source.zip       this project, without build output
//   dist/SHA256SUMS.txt
// ---------------------------------------------------------------------------------------------

val releaseVersion: String = providers.gradleProperty("VERSION_NAME").get()
val staging = layout.buildDirectory.dir("dist-staging")

val sourceZip = tasks.register<Zip>("sourceZip") {
    group = "distribution"
    description = "Zips the project sources, without build output or machine-local files."
    archiveFileName.set("mallet-ui-$releaseVersion-source.zip")
    destinationDirectory.set(staging)
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from(rootDir) {
        into("mallet-ui-$releaseVersion")
        exclude(
            "**/build/**", "**/.gradle/**", ".git/**", "**/.kotlin/**", ".idea/**", ".claude/**",
            "dist/**", "local.properties", "**/*.iml",
        )
        filesMatching("gradlew") { permissions { unix("rwxr-xr-x") } }
    }
}

val mavenRepoZip = tasks.register<Zip>("mavenRepoZip") {
    group = "distribution"
    description = "Zips a Maven repository that contains the library."
    dependsOn(":mallet:publishReleasePublicationToDistRepository")
    archiveFileName.set("mallet-$releaseVersion-maven-repo.zip")
    destinationDirectory.set(staging)
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from(layout.buildDirectory.dir("dist-repo"))
}

// A Sync, not a Copy: whatever an earlier run left in dist/ and this run does not produce is removed.
tasks.register<Sync>("dist") {
    group = "distribution"
    description = "Builds every release artifact into dist/, which then holds nothing else."
    dependsOn(":mallet:assembleRelease", ":showcase:assembleRelease", sourceZip, mavenRepoZip)
    val target = layout.projectDirectory.dir("dist")
    into(target)
    from(project(":mallet").layout.buildDirectory.file("outputs/aar/mallet-release.aar")) {
        rename { "mallet-$releaseVersion.aar" }
    }
    from(layout.buildDirectory.dir("dist-repo")) {
        include("**/mallet-$releaseVersion-sources.jar")
        eachFile { path = name }
        includeEmptyDirs = false
    }
    from(project(":showcase").layout.buildDirectory.file("outputs/apk/release/showcase-release.apk")) {
        rename { "mallet-showcase-$releaseVersion.apk" }
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
