import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// A release key can be supplied from ~/.gradle/gradle.properties or the command line:
//   SHOWCASE_KEYSTORE, SHOWCASE_KEYSTORE_PASSWORD, SHOWCASE_KEY_ALIAS, SHOWCASE_KEY_PASSWORD
// Without one, the release APK is signed with the Android debug key so that it can be
// installed for evaluation. Such an APK must not be published to a store.
val showcaseKeystore = providers.gradleProperty("SHOWCASE_KEYSTORE").orNull

android {
    namespace = "io.github.avalonskyafar.sancai.showcase"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "io.github.avalonskyafar.sancai.showcase"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = providers.gradleProperty("VERSION_NAME").get()
    }

    signingConfigs {
        if (showcaseKeystore != null) {
            create("release") {
                storeFile = file(showcaseKeystore)
                storePassword = providers.gradleProperty("SHOWCASE_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("SHOWCASE_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("SHOWCASE_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            // The demo also proves that the library survives code shrinking.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.maxHeapSize = "768m"
                System.getProperty("https.proxyHost")?.let {
                    test.systemProperty("robolectric.dependency.proxy.host", it)
                }
                System.getProperty("https.proxyPort")?.let {
                    test.systemProperty("robolectric.dependency.proxy.port", it)
                }
                test.systemProperty(
                    "sancai.snapshot.dir",
                    layout.buildDirectory.dir("snapshots").get().asFile.absolutePath,
                )
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":sancai"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
