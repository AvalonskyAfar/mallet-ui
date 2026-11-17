# NOTICE

Sancai UI
Copyright (c) 2026 AvalonskyAfar

Licensed under the MIT License. See [LICENSE](LICENSE).

## Provenance

Sancai UI is derived from the user-interface code of **Gravekeeper (守目人)**,
Copyright (c) 2026 AvalonskyAfar, which is itself released under the MIT License.
The design language, the material painters and the gesture logic were extracted from that
application's source (`UiKit.java`, `MainActivity.java`, `FirstLaunchActivity.java`,
`WhitelistAccountsPage.java`, `DeveloperOptionsActivity.java`, `MotionSpec.java` and
related files), then rewritten in Kotlin as an independent library and extended.

The same copyright holder publishes both works under the same licence. The MIT copyright
and permission notice of the original is reproduced unchanged in [LICENSE](LICENSE), which
satisfies the notice condition of that licence for the derived portions.

## What is not included

The following parts of the original repository are **not** part of Sancai UI, in source or
in any built artifact:

| Original material | Its licence | Status here |
|---|---|---|
| Detection logic, accessibility service, configuration store | MIT | not used |
| On-device models (`*.tflite`, `*.bin`, fusion model) | CC BY-NC 4.0 | not used, not copied |
| Illustrations and launcher artwork (`drawable-nodpi/*.png`) | not stated separately | not used, not copied |
| LiteRT, Google ML Kit text recognition | Apache-2.0 / Google ML Kit terms | not used |
| Product name, mascot and application copy | — | not used |

Every picture in the library and in the Showcase app is drawn by this project (canvas code,
plus the Showcase's vector launcher icon). The sources hold no raster image, font or
third-party icon file; a built Showcase APK may carry `androidx.core`'s own PNG resources.

## Third-party software

Build-time and run-time dependencies, with their licences, are listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The reasoning behind each decision is
recorded in [docs/LICENSE_AUDIT.md](docs/LICENSE_AUDIT.md).
