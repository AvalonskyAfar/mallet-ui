# Package `gestures`

Small finger-following containers: reveal, expand, page.

## Files you own (all new)

- `interaction/SancaiRevealLayout.kt`, `interaction/SancaiExpandableLayout.kt`,
  `interaction/SancaiPager.kt`, `widget/SancaiPageIndicator.kt`
- `res/values/sancai_attrs_gestures.xml`
- `res/values/sancai_strings_gestures.xml`, `res/values-zh/sancai_strings_gestures.xml`
- tests: `interaction/SancaiGesturesTest.kt`

(Paths are under `sancai/src/main/kotlin/io/github/avalonskyafar/sancai/`,
`sancai/src/main/` and `sancai/src/test/kotlin/io/github/avalonskyafar/sancai/`.)

## 1. `SancaiRevealLayout` — extracted

Origin: `WhitelistAccountsPage.AccountItem`, `WhitelistAccountsPage.java` lines 206-280,
with `UiKit.destructiveRevealDrawable` / `embeddedRowDrawable`. A row inside a card that
slides aside to uncover one action, typically "Delete".

- A `FrameLayout` with exactly one child, the foreground row. The layout draws a recessed
  coloured bed (`material.drawRevealBed`) behind it and gives the foreground a flat row fill
  (`material.drawRowFill`) when it has no background, so the bed is hidden while closed. A
  row never adds a rim or shadow of its own: it lives in its card.
- `setAction(label, tone = DANGER, icon = null, onClick)`. The action occupies a
  78 dp strip at the reveal edge (`revealEdge`: START as in the original, or END); label
  13.5 sp medium, white, centred; pressing sinks the label.
- Drag: horizontal movement towards the open direction translates the foreground by at most
  the strip width. Vertical movement gives the gesture back. A gesture that starts on a
  `SancaiHorizontalDragOwner` (a switch in the row) is never taken.
- Release: opens when at least 42 % of the strip is uncovered, otherwise closes; settles
  with the feedback duration and the glide. Cancel closes.
- `open()`, `close()`, `isOpen`; a tap on the open foreground closes it; opening one row
  closes the other open rows of the same parent (`closeSiblings`, default true).
- Accessibility: the action is also offered as a custom accessibility action on the row, so
  nobody has to swipe to reach it. RTL mirrors the edge. `isOpen` is not saved (rows come
  back closed).

## 2. `SancaiExpandableLayout` — new, in-language

Expand and collapse. A vertical container whose first child is the always-visible header
and whose remaining children form the collapsible body.

- Expanding grows the body's height from zero while the body stays anchored at its top
  edge and is clipped, as if a cover slid away; nothing fades or scales. Collapsing is the
  reverse. Duration: `SancaiMotion.settleDuration(fraction)` capped by `PAGE_MS`, glide.
  Reduced motion uses the reduced duration.
- `isExpanded`, `setExpanded(expanded, animate)`, `toggle()`,
  `OnExpansionChangeListener(layout, expanded)` (user toggles only) and
  `OnExpansionProgressListener(layout, fraction)` so a header chevron can turn with it.
- `toggleOnHeaderClick` (default true): the header becomes clickable with the Sancai press
  (content sinks) and toggles. If the header contains a view tagged as the indicator
  (`setIndicator(view)`), it is rotated from 0 to 180 degrees with the progress.
- A collapsed body is `GONE` for layout and accessibility; interruption mid-animation
  reverses from the current height.
- Saved state. Accessibility: expand / collapse actions and an expanded / collapsed state
  description on the header.
- XML: `sancaiExpanded` (boolean), `sancaiToggleOnHeaderClick`.

## 3. `SancaiPager` — extracted

Origin: the first-launch flow, `FirstLaunchActivity.java` lines 330-543 (programmatic move,
finger-following move, settle, thresholds), and its dots (lines 263-277, 539-543). A
horizontal pager for a handful of pages.

- Adapter: `fun interface PageProvider { fun createPage(index: Int, parent: ViewGroup): View }`
  with `setPages(count, provider)`. Pages are created on demand; the current page and the
  one being dragged in are attached; `offscreenPageLimit` (default 1) keeps neighbours.
- Drag: horizontal, finger-following, both pages move edge to edge; first and last page do
  not over-scroll. Vertical movement gives the gesture back. Gestures that start on a
  `SancaiHorizontalDragOwner` are left to it.
- Commit: 22 % of the width, or a velocity of 0.45 widths per second towards the target.
  These are the original's values, lower than `SancaiMotion.COMMIT_FRACTION` because paging
  is the main gesture of such a screen; keep them as documented constants of the pager.
- `currentPage`, `setCurrentPage(index, animate)`; while a move runs further requests are
  ignored, as in the original. Settle via `SancaiMotion.settleDuration`.
- `OnPageChangeListener`: `onPageSelected(index)` and `onPageScrolled(index, offset)`.
- `linkWith(other: SancaiPager)`: the other pager follows this one's position continuously
  (the original moved a heading area and a content area in step).
- Saved state; RTL reverses the direction; accessibility scroll actions and a page
  announcement ("Page 2 of 8" from a string resource).

## 4. `SancaiPageIndicator` — extracted

- One dot per page: 7 dp circles in 28 dp touch targets, muted, the current one in the
  accent colour; while the pager moves the two dots involved cross-blend with the offset.
- `attachTo(pager)`; tapping a dot moves there. Also usable standalone with `count` and
  `selected`.
- Accessibility: one element with the same "Page x of n" text and scroll actions.

## Tests

Reveal: drag past / short of 42 %, clamp at the strip width, vertical abort, switch inside
the row keeps its gesture, action click, tap closes, siblings close, accessibility action,
RTL. Expandable: heights at start, middle and end of the animation, interruption, body gone
when collapsed, header toggle, indicator rotation, save / restore. Pager: drag commit and
return, fling, bounds, programmatic move, link keeps two pagers in step, listener values,
save / restore, RTL. Indicator: colours and tap. Snapshots `gestures_light`,
`gestures_dark`: a half-open reveal row, an expanded and a collapsed section, a pager
mid-drag with its indicator.
