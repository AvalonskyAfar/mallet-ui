// A project of its own: it is not part of the Sancai UI build, and it uses the library the
// way any other app does, as a binary from a Maven repository.

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // The Maven repository of a Sancai UI release, unzipped. `./gradlew dist` in the
        // main project leaves the same repository in build/dist-repo, which is the default
        // here; pass -PsancaiRepo=<directory> to use another one.
        maven { url = uri(providers.gradleProperty("sancaiRepo").getOrElse("../../build/dist-repo")) }
    }
}

rootProject.name = "sancai-minimal"
