# Package `page`

The page scaffold and the reading components.

## Files you own (all new)

- `theme/SancaiSpacing.kt`
- `widget/SancaiPage.kt`, `widget/SancaiMediaFrame.kt`, `widget/SancaiParagraph.kt`,
  `widget/SancaiCallout.kt`, `widget/SancaiFlowRow.kt`
- `res/values/sancai_attrs_page.xml`
- tests: `widget/SancaiPageTest.kt`

(Paths are under `sancai/src/main/kotlin/io/github/avalonskyafar/sancai/`,
`sancai/src/main/` and `sancai/src/test/kotlin/io/github/avalonskyafar/sancai/`.)

Look at `Gravekeeper-master/screenshots/screenshot_settings.png` and
`screenshot_tutorial.png` for the rhythm these components must reproduce.

## 1. `SancaiSpacing` — extracted

A public object of `const val ... : Float` in dp, documented one by one, with the vertical
rhythm of a page. Values from `UiKit.pageColumn` (`UiKit.java` 329-334) and the page
builders in `MainActivity.java` (402-460, 1040-1101, 1217-1227):

| Constant | dp | Meaning |
|---|---|---|
| `PAGE_SIDE` | 12 | page padding, left and right |
| `PAGE_TOP` | 22 | page padding above the first element |
| `PAGE_BOTTOM` | 34 | page padding below the last element |
| `TEXT_INSET` | 10 | extra inset of headings and loose text, so they align with card content |
| `TITLE_GAP` | 16 | page title to the first heading |
| `HEADING_GAP` | 10 | heading to its card |
| `SECTION_GAP` | 22 | card to the next heading |
| `CARD_PADDING` | 9 | content padding of a card |
| `DIVIDER_GAP` | 5 | space above and below a divider between row groups |
| `BUTTON_SIDE` | 30 | side margin of a full-width capsule button on a page |

## 2. `SancaiPage` — extracted and completed

Origin: `UiKit.pageColumn`, `scroll` and `applySystemInsets`, `UiKit.java` lines 269-282 and
329-365. A vertically scrolling page.

- A `ScrollView` holding one vertical `LinearLayout` (`content`). Views added in XML or with
  `addView` go into the column, not into the scroll view.
- Canvas background; page padding from `SancaiSpacing`; `fillViewport`; no scroll bars;
  `clipToPadding = false`; over-scroll only when the content scrolls
  (`OVER_SCROLL_IF_CONTENT_SCROLLS`), so the platform stretch effect is kept and short pages
  stay still.
- System bars: the page adds the system-bar and display-cutout insets to its padding
  (`ViewCompat.setOnApplyWindowInsetsListener`), and the IME inset at the bottom, so it
  works edge to edge. `fitSystemBars` (default true) turns this off for pages inside a host
  that already handles insets.
- Helpers that encode the rhythm: `addTitle(text)`, `addSection(title, detail, card)`,
  `addButton(button)` and `addSpace(dp)`. `addSection` creates the heading as a plain
  `SancaiText` pair (do not depend on other new packages).
- Saves and restores its scroll position (it has to have an id, like any view).

## 3. `SancaiMediaFrame` — extracted

Origin: `UiKit.recessedMediaFrame` and `ClippedImageView`, `UiKit.java` lines 414-428 and
1162-1196; used 152 dp high in the tutorial cards. A picture set into a recessed well.

- Well: `SancaiWellDrawable` with radius 7 dp; the image is inset 4 dp and clipped to a 4 dp
  radius; `centerCrop` by default.
- `setImageDrawable` / `setImageBitmap` / `setImageResource`, `imageView` accessor,
  `scaleType`; `aspectRatio` (width / height, 0 = use the layout height).
- With no image it shows the empty well with a muted IMAGE icon in the centre.
- XML: `android:src`, `sancaiMediaAspectRatio`, `android:contentDescription`.

## 4. `SancaiParagraph` — extracted

Origin: `UiKit.explanatoryText` (`UiKit.java` 1002-1011) and
`MainActivity.markdownTutorialParagraph` / `keepClosingPunctuationWithPrevious`
(`MainActivity.java` 700-729). Long-form copy.

- Extends `SancaiText`, role BODY by default, start and top aligned.
- Fills each line before wrapping: simple break strategy, no hyphenation.
- `protectClosingPunctuation` (default true): CJK closing punctuation never starts a line.
  A word joiner (U+2060) is inserted before each of `，。！？；：、）】》”’` when the text is
  set. `getText()` returns the processed text; keep the original available as `rawText`.

## 5. `SancaiCallout` — extracted

Origin: the accent-bar blocks in `MainActivity.java` lines 771-781 and 905-923. A note beside
running text.

- A 3 dp bar at the start in the tone colour, 14 dp gap, then a title (SUBHEAD, tone colour)
  and a body (BODY_COMPACT, ink) 7 dp below. No card, no background.
- `tone`: ACCENT (default), DANGER, WARNING, SUCCESS. The bar spans the full height.
- XML: `sancaiTitle`, `sancaiDetail` (the body), `sancaiTone`.

## 6. `SancaiFlowRow` — extracted

Origin: `MainActivity.accessibilityFlow`, `MainActivity.java` lines 931-970. A flat sequence
of steps: equal-width columns, each a centred title (SUBHEAD) over a centred detail
(13.5 sp, muted, 4 dp below), separated by 28 dp slots holding an ARROW_RIGHT icon in the
accent colour. `setSteps(list of title / detail)`. Mirrors in RTL. One accessibility element
per step, in order.

## Tests

Spacing constants; page padding with and without insets (dispatch `WindowInsetsCompat`);
XML / `addView` children land in the column; scroll position restore; media frame clips
its corners (corner pixel is well colour, centre pixel is image colour) and keeps aspect
ratio; paragraph inserts joiners only before closing punctuation; callout bar colour per
tone and full height; flow row columns are equal. Snapshots `page_light`, `page_dark`: a
page with title, two sections, a media frame card, paragraphs, a callout and a flow row.
