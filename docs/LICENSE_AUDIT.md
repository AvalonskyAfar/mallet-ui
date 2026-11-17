# Licence audit

Date: 2026-11-17. Scope: everything Sancai UI takes from the original application
repository (`Gravekeeper-master`), and everything Sancai UI depends on or ships.

This document records what was checked, what was found and what was decided. It is an
engineering record, not legal advice.

## 1. The original repository

| Item | Finding | Evidence |
|---|---|---|
| Licence file | MIT License, "Copyright (c) 2026 AvalonskyAfar" | `Gravekeeper-master/LICENSE` |
| README licence table | application code: MIT; models and training artefacts: CC BY-NC 4.0; third-party components: their upstream licences | `README.md`, section "许可证"; `README.en.md`, section "License" |
| Broken link | both READMEs link to `Gravekeeper/LICENSE`, which does not exist; the licence file is at the repository root | directory listing |
| Third-party code in the app | LiteRT 2.1.4, Google ML Kit Chinese text recognition 16.0.1, JUnit 4.13.2 (tests) | `Gravekeeper/app/build.gradle` |
| Stated research references | ViewPager2, ViewDragHelper, NestedScrolling, overscroll-decor, Material Components, SwipeRevealLayout, SwipeBackLayout: "studied for mechanisms, not included as dependencies" | `MainActivity.java`, open-source notes text |
| Icon reference | chevron and arrow glyphs are described as "Lucide-compatible" | `UiKit.java` lines 847 and 893 |
| Artwork | ten PNG illustrations in `res/drawable-nodpi`; no separate licence statement | directory listing, READMEs |
| Artwork provenance | `ic_launcher_art.png` carries a C2PA manifest: generator "gpt-image" 2.0 via "OpenAI Media Service API", digital source type `trainedAlgorithmicMedia`, created 2026-08-15. The other nine files carry no metadata and share the same visual style. | PNG `caBX` chunk of the file |

## 2. Decisions

1. **Sancai UI is MIT licensed**, with the original copyright line unchanged. The UI code it
   derives from is MIT, both works have the same copyright holder, and MIT only requires
   that its notice be kept, which it is. Nothing in the original repository is relicensed
   or modified; the original checkout is left untouched.
2. **Only UI source code was used**: `UiKit.java`, the page and gesture code of the
   activities, `MotionSpec.java`, `ReadingLayoutSpec.java`, the theme XML. It was read,
   re-implemented in Kotlin and extended. Detection, configuration and service code was not
   used.
3. **No artwork was copied.** The illustrations are not covered by an explicit licence
   statement, at least one is AI-generated media whose reuse terms depend on the generating
   service, and a UI toolkit does not need them. The Showcase app draws its own pictures in
   code.
4. **No model file was copied.** They are CC BY-NC 4.0, which is incompatible with an MIT
   distribution, and they are irrelevant to a UI library.
5. **None of the original's third-party dependencies is used.** Sancai UI depends on three
   AndroidX libraries and the Kotlin standard library, all Apache-2.0, which is compatible
   with distributing the library under MIT. See `THIRD_PARTY_NOTICES.md`.
6. **Icons are drawn for this project.** The 50 glyphs are canvas drawings written here; no
   file or path data from an icon library was imported. Because the original states that
   its chevrons and arrows follow Lucide's proportions, and Sancai keeps those two shapes,
   the Lucide ISC notice is reproduced as a courtesy.
7. **No product identity was carried over.** The names "Gravekeeper" / "守目人", the mascot
   and the application copy are not used in the library or the Showcase app.

## 3. What a reader can verify

- `git grep -n -i "gravekeeper\|守目人"` in this repository returns matches only in
  documentation that describes the provenance.
- The AAR contains classes and XML resources only: `unzip -l sancai-<version>.aar`.
- The dependency list in `THIRD_PARTY_NOTICES.md` matches
  `./gradlew :showcase:dependencies --configuration debugRuntimeClasspath`.

## 4. Open points for the maintainer

These need a human decision before the project is published; none blocks local use.

- Confirm the copyright holder name and year in `LICENSE`.
- Confirm the Maven group id `io.github.avalonskyafar` (taken from the GitHub account named
  in the original README) and the artifact id `sancai`.
- The name "Sancai" (三彩) is a generic term for Tang-dynasty three-colour glazed pottery. A
  brief web search on 2026-11-17 found no Android UI library of that name; this is not a
  trademark search.
- If illustrations from the original app are wanted in the project later, state their
  licence explicitly first.
