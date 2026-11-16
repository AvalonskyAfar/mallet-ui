# Package `transitions`

Window-level effects: moving between activities in space, swiping an activity away, and
changing the theme without a flash.

## Files you own (all new)

- `interaction/SancaiSpatialTransition.kt` (also holds the internal swipe host)
- `interaction/SancaiThemeTransition.kt`
- `res/anim/sancai_slide_in_start.xml`, `sancai_slide_in_end.xml`,
  `sancai_slide_out_start.xml`, `sancai_slide_out_end.xml`, and the four `_reduced`
  variants; `res/interpolator/sancai_glide.xml`
- tests: `interaction/SancaiTransitionsTest.kt`

(Paths are under `sancai/src/main/kotlin/io/github/avalonskyafar/sancai/`,
`sancai/src/main/` and `sancai/src/test/kotlin/io/github/avalonskyafar/sancai/`.)

## 1. `SancaiSpatialTransition` — extracted

Origin: `UiKit.startSpatial`, `finishSpatial`, `spatialRoot`, `captureSpatialSnapshot` and
`SpatialActivityHost`, `UiKit.java` lines 67-80, 186-194, 1090-1160 and 1198-1412; the XML
animations in `Gravekeeper/app/src/main/res/anim/` and `interpolator/gk_spatial.xml`; its use
in `DeveloperOptionsActivity.java` (lines 63-75, 1048-1060) and `ConfigEditorActivity.java`.

A second activity behaves like a neighbouring page: it slides in from one side while the
activity below slides out the other way, and the user can drag it back out by swiping
anywhere inside it.

How the original does it, to be kept:

- Before `startActivity`, the launching activity's content is drawn into a bitmap (skipped
  above 16 MB), stored in a process-wide map under an integer token, and the token and the
  entry side travel in the intent. The window animation is then disabled.
- The new activity wraps its content in a host that shows the bitmap behind the real page.
  First layout: the page starts one width away and glides in over `SancaiMotion.PAGE_MS`
  while the bitmap glides out the opposite way. Afterwards the bitmap waits just off screen.
- An interior horizontal swipe towards the entry side (not in the 24 dp edge zones, not on a
  `SancaiHorizontalDragOwner` or an `EditText`, and only if the activity allows it) drags
  page and bitmap together. Release past `COMMIT_FRACTION` or `COMMIT_VELOCITY_DP` finishes
  the activity when the settle ends (window animation disabled), otherwise it returns.
- Without a snapshot (direct launch, process death, bitmap too large) the host shows the
  page immediately and the XML slide animations are used for start and finish.
- The bitmap is recycled when the host is detached. Stale tokens are dropped.

API:

```kotlin
SancaiSpatialTransition.start(activity, intent, enterFrom = Side.END)
// in the new activity
setContentView(SancaiSpatialTransition.wrap(this, pageView))
SancaiSpatialTransition.finish(this)          // glide out; falls back to finish() + XML
```

- `Side`: START or END (reuse `SancaiSpatialGraph.Side` only if that class already exists in
  the tree; otherwise declare a small `enum class Edge { START, END }` here).
- `fun interface SwipeGate { fun canSwipeBack(): Boolean }`, implemented by the activity or
  set on the host, for pages that are in the middle of something.
- Settle duration: use `SancaiMotion.settleDuration`. (The original used
  `max(130, 90 + remaining * 330)` here and a different formula elsewhere; the toolkit has
  one settle curve. Record this as a deliberate unification in your result.)
- RTL: START and END resolve against the layout direction.
- The XML animations use `sancai_glide` and 420 ms (120 ms for `_reduced`).

## 2. `SancaiThemeTransition` — extracted

Origin: `UiKit.prepareThemeTransition` and `playPreparedThemeTransition`, `UiKit.java` lines
144-194, driven by `MainActivity.recreateAfterControlCommit` (lines 1278-1289).

Changing the palette recreates the activity. To hide the hard cut, the old appearance is
captured just before `recreate()`, laid over the new content, and faded out.

```kotlin
SancaiThemeTransition.recreate(activity)                 // capture, then activity.recreate()
SancaiThemeTransition.recreateAfter(view, delayMs)       // after a control finished settling
SancaiThemeTransition.play(activity)                     // in onCreate, after setContentView
```

- Capture: content view drawn into a bitmap (same 16 MB limit), kept under a token in the
  activity's intent extras, so it survives recreation but not process death.
- Play: an overlay `ImageView` with the bitmap, not important for accessibility, fading to
  zero over `SancaiMotion.THEME_FADE_MS` with `SancaiMotion.fade()`, then removed and the
  bitmap recycled. With reduced motion the overlay is skipped.
- `recreateAfter` defaults to `SancaiMotion.CONTROL_MS + 32` ms, so the thumb that triggered
  the change finishes its glide first; repeated calls coalesce.
- This is the one place where a fade is right: nothing moves, the light changes.

## Tests

Snapshot store: token round trip, bitmap over the limit is refused, stale tokens removed.
Host built directly with a test bitmap: enter animation ends with the page at 0; swipe past
the threshold calls the finish callback; swipe short of it returns; swipe starting on a
`SancaiSegmentedTrack` or in the edge zone does nothing; gate refusal; no-snapshot mode
shows the page at once. Theme transition: `play` adds an overlay that is removed after the
fade; nothing happens without a prepared token; reduced motion skips. The XML resources
inflate.
