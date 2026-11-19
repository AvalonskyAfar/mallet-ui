# Mallet design system

Mallet is a design language for Android Views. The name is the craftsman's hammer, and a
salute to Smartisan (锤子科技, "Hammer Technology"; the name Smartisan is *smart* +
*artisan*), the phone maker that treated every interface detail as a piece of craft. A
mallet shapes a sheet in two ways only: it raises some parts and sinks others. That is all a
Mallet interface is made of: raised surfaces, recessed tracks, and a press that sinks.

The salute is a sentence, nothing more: the project is not affiliated with Smartisan or its
owners and uses none of its marks ([LICENSE_AUDIT.md](LICENSE_AUDIT.md#4-the-name)).

Colour has one job. Pages, cards and text are neutral. Colour is reserved for **state**,
and it sits in the recessed bed of a control, never on the part you touch.

This document defines the tokens and rules. Every value is implemented in
`io.github.avalonskyafar.mallet.theme` and `...material`; nothing here exists only on paper.

Each rule carries one of three marks. They say where the rule comes from; the evidence for
each is in [DESIGN_DECISIONS.md](DESIGN_DECISIONS.md), and a number such as D-24 names a
decision recorded there.

| Mark | Meaning |
|---|---|
| **Extracted** | Taken from the original application, values unchanged |
| **Extended** | An original idea generalised or completed |
| **New** | Designed for Mallet in the same language |

Contents: [Principles](#1-principles) · [Colour](#2-colour) · [Hues](#3-hues) ·
[Typography](#4-typography) · [Space and layout](#5-space-and-layout) · [Shape](#6-shape) ·
[Materials and depth](#7-materials-and-depth) · [States](#8-states) · [Motion](#9-motion) ·
[Iconography](#10-iconography) · [Theming](#11-theming) · [Accessibility](#12-accessibility)

---

## 1. Principles

1. **Colour is state, and it lives in the bed.** A switch that is off is bronze; on, it is
   cobalt. A four-level control runs bronze, ochre, cobalt, jade. The thumb that
   travels over the bed is always the same neutral bead. *(Extracted)*
2. **Four materials, nothing else.** Surface (raised, neutral), track (recessed, carries the
   hue), thumb (raised, neutral, inside a track) and the well (recessed, neutral, for text
   and pictures; section 7). There are no flat tinted buttons, no ripples, no elevation
   overlays. *(Extracted; the well completes the three)*
3. **Text is centred; arrows are not part of the text.** In rows, title and detail are
   centred on the row. A chevron is placed on its own at the edge and never pushes the text
   aside. It points to where the next page lies. *(Extracted)*
4. **Dividers run from rim to rim.** A hairline inside a card touches both edges of the card.
   Rows read as sections of one body, not as boxes in a box. A row that slides aside runs
   from rim to rim as well: the coloured bed under it starts at the rim of the card, not at
   the edge of the card's content (section 7.5). *(Extracted; the row that slides aside is
   Extended and departs from the original on purpose, D-24)*
5. **Pressing sinks; it does not tint.** What you touch moves down 1.5 dp and comes straight
   back. Tracks and thumbs tighten their shadow. Nothing changes colour. *(Extracted)*
6. **Everything follows the finger, or glides.** Thumbs, pages, sheets and rows that slide
   aside move with the finger at finger speed. When released they settle along one easing
   curve. Nothing bounces, nothing fades in. *(Extracted)*
7. **Pages are places.** A page lies beside, above or below another one; dragging the current
   page towards that side slides it in from there. Navigation is movement through a map, not a
   stack of cards. A place
   carries no signpost. Home, the root page of the map, shows nothing that points at its
   neighbours: no arrows, no page names at the edges. It names the pages around it once, in
   a row of entries at its bottom, one entry for each first-level page (a page that lies
   directly beside, above or below Home). A first-level page shows no way back: no back
   row, no arrow, no bar. It is left the way it was reached, by dragging back towards Home,
   or with the system's Back. A page without a button to tap is how the drag becomes a
   habit. *(Extracted, in part Extended: D-23)* The deeper pages of the showcase, those
   opened from a row, keep a quiet way-back row that names their parent; that row is New,
   the original has no back control at any depth. The rule in full is in the
   [interaction guide](INTERACTION_GUIDE.md#54-marks-and-their-absence).
8. **Depth is painted, short and downward.** One hairline rim, one short soft shadow below,
   a trace of inner shading. Shadows never spread sideways into a glow. *(Extracted)*

---

## 2. Colour

### 2.1 Neutral and semantic roles

`MalletColors` holds the roles. The light theme is white on white: canvas, surface and
raised are the same white, and separation comes from rims and shadows alone. The dark theme
is a blue-black slate with three real steps.

| Role | Use | Light | Dark | Mark |
|---|---|---|---|---|
| `canvas` | page background | `#FFFFFF` | `#181D24` | Extracted |
| `surface` | card body | `#FFFFFF` | `#1F252E` | Extracted |
| `raised` | thumbs | `#FFFFFF` | `#262D38` | Extracted |
| `ink` | primary text, icons, shadow tint (light) | `#1A1C1F` | `#EBEFF4` | Extracted |
| `muted` | secondary text, chevrons, navigation | `#8E8F90` | `#A1ACBB` | Extracted |
| `hairline` | rim of cards and capsules | `#EDEDED` | `#36404D` | Extracted |
| `divider` | full-bleed separators | `#DFDFE0` | `#363A41` | Extracted |
| `accent` | links, confirming actions, primary capsule | `#315A9B` | `#5B7EA4` | Extracted |
| `danger` | destructive fills and text | `#C85858` | `#975960` | Extracted |
| `warning` | caution text and marks | `#9A6A14` | `#9D804B` | Light: New (see below) |
| `success` | positive text and marks | `#2E9168` | `#598569` | Extracted |

`divider` is not a free colour: it is 14 % of `ink` laid over `canvas`
(`MalletColors.blend(canvas, ink, 0.14f)`), so it follows any custom palette.

One role departs from the original. Its light `yellow` was `#E0B33A`, a colour the app
declared but never drew; as text on white it reaches only 2.0 : 1. Mallet's `warning` is a
role for text and marks, so it is a deep ochre, `#9A6A14` (4.7 : 1), of the same colour
family as the ochre hue.

### 2.2 High contrast

`MalletTheme.highContrast` strengthens the weakest roles and the labels on a hue
(section 3.4), until every text role reaches at least 4.5 : 1 on the canvas and on a card.
When the theme does not set it, it follows the system's high-contrast text setting
(Android 16 and later). *(`muted`, `hairline`, `divider`: Extracted. The rest: New)*

| Role | Light | Light, high contrast | Dark | Dark, high contrast |
|---|---|---|---|---|
| `muted` | `#8E8F90` | `#4C4E50` | `#A1ACBB` | `#C9D0D9` |
| `hairline` | `#EDEDED` | `#D1D2D2` | `#36404D` | `#586576` |
| `divider` | `#DFDFE0` | = hairline | `#363A41` | = hairline |
| `accent` | `#315A9B` | unchanged | `#5B7EA4` | `#7895B4` |
| `danger` | `#C85858` | `#BA5353` | `#975960` | `#B0868C` |
| `warning` | `#9A6A14` | unchanged | `#9D804B` | `#A58B5C` |
| `success` | `#2E9168` | `#2C835F` | `#598569` | `#6F957E` |

In high contrast the label of a coloured capsule (primary, destructive) is whichever of white
and the theme's darkest neutral reads better on the tint (`theme.labelOnTint`).

### 2.3 Rules

- Neutral roles never carry state. A card is never tinted to say "selected" or "error".
- `accent`, `danger`, `warning` and `success` are for **text and marks**, for the two
  coloured capsule buttons (primary and destructive), and for the bed under a row that
  slides aside (section 7.5). They are not track colours.
- Shadows in the light theme are tinted with `ink`, not black, so they stay cool and soft.
  In the dark theme they are pure black.

---

## 3. Hues

A hue is the colour that fills the bed of a recessed track. It is always a triple
(`MalletHue`): the **bed**, a darker **edge** of the same colour for the one-dp rim, and the
**on-bed** colour for labels and marks resting on it.

### 3.1 The five hues

| Hue | Bed | Edge | On-bed | Reads as | Mark |
|---|---|---|---|---|---|
| Bronze | `#CE8353` | `#BE6938` | `#FFFFFF` | off, none, stop | Extracted |
| Ochre | `#DEBE77` | `#C29E52` | `#2C2104` | gentle, some, caution | Extracted |
| Cobalt | `#86ACF7` | `#6990E0` | `#FFFFFF` | on, standard | Extracted |
| Jade | `#80B897` | `#5B9774` | `#F4FFF9` | strict, full, good | Extracted |
| Mist | `#E2EDF7` | `#B7CCE0` | `#34485C` | a plain choice, no level | Extracted |

These are screen colours, tuned on a device for how they look under the track's inner
shading. They are deliberately softer than the semantic text colours of section 2 and must
not be swapped for them.

The original names these colours by level, not by colour: `TRACK_OFF`, `TRACK_LIGHT`,
`TRACK_STANDARD`, `TRACK_STRICT` and `TRACK_THEME_LIGHT`, in the order of the table. The
five names are Mallet's. *(New, D-26)*

### 3.2 The scale

Options of an ordinal control take their hue from position
(`theme.hues.scale(count)`; `theme.hues` is a `MalletHues`): *(Extracted)*

| Options | Hues |
|---|---|
| 2 | bronze, cobalt |
| 3 | bronze, ochre, cobalt |
| 4 | bronze, ochre, cobalt, jade |
| 5 or more | as four, further options stay jade *(Extended)* |

While a thumb travels, the bed blends continuously between the hues of the two options it
is between. The control therefore answers "which option" by position and "how strong" by
colour at once.

For options that are not levels (a theme picker, a platform picker) use one hue under all
of them: `theme.hues.uniform(count, theme.hues.mist)`. Mist is the quiet choice for that;
pass it explicitly, because the second argument, `hue`, defaults to cobalt.
*(Extended: the original special-cased one theme picker.)*

### 3.3 Hue at night

On the dark canvas a full-strength bed would glare, so the original drew every dark track as
one neutral groove (`#191E25`) and let only the labels keep a tint. That is safe but it gives
up the idea that colour is state exactly where a switch has no labels.

Mallet keeps a restrained share of the hue in the groove: *(New)*

```
dark bed  = blend(groove, hue.bed,  nightHue)      nightHue = 0.30 by default
dark edge = blend(blend(ink, canvas, 0.93), hue.edge, nightHue)
```

| Hue | Dark bed | Dark edge |
|---|---|---|
| Bronze | `#4F3C33` | `#543E34` |
| Ochre | `#544E3E` | `#564E3C` |
| Cobalt | `#3A4964` | `#3B4A67` |
| Jade | `#384C47` | `#374C46` |
| Mist | `#555C64` | `#525C67` |

A theme with `nightHue = 0f` (XML: `malletNightHue`) restores the original neutral groove.
Always paint through `theme.bedColor(hue)` and `theme.bedEdgeColor(hue)`.

### 3.4 Labels on a hue

`theme.labelOnBed(active, own, optionCount)` decides the colour of text and marks resting on
a bed.

- **Light.** Every resting label takes the on-bed colour of the hue that fills the bed at
  that moment (white on bronze, cobalt and jade; deep brown on ochre; slate on mist). The
  label under the thumb is `ink` on the white bead. *(Extracted)*
- **Light, high contrast.** Resting labels switch to a deep tone of the hue:
  `blend(edge, ink, 0.82)`. *(New)*
- **Dark.** Each label keeps a pale tint of its *own* option's hue,
  `blend(own.bed, ink, 0.62)` for two options and `0.70` for more, so the scale stays
  readable inside the groove. *(Extracted)* High contrast raises the ink share to `0.86`.
  *(New)*

Contrast of resting labels against their bed is listed in section 12.1.

---

## 4. Typography

Mallet uses the system typeface of the device, in three weights: regular, medium
(`sans-serif-medium`) and bold. It ships no font. On a Chinese-language device the same roles
render in the system CJK face, which is how the language was first drawn.

### 4.1 The scale

`MalletTextRole` names eleven roles. The original application wrote about twenty different sizes
at its call sites; the roles below keep its recurring values and fold the near-duplicates
into them. *(Extended)*

| Role | Size (sp) | Weight | Leading | Use |
|---|---|---|---|---|
| `DISPLAY` | 20.5 | medium | ×1.16 | onboarding and hero titles |
| `TITLE` | 20 | medium | ×1.16 | page title |
| `HEADLINE` | 18 | medium | ×1.16 | dialog title, article group title |
| `HEADING` | 17 | medium | ×1.16 | section heading, above a card or a control on the canvas |
| `SUBHEAD` | 15.5 | medium | ×1.16 | row title, article sub-heading |
| `BODY` | 15.5 | regular | ×1.24 + 3 dp, tracking 0.01 | long-form copy |
| `BODY_COMPACT` | 14.5 | regular | ×1.28 + 3 dp, tracking 0.01 | dense article copy |
| `LABEL` | 14.5 | medium | ×1.16 | buttons and actions |
| `FOOTNOTE` | 13.5 | regular | ×1.16 | facts, step details, the label of the action a row uncovers |
| `CAPTION` | 12.5 | regular | ×1.16 | supporting line under a title |
| `MICRO` | 11.5 | regular | ×1.16 | detail line inside a row |

Every text in the library takes its size from this scale; a component changes only the
weight (navigation labels are `CAPTION` in bold, tags `MICRO` in bold, chips and field labels
`FOOTNOTE` in medium). Two signature controls keep the label sizes their geometry was drawn
for: the segmented track (bold 12.2 sp, 11.2 sp with four or more options) and the power
switch (medium 14 sp). *(Extracted)*

### 4.2 Rules

- **No font padding.** Text views switch `includeFontPadding` off, so a line is as tall as
  its glyphs and vertical centring is optical, not approximate. *(Extracted)*
- **Titles and labels are medium, never bold.** Bold is kept for the small labels on a hue,
  where it has to hold against colour. *(Extracted)*
- **Only body text opens its leading.** Titles stay tight. *(Extracted)*
- **Paragraphs fill their lines.** Long copy uses the simple line breaker without
  hyphenation, and CJK closing punctuation (`，。！？；：、）】》”’`) is tied to the
  preceding character so it never starts a line. *(Extracted)*
- **Row and card text is centred**; headings and article text are start-aligned.
  *(Extracted)*
- **Text scale.** Every size is multiplied by the system font scale and by
  `MalletTheme.textScale`. The original offered one extra step, 1.13
  (`MalletTheme.LARGE_TEXT_SCALE`). Controls shrink labels that no longer fit rather than
  clip them. *(Extracted; shrink-to-fit New)*

### 4.3 Tone

`MalletTextTone` maps text to a colour role: `INK`, `MUTED`, `ACCENT`, `DANGER`, `WARNING`,
`SUCCESS`. A status line is a short coloured sentence, green when fine and red when not;
it needs no icon and no container. *(Extracted)*

---

## 5. Space and layout

### 5.1 Page rhythm

A page is one column. It is no wider than 560 dp (`MalletPage.maxContentWidthDp`) and stands
in the middle of a wider screen, so that on a tablet, or on a phone lying on its side, a
title and its control do not drift to opposite ends of a row. The page reaches behind the
system bars, but what scrolls in it ends at the status bar, softened over 10 dp, and does
not slide under the clock. *(New)*

`MalletSpacing` holds the vertical rhythm of a page, in dp. *(Extracted)*

| Token | dp | Meaning |
|---|---|---|
| `PAGE_SIDE` | 12 | page padding, left and right |
| `PAGE_TOP` | 22 | above the first element |
| `PAGE_BOTTOM` | 34 | below the last element |
| `TEXT_INSET` | 10 | extra inset of headings and loose text, aligning them with card content |
| `TITLE_GAP` | 16 | page title to first heading |
| `HEADING_GAP` | 10 | heading to what stands under it: a card, or a control on the canvas |
| `SECTION_GAP` | 22 | that card or control to the next heading |
| `CARD_PADDING` | 9 | content padding of a card |
| `DIVIDER_GAP` | 5 | above and below a divider between row groups |
| `BUTTON_SIDE` | 30 | side margin of a full-width capsule on a page |

A page is a single scrolling column on the canvas: a title, then section after section. A
section is a heading and what stands under it: a card, or a control on the canvas. Cards
span the column; the one or two actions that apply to the whole page are capsule buttons
below the last section.

**A card is a group.** A card groups at least two rows or blocks that belong together. A
lone control never gets a card of its own: it joins the card of its relatives, or it stands
on the canvas under its heading, as a hero switch or a capsule button does. A caption or
status line under a control does not make a group. A card around one composite block (a
list, a state view, a pager with its dots, a paragraph, a picture, a stage with the knob
that drives it) may stay. *(The grouping is Extracted; the rule about lone controls is New
and departs from the original, D-25)*

A raised surface says "these belong together". Around a single switch it says nothing and
only adds a frame, so the page reads as a stack of little boxes. The original gives every
headed section one card whatever its size, and so has two cards with one lone control row.
The rule goes beyond the original, and it does so with the original's own means:

- **In the card of its relatives.** Controls that belong together are the rows or labelled
  blocks of one card. A full-bleed divider, and where it helps a short label inside the
  card, separates one sub-group from the next. A switch, a checkbox or a short track
  beside its title is a control row (`MalletControlRow`, section 5.2).
- **On the canvas.** The original stands its power switch and its capsule buttons there.
  Under its heading, a control without a card keeps the place a card would have:
  `HEADING_GAP` below the heading, the full width of the column, `SECTION_GAP` below it
  (`MalletPage.addSection` takes a control as it takes a card). A full-width capsule button
  keeps its side margin, `BUTTON_SIDE`.

### 5.2 Rows

| Row | Minimum height (dp) | Mark |
|---|---|---|
| Entry row, title only | 62 | Extracted |
| Entry row, title and detail | 76 | Extracted |
| Control row, title only | 64 | Extracted |
| Control row, title and detail | 72 | Extracted |
| Key-value row | 52 | Extracted |
| Link row | 64 | Extracted |
| Navigation row (the row of entries at the bottom of Home) | 72 | Extracted |
| Dialog and sheet action | 54 / 56 | Extracted / New |
| Menu item | 48 | New |

In a control row the text column and the control share the width by weight, chosen from the
number of options so that every track gets room for its labels: 2 : 1 for two options,
1 : 3 for three, 1 : 4 for four; utility rows use 1 : 1.35. *(Extracted)*

### 5.3 Centring and independence

The title of a row is centred on the **row**, not on the space left over beside an icon or
chevron. Leading icons and chevrons are positioned on their own, 12 dp from the edge; a
trailing value stands 14 dp from the edge, or next to the chevron. Adding or removing one
never moves the text. The same rule holds for the title of
the top bar and for the label of a link row. *(Extracted; top bar New)*

Centred text suits short rows that lead somewhere or carry a wide track. A list of switches,
or rows with long text, reads better from one edge: entry rows and control rows can align
their text to the start instead (`Alignment.START`), 14 dp from the edge, and a card should
use one alignment for all of its rows. *(New)*

### 5.4 Touch targets

Interactive elements are at least 44 dp in their smaller dimension, either by their own
size or through the row that contains them. The two exceptions are the dots of a page
indicator (28 dp cells; the pager itself is the large target) and a chip in a dense group,
whose 44 dp targets overlap those of its neighbours. *(New)*

---

## 6. Shape

Two shapes only: the rounded rectangle for bodies that hold content, and the capsule for
anything you operate.

| Element | Radius | Mark |
|---|---|---|
| Card | 8 dp | Extracted |
| Text well | 9 dp | Extracted |
| Media well | 7 dp, image inside 4 dp | Extracted |
| Floating panel, menu | 10 dp | Extracted / New |
| Dialog; top corners of a sheet | 16 dp | Extracted / New |
| Checkbox bed | 7 dp | New |
| Bed under a row that slides aside | follows its card: at the corners it shares with the card, the card's radius less the rim (7.1 dp in an 8 dp card); square everywhere else | Extended (D-24) |
| Buttons, tracks, thumbs, toasts, chips, tags | capsule (half the height) | Extracted |

Rims are 0.85 to 1 dp. Dividers are 1 dp. Nothing in the system has a thicker line except
keyboard focus (2 dp, section 8).

---

## 7. Materials and depth

Android's elevation shadows are not used. Every rim, shadow and recess is painted by
`MalletMaterial`, so the four materials (surface, track, thumb, well) look the same on every
device and API level, and your own views can use them.

### 7.1 Surface — `drawSurface`, `MalletSurfaceDrawable`

The raised, neutral material: cards, capsule buttons, dialogs, toasts, menus. What a card
may hold is in section 5.1. Layers, bottom to top: *(Extracted)*

Shadow figures in this section are the nominal parameters (offset / blur, tint and opacity)
that the painter approximates with stacked translucent shapes inside the gutter; the result
on screen is a little lighter than a free blur of the same numbers.

| Layer | Light | Dark |
|---|---|---|
| Outer lift | shadow 0 / 3.52 dp / blur 8 dp, `ink` at 9 % | four black shells, 10 to 25 alpha |
| Body | flat `surface` | vertical gradient: `surface` +2.8 % ink, `surface`, `surface` 15 % towards canvas |
| Inner depth | top shade 1.6 dp at 4 % ink, bottom shade at 5 % ink | none |
| Rim | 0.85 dp `hairline` | 0.85 dp gradient: white 26 %, canvas +8.2 % ink, shadow 31 % |

A **coloured body** (primary or destructive capsule) uses the dark recipe in both themes: a
three-stop gradient of the tint (+10 % white, tint, +5.5 % black in light) and a gradient
rim. *(Extracted for danger; primary New)*

A pressed surface tightens its lift to 0 / 1.8 dp / blur 4.6 dp at 8 %. *(Extracted values,
first used by Mallet's button)*

### 7.2 Track — `drawTrack`

The recessed material that carries the hue: segmented tracks, switches, steppers, sliders,
progress. *(Extracted)*

| Layer | Value |
|---|---|
| Outer lift | shadow 0 / 6.08 dp / blur 12.8 dp, `ink` at 12 %; pressed 0 / 2.5 / 5.3 at 11 % |
| Bed | the bed colour of the hue, painted as is; depth is laid over it, never mixed into it |
| Top inner shade | black 16 % (dark theme 27 %), fading over 3.2 dp + 2 × 5.12 dp |
| Bottom inner light | white 34 % (dark theme 15 %), fading over 2.4 dp + 2 × 4 dp |
| Rim | 1 dp in the hue's edge colour |

The hero switch uses a deeper lift: 0 / 8.8 dp / blur 17.6 dp at 16 %.

### 7.3 Thumb — `drawThumb`

The neutral bead inside a track. *(Extracted)*

| Layer | Light | Dark |
|---|---|---|
| Lift | shadow 0 / 4.48 dp / blur 5.12 dp at 23 %; pressed 0 / 1.92 / 2.72 at 19 % | compact black shells |
| Body | flat `raised` | gradient: `raised` +5.5 % ink, `raised`, `raised` 19 % towards canvas |
| Inner depth | top 2.56 dp at 8 %, bottom 1.92 dp at 10 % | none |
| Rim | 0.9 dp, canvas with 24 % ink | 0.9 dp gradient, light at the top |

The thumb's lift is clipped to its track, so a thumb resting at either end never grows a
shadow outside the capsule.

### 7.4 Well — `drawWell`, `MalletWellDrawable`

Text fields, editors, media frames. A neutral recess: a body slightly darker at the top, and
a rim that runs from a dark top edge to a light bottom edge (in the light theme, to a
hairline tone). Replacing the rim with a solid
colour is how a field shows focus (`ink`) or an error (`danger`). *(Extracted; solid rims
New)*

### 7.5 The bed under a row — `drawRevealBed`, `drawRevealShade`

A row that slides aside uncovers a coloured bed, and on it one action. This is not a fifth
material: it is a recess, as the bed of a track is, but it lies under the card and has no
rim and no lift of its own. Its colour is that of the action, `danger` by default; the
label of the action, and the icon above it where there is one, are white in both themes.
*(The paint of the bed and the white label are Extracted; the optional icon is New. The
place of the bed and its corners are Extended, and the shade of the row was added with
them: D-24)*

| Layer | Light | Dark |
|---|---|---|
| Bed | vertical gradient: the colour +9 % `ink` at the top, the colour itself at 54 % of the height, +12 % white at the bottom | the colour is first moved 26 % towards `canvas`; then +13 % `ink`, itself, +3.5 % white |
| Upper edge | a dark line of 1.2 dp, black at 16 % | the same line, black at 20 % |
| Lower edge | a light line of 1.1 dp, white at 20 % | the same line, white at 10 % |
| Shade of the row | 5 dp of the bed along the edge of the row, from black at 15 % to nothing | the same, from black at 24 % |

The bed starts at the rim of the card, not at the edge of the card's content, and it
follows its card: where the row is the first or the last child of the card, the bed takes
the card's corners (section 6). It lies inside the hairline rim, `MalletMaterial.RIM_DP`
(0.9 dp) from the edge of the card's body, so the rim stays whole around it. The bed shows
exactly as far as the row has moved, and a closed row shows no colour. Outside a card the
bed is flush with the edges of the layout. The cell a card gives such a row, and how the
row moves, are in section 9.1 of the
[interaction guide](INTERACTION_GUIDE.md#9-reveal-expand-page).

The original draws the bed as a square box inside the card's 9 dp padding. Mallet departs
from it on purpose: a coloured box inside the padding is a box in a box, which principle 4
speaks against.

### 7.6 Gutters

A view draws its own shadow **inside its own bounds**. The visible body is therefore inset
from the view by a gutter, and a view is a little larger than what the eye sees: *(Extracted)*

| Body | Sides | Top | Bottom |
|---|---|---|---|
| Surface | 1.4 dp | 1 dp | 4.8 dp |
| Track (60 dp view, 52 dp capsule) | 1.5 dp | 1.5 dp | 6.5 dp |
| Hero switch (92 dp view, 80 dp capsule) | 2.5 dp | 2 dp | 10 dp |

Shadows measure the room the canvas clip leaves them and fade to zero inside it, so they are
never cut off, whatever clips the parent.

### 7.7 What depth means

| Depth | Meaning |
|---|---|
| Raised (surface, thumb) | something that holds content or can be moved |
| Recessed and coloured (track) | a state; its colour is the value |
| Recessed and neutral (well) | a place to put text or a picture |
| Recessed and coloured, under a row | one action, uncovered when the row is slid aside |
| Flat on the canvas | text, icons, navigation |

---

## 8. States

| State | Treatment | Mark |
|---|---|---|
| Pressed | operated content sinks 1.5 dp in 55 ms and returns at once on release; tracks and thumbs sink their content 1 dp and tighten their shadow; no colour change | Extracted |
| Focused (keyboard, D-pad) | the rim of the focused body thickens to 2 dp of `ink`, drawn inside the body; shown only outside touch mode | New |
| Disabled | the whole control is drawn at 42 % alpha; shape and layout do not change | New |
| Selected | position of the thumb plus the hue of the bed; in lists, `ink` instead of `muted` | Extracted / New |
| Error | danger-coloured rim on a well, a danger line below it, or a danger capsule inside a dialog | Extracted / Extended |
| Loading | an open ring turning (the ring of the power glyph), or a coloured fill travelling in a groove; content waits in place, nothing covers the page | New, after the original's habit |
| Empty | nothing is drawn in place of the missing content except one centred action; no empty card | Extracted |

---

## 9. Motion

`MalletMotion` holds the tokens.

| Token | Value | Use | Mark |
|---|---|---|---|
| `glide()` | cubic-bezier(0.22, 0.78, 0.18, 1) | the one easing curve | Extracted |
| `CONTROL_MS` | 260 | thumb and hue settle | Extracted |
| `PAGE_MS` | 420 | full-distance page move | Extracted |
| `settleDuration(f, reduceMotion)` | 210 + 210 × f, at least 220, at most 420; `REDUCED_MS` when motion is reduced | landing after a drag; f = remaining share | Extracted |
| `DIALOG_MS` | 320 | dialog and sheet enter and exit | Extracted |
| `FEEDBACK_MS` | 220 | toast, row that slides aside, expander, banner | Extracted |
| `PRESS_MS` | 55 | press sink | Extracted |
| `THEME_FADE_MS` | 300, cubic-bezier(0.2, 0, 0, 1) | old palette fading over the new one | Extracted |
| `COMMIT_FRACTION` | 0.28 | a drag past this share of the travel commits | Extracted |
| `COMMIT_VELOCITY_DP` | 320 dp/s | a flick faster than this commits | Extended: the original used a fixed 850 px/s, which is 310 to 325 dp/s on common phones; Mallet scales with density |
| `REDUCED_MS` / `FEEDBACK_REDUCED_MS` | 120 / 90 | moving controls, pages and sheets / small feedback when motion is reduced; dialogs and the theme fade do not animate at all | Extracted |

Rules:

- **Follow, then glide.** While the finger is down the element is exactly under it. After
  release it glides to rest; the duration depends on the distance left.
- **One curve.** `glide()` starts fast and lands softly. No bounce, no overshoot, no spring.
- **No fade for things that move.** Pages, sheets, toasts and rows enter by travelling.
  Fades are for light (the theme change, scrims) and for dialogs, which also rise 11.5 dp
  and grow from 97.8 %.
- **Interruptible.** A touch during a landing takes over at once; whichever page is at least
  half way in becomes the current one.
- **Colour moves with position.** A track's bed blends between hues as the thumb travels, not
  after it arrives; a slider's stretch holds the hue nearest to the bead and switches at the
  half-way point between two hues, over a short fade.
- **Reduced motion** shortens every animation to a brief settle and removes decorative
  movement. It is on when the system has animations switched off, unless the theme says
  otherwise.

---

## 10. Iconography

`MalletIcon` is a set of 50 line icons drawn in code by `MalletIconPainter`. No image or
vector file is shipped, so an icon takes any colour and any size.

| Property | Value | Mark |
|---|---|---|
| Grid | 24 × 24, ink kept inside 2..22 | Extracted |
| Stroke | one weight per icon, round caps and joins; 2 dp at 24 dp | Extracted |
| Fills | none, except small dots | Extracted |
| Default colour | `muted`; `ink` for the chevron at the start of an entry row, whose page lies on that side | Extracted |
| Mirroring | chevrons and arrows left/right mirror in right-to-left layouts | New |

The original drew six glyphs (two chevrons, an arrow, the power ring, and the book, sliders
and "more" icons of the row of entries on its main page). They are kept with their
proportions; the other forty-four were drawn for Mallet in the same hand. Those three icons
were drawn 24 dp wide, a third larger than the rest, so on the common grid they are scaled
to three quarters and are as large as every other icon. The navigation row (`MalletNavBar`)
draws its icons in a 32 dp box, which gives them their original size, with a lighter 1.7 dp
stroke, 2 dp when selected.

The ink chevron of an entry row is not a way back: the page it points at lies on that side,
and the original has no back control on any page (principle 7). The navigation button of a
top bar (`MalletTopBar`) is drawn in `ink` as well. *(New)*

The power glyph's open ring (a 276° arc) doubles as the loading spinner.

Use icons sparingly. In this language a row does not need an icon to be a row, and a status
does not need an icon to be a status.

---

## 11. Theming

A view resolves its `MalletTheme` once, when it is constructed, in this order:

1. a context made with `MalletTheme.wrap(context, theme)`;
2. the process-wide `MalletTheme.resolver`, if it returns a theme;
3. the `mallet*` attributes of the Android theme;
4. the built-in light or dark palette, chosen by the current night mode.

With nothing configured, step 4 applies: Mallet views work in any app theme and follow the
system's dark mode.

### 11.1 From XML

Every role and every hue is a theme attribute. Set only the ones you want to change.

```xml
<style name="Theme.MyApp" parent="Theme.Mallet">
    <item name="malletColorAccent">#7A4B8F</item>
    <item name="malletHueCobalt">#9C8CF0</item>
    <item name="malletHueCobaltEdge">#7B69DA</item>
    <item name="malletTextScale">1.13</item>
    <item name="malletHighContrast">true</item>
    <item name="malletNightHue">0.3</item>
</style>
```

| Attribute | Type | Meaning |
|---|---|---|
| `malletIsDark` | boolean | force the dark or light palette |
| `malletHighContrast` | boolean | section 2.2 |
| `malletTextScale` | float | extra text scale |
| `malletReduceMotion` | `system` / `always` / `never` | section 9 |
| `malletHaptics` | boolean | ticks on committed changes |
| `malletNightHue` | float 0..1 | section 3.3 |
| `malletColorCanvas`, `…Surface`, `…Raised`, `…Ink`, `…Muted`, `…Hairline`, `…Divider`, `…Accent`, `…Danger`, `…Warning`, `…Success`, `…Shadow` | colour | section 2.1 |
| `malletHue<Name>`, `malletHue<Name>Edge`, `malletHueOn<Name>` | colour | section 3.1, for Bronze, Ochre, Cobalt, Jade, Mist |

`Theme.Mallet` (follows night mode), `Theme.Mallet.Light` and `Theme.Mallet.Dark` are
optional activity themes that also colour the window and the system bars.

### 11.2 From code

```kotlin
// One subtree with its own theme
val warm = MalletTheme.light().copy(
    hues = MalletHues.default().copy(cobalt = MalletHue(bed, edge, onBed)),
)
val view = MalletSegmentedTrack(MalletTheme.wrap(context, warm))

// The whole app, from your own settings
MalletTheme.resolver = MalletTheme.Resolver { context -> settings.toMalletTheme(context) }
```

Because a view reads its theme once, change the theme by rebuilding the views: recreate the
activity, and use `MalletThemeTransition` to fade the old appearance over the new one.

### 11.3 Making a palette of your own

- Keep canvas, surface and raised calm. In a light theme they may all be the same colour.
- A hue needs all three values. Pick the edge 10 to 15 % darker than the bed, and an on-bed
  colour that reads on it; test the pair in the dark theme, where only 30 % of it remains.
- Do not reuse a text colour (`accent`, `danger`) as the bed of a hue. The bed of a track
  sits under inner shading and needs a softer, lighter colour.
- Keep the order of the scale: the first hue must read as "least" and the last as "most".

---

## 12. Accessibility

### 12.1 Contrast

Measured with the WCAG 2 formula on the default palettes.

| Pair | Light | Dark |
|---|---|---|
| `ink` on canvas | 17.1 : 1 | 14.7 : 1 |
| `muted` on canvas | 3.2 : 1 | 7.4 : 1 |
| `muted` on canvas, high contrast | 8.4 : 1 | 10.9 : 1 |
| `accent` on canvas | 6.9 : 1 | 4.0 : 1 |
| `danger` on canvas | 4.2 : 1 | 3.2 : 1 |
| `warning` on canvas | 4.7 : 1 | 4.5 : 1 |
| `success` on canvas | 3.9 : 1 | 4.0 : 1 |
| `accent`, `danger`, `warning`, `success` on canvas and on a card, high contrast | at least 4.6 : 1 | at least 4.6 : 1 |
| white on `accent` capsule | 6.9 : 1 | 4.2 : 1 |
| white on `danger` capsule | 4.2 : 1 | 5.4 : 1 |

Resting labels on the bed of a track, by hue:

| Hue | Light | Light, high contrast | Dark | Dark, high contrast |
|---|---|---|---|---|
| Bronze | 3.0 : 1 | 4.6 : 1 | 6.4 : 1 | 8.0 : 1 |
| Ochre | 8.9 : 1 | 7.0 : 1 | 6.1 : 1 | 6.7 : 1 |
| Cobalt | 2.3 : 1 | 5.8 : 1 | 6.2 : 1 | 7.2 : 1 |
| Jade | 2.2 : 1 | 5.8 : 1 | 6.2 : 1 | 7.3 : 1 |
| Mist | 7.9 : 1 | 9.4 : 1 | 5.8 : 1 | 5.9 : 1 |

**Read this table before you ship.** The default light theme keeps the original's look:
white labels on cobalt, jade and bronze, and a light grey for secondary text. Those
pairs do **not** reach WCAG AA (4.5 : 1). The selected option is always `ink` on a white
bead (17 : 1) and the state is also carried by position, so no information depends on the
weak pairs alone, but small resting labels are hard to read for some people.

`malletHighContrast` brings every pair in the tables above to at least 4.5 : 1, in both
themes; this is checked by unit tests (`MalletFoundationTest`). When the theme comes from
the Android theme (not from `wrap` or the resolver) it switches on by itself when the user
has enabled high-contrast text in the system settings of Android 16 or later.
If your audience needs AA by default, set it in your theme.

### 12.2 Other commitments

- **Screen readers.** Every control reports a platform role (`Switch`, `CheckBox`,
  `RadioButton`, `SeekBar`, `Button`, `ProgressBar`) and its state. Controls with several
  zones expose each zone as its own element. Decorative views are hidden.
- **Keyboard and D-pad.** Controls with a direction answer arrow keys; controls with an
  action answer Space and Enter. A key that can do nothing is passed on, so focus is never
  trapped. Focus is visible (section 8).
- **Gestures have alternatives.** Whatever a swipe does can also be done by a tap, a key or
  an accessibility action: a row that slides aside offers its action as an accessibility
  action and from the keyboard, and a sheet has a dismiss action. A page is reached without
  a drag by a tap on what names it: an entry of Home's row, or a row that leads to a deeper
  page; Home's entries are focusable and operable with keys. The way back without a drag is
  the system's Back, which leads to the parent of the page (`MalletBack`). On a first-level
  page nothing on screen offers a way back, so for a keyboard, a switch or a screen reader
  the system's Back is the way (principle 7).
- **Touch targets** are at least 44 dp (section 5.4).
- **Text scale.** The unit tests draw every page of the showcase at the system font scale
  1.3 together with `textScale` 1.13; those pictures are judged by eye, not by assertion.
- **Right to left.** Start and end are respected everywhere, including the side on which a
  page lies, the order of Home's entries, the edge at which a row uncovers its action, and
  directional icons.
- **Motion.** Reduced motion is honoured (section 9). Nothing flashes, and nothing loops
  except the loading indicators.
- **Haptics** confirm committed changes only, and can be switched off in the theme.
