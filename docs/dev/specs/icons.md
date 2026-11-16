# Package `icons`

Finish the icon set and give it a view.

## Files you own

- `sancai/src/main/kotlin/io/github/avalonskyafar/sancai/icon/SancaiIconPainter.kt`
  (exists; you are the only one editing it)
- `sancai/src/main/kotlin/io/github/avalonskyafar/sancai/icon/SancaiIconView.kt` (new)
- `sancai/src/main/res/values/sancai_attrs_icon_view.xml` (new)
- `sancai/src/test/kotlin/io/github/avalonskyafar/sancai/icon/SancaiIconTest.kt` (new)

Do not change `SancaiIcon.kt` (the enum order is an XML contract) or `SancaiIconDrawable.kt`.

## 1. Glyphs

`SancaiIconPainter.glyph()` draws 20 icons and falls back to a rounded square for the rest.
Replace the fallback with real drawings for every remaining constant:

SEARCH, MORE_HORIZONTAL, MORE_VERTICAL, MENU, INFO, WARNING, ERROR, SUCCESS, TRASH, EDIT,
COPY, SHARE, DOWNLOAD, REFRESH, EYE, EYE_OFF, LOCK, BELL, STAR, HEART, HOME, USER, SUN,
MOON, LAYERS, GRID, LIST, IMAGE, INBOX, CLOUD_OFF, CLOCK, SHIELD, HAND, PALETTE.

Then make the `else` branch unreachable (exhaustive `when`).

Style rules (the existing chevrons, POWER, BOOK, SLIDERS and MORE_CIRCLE show the hand):

- 24 x 24 grid; keep all ink inside 2..22 on both axes.
- One stroke weight (set by the caller), round caps, round joins. No filled shapes except
  small dots (radius 1.2 to 2.2) via `dot()`.
- Simple, geometric, calm: circles, arcs, straight lines, at most a few cubic curves.
  Rounded rather than pointed where there is a choice. Optical size comparable to the
  existing icons (a circle icon is about 20 wide, a square one about 18).
- INFO / WARNING / ERROR / SUCCESS are status marks: circle with "i"; rounded triangle with
  "!"; circle with a cross; circle with a check.
- Draw the geometry yourself from the description of the object. **Do not copy path data
  from any icon library** (Material Symbols, Lucide, Feather, Tabler ...); this project must
  not carry third-party icon licences.
- Keep the helper style: `polyline(...)`, `path.addArc/addCircle/cubicTo`, `stroke(canvas)`,
  `dot(canvas, x, y, r)`. Add small private helpers if they remove repetition.

## 2. `SancaiIconView`

A plain `View` that shows one icon.

- Properties: `icon: SancaiIcon?`, `tone: SancaiTextTone` (default MUTED), `color: Int?`
  (overrides the tone when set), `strokeDp: Float` (default 2).
- Size: the icon fills the smaller side of the content box; `wrap_content` measures 24 dp
  plus padding. Mirrors directional icons in RTL.
- XML: `app:sancaiIcon`, `app:sancaiTone` (both declared in the common file) and
  `app:sancaiIconStroke` (dimension, new in your file).
- Not focusable, not clickable; `contentDescription` is left to the developer, and with no
  description the view is not important for accessibility.

## 3. Tests

- Every enum constant draws a non-empty image whose ink stays inside the 24-grid bounds.
- No two icons render identically (catches copy-paste and leftover placeholders).
- Directional icons mirror in RTL, others do not.
- `SancaiIconView` measures 24 dp on `wrap_content` and honours tone / colour.
- Snapshot: a labelled contact sheet of all 50 icons at 24 dp and a row at 48 dp, in light
  and dark (`icons_light`, `icons_dark`). Look at it critically: every icon must be
  recognisable at 24 dp, evenly weighted and optically centred. Redraw the weak ones.
