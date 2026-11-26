# Minimal sample

A one-screen app in **Java and XML** that uses Mallet UI the way any other app does: as a
binary from a Maven repository. It is a project of its own, not a module of the Mallet UI
build, and it has one dependency:

```kotlin
implementation("io.github.avalonskyafar:mallet:0.1.1")
```

- `src/main/res/layout/activity_main.xml` declares the whole screen.
- `src/main/java/.../MainActivity.java` adds the listeners. It extends plain `Activity`.
- `src/main/res/values/themes.xml` inherits `Theme.Mallet` and changes one hue.
- The release build is shrunk with R8, without a single keep rule for the library.

## Build

The sample reads the library from a Maven repository in a directory. Build one in the main
project first, then build the sample with the Gradle wrapper of the main project:

```bash
./gradlew :mallet:publishReleasePublicationToDistRepository
```

```bash
cd samples/minimal
```

```bash
../../gradlew assembleRelease
```

The first command fills `build/dist-repo` of the main project, which is where the sample
looks by default. The APK is written to `build/outputs/apk/release/`.

To use the repository of a downloaded release instead, unzip
`mallet-0.1.1-maven-repo.zip` anywhere and pass its directory:

```bash
../../gradlew assembleRelease -PmalletRepo=/path/to/unzipped/repo
```
