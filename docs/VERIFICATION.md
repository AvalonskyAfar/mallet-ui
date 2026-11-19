# Verification

What was built, run and looked at for version 0.1.0, on which machine, with which result,
and what was not verified. Every number here was produced by a run on the sources of this
release; where something was only read, or only looked at, it says so.

Contents: [Summary](#summary) · [Environment](#environment) · [Tests on the JVM](#tests-on-the-jvm) ·
[Static checks](#static-checks) · [Tests on devices](#tests-on-devices) ·
[What the device runs found](#what-the-device-runs-found) · [Pictures and clips](#pictures-and-clips) ·
[Release artifacts](#release-artifacts) · [Integration](#integration) · [Reviews](#reviews) ·
[Not verified](#not-verified) · [Known limits](#known-limits)

---

## Summary

| Check | Result |
|---|---|
| Unit tests of the library (JVM, Robolectric) | 1,045 tests, 0 failures |
| Unit tests of the showcase (JVM, Robolectric) | 35 tests, 0 failures; 8 more (films) pass when asked for |
| Android Lint, warnings as errors | no issues in `:mallet`, no issues in `:showcase` |
| Device tests, Android 16 (API 36) emulator | 54 tests, 0 failures (re-run at the revision commit) |
| Device tests, Android 6.0 (API 23) emulator | 37 tests, 0 failures (recorded before the revision; not re-run) |
| Shrunk release APK, every page opened on both emulators | 27 of 27 pages on each, no crash |
| Library used as a binary by a separate Java project | builds shrunk, runs on both emulators |
| The source archive, unpacked elsewhere, builds and passes its tests | 1,038 + 35 tests, lint without issues, both release builds |

Nothing was published: no push, no release, no upload.

---

## Environment

| | |
|---|---|
| Machine | Windows 11, Intel Core Ultra 7 155H, 16 GB RAM |
| JDK | Eclipse Temurin 21.0.13 |
| Build | Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.2.21 (library compiled with language and API level 2.0) |
| Android SDK | Platform 36, Build-Tools 36.0.0; `minSdk` 23 |
| JVM test runtime | Robolectric 4.16.1 with Android's native graphics; Android 14 (API 34) unless stated |
| Emulator | Android Emulator 37.2.13, hardware graphics (`-gpu host`, Intel Arc) |
| Device A | Android 16 (API 36), Google APIs image, x86_64, 1080 × 2400 at 420 dpi, gesture navigation |
| Device B | Android 6.0 (API 23), AOSP image, x86_64, 1080 × 1920 at 420 dpi, three-button navigation |

Both devices are emulators. No physical device was used.

---

## Tests on the JVM

```bash
./gradlew :mallet:testDebugUnitTest :showcase:testDebugUnitTest
```

**Library: 1,045 tests in 50 classes, 0 failures, 0 skipped.** One class per component, plus
the foundation, the drag gate, the press helper and the rules below. They assert behaviour:
the state after taps, drags and keys; that listeners are called for user input only; the
order of listener and haptic; saved and restored state; right-to-left placement;
accessibility nodes, roles and actions; exact pixels where colour is the contract (hue
under a thumb, contrast ratios of every text and label colour); and geometry in dp.

**Showcase: 20 tests, 0 failures.** Every page of the demo app is built the way the app
builds it and drawn in five variants: light, dark, Chinese, right-to-left, and large text
(system font scale 1.3 with the app's own 1.13). A page that throws or comes out blank
fails. The XML demonstration is checked against the layout file it shows.

**Films: 8 tests, 0 failures**, run on request (`-PmalletFilms`; skipped otherwise). They
start the real activity of the showcase, touch it with `MotionEvent`s and step an exact
clock, frame by frame, asserting where the app ends up: travelling between pages, dragging
tracks, switches and sliders, rows that slide aside, sections, linked pagers.

**Older platforms.** `PlatformLevelsTest` runs every view of the library through a whole
life (build, fill, measure, lay out, draw, touch, keys, accessibility node, save, restore,
disable, focus, right-to-left, detach), opens and closes every overlay, and calls the
window, back, haptics, theme and transition helpers, on **API 23, 28 and 32**. It catches a
call to something an older Android does not have. It uses Robolectric's plain canvas, so it
says nothing about how those versions draw.

Each test class also draws a gallery of its component in both themes
(`mallet/build/snapshots`, copied to `docs/images/components`). Those pictures were looked
at; they are not compared with stored references by the tests.

Where a test was written for a defect, it was run against the code without the fix and
seen to fail, for most fixes; the reviews below name the cases where that was only reasoned.

---

## Static checks

```bash
./gradlew :mallet:lintDebug :showcase:lintDebug
```

- Android Lint with `warningsAsErrors`: **no issues** in either module. Three version
  checks are switched off on purpose (the library pins the oldest dependency versions that
  give it what it needs); nothing else is suppressed globally.
- The library is compiled in Kotlin's explicit API mode: nothing is public by accident.
- `SourceRulesTest` reads the library's own sources and fails on a ripple, on platform
  elevation, on a text size that is not a role of the type scale, and on a listener that is
  a property instead of a `setOn…Listener` function.
- `tools/catalog.py --check` confirms that the component catalog matches the sources.

---

## Tests on devices

```bash
python tools/device.py build
python tools/device.py install
python tools/device.py test
```

The device tests (`showcase/src/androidTest`) drive the installed showcase through
UiAutomator, so every touch travels the way a finger's does: the system's input dispatcher,
the window, the view tree. State is read from the views afterwards.

| Class | Tests | What it drives |
|---|---|---|
| `NavigationDeviceTest` | 9 | the power switch by tap and drag; dragging to the four pages around Home and Back from each; an entry at the bottom of Home opens its page; a page around Home carries no way back and is left by dragging back; a short slow drag that glides back; a row that opens a page; a page attached later keeps clear of the status bar; a second activity that arrives like a page and is swiped back; rotation |
| `ControlsDeviceTest` | 9 | a track by tap and by drag; a vertical drag that starts on a track and scrolls the page; a change the listener refuses; a switch; a stepper tapped and held; a slider; checkboxes and radios; typing into a field; focus walking into a page with the arrow keys |
| `GesturesDeviceTest` | 7 | a detail page dragged back to its parent; a row slid aside and its action; a short drag that glides shut; a switch inside a sliding row; a section; linked pagers; a pager at its first page handing the drag to the page |
| `OverlaysDeviceTest` | 8 | dialogs confirmed, cancelled with Back, with three actions, with input that is refused and then accepted; a sheet dragged down; a sheet action; a menu; a toast that leaves by itself |
| `AppearanceDeviceTest` | 2 | switching to dark and back, and to larger text, while the app runs |
| `PagesDeviceTest` | 3 | every page opened and scrolled from top to bottom in light and in dark with hardware rendering; every page opens at its top; the window is not blank |

**Device A, Android 16: 38 tests, 0 failures**, in 331 s
([report](verification/device-tests-android-16.txt)).

**Device B, Android 6.0: 37 tests, 0 failures**, in 229 s
([report](verification/device-tests-android-6.txt)). The one test that does not run there needs an
API from Android 8.0 to read the window's pixels. Every page was also photographed on this
device in both themes, and the overlays in both themes, and the pictures were looked at.

The screenshot tour (8 parts, 159 pictures;
[report](verification/screenshot-tour-android-16.txt)) and the motion tour (8 clips;
[report](verification/motion-tour-android-16.txt)) ran on device A without a failure; the motion tour fails if a clip does not show what it is meant to show
(a thumb that did not arrive, a row that did not open).

---

## What the device runs found

Running the app on real system images found defects that 1,000 tests on the JVM had not.
They are fixed in this release, each with a test:

- **Pages under the status bar.** Android hands window insets to a view when they change,
  not when the view arrives. Pages attached by the page host after the first layout pass
  never got them and lay under the status bar.
- **Text under the clock.** Scrolled content was drawn behind the transparent status bar.
  A page now ends what scrolls at the bar.
- **Pages that opened half-way down, on Android 6.0.** Before Android 8.0 a window gives
  the focus to the first view that takes it when it opens; a scroll view then scrolls to
  it. Pages with a text field or selectable text opened scrolled to it.
- **A row shut by the drag that opened it.** A drag longer than a sliding row's travel was
  taken over by the page host, which cancelled and closed the row. Found by the films.
  Sideways, a gesture now has one owner from start to finish.
- **In the test code itself:** a gesture helper that sent no intermediate moves, so that a
  "slow drag" was a jump (the tests then asserted nothing about following the finger), and
  a wait that returned before the window had input focus.

One problem was the emulator's and not the app's: with software graphics
(`-gpu swiftshader_indirect`) the emulator's render pipe stalled after about nine minutes
and every app on it froze. All results here are from runs with `-gpu host`.

---

## Pictures and clips

- `docs/images/components`: drawn by the unit tests on the JVM. They show what the code
  draws; they contain no status bar and use Robolectric's fonts.
- `docs/images/device`, `docs/images/hero.png` and [GALLERY.md](GALLERY.md): screenshots of
  the showcase running on device A, taken by the screenshot tour
  (`python tools/device.py shots`), scaled to 480 px and stored with a palette of 256
  colours by `tools/gallery.py`. The status bar shows Android's demo mode (a fixed clock).
  Nothing is drawn into them; the hero rounds the corners of five of them and sets them on
  a plain ground.
- `docs/images/motion`: screen recordings of device A, made by the motion tour
  (`python tools/device.py motion`) at normal speed with real touch events, then turned
  into GIFs (320 px wide, 25 frames a second, 128 colours, the first 1.1 s of still lead
  cut off). **The dot that marks the finger is drawn by the tour**, in the overlay of the
  app's window, because Android's "show taps" does not show injected touches; it does not
  mark touches on dialogs, sheets and menus, which have windows of their own. On an
  emulator the recorder receives about 20 to 40 frames a second; a phone is smoother.

---

## Release artifacts

```bash
./gradlew dist
```

The artifacts are built from a clean export of the release commit (`git archive`), not from
a working directory, and without the build cache.

| File | What it is | Size |
|---|---|---|
| `mallet-0.1.0.aar` | the library | 0.8 MB |
| `mallet-0.1.0-sources.jar` | its sources, for IDEs | 0.3 MB |
| `mallet-0.1.0-maven-repo.zip` | a Maven repository with the AAR, its POM, Gradle module metadata and the sources | 1.1 MB |
| `mallet-showcase-0.1.0.apk` | the demo app, shrunk by R8 | 0.5 MB |
| `mallet-ui-0.1.0-source.zip` | this project without build output | 23 MB |
| `SHA256SUMS.txt` | the checksums of the five files | |

The AAR holds the library's classes and resources only: no image, no font, no native code,
no bundled dependency. Its POM declares `androidx.annotation` and the Kotlin standard
library as compile dependencies and `androidx.core` and `androidx.customview` as run-time
dependencies.

The release build of the showcase is shrunk and optimised by R8 with resource shrinking and
no keep rule for the library; the library ships an empty consumer rules file.

**The demo APK is signed with the Android debug key**, because no release key was supplied
(`SHOWCASE_KEYSTORE` and its companions in `showcase/build.gradle.kts`). It installs for
evaluation; it must not be published to a store as it is.

---

## Integration

- **As a binary.** `samples/minimal` is a project of its own, in Java and XML, with one
  dependency: `io.github.avalonskyafar:mallet:0.1.0` from the Maven repository that `dist`
  produces. It was built against the repository unpacked from
  `mallet-0.1.0-maven-repo.zip`, shrunk by R8 without a keep rule (an APK of 0.17 MB),
  installed on both emulators and operated: the screen appears with the hue its theme
  overrides, a save without a name shows the field's error, the track answers, the
  confirmation dialog restores the defaults.
- **From the source archive.** `mallet-ui-0.1.0-source.zip` was unpacked into an empty
  directory outside the working tree. There
  `./gradlew :mallet:testDebugUnitTest :showcase:testDebugUnitTest :mallet:lintDebug :showcase:lintDebug :mallet:assembleRelease :showcase:assembleRelease --no-build-cache`
  succeeded: the same 1,045 and 35 tests, no lint issue, both release builds.
- **Shrunk.** `python tools/device.py smoke` installed the release APK from `dist/` on each
  emulator, opened all 27 pages and scrolled each: none crashed. The check itself was
  checked: a crash forced with `am crash` is seen by it. The page that is inflated from a
  layout file, where shrinking could remove a view class, and the page of overlays were
  also looked at in this build.

---

## Reviews

- **Code.** Every source file of the library was read by a reviewer looking for defects;
  each finding was then checked by a second reviewer against the code. 103 findings were
  confirmed and 10 rejected. All 103 are fixed: each area by an engineer, then examined by
  another reviewer who compared the change with the finding, ran the whole suite and the
  showcase build before the change was accepted.
- **Documents.** Every checkable statement in the documents, in the copy of the demo app
  (English and Chinese) and in the KDoc of the library, 2,350 in all, was compared with
  the sources by 22 readers: names, numbers, defaults, orders of events, code samples,
  citations of the original app. 98 statements were reported as untrue. One report was
  mistaken; the others are corrected, three of them by changing the code to do what the
  text said (the start inset of a control row, the width limit of a page, a build script
  that needed two properties).
- **Licences.** See [LICENSE_AUDIT.md](LICENSE_AUDIT.md).

The reviewers were AI agents working on separate copies of the project. Their reports were
read, and their fixes re-run, before anything was accepted.

---

## Not verified

- **Physical devices.** Touch latency, frame pacing at 90 or 120 Hz, and haptics can only
  be judged on hardware. On the emulator haptic calls are made but nothing is felt.
- **Android versions between 6.0 and 16 on a device.** API 28 and 32 were exercised on the
  JVM only; the versions in between not at all.
- **Screen readers in use.** Roles, states, actions and virtual children are asserted on
  accessibility nodes in the unit tests. Nobody operated the app with TalkBack or Switch
  Access.
- **Tablets, foldables, landscape.** The page's width limit is tested on the JVM, and one
  device test rotates the screen; the showcase was not looked at on a large screen.
- **Right-to-left and Chinese on a device** were photographed and looked at, not asserted;
  the assertions for both are in the JVM tests.
- **Performance and memory** were not measured.
- **The JitPack build file** (`jitpack.yml`) and the CI workflow (`.github/workflows/ci.yml`)
  were written but have never run, since the project has not been pushed anywhere.
- **Binary compatibility across versions**: there is no earlier version to compare with.

---

## Known limits

Left as they are, on purpose or for later:

- The default light palette keeps the look of the original, and with it three pairs that
  do not meet WCAG AA: white labels resting on the bronze, cobalt and jade hues
  (3.0, 2.3 and 2.2 : 1) and muted text on the canvas (3.2 : 1). `malletHighContrast`
  brings every pair to at least 4.5 : 1; it follows the system's high-contrast text setting
  on Android 16 when the theme is read from the Android theme. The figures are in
  [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md#121-contrast).
- A view reads its theme when it is built; a theme change needs the views rebuilt
  (`MalletThemeTransition` hides the cut).
- `MalletStepper` does not mirror in right-to-left layouts: minus stays on the left.
- Pressing Retry twice with a keyboard within the 220 ms in which `MalletStateLayout`
  changes state can call the listener twice; touches are guarded.
- `MalletThemeTransition.recreateAfter` called on an activity that is never attached to a
  window keeps that activity referenced until the process ends.
- `MalletRevealLayout` with padding of its own lets the open row slide over that padding.
- Space or Enter on a radio button that is already chosen is passed on unconsumed, while a
  tap on it calls the click listener.
- A scrolling row of tabs does not offer scroll actions to accessibility services; a tab
  is scrolled into view when it takes accessibility focus.
