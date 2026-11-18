# Foundation cheat sheet

Everything below already exists in `:mallet`. Use it; do not re-implement it. Package root:
`io.github.avalonskyafar.mallet` (R class: `io.github.avalonskyafar.mallet.R`).

## theme

```kotlin
val theme = MalletTheme.of(context)          // resolve once per view
theme.colors      // MalletColors: canvas surface raised ink muted hairline divider accent danger warning success (all @ColorInt), isDark
theme.hues      // MalletHues: bronze ochre cobalt jade mist; scale(count), uniform(count, hue), fromAttr(int)
theme.textScale   theme.highContrast   theme.reduceMotion   theme.haptics   theme.isDark   theme.groove
theme.neutralBed / theme.neutralBedEdge   // an unglazed groove (empty part of a slider or progress bar)
theme.bedColor(hue)          // fill of a glazed track in this theme  (never use hue.bed directly)
theme.bedEdgeColor(hue)      // rim of that track
theme.labelOnBed(active, own = active, optionCount = 2)   // text / icon colour resting on a glazed bed
MalletColors.blend(first, second, secondShare)   MalletColors.withAlpha(color, alpha0to255)
MalletTheme.light() / dark() / wrap(context, theme)      // wrap(): explicit theme for a subtree (used by tests)
```

`MalletHue(bed, edge, onBed)`.

`MalletTextRole` (sp, weight): DISPLAY 20.5 M, TITLE 20 M, HEADLINE 18 M, HEADING 17 M, SUBHEAD 15.5 M,
BODY 15.5 R, BODY_COMPACT 14.5 R, LABEL 14.5 M, FOOTNOTE 13.5 R, CAPTION 12.5 R, MICRO 11.5 R.
`role.applyTo(textView, theme)` sets size (with textScale), typeface, leading, no font padding.
Never write a text size as a literal: take `MalletTextRole.X.sizeSp` (or apply the role) and
change only the weight, with `MalletWeight.X.typeface()`.
`role.applyTo(paint, theme, scaledDensity)` for canvas text. `MalletWeight.REGULAR/MEDIUM/BOLD.typeface()`.
`MalletTextTone` INK MUTED ACCENT DANGER WARNING SUCCESS with `.resolve(colors)`.

`MalletMotion`: `CONTROL_MS` 260, `REDUCED_MS` 120, `PAGE_MS` 420, `DIALOG_MS` 320, `FEEDBACK_MS` 220,
`FEEDBACK_REDUCED_MS` 90, `PRESS_MS` 55, `PRESS_SINK_DP` 1.5, `THEME_FADE_MS` 300, `COMMIT_FRACTION` 0.28,
`COMMIT_VELOCITY_DP` 320; `glide()` (the one easing), `fade()`, `controlDuration(reduce)`,
`feedbackDuration(reduce)`, `settleDuration(remainingFraction, reduce)`.

## material

```kotlin
val material = MalletMaterial(context, theme)      // one per view; UI thread only
material.dp(v)
material.drawTrack(canvas, bounds, bed, edge, pressed = false, hero = false, radius = h/2, lifted = true)
material.drawThumb(canvas, bounds, pressed = false, radius = h/2, lifted = true)
material.drawSurface(canvas, bounds, radius, tint = null, shadow = true, pressed = false, corners = MalletCorners.ALL)
material.drawWell(canvas, bounds, radius, rim = null, pressed = false)
material.drawRowFill(canvas, bounds)        material.drawRevealBed(canvas, bounds, tint = danger)
material.drawFocusRim(canvas, bounds, radius)      // keyboard focus, drawn inside bounds
MalletMaterial.RADIUS_CARD_DP 8, RADIUS_DIALOG_DP 16, RADIUS_PANEL_DP 10, RADIUS_WELL_DP 9, RADIUS_MEDIA_DP 7
```

All `bounds` are `RectF` of the visible body. The outer shadow is drawn *outside* `bounds` but inside the
canvas clip, so leave a gutter between `bounds` and the view edge: track 1.5 dp sides and top, 6.5 dp bottom;
porcelain 1.4 dp sides, 1 dp top, 4.8 dp bottom (`MalletGutters`).

Drawables: `MalletSurfaceDrawable(material or context, radiusDp = 8f, pill = false, tint = null, shadow = true,
corners = ALL)` with `tint`, `pressed`, `sinkPx`, `bodyBounds(Rect, RectF)`;
`MalletWellDrawable(material or context, radiusDp = 9f)` with `rim: Int?`, `focused`.

## interaction and internal

```kotlin
MalletHaptics.tick(view) / detent(view) / confirm(view) / reject(view) / longPress(view)   // honour theme.haptics
MalletPress.attach(host, content = host, onClick)    // content sinks 1.5 dp on touch-down; a click ticks, then calls back
MalletPress.attachSink(host, content = host)         // the sink only; you keep the click listener
MalletPress.sink(content, px) / release(content)
interface MalletHorizontalDragOwner                   // marker: implement on views that own horizontal drags

val gate = HorizontalDragGate(touchSlop)              // internal helper for horizontal controls
ACTION_DOWN  -> gate.down(event)
ACTION_MOVE  -> when (gate.move(this, event)) { DRAGGING -> follow finger; ABORTED -> give up; UNDECIDED -> wait }
ACTION_UP / ACTION_CANCEL -> gate.finish(this); if (gate.isAborted) restore else commit
```

## icon

`MalletIcon` (enum, 50 icons: CHEVRON_LEFT/RIGHT/UP/DOWN, ARROW_*, CHECK, CLOSE, PLUS, MINUS, SEARCH, POWER,
INFO, WARNING, ERROR, SUCCESS, TRASH, EDIT, EYE, EYE_OFF, INBOX, CLOUD_OFF, IMAGE ...; `isDirectional`,
`MalletIcon.fromAttr(int)`).
`MalletIconPainter.draw(canvas, icon, left, top, sizePx, color, strokePx, mirrored = false)`.
`MalletIconDrawable(context, icon, color = muted, sizeDp = 24f, strokeDp = 2f)`.
`MalletIconView` (`icon`, `tone`, `color`, `strokeDp`).

## widget

`MalletControl` — base class for custom-drawn controls. Gives: `theme`, `material`, `touchSlop`, `textPaint`,
`isRtl`, `showsFocus`, `dp(v)`, `sp(v)` (includes `theme.textScale`), `settle(from, to, durationMs) { value -> }`
(glide animation; jumps when motion is reduced or the view is not laid out), disabled fade
(`DISABLED_ALPHA` = 107), repaint on focus change. It is clickable and focusable by default.

`MalletText(context)` with `role`, `tone`; `MalletText.create(context, text, role, tone)`.
`MalletDivider` (`fullBleed`, `orientation`). `MalletSurface` (vertical LinearLayout card; `addDivider(verticalMarginDp)`,
`cornerRadiusDp`, `elevated`, `corners`; 9 dp content padding).
`MalletButton` (`variant` NEUTRAL / PRIMARY / DANGER / TEXT, `tone`, `icon`, `isLoading`).
`MalletSegmentedTrack` (`options`, `selectedIndex`, `select(index, animate)`, `setHues(list?)`,
`setOnSelectionChangeListener { track, index -> }`; 60 dp high). **Read this file as the reference for a custom-drawn control**
(measure, draw, touch, keys, saved state, accessibility with `ExploreByTouchHelper`).

## resources that exist

Shared XML attributes (refer to them without `format`): `malletHue`, `malletTone`, `malletTextRole`,
`malletTitle`, `malletDetail`, `malletChecked`, `malletOptions`, `malletSelectedIndex`, `malletCornerRadius`,
`malletElevated`, `malletValueFrom`, `malletValueTo`, `malletStepSize`, `malletValue`, `malletIcon`.
Enum orders: hue 0 bronze, 1 ochre, 2 cobalt, 3 jade, 4 mist; tone = `MalletTextTone` ordinal;
icon = `MalletIcon` ordinal (-1 none).
Strings: `mallet_action_ok`, `_cancel`, `_save`, `_add`, `_delete`, `_retry`, `_close`, `_back`,
`mallet_state_on`, `mallet_state_off`, `mallet_state_loading` (English and `values-zh`).

## tests

```kotlin
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MalletFooTest {
    val context = Snapshots.context(MalletTheme.light())          // themed context
    Snapshots.layout(view, widthDp = 300)                         // measure + layout, no window
    Snapshots.attach(view, widthDp = 360)                         // real window: clicks, focus, animators
    Snapshots.idle()                                              // run posted work (performClick is posted!)
    val bitmap = Snapshots.render(view, 300)                      // pixels: bitmap.getPixel(x, y)
    Snapshots.capture(view, "foo_light", 360)                     // also writes build/snapshots/foo_light.png
    Touch.tap(view, x, y)   Touch.drag(view, x0, y0, x1, y1, steps = 8, release = true)
    Touch.dispatch(view, MotionEvent.ACTION_UP, x, y)
}
```

Facts that cost time if unknown:

- A touch-up posts the click. After `Touch.tap` on a clickable view call `Snapshots.idle()`, and attach the
  view first. Custom controls that handle `ACTION_UP` themselves need neither.
- Animators only run for attached, laid-out views. Advance time with
  `ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS)`.
- `view.accessibilityNodeProvider` and the node getters are nullable in tests; assert not null first.
- Sample pixels where there is no text and no thumb. The test device is 411 dp wide at density 2.625.
- Assertions: Google Truth (`assertThat(x).isEqualTo(y)`). Test names may use backticks.
- Kotlin language level is 2.0. `explicitApi()` applies to main code only, not to tests.
- Top-level helper functions and constants in main code must be `private` (file-private) to avoid clashes; the shared ones are `internal` in `internal/Units.kt` (`dp`, `dpInt`, `sp`).
