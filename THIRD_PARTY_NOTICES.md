# Third-party notices

Sancai UI itself is MIT licensed (see [LICENSE](LICENSE)). This file lists the software it
is built with and the software its artifacts depend on or contain. Versions are the ones
resolved by Gradle for this release; licences are the ones declared in each artifact's
published POM (or its parent POM), checked on 2026-11-17.

## 1. The library artifact (`sancai-<version>.aar`)

The AAR contains **only** Sancai UI's own classes and resources. Its dependencies are
declared in the POM and resolved by the consumer's build; none is bundled.

| Dependency | Version | Scope | Licence |
|---|---|---|---|
| `androidx.annotation:annotation` | 1.9.1 | api | Apache-2.0 |
| `androidx.core:core` | 1.13.1 | implementation | Apache-2.0 |
| `androidx.customview:customview` | 1.1.0 | implementation | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.0.21 | api (added by the Kotlin plugin) | Apache-2.0 |

## 2. The Showcase APK

The demo APK bundles the library plus the complete run-time closure of the dependencies
above. All of it is Apache-2.0.

| Component | Version | Licence |
|---|---|---|
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.21 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm`, `-android` | 1.6.4 | Apache-2.0 |
| `org.jetbrains:annotations` | 13.0 | Apache-2.0 |
| `androidx.annotation:annotation`, `annotation-jvm` | 1.9.1 | Apache-2.0 |
| `androidx.annotation:annotation-experimental` | 1.4.0 | Apache-2.0 |
| `androidx.arch.core:core-common`, `core-runtime` | 2.2.0 | Apache-2.0 |
| `androidx.collection:collection` | 1.1.0 | Apache-2.0 |
| `androidx.concurrent:concurrent-futures` | 1.1.0 | Apache-2.0 |
| `androidx.core:core` | 1.13.1 | Apache-2.0 |
| `androidx.customview:customview` | 1.1.0 | Apache-2.0 |
| `androidx.interpolator:interpolator` | 1.0.0 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-common`, `lifecycle-runtime` | 2.6.2 | Apache-2.0 |
| `androidx.profileinstaller:profileinstaller` | 1.3.0 | Apache-2.0 |
| `androidx.startup:startup-runtime` | 1.1.1 | Apache-2.0 |
| `androidx.tracing:tracing` | 1.0.0 | Apache-2.0 |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 | Apache-2.0 |
| `com.google.guava:listenablefuture` | 1.0 | Apache-2.0 |

AndroidX is Copyright The Android Open Source Project. Kotlin and kotlinx.coroutines are
Copyright JetBrains s.r.o. and contributors. The full licence text is in
[licenses/Apache-2.0.txt](licenses/Apache-2.0.txt).

## 3. Test-only dependencies (never distributed)

| Dependency | Version | Licence |
|---|---|---|
| `junit:junit` | 4.13.2 | EPL-1.0 |
| `org.hamcrest:hamcrest-core` | 1.3 | BSD-3-Clause |
| `com.google.truth:truth` | 1.4.4 | Apache-2.0 |
| `org.robolectric:robolectric` | 4.16.1 | MIT |
| `androidx.test:core` | 1.7.0 | Apache-2.0 |
| `androidx.test:runner` | 1.7.0 | Apache-2.0 |
| `androidx.test.ext:junit` | 1.3.0 | Apache-2.0 |
| `androidx.test.uiautomator:uiautomator` | 2.3.0 | Apache-2.0 |

The last three are used only by the tests that run on a device (`showcase/src/androidTest`).
They are packaged into a separate test APK that is installed next to the demo app during a
test run and is not part of any release artifact.

## 4. Build tooling

| Tool | Version | Licence | Redistributed here? |
|---|---|---|---|
| Gradle Wrapper (`gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`) | 8.13 | Apache-2.0 | yes, unmodified |
| Android Gradle Plugin | 8.13.2 | Apache-2.0 | no |
| Kotlin Gradle Plugin | 2.2.21 | Apache-2.0 | no |
| Android SDK Platform 36, Build-Tools 36.0.0 | — | Android SDK licence | no (compile-time only) |

## 5. Design references

- **Lucide** (ISC License, Copyright Lucide Icons and Contributors; portions derived from
  Feather, MIT License, Copyright Cole Bemis). The original application drew its chevron and
  arrow glyphs to be "Lucide-compatible", and Sancai's `CHEVRON_*` and `ARROW_*` icons keep
  those proportions. No Lucide file, path string or code is included: every icon is drawn
  with canvas calls written for this project. The licence text is reproduced in
  [licenses/Lucide-ISC.txt](licenses/Lucide-ISC.txt) as a courtesy.

## 6. Fonts, images and other media

None. Sancai UI uses the system typefaces of the device. The sources of the library and the
Showcase app
contain no raster image, font file, sound or third-party vector asset; all drawings are
made in this repository (canvas code, plus the Showcase's own vector launcher icon). A built
Showcase APK can still carry the stock PNG resources of `androidx.core` (Apache-2.0).
