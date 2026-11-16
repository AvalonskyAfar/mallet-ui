# Package `bars`

Navigation chrome: the bottom navigation row, the top bar, tabs, chips and badges.

## Files you own (all new)

- `widget/SancaiNavBar.kt`, `widget/SancaiTopBar.kt`, `widget/SancaiTabs.kt`,
  `widget/SancaiChip.kt`, `widget/SancaiBadge.kt`
- `res/values/sancai_attrs_bars.xml`
- `res/values/sancai_strings_bars.xml`, `res/values-zh/sancai_strings_bars.xml`
- tests: `widget/SancaiBarsTest.kt`

(Paths are under `sancai/src/main/kotlin/io/github/avalonskyafar/sancai/`,
`sancai/src/main/` and `sancai/src/test/kotlin/io/github/avalonskyafar/sancai/`.)

Look at `Gravekeeper-master/screenshots/screenshot_main.png`: the navigation row at the
bottom is three quiet outline icons with labels on the bare canvas. No bar, no background,
no indicator. That restraint is the style.

## 1. `SancaiNavBar` — extracted and extended

Origin: `MainActivity.mainPage` navigation row and `MainNavItem`, `MainActivity.java` lines
291-400.

- A horizontal row of equal-width items, 72 dp high, 18 dp side margins inside the view.
- Item: a 24 dp outline icon (`SancaiIcon`, stroke 1.7 dp) whose centre lies 21 dp below the
  top, and a label 12.5 sp bold with its baseline 57 dp below the top; both muted. Pressing
  sinks the whole item content.
- Two modes:
  - `Mode.LAUNCHER` (the original): every item is a shortcut; nothing is selected; the
    listener fires on every tap.
  - `Mode.TABS` (addition): one item is selected and drawn in ink with a slightly heavier
    icon stroke (2 dp); the others stay muted. No pill, no bar: selection is expressed by
    ink alone. `selectedIndex`, saved state, reselect callback.
- `addItem(icon, label)`, `setItems(list)`, `OnItemSelectedListener(bar, index)`.
  `setBadge(index, count)` shows a `SancaiBadge` at the top end of the icon.
- Optional `elevated` flag: draws a full-bleed hairline above the row, for content that
  scrolls underneath. Pads itself for the navigation-bar inset.
- Accessibility: items are tabs with selected state in TABS mode, buttons in LAUNCHER mode.
  Arrow keys move between items. RTL reverses the order.

## 2. `SancaiTopBar` — new, in-language

The original has no top bar at all: page titles are plain text in the content and "back" is
a gesture. The toolkit still needs one for apps with deeper hierarchies, and it keeps the
same restraint.

- 56 dp high, canvas colour, no shadow. A hairline at the bottom appears only when
  `lifted` is true; `bindTo(scrollView)` sets it automatically once content has scrolled.
- Title: TITLE-like but 18 sp medium, ink, **centred in the bar**, independent of the
  buttons on either side (the centring rule of the rows applies here too). Optional
  subtitle in CAPTION, muted, below it.
- Navigation button at the start: a `SancaiIcon` (CHEVRON_LEFT by default, mirrored in
  RTL), 24 dp in a 48 dp target, ink. Up to two action icons at the end, muted. Pressing
  sinks the icon. Each needs a content description; the navigation button defaults to the
  library "Back" string.
- Pads itself for the status-bar inset.
- `setNavigation(icon, description) { }`, `addAction(icon, description) { }`, `title`,
  `subtitle`.

## 3. `SancaiTabs` — new, in-language

Text tabs for switching between sibling views. A different idea from the segmented track on
purpose: the track selects a *value* and shows it with glaze; tabs select a *view* and must
stay quiet.

- 44 dp high. Labels 14.5 sp medium, muted; the selected one ink. Under the selected label
  a 3 dp high capsule in the accent colour, as wide as the label, glides to the new label on
  change (`SancaiMotion.CONTROL_MS`) and follows `setScrollPosition(index, offset)` so it
  can track a `SancaiPager`.
- `Layout.FILL`: equal widths. `Layout.SCROLL`: natural widths with 16 dp padding in a
  horizontal scroller that keeps the selected tab in view.
- A full-bleed hairline along the bottom edge.
- `setTabs(labels)`, `selectedIndex`, `select(index, animate)`,
  `OnTabSelectedListener(tabs, index)`. If `SancaiPager` exists in the tree, offer
  `attachTo(pager)`; otherwise leave that out and report it.
- Keys, RTL, saved state, accessibility as a tab list.

## 4. `SancaiChip` — new, in-language

A small porcelain capsule for filters and tags.

- 32 dp high, capsule porcelain (`SancaiSurfaceDrawable(pill = true)`), label 13 sp medium,
  padding 14 dp; optional leading icon (16 dp).
- `Kind.ACTION`: behaves like a small neutral button. `Kind.FILTER`: checkable. Checked
  turns the body into a glazed capsule (cobalt by default, `glaze` property; paint with
  `theme.bedColor` and take the label colour from `theme.labelOnBed`) and shows a small
  check mark before the label; the width change is animated.
- Press sinks; disabled pales; focus rim. `SancaiChipGroup`: a wrapping flow layout with
  8 dp gaps and optional single selection.

## 5. `SancaiBadge` — new, in-language

- A count badge: capsule glazed in the danger colour, white 11 sp bold text, min 18 dp,
  "99+" beyond 99; a 8 dp dot when the count is zero and `showDot` is set.
- A plain `View` for use on its own, and `SancaiBadge.attach(anchor)` which overlays one at
  the top end corner of any view through the anchor's overlay.

## Tests

Nav bar: equal widths, both modes, tap and reselect callbacks, badge, keys, save / restore,
RTL, exact vertical positions of icon and label. Top bar: the title's centre equals the
bar's centre whatever buttons are present; hairline follows scrolling; insets. Tabs:
selection, indicator geometry at rest and half way, scroll layout keeps the selection
visible. Chip: both kinds, checked pixel colour, group single selection and wrapping.
Badge: text for 0, 5, 120. Snapshots `bars_light`, `bars_dark` with every component.
