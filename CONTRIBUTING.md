# Contributing

Thank you for looking into Mallet UI. This page says how the project is built, what a change
has to satisfy, and how new design is judged.

## Set up

- JDK 17 or newer (the build is tested with 21) and the Android SDK with Platform 36 and
  Build-Tools 36.0.0. Point `ANDROID_HOME` at the SDK or write `sdk.dir=...` into
  `local.properties`.
- Clone and run the tests. Nothing else has to be installed; the tests run on the JVM.

```bash
./gradlew :mallet:testDebugUnitTest :showcase:testDebugUnitTest
```

- Android Studio opens the project as it is. The layout editor previews the views.

## The project

```
mallet/      the library; public API in io.github.avalonskyafar.mallet.*
showcase/    the demo app; it uses the library exactly as another app would
samples/     minimal: a project of its own, in Java and XML, that uses the published binary
docs/        the published documentation, and docs/dev for contributors
tools/       catalog.py and api_index.py (generated documents), pictures.py, films.py and
             gallery.py (pictures and clips), device.py (tests and tours on a device)
```

`docs/dev/CONVENTIONS.md` is the rule book for library code: the anatomy of a widget, the
behaviour contract (listeners, haptic order, drags, reduced motion, right-to-left, saved
state, accessibility, keyboard, large text) and how tests are written. `docs/dev/FOUNDATION.md`
is a cheat sheet of the theme, material and motion APIs. `docs/dev/SHOWCASE.md` describes how
a demo page is built. `docs/dev/API.md` is a generated index of the public API.

## What a change has to satisfy

1. **Tests pass.** `./gradlew :mallet:testDebugUnitTest :showcase:testDebugUnitTest`.
   New behaviour comes with tests that assert it: state after taps, drags and keys, listener
   rules, save and restore, right-to-left.
2. **You looked at the pictures.** Tests that end in `snapshot of ...` write PNGs to
   `mallet/build/snapshots` and `showcase/build/snapshots`, in the light and in the dark
   theme. Open them. A drawing that passes its assertions can still look wrong.
3. **Lint is clean.** `./gradlew :mallet:lintDebug :showcase:lintDebug`. Warnings are errors
   in both modules.
4. **The source rules hold.** `SourceRulesTest` fails on a ripple, on elevation, on a text
   size that is not a `MalletTextRole`, and on a listener that is a property instead of a
   `setOn…Listener` function.
5. **Public API has KDoc**, and user-visible strings are resources in English and Simplified
   Chinese.
6. **The documents follow.** Regenerate the catalog (`python tools/catalog.py`) when KDoc,
   attributes or provenance change. When a drawing changes, run `python tools/pictures.py`
   after the tests (it needs Pillow), so that the pictures of the catalog show it. Add a
   line to `CHANGELOG.md`.

Changes to how something moves under the finger should also be tried on a device:

```bash
python tools/device.py build
python tools/device.py install
python tools/device.py test
```

## New design

Mallet is a small language, and it stays recognisable only if additions are made from the
same parts. Before proposing a component or a variant, answer these, in the issue or in the
pull request:

- **Which material is it?** Surface, track, thumb or well. If it needs a fifth, the
  proposal is about the language, not about a component: open an issue first.
- **Where does its colour live, and what state does the colour mean?** Colour that decorates
  is not Mallet.
- **Where does it stand: in a card, or on the canvas?** A card is a group of at least two
  rows or blocks that belong together; a lone control never gets a card of its own
  (decision D-25 in `docs/DESIGN_DECISIONS.md`).
- **What does it do under the finger?** It sinks, it follows, or it glides on the one curve.
- **What are its disabled, focused and pressed states**, and how does it read to a screen
  reader, mirror in right-to-left, and behave with reduced motion?

Every part of the library carries a mark in `docs/DESIGN_DECISIONS.md`: **Extracted** from
the original app (with file and lines), **Extended**, or **New**. A new component gets a
`New` row and a decision entry (`D-nn`) that states the reasoning. A change that departs
from the original on purpose gets a decision entry too, as D-24 and D-25 do. Please do not
present new design as extracted.

## Commits and pull requests

- One topic per pull request; describe what changes for the user of the library.
- Do not reformat files you are not changing.
- By contributing you agree that your contribution is licensed under the MIT License of the
  project. Do not add code, icons, fonts or pictures under other terms; if something is
  derived from another work, say so in the pull request, and it will be recorded in
  `THIRD_PARTY_NOTICES.md`.

## Releases

Maintainers build the artifacts with `./gradlew dist` (AAR, sources, Maven repository zip,
demo APK, source zip, checksums) and record what was run for the release in
`docs/VERIFICATION.md`.
