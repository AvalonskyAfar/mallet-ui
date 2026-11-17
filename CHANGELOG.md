# Changelog

All notable changes to Sancai UI are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[Semantic Versioning](https://semver.org/). Until 1.0, a minor version may change the API.

## 0.1.0 — 2026-11-18

The first release: the design language of the Gravekeeper app, restated as an independent
library, completed, documented and demonstrated. What was taken from the original, what was
extended and what is new is recorded part by part in
[docs/DESIGN_DECISIONS.md](docs/DESIGN_DECISIONS.md).

### Design system

- Colour roles for a light and a dark palette, with high-contrast variants; five glazes
  (terracotta, ochre, cobalt, celadon, mist) with their scale for two, three and four
  options and their night form.
- A type scale of eleven roles, tones for text, a spacing scale, corner radii.
- Three painted materials (porcelain, glazed track, thumb) and the well, with pressed,
  focused and disabled forms; shadows are drawn inside each view's own bounds.
- Motion tokens: one easing curve, five durations, the commit and flick rules.
- Fifty icons drawn in code, mirrored in right-to-left layouts where they point.
- `SancaiTheme`: from XML theme attributes, from code, from an app's own settings
  (`SancaiTheme.resolver`), or for one part of a screen (`SancaiTheme.wrap`).

### Components

- Surfaces and text: `SancaiSurface`, `SancaiDivider`, `SancaiText`, `SancaiIconView`.
- Actions: `SancaiButton` (neutral, primary, destructive, text; icon, loading), `SancaiChip`,
  `SancaiChipGroup`, `SancaiTag`, `SancaiBadge`.
- Choice: `SancaiSegmentedTrack`, `SancaiSwitch`, `SancaiPowerSwitch`, `SancaiCheckbox`,
  `SancaiRadioButton`, `SancaiRadioGroup`.
- Numbers and progress: `SancaiStepper`, `SancaiSlider`, `SancaiProgressBar`, `SancaiSpinner`.
- Text input: `SancaiTextField` (label, helper, error, counter, end actions).
- Rows and pages: `SancaiPage` (a scrolling page that pads itself for the system bars and
  the keyboard, keeps what scrolls clear of the status bar, and holds its column to
  560 dp), `SancaiHeading`, `SancaiEntryRow`, `SancaiControlRow`, `SancaiLinkRow`,
  `SancaiKeyValueList`.
- Reading: `SancaiParagraph`, `SancaiCallout`, `SancaiFlowRow`, `SancaiMediaFrame`.
- Navigation: `SancaiNavBar`, `SancaiTopBar`, `SancaiTabs`, `SancaiPager`,
  `SancaiPageIndicator`.
- Overlays: `SancaiDialog`, `SancaiBottomSheet`, `SancaiMenu`, `SancaiToast`,
  `SancaiFloatingPanel`.
- States: `SancaiStateView`, `SancaiStateLayout`, `SancaiSkeleton`, `SancaiBanner`.

Every component can be declared in XML or built in code, from Kotlin and from Java; has
disabled, focused and pressed states; works with keyboard and D-pad; describes itself to
screen readers; mirrors in right-to-left layouts; and keeps its state across rotation.

### Interaction

- `SancaiPress`: content that sinks under the finger; `SancaiHaptics`: tick, detent, confirm,
  reject, long press.
- `SancaiSpatialGraph`, `SancaiSpatialHost`: pages laid out as places and dragged at finger
  speed; `SancaiBack`: system Back along the map, with the predictive preview on Android 14
  and later; `SancaiSpatialTransition`: a second activity that arrives and leaves like a page.
- `SancaiRevealLayout`: a row that slides aside for one action; `SancaiExpandableLayout`: a
  section that opens in place.
- `SancaiThemeTransition`: the old look fades over the new one when the theme changes.
- One rule for who owns a gesture: the deepest control that can use a drag takes it, and
  hands it on when it cannot (`SancaiHorizontalDragOwner`).

### Showcase app

- A demo app built from the library alone: Foundations, Components, Interactions and
  Appearance around Home, every component live with the code that makes it, in English and
  Simplified Chinese.

### Documentation and tools

- Design system, component catalog (generated from the sources), interaction guide,
  integration guide, design decisions and provenance, licence audit, verification record.
- `samples/minimal`: a one-screen app in Java and XML that uses the released binary.
- `tools/catalog.py`, `tools/api_index.py`, `tools/pictures.py`, `tools/films.py`,
  `tools/device.py`, `tools/gallery.py`; `./gradlew dist` builds the AAR, a Maven
  repository zip, the demo APK, a source zip and checksums.

### Verified

- 1,030 library tests and 20 showcase tests on the JVM; Android Lint without issues in
  both modules; 36 device tests on an Android 16 emulator and 35 on Android 6.0. What was
  run, and what was not, is in [docs/VERIFICATION.md](docs/VERIFICATION.md).
