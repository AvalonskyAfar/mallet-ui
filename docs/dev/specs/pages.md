# Showcase page briefs

One brief per page. Read `docs/dev/SHOWCASE.md` first: it explains `PageScope`, the rules
(interactive, states, code samples, provenance caption, strings in English and Chinese) and
how to build and look at a page.

A brief lists what the page must let a visitor **do**. You decide the exact layout. Keep each
page focused: four to six sections, nothing decorative.

Widgets named here exist in the library unless marked *(if present)*: check the directory
listing of your work copy and leave a section out, saying so in your report, when its widget
is not there.

---

## f_colour — Colour and glazes

1. **Neutral roles**: canvas, surface, raised, ink, muted, hairline, divider as swatches with
   name and hex, read from `page.theme.colors` (so the page is right in dark too).
2. **Semantic roles**: accent, danger, warning, success, each as a sentence set in that
   colour (that is how they are used) with its hex.
3. **Glazes**: the five glazes as short recessed beds painted with
   `SancaiMaterial.drawTrack(theme.bedColor(g), theme.bedEdgeColor(g))`, each with its name
   and a sample label in `theme.labelOnBed(g)`.
4. **The scale**: three `SancaiSegmentedTrack`s with two, three and four options. Caption:
   drag slowly and watch the glaze blend.
5. **A plain choice**: a track with `setGlazes(theme.glazes.uniform(3, theme.glazes.mist))`.
6. **Day and night**: the same switch and track twice, side by side, built with
   `SancaiTheme.wrap(context, SancaiTheme.light())` and `SancaiTheme.wrap(context, SancaiTheme.dark())`,
   each on a panel filled with that theme's canvas colour.

## f_type — Typography

1. **The scale**: every `SancaiTextRole` (iterate `values()`), its name set in its own style,
   with size and weight beside it in MICRO muted.
2. **Tones**: one line per `SancaiTextTone`.
3. **Reading**: a few sentences of body copy in BODY and in BODY_COMPACT, in both languages'
   string files, long enough to show the leading. Use `SancaiParagraph` *(if present)*.
4. **Larger text**: the same heading and row at text scale 1.0 and 1.13
   (`SancaiTheme.wrap(context, page.theme.copy(textScale = SancaiTheme.LARGE_TEXT_SCALE))`).

## f_material — Materials and depth

1. **Porcelain**: a card with a line of text, a flat card (`elevated = false`), a neutral
   capsule and a glazed capsule (`SancaiButton` neutral and primary).
2. **Glazed track and thumb**: a private custom view that paints a large track with
   `material.drawTrack` and a thumb with `material.drawThumb`; a two-option track above it
   switches the drawing between rest and pressed so the shadow can be seen tightening.
3. **Well**: three wells (`SancaiWellDrawable`): default, focus rim (`rim = colors.ink`),
   error rim (`rim = colors.danger`).
4. **The shadow lives inside the view**: a private custom view that draws a
   `SancaiSurfaceDrawable` and outlines its own bounds with a thin dashed line, to show the
   gutter (1.4 dp sides, 1 dp top, 4.8 dp bottom).
5. **Split card**: two `SancaiSurface` halves (`corners = TOP` and `BOTTOM`) around a divider.

## f_icons — Icons

1. **All fifty**: a grid of every `SancaiIcon` with its lower-case name in MICRO muted.
   Tapping one sinks it (`SancaiPress.attach`) and says `SancaiIcon.NAME` through `host.say`.
2. **Size and weight**: one icon whose size follows a `SancaiSlider` (16 to 64 dp) and whose
   stroke follows a three-option track (1.5, 2, 2.5 dp).
3. **Tones**: the same icon in every `SancaiTextTone`.
4. **Direction**: the two chevrons and two arrows in a left-to-right row and in a
   right-to-left row (`layoutDirection`), to show the mirroring.

## c_tracks — Tracks and switches

1. **Segmented track**: two, three and four options, each with a status line under the card
   that names the current selection (set from the listener).
2. **Plain choice**: uniform mist glaze, and a uniform celadon one.
3. **A change can be refused**: a four-option track whose listener rolls the selection back
   with `select(previous)` when the last option is chosen, and says why through `host.say`.
4. **Switch**: a few rows of label plus `SancaiSwitch` (row at least 56 dp high, label start
   aligned, switch at the end); one with other glazes; one disabled.
5. **Power switch**: one with custom labels and a status line; one disabled.

## c_choice — Checkboxes and radios

1. **Checkboxes**: three options and a "select all" box above them that follows and drives
   them (shows that `setChecked` from code does not call listeners); one with another glaze;
   one disabled and checked.
2. **Radios**: a `SancaiRadioGroup` of three with a status line; a row of radios in the five
   glazes.

## c_numbers — Numbers and progress

1. **Stepper**: an integer with a unit, a decimal with step 0.01, and one at its upper bound
   (the plus mark pales). Tapping the value opens an input through `SancaiDialog`
   *(if present)*, otherwise says the value through `host.say`. Caption: hold a mark to repeat.
2. **Slider**: continuous with a live value; stepped; one that runs through the glaze scale.
3. **Progress**: a determinate bar driven by the slider above; a "Run" button that fills a
   bar from empty to full; an indeterminate bar; other glazes.
4. **Spinner**: sizes and tones; a `SancaiButton` in its loading state, toggled by a switch.

## i_press — Press and haptics

1. **Pressing sinks**: a card with two `SancaiEntryRow`s and one hand-made row using
   `SancaiPress.attach(row, content)`; a neutral button below. Caption: the card stays, only
   what you touch moves, and nothing changes colour.
2. **Haptics**: five buttons, one per `SancaiHaptics` effect, each with a caption that says
   what it means. Note that they follow the Haptics setting on the Appearance page.
3. **Order of events**: a switch and a button that each append a line to a small log
   ("listener, then tick" for the switch; "tick, then listener" for the button).

## i_follow — Follow the finger

1. **Drag, do not tap**: a large four-option track and a power switch. Caption: put a finger
   on the thumb and move slowly; let go half way and it settles on the nearer side.
2. **Colour moves with position**: a `SancaiSlider` running through the four-glaze scale with
   a live value.
3. **Vertical movement gives the gesture back**: a track far enough down the page that the
   page scrolls; caption: start on the track and move up or down, the page scrolls and the
   value stays.
4. **From code**: four buttons that call `select(index)` on a track (it glides), and one that
   uses `animate = false` (it jumps).

## i_spatial — Pages as places

1. **The map**: a private custom view that draws the app's map (Appearance above Home,
   Foundations at the start side, Interactions at the end side, Components below; this page
   beyond Interactions) with this page marked. Names come from the page titles.
2. **Try it**: short instructions. On Home, drag in four directions; drag slowly and
   reverse; let go before 28 % and after; flick.
3. **Another activity as a neighbour**: a primary button that calls
   `SancaiSpatialTransition.start(page.host.activity, Intent(page.context, NeighbourActivity::class.java))`.
4. **System back**: two sentences on the back gesture previewing the move on Android 14+.

## i_reveal — Reveal and expand

1. **Swipe a row aside**: a card of four `SancaiRevealLayout` rows (a name and a
   `SancaiSwitch` each) with a destructive action that removes the row and says so; a text
   button that restores the rows. A second, short card with `revealEdge = END`.
2. **Expand**: three `SancaiExpandableLayout` sections in one card. Each header is a row
   with a chevron set as the indicator; each body is a short paragraph.

## i_pager — Pager

1. A `SancaiPager` about 220 dp high with four pages and a `SancaiPageIndicator` attached.
   Above it a second, short pager used as a heading and linked with `linkWith`.
2. Previous and Next buttons that move it from code.

## i_arbitrate — Who owns the gesture

1. One card that stacks a `SancaiSegmentedTrack`, a `SancaiRevealLayout` row and a small
   horizontal `SancaiPager`, each with a one-line caption that says what happens when a drag
   starts there sideways and what happens when it goes up or down.
2. Enough text below that the page scrolls, ending with a short table (two columns of text)
   of "the finger lands on" and "owner", and a note on the 24 dp edge zones left to the
   system.

## i_motion — Motion tokens

1. **One curve**: a private custom view that plots `SancaiMotion.glide()` on a small grid;
   below it two grooves with a bead each. "Play" sends both beads across, one with the glide,
   one linear, for comparison.
2. **Durations**: a card of rows, one per token (`CONTROL_MS`, `PAGE_MS`, `DIALOG_MS`,
   `FEEDBACK_MS`, `PRESS_MS`, `REDUCED_MS`) with its value; tapping a row plays a bead for
   that long.
3. **Settle**: a slider for "distance left" (0 to 1) with the resulting
   `SancaiMotion.settleDuration(...)` in milliseconds, and a Play button.

---

## Second batch

## c_buttons — Buttons and chips

1. **Capsule buttons**: neutral, primary, destructive, text; with an icon; disabled; a switch
   that puts them into the loading state.
2. **Chips**: a `SancaiChipGroup` of filter chips, a single-selection group, action chips.
3. **Tags and badges**: `SancaiTag` in its five tones; `SancaiBadge` with 0, 5 and 120.

## c_fields — Text fields

1. A labelled field with helper text; a field with a counter; a password field with the
   toggle; a field with a leading icon and the clear action.
2. **Validation**: a field and a button that calls `validate { ... }`, showing the error
   line and clearing it once the text is acceptable.
3. Multi-line, monospace and disabled fields.

## c_rows — Rows and lists

1. **Headings**: both levels.
2. **Entry rows**: centred with an end chevron, with a start chevron, with an icon, with a
   value; start-aligned.
3. **Control rows**: with a two-, three- and four-option track, and with a switch, a stepper
   and a slider as the control.
4. **Link rows**: both placements. **Facts**: a `SancaiKeyValueList` with tones.

## c_nav — Navigation

1. **Navigation row**: launcher mode and tabs mode, with a badge on one item.
2. **Top bar**: with navigation and two actions, above a short scrolling area so the
   hairline can be seen to appear.
3. **Tabs**: fill and scroll layouts; one set attached to a `SancaiPager`.

## c_overlays — Dialogs and sheets

One button per overlay, each showing the result through a status line or a toast:
confirm, destructive confirm, input with a validation error, three actions; an action sheet
and a sheet with custom content; a menu anchored to its button; neutral, destructive and
action toasts; a floating panel that can be attached and removed.

## c_states — Empty, loading, error

1. **State views**: the presets, and the compact variant inside a card.
2. **State layout**: one `SancaiStateLayout` with a four-option track above it that switches
   between content, loading, empty and error.
3. **Skeletons**: a card of skeleton rows. **Banners**: the four tones, each dismissible, and
   a button that shows them again.

## c_reading — Reading

Paragraphs (with a Chinese sentence that shows closing punctuation staying on its line),
callouts in four tones, a flow row of three steps, a media frame holding a picture drawn in
code (a private `Drawable`) and an empty media frame.
