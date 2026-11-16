# Package `rows`

The rows that make up a settings card, and the headings above the cards.

## Files you own (all new)

- `widget/SancaiHeading.kt`, `widget/SancaiEntryRow.kt`, `widget/SancaiControlRow.kt`,
  `widget/SancaiLinkRow.kt`, `widget/SancaiKeyValueList.kt`
- `res/values/sancai_attrs_rows.xml`
- tests: `widget/SancaiRowsTest.kt`

(Paths are under `sancai/src/main/kotlin/io/github/avalonskyafar/sancai/`,
`sancai/src/main/` and `sancai/src/test/kotlin/io/github/avalonskyafar/sancai/`.)

The signature of these rows: **text is centred, and the arrow is not part of the text.** A
chevron sits at the edge of the row on its own; it never takes width from, or shifts, the
centred title. Compare `Gravekeeper-master/screenshots/screenshot_settings.png` and
`screenshot_more.png`.

## 1. `SancaiHeading` — extracted

Origin: `UiKit.heading`, `UiKit.java` lines 303-315 (and `pageTitle`, 296-301).
Title in HEADING (17 sp medium, ink); optional detail in CAPTION (12.5 sp, muted) 4 dp
below; 10 dp horizontal padding; start aligned. A `level` property switches the title to
TITLE (20 sp) for page titles. XML: `sancaiTitle`, `sancaiDetail`, `sancaiHeadingLevel`.

## 2. `SancaiEntryRow` — extracted and extended

Origin: `UiKit.entry`, `UiKit.java` lines 800-845.

- Min height 62 dp, or 76 dp when there is a detail line.
- Title SUBHEAD (15.5 sp medium, ink), centred. Detail 11.8 sp regular, muted, centred, 4 dp
  below the title.
- `arrow`: NONE, END (chevron pointing to the end, muted) or START (chevron pointing to the
  start, ink; the destination lies on that side). The chevron is 24 dp, vertically centred,
  12 dp from the edge, positioned independently of the text. Directional in RTL.
- Press: only the content (text and chevron) sinks, never the row background. Use
  `SancaiPress.attach(row, content, listener)`; set the click listener through
  `setOnClickListener` and route it to that.
- Content description: "title, detail". Focusable, focus shown as a rim on the row.

Added to the original:

- `alignment`: CENTER (default) or START. With START the text is start aligned with 14 dp
  padding, for apps with long, list-like content.
- `leadingIcon: SancaiIcon?` at the start (24 dp, muted), independent like the chevron.
- `value: CharSequence?`, a short muted value shown before the end chevron (for example the
  current choice of a sub-page). In CENTER alignment it sits at the end edge, independent.
- XML: `sancaiTitle`, `sancaiDetail`, `sancaiIcon`, `sancaiEntryArrow`,
  `sancaiEntryAlignment`, `sancaiEntryValue`.

## 3. `SancaiControlRow` — extracted

Origin: `UiKit.controlRow` and `utilityControlRow`, `UiKit.java` lines 467-508.

- Horizontal: a text column and a control.
- Text column: title SUBHEAD centred; optional detail 11.5 sp muted centred, 3 dp below;
  10 dp margin towards the control.
- Min height 64 dp, or 72 dp with a detail. The control is vertically centred.
- Widths are weights `textWeight : controlWeight`. `setControl(view, textWeight,
  controlWeight)` takes any view (a switch, a stepper, a slider).
- `setSegmented(options, selected, listener)` creates a `SancaiSegmentedTrack` and picks the
  original proportions: two options 2 : 1, three options 1 : 3, four or more 1 : 4.
  `Layout.UTILITY` gives 1 : 1.35 regardless of the count. `track` returns the created
  control.
- XML: `sancaiTitle`, `sancaiDetail`, `sancaiOptions`, `sancaiSelectedIndex`,
  `sancaiRowLayout` (auto / utility); a single child declared in XML becomes the control.

## 4. `SancaiLinkRow` — extracted

Origin: `UiKit.pageLink` and `pageLinkAdjacent`, `UiKit.java` lines 929-975. A quiet link to
another page, usually below the last card.

- 64 dp high. Label 15 sp medium, muted, centred.
- `arrowPlacement`: EDGE (chevron at the start or end edge, 12 dp in, independent of the
  label) or ADJACENT (chevron 5 dp after the label, the pair centred as a group).
  `arrowDirection`: START or END. Press sinks the content.

## 5. `SancaiKeyValueList` — extracted

Origin: `UiKit.readOnlyKeyValueSurface`, `UiKit.java` lines 1020-1063. A read-only card of
facts. Extends `SancaiSurface`.

- `setItems(list)` / `addItem(key, value, tone)`. Each row: min height 52 dp, padding 12 dp
  horizontal and 8 dp vertical; key 13.5 sp regular ink, start aligned, weight 1.22, 10 dp
  gap; value 13.5 sp medium, end aligned, weight 1, coloured by an optional tone (INK by
  default; SUCCESS and DANGER for good and bad states). Full-bleed dividers between rows.
- Rows are not clickable and show no press feedback. A null value shows an em dash.
- Each row is one accessibility element: "key, value".

## Tests

Entry row: the title's centre equals the row's centre with and without a chevron, icon or
value (this is the contract); min heights; click fires once and ticks; the content sinks on
press while the row does not move; RTL swaps the chevron side. Control row: weights for 2,
3 and 4 options and for utility; arbitrary control. Link row: both placements. Key-value
list: column alignment across rows with long and short text, tones, dividers reach the rim.
Snapshots `rows_light`, `rows_dark`: a page with a heading, a card mixing control rows,
a divider and entry rows of every variant, a key-value card and both link rows.
