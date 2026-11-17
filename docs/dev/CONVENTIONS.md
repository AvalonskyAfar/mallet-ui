# Sancai UI — engineering conventions

Rules every source file in `:sancai` follows. Read this before adding a widget.

## 1. The design language in one page

Sancai (三彩, "three-colour glaze") has three materials, all painted by
`material/SancaiMaterial.kt`. Never hand-roll a shadow, rim or recess: call the painter.

| Material | Painter | Looks like | Used for |
|---|---|---|---|
| Porcelain | `drawSurface` / `SancaiSurfaceDrawable` | raised body, one hairline rim, one short soft shadow below, faint inner shading | cards, capsule buttons, dialogs, toasts, menus |
| Glazed track | `drawTrack` | recessed capsule; the **bed colour is the state** | segmented track, switches, stepper, slider, progress |
| Thumb | `drawThumb` | neutral raised bead that rides inside a track | everything that moves inside a track |
| Well | `drawWell` / `SancaiWellDrawable` | recessed neutral field | text fields, editors, media frames |

Principles that make a screen recognisably Sancai:

1. **Colour is state, and it lives in the bed.** Thumbs, cards and text stay neutral.
   Glazes: terracotta (off), ochre (gentle), cobalt (standard / on), celadon (strict),
   mist (plain neutral choice). Use `theme.bedColor(glaze)` / `theme.bedEdgeColor(glaze)`
   and `theme.labelOnBed(...)`; never read `glaze.bed` directly when painting, or dark
   themes break.
2. **Text is centred; arrows are not part of the text.** A chevron is positioned on its own
   at the edge of a row and never shifts the centred title.
3. **Dividers run from rim to rim.** Inside a `SancaiSurface` use `SancaiDivider`
   (full-bleed by default). No inset dividers.
4. **Pressing sinks, it does not tint.** Operated content moves down 1.5 dp
   (`SancaiMotion.PRESS_SINK_DP`) in 55 ms and returns at once on release. Tracks and
   thumbs tighten their shadow (`pressed = true`). No ripples, no colour change.
5. **Everything follows the finger or glides.** One easing only: `SancaiMotion.glide()`.
   No bounce, no overshoot, no fade-in of content. (Scrims may fade.)
6. **Focus thickens the rim.** Keyboard focus is `material.drawFocusRim(...)`: a 2 dp ink
   rim inside the body. No glow, no outline outside the bounds.
7. **Disabled pales.** The whole control is drawn at alpha 107/255
   (`SancaiControl.DISABLED_ALPHA`); shape and layout do not change.
8. **Shadows live inside the view.** A view that draws a raised or recessed body reserves a
   gutter for the shadow inside its own bounds (see `SancaiGutters` and the constants at the
   bottom of `SancaiSegmentedTrack`). Do not rely on `clipChildren=false` of parents.

Numbers come from the theme and motion objects, never from literals scattered in widgets:
`SancaiTheme`, `SancaiColors`, `SancaiGlazes`, `SancaiTextRole`, `SancaiMotion`,
`SancaiMaterial.RADIUS_*_DP`.

## 2. Anatomy of a widget

Take `widget/SancaiSegmentedTrack.kt` (custom-drawn control) and `widget/SancaiButton.kt`
(TextView subclass) as the reference implementations.

- Package: `io.github.avalonskyafar.sancai.<area>`; class name `Sancai<Name>`.
- Kotlin, `explicitApi()` is on: every declaration needs a visibility modifier and public
  functions need explicit return types. Language/API level is Kotlin 2.0. `minSdk` is 23:
  guard newer platform APIs with `Build.VERSION.SDK_INT` or use `androidx.core` compat.
- Dependencies allowed: `androidx.annotation`, `androidx.core`, `androidx.customview`.
  Nothing else. No AppCompat, no Material Components, no Compose.
- Constructors: `@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
  defStyleAttr: Int = 0)` so the view works from XML and from code.
- Resolve the theme once: `SancaiTheme.of(context)`. Custom-drawn controls extend
  `SancaiControl`, which already gives `theme`, `material`, `touchSlop`, `textPaint`,
  `dp()`, `sp()`, `settle()`, `isRtl`, `showsFocus` and the disabled fade.
- XML attributes: one file per package of work, `res/values/sancai_attrs_<name>.xml`.
  Attribute names start with `sancai`. Shared attributes are declared once in
  `sancai_attrs_common.xml` (`sancaiGlaze`, `sancaiTone`, `sancaiTextRole`, `sancaiTitle`,
  `sancaiDetail`, `sancaiChecked`, `sancaiOptions`, `sancaiSelectedIndex`,
  `sancaiCornerRadius`, `sancaiElevated`, `sancaiValueFrom/To/StepSize/Value`,
  `sancaiIcon`); refer to them without a `format`. **Never edit the common file or another
  widget's file**; declare new attributes in your own file with unique names.
- Strings: never hard-code user-visible text. Add `res/values/sancai_strings_<name>.xml`
  (English) **and** `res/values-zh/sancai_strings_<name>.xml` (Simplified Chinese). Names
  start with `sancai_`. Anything a developer would reasonably want to change is also a
  property on the view.
- Every resource name starts with `sancai` (`resourcePrefix` is enforced).

## 3. Behaviour contract

- **Listeners fire for user input only** (touch, keyboard, accessibility action). Setters
  called from code never call back. This is what makes "roll back inside the listener" safe.
- **Haptic order.** Controls that hold a *value* (tracks, switches, checkboxes, radios,
  steppers, sliders, chips that check) call the listener first and tick second, so a control
  that switches haptics off stays silent. *Actions* (buttons, rows, links, menu items, dialog
  actions: everything pressed rather than set) tick first and then call the listener, so the
  confirmation never waits for what the action starts. `SancaiPress.attach` does the latter;
  `SancaiPress.attachSink` gives the sink alone when a view manages its own click listener.
- **Horizontal drags** use `internal/HorizontalDragGate`: undecided inside the touch slop;
  mostly-vertical movement aborts and hands the gesture to the scrolling parent; otherwise
  the control calls `requestDisallowInterceptTouchEvent(true)` and follows the finger.
- **Reduced motion** (`theme.reduceMotion`): use `settle()` (jumps) or
  `SancaiMotion.*duration(reduce)`; never hard-code a duration.
- **Animators** are cancelled in `onDetachedFromWindow` and never leak a view.
- **RTL**: anything with a start and an end mirrors when `isRtl`. Use start/end, not
  left/right, in layouts.
- **State**: a control with user-changeable state saves it in `onSaveInstanceState` (Bundle
  with the super state, see the track). Restoring does not call listeners.
- **Accessibility**: set a meaningful class name (`getAccessibilityClassName`), state
  (`ViewCompat.setStateDescription`, checked / range info), and actions. Multi-zone controls
  expose virtual children with `ExploreByTouchHelper` (see the track). Decorative views are
  `IMPORTANT_FOR_ACCESSIBILITY_NO`. Touch targets are at least 44 dp in their smaller side.
- **Keyboard**: focusable controls react to D-pad / arrows where there is a direction and to
  Space / Enter where there is an action; a key that cannot do anything is not consumed.
- **Large text**: sizes go through `sp()` / `SancaiTextRole.applyTo`, which include
  `theme.textScale`. Layouts must survive `textScale = 1.13` and system font scale 1.3.

## 4. Documentation and tests

- KDoc on every public class and member: what it is for, then the non-obvious behaviour.
  Class KDoc of a view includes a short XML usage example.
- Do not mention the original app's internal code names (L1, S1, K1 ...) in public KDoc.
  Provenance is recorded in `docs/` instead.
- Tests live in `sancai/src/test/kotlin/...`, run on the JVM with Robolectric native
  graphics, and use `testing/Snapshots.kt` and `testing/Touch.kt`.
  - Assert behaviour (state after taps, drags, keys; listener rules; save/restore; RTL).
  - Assert a few exact pixels where colour is the contract.
  - End with a `snapshot of ...` test that calls `Snapshots.capture(...)` for light and dark;
    then **open the PNG and look at it** (`sancai/build/snapshots/<name>.png`).
  - Use `Snapshots.attach(view)` when a test needs a window (clicks through
    `performClick`, focus, animations, dialogs) and `Snapshots.idle()` to run posted work.
- A change is done when `./gradlew :sancai:testDebugUnitTest` is green.
