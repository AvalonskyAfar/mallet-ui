# Package `numeric`

Controls and indicators for numbers and progress.

## Files you own (all new)

- `widget/SancaiStepper.kt`, `widget/SancaiSlider.kt`, `widget/SancaiProgressBar.kt`,
  `widget/SancaiSpinner.kt`
- `res/values/sancai_attrs_numeric.xml`
- `res/values/sancai_strings_numeric.xml`, `res/values-zh/sancai_strings_numeric.xml`
- tests: `widget/SancaiNumericTest.kt`

(Paths are under `sancai/src/main/kotlin/io/github/avalonskyafar/sancai/`,
`sancai/src/main/` and `sancai/src/test/kotlin/io/github/avalonskyafar/sancai/`.)

## 1. `SancaiStepper` — extracted and completed

Origin: `UiKit.PreciseValueControl`, `UiKit.java` lines 686-793; used 61 dp high
(`DeveloperOptionsActivity.java` lines 548-592, which also shows how the app drove it).

Visual, ported exactly:

- Default height 61 dp. Track gutters 1.5 dp left / top / right, 6.5 dp bottom. Bed cobalt
  (`glaze` property, painted through `theme.bedColor`).
- Three touch zones with weights 1 : 1.35 : 1 (minus, value, plus).
- A neutral thumb sits in the centre and never moves: width 42 % of the track, 4 dp inset
  top and bottom. The value is written on it, 12.5 sp medium, ink.
- Minus and plus marks rest on the bed at the centre of their zones, colour
  `theme.labelOnBed(glaze)`. Draw them with the MINUS and PLUS icons (about 16 dp).
- While a zone is pressed all content sinks 1 dp; the thumb draws pressed only for the
  centre zone. Sliding to another zone before release moves the press with the finger.

The original only forwarded three callbacks. Here the control owns its value (addition):

- `value`, `valueFrom`, `valueTo`, `stepSize`, `decimals` and an optional `unit` suffix or a
  custom `formatter`. Values snap to the step grid and clamp to the range.
- Tap minus / plus: one step. `OnValueChangeListener(stepper, value)` for user changes only.
- Long press on minus / plus repeats: first repeat after 400 ms, then every 80 ms, every
  40 ms after ten repeats; a detent haptic per step; stops at the bound or on release.
- At a bound the corresponding mark pales to 40 % alpha and its zone does nothing.
- Tap on the value: `OnEditRequestListener(stepper)`, so the app can open an input dialog.
- Keys: left / minus and right / plus step; Enter requests editing. Not mirrored in RTL
  (minus stays on the left).
- Accessibility: class `android.widget.SeekBar`, `RangeInfo`, scroll forward / backward
  actions, state description = the formatted value, and a custom "Edit" action.

## 2. `SancaiSlider` — new, in-language

A slim recessed track with a glazed stretch from the start to a neutral bead.

- Default height 44 dp. Track 14 dp high, vertically centred, inset horizontally by half the
  bead so the bead never leaves the view. The whole track is first drawn as a neutral groove
  (`material.drawTrack` with the theme's unglazed colours: in light use the mist glaze, in
  dark `theme.groove`); the stretch from the start to the bead centre is then repainted with
  the active glaze using `lifted = false` and a clip.
- Bead: 28 dp circle, `material.drawThumb`, pressed look while dragging.
- `value`, `valueFrom`, `valueTo`, `stepSize` (0 = continuous). With steps the bead snaps and
  each crossed step gives a detent haptic.
- Glaze: one glaze (`glaze`, default cobalt) or `setGlazeScale(list)`: the fill then blends
  through the list as the value rises, e.g. terracotta, ochre, cobalt, celadon.
- Drag anywhere on the track follows the finger (`HorizontalDragGate`, vertical abort,
  `SancaiHorizontalDragOwner`). A tap moves the bead there with a glide.
- `OnValueChangeListener(slider, value, fromUser)`; plus `onStopTrackingTouch`-style
  callback when the finger lifts. Keys: arrows step (one step, or 5 % when continuous).
  Mirrored in RTL. Saved state. Accessibility as `android.widget.SeekBar` with `RangeInfo`.

## 3. `SancaiProgressBar` — new, in-language

- Default height 22 dp: a 10 dp recessed groove (same neutral colours as the slider) with a
  glazed fill growing from the start. `progress` in 0..1, `setProgress(value, animate)`
  glides. `glaze` property.
- `isIndeterminate`: a glazed segment 30 % of the width travels from start to end and
  re-enters, 1400 ms per pass, eased with `SancaiMotion.glide()`. With reduced motion it
  shows a still, full-width fill at 40 % alpha instead.
- Not focusable. Accessibility class `android.widget.ProgressBar` with `RangeInfo`.
  Animators stop when detached or not visible.

## 4. `SancaiSpinner` — new, in-language

- An open ring that turns: a 276 degree arc (the same open ring as the power icon and the
  button's loading state), stroke 2 dp with round caps, one turn per 900 ms, linear.
  Reduced motion halves the speed rather than stopping, because it signals activity.
- Default 28 dp; `tone` (default MUTED) or explicit `color`. Runs only while attached and
  visible. Accessibility: class `android.widget.ProgressBar`, content description from a
  "Loading" string resource unless one is set.

## Tests

Stepper: step, clamp, snap, formatting, bounds disable a zone, edit request, auto-repeat
(advance the Robolectric looper), keys, save / restore, accessibility actions. Slider: tap,
drag, snap, vertical abort, RTL, keys, save / restore, fill pixel colour left and right of
the bead. Progress: fill width follows progress (pixel probes). Spinner: draws an arc.
Snapshots `numeric_light`, `numeric_dark` showing every control in several states.
