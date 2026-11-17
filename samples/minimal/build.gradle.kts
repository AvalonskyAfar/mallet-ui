plugins {
    id("com.android.application") version "8.13.2"
}

android {
    namespace = "com.example.sancaiminimal"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.example.sancaiminimal"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Shrunk on purpose: the library must survive R8 without any keep rule of yours.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // The only dependency. AndroidX and the Kotlin standard library follow from its POM.
    implementation("io.github.avalonskyafar:sancai:0.1.0")
}
