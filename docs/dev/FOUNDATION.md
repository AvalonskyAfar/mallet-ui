# Foundation cheat sheet

Everything below already exists in `:sancai`. Use it; do not re-implement it. Package root:
`io.github.avalonskyafar.sancai` (R class: `io.github.avalonskyafar.sancai.R`).

## theme

```kotlin
val theme = SancaiTheme.of(context)          // resolve once per view
theme.colors      // SancaiColors: canvas surface raised ink muted hairline divider accent danger warning success (all @ColorInt), isDark
theme.glazes      // SancaiGlazes: terracotta ochre cobalt celadon mist; scale(count), uniform(count, glaze), fromAttr(int)
theme.textScale   theme.highContrast   theme.reduceMotion   theme.haptics   theme.isDark   theme.groove
theme.bedColor(glaze)          // fill of a glazed track in this theme  (never use glaze.bed directly)
theme.bedEdgeColor(glaze)      // rim of that track
theme.labelOnBed(active, own = active, optionCount = 2)   // text / icon colour resting on a glazed bed
SancaiColors.blend(first, second, secondShare)   SancaiColors.withAlpha(color, alpha0to255)
SancaiTheme.light() / dark() / wrap(context, theme)      // wrap(): explicit theme for a subtree (used by tests)
```

`SancaiGlaze(bed, edge, onBed)`.

`SancaiTextRole` (sp, weight): DISPLAY 20.5 M, TITLE 20 M, HEADLINE 18 M, HEADING 17 M, SUBHEAD 15.5 M,
BODY 15.5 R, BODY_COMPACT 14.5 R, LABEL 14.5 M, CAPTION 12.5 R, MICRO 11.5 R.
`role.applyTo(textView, theme)` sets size (with textScale), typeface, leading, no font padding.
`role.applyTo(paint, theme, scaledDensity)` for canvas text. `SancaiWeight.REGULAR/MEDIUM/BOLD.typeface()`.
`SancaiTextTone` INK MUTED ACCENT DANGER WARNING SUCCESS with `.resolve(colors)`.

`SancaiMotion`: `CONTROL_MS` 260, `REDUCED_MS` 120, `PAGE_MS` 420, `DIALOG_MS` 320, `FEEDBACK_MS` 220,
`FEEDBACK_REDUCED_MS` 90, `PRESS_MS` 55, `PRESS_SINK_DP` 1.5, `THEME_FADE_MS` 300, `COMMIT_FRACTION` 0.28,
`COMMIT_VELOCITY_DP` 320; `glide()` (the one easing), `fade()`, `controlDuration(reduce)`,
`feedbackDuration(reduce)`, `settleDuration(remainingFraction, reduce)`.

## material

```kotlin
val material = SancaiMaterial(context, theme)      // one per view; UI thread only
material.dp(v)
material.drawTrack(canvas, bounds, bed, edge, pressed = false, hero = false, radius = h/2, lifted = true)
material.drawThumb(canvas, bounds, pressed = false, radius = h/2, lifted = true)
material.drawSurface(canvas, bounds, radius, tint = null, shadow = true, pressed = false, corners = SancaiCorners.ALL)
material.drawWell(canvas, bounds, radius, rim = null)
material.drawRowFill(canvas, bounds)        material.drawRevealBed(canvas, bounds, tint = danger)
material.drawFocusRim(canvas, bounds, radius)      // keyboard focus, drawn inside bounds
SancaiMaterial.RADIUS_CARD_DP 8, RADIUS_DIALOG_DP 16, RADIUS_PANEL_DP 10, RADIUS_WELL_DP 9, RADIUS_MEDIA_DP 7
```

All `bounds` are `RectF` of the visible body. The outer shadow is drawn *outside* `bounds` but inside the
canvas clip, so leave a gutter between `bounds` and the view edge: track 1.5 dp sides and top, 6.5 dp bottom;
porcelain 1.4 dp sides, 1 dp top, 4.8 dp bottom (`SancaiGutters`).

Drawables: `SancaiSurfaceDrawable(material or context, radiusDp = 8f, pill = false, tint = null, shadow = true,
corners = ALL)` with `tint`, `pressed`, `sinkPx`, `bodyBounds(Rect, RectF)`;
`SancaiWellDrawable(material or context, radiusDp = 9f)` with `rim: Int?`, `focused`.

## interaction and internal

```kotlin
SancaiHaptics.tick(view) / detent(view) / confirm(view) / reject(view) / longPress(view)   // honour theme.haptics
SancaiPress.attach(host, content = host, onClick)    // content sinks 1.5 dp on touch-down, click ticks then calls back
SancaiPress.sink(content, px) / release(content)
interface SancaiHorizontalDragOwner                   // marker: implement on views that own horizontal drags

val gate = HorizontalDragGate(touchSlop)              // internal helper for horizontal controls
ACTION_DOWN  -> gate.down(event)
ACTION_MOVE  -> when (gate.move(this, event)) { DRAGGING -> follow finger; ABORTED -> give up; UNDECIDED -> wait }
ACTION_UP / ACTION_CANCEL -> gate.finish(this); if (gate.isAborted) restore else commit
```

## icon

`SancaiIcon` (enum, 50 icons: CHEVRON_LEFT/RIGHT/UP/DOWN, ARROW_*, CHECK, CLOSE, PLUS, MINUS, SEARCH, POWER,
INFO, WARNING, ERROR, SUCCESS, TRASH, EDIT, EYE, EYE_OFF, INBOX, CLOUD_OFF, IMAGE ...; `isDirectional`,
`SancaiIcon.fromAttr(int)`).
`SancaiIconPainter.draw(canvas, icon, left, top, sizePx, color, strokePx, mirrored = false)`.
`SancaiIconDrawable(context, icon, color = muted, sizeDp = 24f, strokeDp = 2f)`.
`SancaiIconView` (`icon`, `tone`, `color`, `strokeDp`).

## widget

`SancaiControl` — base class for custom-drawn controls. Gives: `theme`, `material`, `touchSlop`, `textPaint`,
`isRtl`, `showsFocus`, `dp(v)`, `sp(v)` (includes `theme.textScale`), `settle(from, to, durationMs) { value -> }`
(glide animation; jumps when motion is reduced or the view is not laid out), disabled fade
(`DISABLED_ALPHA` = 107), repaint on focus change. It is clickable and focusable by default.

`SancaiText(context)` with `role`, `tone`; `SancaiText.create(context, text, role, tone)`.
`SancaiDivider` (`fullBleed`, `orientation`). `SancaiSurface` (vertical LinearLayout card; `addDivider(marginDp)`,
`cornerRadiusDp`, `elevated`, `corners`; 9 dp content padding).
`SancaiButton` (`variant` NEUTRAL / PRIMARY / DANGER / TEXT, `tone`, `icon`, `isLoading`).
`SancaiSegmentedTrack` (`options`, `selectedIndex`, `select(index, animate)`, `setGlazes(list?)`,
`onSelectionChangedListener`; 60 dp high). **Read this file as the reference for a custom-drawn control**
(measure, draw, touch, keys, saved state, accessibility with `ExploreByTouchHelper`).

## resources that exist

Shared XML attributes (refer to them without `format`): `sancaiGlaze`, `sancaiTone`, `sancaiTextRole`,
`sancaiTitle`, `sancaiDetail`, `sancaiChecked`, `sancaiOptions`, `sancaiSelectedIndex`, `sancaiCornerRadius`,
`sancaiElevated`, `sancaiValueFrom`, `sancaiValueTo`, `sancaiStepSize`, `sancaiValue`, `sancaiIcon`.
Enum orders: glaze 0 terracotta, 1 ochre, 2 cobalt, 3 celadon, 4 mist; tone = `SancaiTextTone` ordinal;
icon = `SancaiIcon` ordinal (-1 none).
Strings: `sancai_action_ok`, `_cancel`, `_save`, `_add`, `_delete`, `_retry`, `_close`, `_back`,
`sancai_state_on`, `sancai_state_off`, `sancai_state_loading` (English and `values-zh`).

## tests

```kotlin
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiFooTest {
    val context = Snapshots.context(SancaiTheme.light())          // themed context
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
- Top-level helper functions and constants in main code must be `private` (file-private) to avoid clashes.
