# Foundation cheat sheet

Everything below already exists in `:mallet`. Use it; do not re-implement it. Package root:
`io.github.avalonskyafar.mallet` (R class: `io.github.avalonskyafar.mallet.R`).

## theme

```kotlin
val theme = MalletTheme.of(context)          // resolve once per view
theme.colors      // MalletColors: canvas surface raised ink muted hairline divider accent danger warning success shadow (all @ColorInt), isDark
theme.hues        // MalletHues: bronze ochre cobalt jade mist; scale(count), uniform(count, hue = cobalt), fromAttr(int)
theme.textScale   theme.highContrast   theme.reduceMotion   theme.haptics   theme.nightHue   theme.isDark   theme.groove
theme.neutralBed / theme.neutralBedEdge   // a plain recess (the empty part of a slider or progress bar)
theme.bedColor(hue)          // fill of a track coloured with hue, in this theme  (never use hue.bed directly)
theme.bedEdgeColor(hue)      // rim of that track
theme.labelOnBed(active, own = active, optionCount = 2)   // text / icon colour resting on a coloured bed
theme.labelOnTint(tint)      // text / icon colour on a coloured body (a primary or destructive capsule)
MalletColors.blend(first, second, secondShare)   MalletColors.withAlpha(color, alpha0to255)   MalletColors.contrast(first, second)
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

`MalletMotion`: `CONTROL_MS` 260, `REDUCED_MS` 120, `PAGE_MS` 420, `SETTLE_MIN_MS` 220, `SETTLE_MAX_MS` 420,
`DIALOG_MS` 320, `FEEDBACK_MS` 220, `FEEDBACK_REDUCED_MS` 90, `PRESS_MS` 55, `PRESS_SINK_DP` 1.5,
`THEME_FADE_MS` 300, `COMMIT_FRACTION` 0.28, `COMMIT_VELOCITY_DP` 320; `glide()` (the one easing), `fade()`
(the theme cross-fade), `controlDuration(reduce)`, `feedbackDuration(reduce)`,
`settleDuration(remainingFraction, reduce)`.

`MalletSpacing` (the page rhythm, in dp): `PAGE_SIDE` 12, `PAGE_TOP` 22, `PAGE_BOTTOM` 34, `TEXT_INSET` 10,
`TITLE_GAP` 16, `HEADING_GAP` 10, `SECTION_GAP` 22, `CARD_PADDING` 9, `DIVIDER_GAP` 5, `BUTTON_SIDE` 30.

## material

```kotlin
val material = MalletMaterial(context, theme)      // one per view; UI thread only
material.dp(v)
material.drawTrack(canvas, bounds, bed, edge, pressed = false, hero = false, radius = h/2, lifted = true)
material.drawThumb(canvas, bounds, pressed = false, radius = h/2, lifted = true)
material.drawSurface(canvas, bounds, radius, tint = null, shadow = true, pressed = false, corners = MalletCorners.ALL)
material.drawWell(canvas, bounds, radius, rim = null, pressed = false)
material.drawRowFill(canvas, bounds)               // flat fill of a row inside a card: no rim, no shadow
// Under a row that slides aside. bounds is the whole cell of the row; clip to the part that is uncovered.
material.drawRevealBed(canvas, bounds, tint = danger, topRadius = 0f, bottomRadius = 0f)
material.drawRevealShade(canvas, bounds, edgeX, bedOnLeft, topRadius = 0f, bottomRadius = 0f)
material.drawFocusRim(canvas, bounds, radius)      // keyboard focus, drawn inside bounds
material.drawFocusRim(canvas, bounds, topRadius, bottomRadius)   // upper and lower corners rounded differently
MalletMaterial.RADIUS_CARD_DP 8, RADIUS_DIALOG_DP 16, RADIUS_PANEL_DP 10, RADIUS_WELL_DP 9, RADIUS_MEDIA_DP 7
MalletMaterial.RIM_DP 0.9                          // how far the hairline rim of a surface reaches into its body
```

All `bounds` are `RectF` of the visible body. The outer shadow is drawn *outside* `bounds` but inside the
canvas clip, so leave a gutter between `bounds` and the view edge: a segmented track 1.5 dp sides and top,
6.5 dp bottom (`MalletSegmentedTrack.GUTTER_*_DP`; the other tracks keep numbers of their own); a surface
1.4 dp sides, 1 dp top, 4.8 dp bottom, and a flat body that carries no shadow 0.5 dp (`MalletGutters`).
The two primitives under every lift are public too: `drawOuterShadow(canvas, bounds, radius, blurDp, offsetDp,
opacity)` and `drawLift(canvas, bounds, radius, pressed, compact)`.

What is drawn inside a surface from rim to rim starts `RIM_DP` inside its body, and its corners take the
radius of the surface minus `RIM_DP` (7.1 dp in the default 8 dp card). That is the cell of a row that slides
aside: a vertical `MalletSurface` hands it to a `MalletRevealLayout` that is its direct child, which passes
it on to `drawRevealBed`, `drawRevealShade` and the two-radius `drawFocusRim`. `topRadius` and `bottomRadius`
are in pixels, and 0 wherever the cell shares no corner with the card.

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

val gate = HorizontalDragGate(touchSlop)              // internal helper for horizontal controls; follows one finger
when {                                                // action = event.actionMasked
    action == ACTION_DOWN   -> gate.down(event)
    action == ACTION_MOVE   -> when (gate.move(this, event)) {
        DRAGGING -> follow(gate.x(event))             // read the position with gate.x(event), never event.x
        ABORTED -> giveUp()                           // the drag turned vertical: the scrolling parent has it
        UNDECIDED -> Unit                             // still inside the touch slop
    }
    gate.isRelease(event)   -> { val x = gate.x(event); gate.finish(this); if (gate.isAborted) restore() else commit(x) }
    action == ACTION_CANCEL -> { gate.finish(this); restore() }
}
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
`MalletDivider` (`fullBleed`, `orientation`). `MalletSurface` (the card: a LinearLayout, vertical unless told
otherwise; `addDivider(verticalMarginDp = 0f)`, `cornerRadiusDp`, `elevated`, `corners`; 9 dp content padding,
`CONTENT_PADDING_DP`). A card is a group of at least two rows or blocks and never wraps a single control; a
vertical card stretches full-bleed dividers and `MalletRevealLayout` children from rim to rim (see principle 3
of [CONVENTIONS.md](CONVENTIONS.md)).
`MalletPage` (the scrolling column with the page rhythm: `content`, `addTitle(text)`,
`addSection(title, detail, card)` where `card` is a card or a single control that then stands on the canvas,
`addButton(button)`, `addSpace(dp)`).
`MalletButton` (`variant` NEUTRAL / PRIMARY / DANGER / TEXT, `tone`, `icon`, `isLoading`).
`MalletSegmentedTrack` (`options`, `selectedIndex`, `select(index, animate)`, `setHues(list?)`,
`setOnSelectionChangeListener { track, index -> }`; 60 dp high). **Read this file as the reference for a custom-drawn control**
(measure, draw, touch, keys, saved state, accessibility with `ExploreByTouchHelper`).

## resources that exist

Shared XML attributes (refer to them without `format`): `malletHue`, `malletTone`, `malletTextRole`,
`malletTitle`, `malletDetail`, `malletChecked`, `malletOptions`, `malletSelectedIndex`, `malletCornerRadius`,
`malletElevated`, `malletValueFrom`, `malletValueTo`, `malletStepSize`, `malletValue`, `malletIcon`.
Enum orders: hue 0 bronze, 1 ochre, 2 cobalt, 3 jade, 4 mist; tone = `MalletTextTone` ordinal;
text role = `MalletTextRole` ordinal; icon = `MalletIcon` ordinal (-1 none).
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
    Touch.multi(view, MotionEvent.ACTION_POINTER_DOWN, 1, Touch.Finger(0, x0, y0), Touch.Finger(1, x1, y1))   // several fingers
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
- Top-level helper functions and constants in main code must be `private` (file-private) to avoid clashes; the shared ones are `internal` in `internal/Units.kt` (`dp`, `dpInt`, `sp`, and `useOwnFocusRim()` for a view that draws its own focus rim and is not a `MalletControl`).
