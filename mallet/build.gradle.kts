import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

// The coordinates of the published artifact. A build that includes this module as a source
// module need not define them.
group = providers.gradleProperty("GROUP").getOrElse("io.github.avalonskyafar")
version = providers.gradleProperty("VERSION_NAME").getOrElse("0.0.0-SNAPSHOT")

android {
    namespace = "io.github.avalonskyafar.mallet"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    resourcePrefix = "mallet"

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.maxHeapSize = "768m"
                // Robolectric downloads its Android runtime jars itself and does not read the
                // JVM proxy settings, so forward them when the build runs behind a proxy.
                System.getProperty("https.proxyHost")?.let {
                    test.systemProperty("robolectric.dependency.proxy.host", it)
                }
                System.getProperty("https.proxyPort")?.let {
                    test.systemProperty("robolectric.dependency.proxy.port", it)
                }
                // Snapshot tests write PNGs here; see src/test/.../testing/Snapshots.kt.
                test.systemProperty(
                    "mallet.snapshot.dir",
                    layout.buildDirectory.dir("snapshots").get().asFile.absolutePath,
                )
            }
        }
    }

    lint {
        // The library is held to a clean report: a warning fails the build.
        warningsAsErrors = true
        // Versions are pinned on purpose. A library depends on the oldest releases that give
        // it what it needs, so that it never forces an upgrade on an app.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    explicitApi()
    coreLibrariesVersion = libs.versions.kotlin.api.get()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
    }
}

dependencies {
    api(libs.androidx.annotation)
    implementation(libs.androidx.core)
    implementation(libs.androidx.customview)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "mallet"
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("Mallet UI")
                description.set("A tactile design system and interaction toolkit for Android Views.")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
    repositories {
        // `./gradlew :mallet:publishReleasePublicationToDistRepository` writes a plain
        // Maven repository into build/dist-repo that can be zipped and shared.
        maven {
            name = "dist"
            url = uri(rootProject.layout.buildDirectory.dir("dist-repo"))
        }
    }
}

// The staging repository must only ever hold the version that is being published. It is
// emptied first, so that a version left behind by an earlier build is not zipped along.
val cleanDistRepo = tasks.register<Delete>("cleanDistRepo") {
    delete(rootProject.layout.buildDirectory.dir("dist-repo"))
}
tasks.matching { it.name == "publishReleasePublicationToDistRepository" }.configureEach {
    dependsOn(cleanDistRepo)
}
