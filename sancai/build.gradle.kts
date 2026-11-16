import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

android {
    namespace = "io.github.avalonskyafar.sancai"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    resourcePrefix = "sancai"

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
                    "sancai.snapshot.dir",
                    layout.buildDirectory.dir("snapshots").get().asFile.absolutePath,
                )
            }
        }
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
            artifactId = "sancai"
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("Sancai UI")
                description.set("A glazed-ceramic design system and interaction toolkit for Android Views.")
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
        // `./gradlew :sancai:publishReleasePublicationToDistRepository` writes a plain
        // Maven repository into build/dist-repo that can be zipped and shared.
        maven {
            name = "dist"
            url = uri(rootProject.layout.buildDirectory.dir("dist-repo"))
        }
    }
}
