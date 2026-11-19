# Changelog

All notable changes to Mallet UI are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[Semantic Versioning](https://semver.org/). Until 1.0, a minor version may change the API.

## 0.1.0 — 2026-11-18

The first release of Mallet UI: the design language of the Gravekeeper app, restated as an
independent library, completed, documented and demonstrated. What was taken from the
original, what was extended and what is new is recorded part by part in
[docs/DESIGN_DECISIONS.md](docs/DESIGN_DECISIONS.md); the numbers `D-nn` below point to
its decisions.

### Design system

- Colour roles for a light and a dark palette, with high-contrast variants; five hues
  (bronze, ochre, cobalt, jade, mist) with their scale for two, three and four
  options and their night form.
- A type scale of eleven roles, tones for text, a spacing scale, corner radii.
- Four materials (surface, track, thumb, well), with pressed, focused and disabled
  forms; shadows are drawn inside each view's own bounds.
- Motion tokens: one easing curve, five durations, the commit and flick rules.
- Fifty icons drawn in code, mirrored in right-to-left layouts where they point.
- `MalletTheme`: from XML theme attributes, from code, from an app's own settings
  (`MalletTheme.resolver`), or for one part of a screen (`MalletTheme.wrap`).

### Components

- Surfaces and text: `MalletSurface` (the card: it groups at least two rows or blocks that
  belong together; a lone control joins its relatives or stands on the canvas, D-25),
  `MalletDivider`, `MalletText`, `MalletIconView`.
- Actions: `MalletButton` (neutral, primary, destructive, text; icon, loading), `MalletChip`,
  `MalletChipGroup`, `MalletTag`, `MalletBadge`.
- Choice: `MalletSegmentedTrack`, `MalletSwitch`, `MalletPowerSwitch`, `MalletCheckbox`,
  `MalletRadioButton`, `MalletRadioGroup`.
- Numbers and progress: `MalletStepper`, `MalletSlider`, `MalletProgressBar`, `MalletSpinner`.
- Text input: `MalletTextField` (label, helper, error, counter, end actions).
- Rows and pages: `MalletPage` (a scrolling page that pads itself for the system bars and
  the keyboard, keeps what scrolls clear of the status bar, and holds its column to
  560 dp), `MalletHeading`, `MalletEntryRow`, `MalletControlRow`, `MalletLinkRow`,
  `MalletKeyValueList`.
- Reading: `MalletParagraph`, `MalletCallout`, `MalletFlowRow`, `MalletMediaFrame`.
- Navigation: `MalletNavBar`, `MalletTopBar`, `MalletTabs`, `MalletPager`,
  `MalletPageIndicator`.
- Overlays: `MalletDialog`, `MalletBottomSheet`, `MalletMenu`, `MalletToast`,
  `MalletFloatingPanel`.
- States: `MalletStateView`, `MalletStateLayout`, `MalletSkeleton`, `MalletBanner`.

Every component can be declared in XML or built in code, from Kotlin and from Java; has
disabled, focused and pressed states; works with keyboard and D-pad; describes itself to
screen readers; mirrors in right-to-left layouts; and keeps its state across rotation.

### Interaction

- `MalletPress`: content that sinks under the finger; `MalletHaptics`: tick, detent, confirm,
  reject, long press.
- `MalletSpatialGraph`, `MalletSpatialHost`: pages laid out as places and dragged at finger
  speed; `MalletBack`: system Back along the map, with the predictive preview on Android 14
  and later; `MalletSpatialTransition`: a second activity that arrives and leaves like a page.
- `MalletRevealLayout`: a row that slides aside and uncovers one action from the rim of
  its card (D-24); `MalletExpandableLayout`: a section that opens in place.
- `MalletThemeTransition`: the old look fades over the new one when the theme changes.
- One rule for who owns a gesture: the deepest control that can use a drag takes it, and
  hands it on when it cannot (`MalletHorizontalDragOwner`).

### Showcase app

- A demo app built from the library alone: Foundations, Components, Appearance and
  Interactions around Home, every component live with the code that makes it, in English
  and Simplified Chinese.
- Home and the four first-level pages around it carry no navigation marks, as in the
  original app (D-23). Home names its neighbours once, in a row of entries at its bottom;
  an entry can be tapped, and its page is also reached by dragging Home towards the side
  where that page lies. A first-level page shows no way back: it is left by dragging back
  towards Home, or with the system's Back. Deeper pages keep a quiet way-back row that
  names their parent; that row is the showcase's own, the original has none.

### Documentation and tools

- Design system, component catalog (generated from the sources), interaction guide,
  integration guide, design decisions and provenance, licence audit, verification record.
- `samples/minimal`: a one-screen app in Java and XML that uses the released binary.
- `tools/catalog.py`, `tools/api_index.py`, `tools/pictures.py`, `tools/films.py`,
  `tools/device.py`, `tools/gallery.py`; `./gradlew dist` builds the AAR, a Maven
  repository zip, the demo APK, a source zip and checksums.

### Verified

- 1,045 library tests and 35 showcase tests on the JVM; Android Lint without issues in
  both modules; 38 device tests on an Android 16 emulator and 37 on Android 6.0. What was
  run, and what was not, is in [docs/VERIFICATION.md](docs/VERIFICATION.md).
