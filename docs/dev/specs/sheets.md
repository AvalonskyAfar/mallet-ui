# Package `sheets`

Menus, bottom sheets and floating panels. None of these exist in the original app; all three
are designed here from its materials. The designs must look as if they had always been
there.

## Files you own (all new)

- `overlay/SancaiMenu.kt`, `overlay/SancaiBottomSheet.kt`, `overlay/SancaiFloatingPanel.kt`
- `res/values/sancai_strings_sheets.xml`, `res/values-zh/sancai_strings_sheets.xml`
- tests: `overlay/SancaiSheetsTest.kt`

(Paths are under `sancai/src/main/kotlin/io/github/avalonskyafar/sancai/`,
`sancai/src/main/` and `sancai/src/test/kotlin/io/github/avalonskyafar/sancai/`.)

Read the dialog code of the original for the family resemblance (`UiKit.java` lines
1511-1640) and its overlay panel (`UiKit.overlaySurfaceDrawable`, lines 393-396 and
2048-2183; `StatusOverlayController.java`).

## 1. `SancaiMenu` — new, in-language

A small porcelain panel of actions that grows out of the control that opened it.

- Panel: porcelain, corner radius 10 dp (`SancaiMaterial.RADIUS_PANEL_DP`), min width
  168 dp, max width 280 dp, vertical padding 4 dp. Implemented with a `PopupWindow` whose
  content view carries the material (remember the shadow gutter, and disable the platform
  popup elevation and background).
- Items: 48 dp high, label SUBHEAD-sized but regular weight (15.5 sp), ink, start aligned
  with 16 dp padding; optional leading `SancaiIcon` (20 dp, muted) and optional trailing
  check mark for a selected item. A destructive item uses the danger colour. A disabled item
  pales. Full-bleed hairline dividers only between groups (`addDivider()`), not between
  every item.
- Press: the item content sinks; no highlight colour. Keyboard focus draws a rim inside the
  item.
- Placement: anchored below the anchor view, aligned to its end edge; flips above when
  there is no room; kept 8 dp inside the window.
- Motion: grows from the anchor corner, scale 0.92 to 1 with alpha, 180 ms glide; exit
  120 ms. Reduced motion: no scale.
- Dismiss on outside touch, back, or item click (after the callback). Focus moves into the
  menu and returns to the anchor.
- API: `SancaiMenu(context).add(label, icon) { }.addDanger(label) { }.addDivider()
  .setChecked(index).show(anchor)`. Accessibility: items are menu items with their state.

## 2. `SancaiBottomSheet` — new, in-language

A porcelain sheet that rises from the bottom edge and follows the finger down.

- A full-window `Dialog` (transparent background, no platform animation) holding a scrim and
  the sheet. Scrim: the dialog dim colour (black at 27 %), fading with the sheet's position.
- Sheet: porcelain with the top corners rounded at 16 dp (`SancaiCorners.TOP`) and no
  shadow gutter at the bottom; full width up to 560 dp, centred beyond that. A 36 by 4 dp
  grabber pill in the hairline colour sits 8 dp from the top. Optional centred title
  (HEADLINE) and message (CAPTION, muted) like a dialog, then the content.
- Content: any view, or a list of actions built like menu items (56 dp high, centred
  labels, full-bleed dividers between them, destructive in danger colour), plus an optional
  cancel action separated by a divider.
- Motion: glides up from below over `SancaiMotion.DIALOG_MS`; exit glides down. No fade of
  the sheet itself.
- Drag to dismiss: dragging down on the grabber or header, or on content that is scrolled
  to its top, moves the sheet with the finger and fades the scrim in proportion. Release
  past `SancaiMotion.COMMIT_FRACTION` of the sheet height or faster than
  `COMMIT_VELOCITY_DP` dismisses; otherwise it returns. Dragging up beyond the rest
  position is not possible.
- Dismiss on scrim tap and back (`cancelable`). The sheet pads itself for the navigation
  bar and moves above the keyboard.
- API: `SancaiBottomSheet.Builder(context).title().message().view() / .action(label, tone,
  icon) { }.cancel(label).show()`, `dismiss()`, `OnDismissListener`.
- Accessibility: pane title, a "dismiss" action, focus trapped inside while open.

## 3. `SancaiFloatingPanel` — extracted

Origin: `StatusOverlayController.java` (whole file) with `ContextMaterialDrawable`. In the
app it is a small draggable status read-out drawn over other apps through the accessibility
service. The toolkit offers the reusable part: a draggable porcelain panel inside your own
window.

- A `FrameLayout` with the porcelain material at radius 10 dp and padding 12 by 8 dp;
  holds any content (the original showed 12 sp medium text).
- Drag anywhere on it to move it within its parent; it cannot leave the parent's padded
  bounds. On release it stays where it is; `snapToEdge` (default false) glides it to the
  nearer side. Its position is kept as fractions of the parent size, so it survives
  rotation and is saved.
- While dragged it draws the pressed shadow. A drag shorter than the touch slop is a click.
- `SancaiFloatingPanel.attach(activity, content): SancaiFloatingPanel` adds one to the
  activity's content view; `remove()`.
- Document in the KDoc, with a short sample, how to host the same view in a
  `WindowManager` overlay (`TYPE_ACCESSIBILITY_OVERLAY` or `TYPE_APPLICATION_OVERLAY`):
  `SancaiFloatingPanel.overlayLayoutParams()` returns suitable non-focusable, wrap-content
  params, and `bindToWindow(windowManager, params)` makes dragging update the window
  position instead of the view translation.

## Tests

Menu: items, groups, checked, destructive, disabled; click runs the callback and dismisses;
placement flips when the anchor is low. Sheet: structure, action callbacks, scrim tap and
back dismiss, drag past and short of the threshold, drag does not start while content is
scrolled. Panel: drag moves and clamps, click versus drag, snap, position save / restore.
Snapshots `sheets_light`, `sheets_dark`: a menu with icons, a divider and a checked item; an
action sheet with a destructive action and cancel; a floating panel (render the content
views directly, since popups and dialogs have their own windows).
