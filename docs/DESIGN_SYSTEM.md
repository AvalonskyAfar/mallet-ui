# Sancai design system

Sancai (三彩) is a design language for Android Views. It takes its name from the three-colour
glazed pottery of the Tang dynasty: a calm porcelain body on which colour appears only where
glaze was poured.

That is the whole idea. Pages, cards and text are neutral. Colour is reserved for **state**,
and it sits in the recessed bed of a control, never on the part you touch.

This document defines the tokens and rules. Every value is implemented in
`io.github.avalonskyafar.sancai.theme` and `...material`; nothing here exists only on paper.

Each rule carries one of three marks. They say where the rule comes from; the evidence for
each is in [DESIGN_DECISIONS.md](DESIGN_DECISIONS.md).

| Mark | Meaning |
|---|---|
| **Extracted** | Taken from the original application, values unchanged |
| **Extended** | An original idea generalised or completed |
| **New** | Designed for Sancai in the same language |

Contents: [Principles](#1-principles) · [Colour](#2-colour) · [Glazes](#3-glazes) ·
[Typography](#4-typography) · [Space and layout](#5-space-and-layout) · [Shape](#6-shape) ·
[Materials and depth](#7-materials-and-depth) · [States](#8-states) · [Motion](#9-motion) ·
[Iconography](#10-iconography) · [Theming](#11-theming) · [Accessibility](#12-accessibility)

---

## 1. Principles

1. **Colour is state, and it lives in the bed.** A switch that is off is terracotta; on, it is
   cobalt. A four-level control runs terracotta, ochre, cobalt, celadon. The thumb that
   travels over the bed is always the same neutral bead. *(Extracted)*
2. **Three materials, nothing else.** Porcelain (raised, neutral), glazed track (recessed,
   coloured), thumb (raised, neutral, inside a track). A fourth, the well, is a recessed
   neutral field for text and pictures. There are no flat tinted buttons, no ripples, no
   elevation overlays. *(Extracted)*
3. **Text is centred; arrows are not part of the text.** In rows, title and detail are
   centred on the row. A chevron is placed on its own at the edge and never pushes the text
   aside. It points to where the next page lies. *(Extracted)*
4. **Dividers run from rim to rim.** A hairline inside a card touches both edges of the card.
   Rows read as sections of one body, not as boxes in a box. *(Extracted)*
5. **Pressing sinks; it does not tint.** What you touch moves down 1.5 dp and comes straight
   back. Tracks and thumbs tighten their shadow. Nothing changes colour. *(Extracted)*
6. **Everything follows the finger, or glides.** Thumbs, pages, sheets and reveal rows move
   with the finger at finger speed. When released they settle along one easing curve.
   Nothing bounces, nothing fades in. *(Extracted)*
7. **Pages are places.** A page lies beside, above or below another one and is reached by
   dragging towards it. Navigation is movement through a map, not a stack of cards.
   *(Extracted)*
8. **Depth is painted, short and downward.** One hairline rim, one short soft shadow below,
   a trace of inner shading. Shadows never spread sideways into a glow. *(Extracted)*

---

## 2. Colour

### 2.1 Neutral and semantic roles

`SancaiColors` holds the roles. The light theme is white porcelain: canvas, surface and
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
(`SancaiColors.blend(canvas, ink, 0.14f)`), so it follows any custom palette.

One role departs from the original. Its light `yellow` was `#E0B33A`, a colour the app
declared but never drew; as text on white it reaches only 2.0 : 1. Sancai's `warning` is a
role for text and marks, so it is a deep ochre, `#9A6A14` (4.7 : 1), in the hue of the ochre
glaze.

### 2.2 High contrast

`SancaiTheme.highContrast` strengthens the weakest roles and the labels on glaze
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

In high contrast the label of a glazed capsule (primary, destructive) is whichever of white
and the theme's darkest neutral reads better on the tint (`theme.labelOnTint`).

### 2.3 Rules

- Neutral roles never carry state. A card is never tinted to say "selected" or "error".
- `accent`, `danger`, `warning` and `success` are for **text and marks**, and for the two
  glazed capsule buttons (primary and destructive). They are not track colours.
- Shadows in the light theme are tinted with `ink`, not black, so they stay cool and soft.
  In the dark theme they are pure black.

---

## 3. Glazes

A glaze is the colour poured into a recessed track. It is always a triple
(`SancaiGlaze`): the **bed**, a darker **edge** of the same hue for the one-dp rim, and the
**on-bed** colour for labels and marks resting on it.

### 3.1 The five glazes

| Glaze | Bed | Edge | On-bed | Reads as | Mark |
|---|---|---|---|---|---|
| Terracotta | `#CE8353` | `#BE6938` | `#FFFFFF` | off, none, stop | Extracted |
| Ochre | `#DEBE77` | `#C29E52` | `#2C2104` | gentle, some, caution | Extracted |
| Cobalt | `#86ACF7` | `#6990E0` | `#FFFFFF` | on, standard | Extracted |
| Celadon | `#80B897` | `#5B9774` | `#F4FFF9` | strict, full, good | Extracted |
| Mist | `#E2EDF7` | `#B7CCE0` | `#34485C` | a plain choice, no level | Extracted |

These are screen colours, tuned on a device for how they look under the track's inner
shading. They are deliberately softer than the semantic text colours of section 2 and must
not be swapped for them.

### 3.2 The scale

Options of an ordinal control take their glaze from position
(`SancaiGlazes.scale(count)`): *(Extracted)*

| Options | Glazes |
|---|---|
| 2 | terracotta, cobalt |
| 3 | terracotta, ochre, cobalt |
| 4 | terracotta, ochre, cobalt, celadon |
| 5 or more | as four, further options stay celadon *(Extended)* |

While a thumb travels, the bed blends continuously between the glazes of the two options it
is between. The control therefore answers "which option" by position and "how strong" by
colour at once.

For options that are not levels (a theme picker, a platform picker) pour one glaze under all
of them: `SancaiGlazes.uniform(count, glaze)`. Mist is the quiet default for that.
*(Extended: the original special-cased one theme picker.)*

### 3.3 Glaze at night

On the dark canvas a full-strength bed would glare, so the original drew every dark track as
one neutral groove (`#191E25`) and let only the labels keep a tint. That is safe but it gives
up the idea that colour is state exactly where a switch has no labels.

Sancai keeps a restrained share of the glaze in the groove: *(New)*

```
dark bed  = blend(groove, glaze.bed,  nightGlaze)      nightGlaze = 0.30 by default
dark edge = blend(blend(ink, canvas, 0.93), glaze.edge, nightGlaze)
```

| Glaze | Dark bed | Dark edge |
|---|---|---|
| Terracotta | `#4F3C33` | `#543E34` |
| Ochre | `#544E3E` | `#564E3C` |
| Cobalt | `#3A4964` | `#3B4A67` |
| Celadon | `#384C47` | `#374C46` |
| Mist | `#555C64` | `#525C67` |

`SancaiTheme.nightGlaze = 0f` (XML: `sancaiNightGlaze`) restores the original neutral groove.
Always paint through `theme.bedColor(glaze)` and `theme.bedEdgeColor(glaze)`.

### 3.4 Labels on glaze

`theme.labelOnBed(active, own, optionCount)` decides the colour of text and marks resting on
a bed.

- **Light.** Every resting label takes the on-bed colour of the glaze currently poured
  (white on terracotta, cobalt and celadon; deep brown on ochre; slate on mist). The label
  under the thumb is `ink` on the white bead. *(Extracted)*
- **Light, high contrast.** Resting labels switch to a deep tone of the glaze:
  `blend(edge, ink, 0.82)`. *(New)*
- **Dark.** Each label keeps a pale tint of its *own* option's glaze,
  `blend(own.bed, ink, 0.62)` for two options and `0.70` for more, so the scale stays
  readable inside the groove. *(Extracted)* High contrast raises the ink share to `0.86`.
  *(New)*

Contrast of resting labels against their bed is listed in section 12.1.

---

## 4. Typography

Sancai uses the system typeface of the device, in three weights: regular, medium
(`sans-serif-medium`) and bold. It ships no font. On a Chinese-language device the same roles
render in the system CJK face, which is how the language was first drawn.

### 4.1 The scale

`SancaiTextRole` names eleven roles. The original application wrote about twenty different sizes
at its call sites; the roles below keep its recurring values and fold the near-duplicates
into them. *(Extended)*

| Role | Size (sp) | Weight | Leading | Use |
|---|---|---|---|---|
| `DISPLAY` | 20.5 | medium | ×1.16 | onboarding and hero titles |
| `TITLE` | 20 | medium | ×1.16 | page title |
| `HEADLINE` | 18 | medium | ×1.16 | dialog title, article group title |
| `HEADING` | 17 | medium | ×1.16 | section heading above a card |
| `SUBHEAD` | 15.5 | medium | ×1.16 | row title, article sub-heading |
| `BODY` | 15.5 | regular | ×1.24 + 3 dp, tracking 0.01 | long-form copy |
| `BODY_COMPACT` | 14.5 | regular | ×1.28 + 3 dp, tracking 0.01 | dense article copy |
| `LABEL` | 14.5 | medium | ×1.16 | buttons and actions |
| `FOOTNOTE` | 13.5 | regular | ×1.16 | facts, step details, the label of a revealed action |
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
- **Titles and labels are medium, never bold.** Bold is kept for the small labels on glaze,
  where it has to hold against colour. *(Extracted)*
- **Only body text opens its leading.** Titles stay tight. *(Extracted)*
- **Paragraphs fill their lines.** Long copy uses the simple line breaker without
  hyphenation, and CJK closing punctuation (`，。！？；：、）】》”’`) is tied to the
  preceding character so it never starts a line. *(Extracted)*
- **Row and card text is centred**; headings above cards and article text are start-aligned.
  *(Extracted)*
- **Text scale.** Every size is multiplied by the system font scale and by
  `SancaiTheme.textScale`. The original offered one extra step, 1.13
  (`SancaiTheme.LARGE_TEXT_SCALE`). Controls shrink labels that no longer fit rather than
  clip them. *(Extracted; shrink-to-fit New)*

### 4.3 Tone

`SancaiTextTone` maps text to a colour role: `INK`, `MUTED`, `ACCENT`, `DANGER`, `WARNING`,
`SUCCESS`. A status line is a short coloured sentence, green when fine and red when not;
it needs no icon and no container. *(Extracted)*

---

## 5. Space and layout

### 5.1 Page rhythm

`SancaiSpacing` holds the vertical rhythm of a page, in dp. *(Extracted)*

| Token | dp | Meaning |
|---|---|---|
| `PAGE_SIDE` | 12 | page padding, left and right |
| `PAGE_TOP` | 22 | above the first element |
| `PAGE_BOTTOM` | 34 | below the last element |
| `TEXT_INSET` | 10 | extra inset of headings and loose text, aligning them with card content |
| `TITLE_GAP` | 16 | page title to first heading |
| `HEADING_GAP` | 10 | heading to its card |
| `SECTION_GAP` | 22 | card to next heading |
| `CARD_PADDING` | 9 | content padding of a card |
| `DIVIDER_GAP` | 5 | above and below a divider between row groups |
| `BUTTON_SIDE` | 30 | side margin of a full-width capsule on a page |

A page is a single scrolling column on the canvas: title, then heading and card, heading and
card. Cards span the page width; the one or two actions that apply to the whole page are
capsule buttons below the last card.

### 5.2 Rows

| Row | Minimum height (dp) | Mark |
|---|---|---|
| Entry row, title only | 62 | Extracted |
| Entry row, title and detail | 76 | Extracted |
| Control row, title only | 64 | Extracted |
| Control row, title and detail | 72 | Extracted |
| Key-value row | 52 | Extracted |
| Link row | 64 | Extracted |
| Dialog and sheet action | 54 / 56 | Extracted / New |
| Menu item | 48 | New |

In a control row the text column and the control share the width by weight, chosen from the
number of options so that every track gets room for its labels: 2 : 1 for two options,
1 : 3 for three, 1 : 4 for four; utility rows use 1 : 1.35. *(Extracted)*

### 5.3 Centring and independence

The title of a row is centred on the **row**, not on the space left over beside an icon or
chevron. Leading icons, trailing values and chevrons are positioned on their own, 12 dp from
the edge. Adding or removing one never moves the text. The same rule holds for the title of
the top bar and for the label of a link row. *(Extracted; top bar New)*

### 5.4 Touch targets

Interactive elements are at least 44 dp in their smaller dimension, either by their own
size or through the row that contains them. *(New)*

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
| Buttons, tracks, thumbs, toasts, chips, tags | capsule (half the height) | Extracted |

Rims are 0.85 to 1 dp. Dividers are 1 dp. Nothing in the system has a thicker line except
keyboard focus (2 dp, section 8).

---

## 7. Materials and depth

Android's elevation shadows are not used. Every rim, shadow and recess is painted by
`SancaiMaterial`, so the three materials look the same on every device and API level, and
your own views can use them.

### 7.1 Porcelain — `drawSurface`, `SancaiSurfaceDrawable`

Cards, capsule buttons, dialogs, toasts, menus. Layers, bottom to top: *(Extracted)*

Shadow figures in this section are the nominal parameters (offset / blur, tint and opacity)
that the painter approximates with stacked translucent shapes inside the gutter; the result
on screen is a little lighter than a free blur of the same numbers.

| Layer | Light | Dark |
|---|---|---|
| Outer lift | shadow 0 / 3.52 dp / blur 8 dp, `ink` at 9 % | four black shells, 10 to 25 alpha |
| Body | flat `surface` | vertical gradient: `surface` +2.8 % ink, `surface`, `surface` 15 % towards canvas |
| Inner depth | top shade 1.6 dp at 4 % ink, bottom shade at 5 % ink | none |
| Rim | 0.85 dp `hairline` | 0.85 dp gradient: white 26 %, hairline tone, shadow 31 % |

A **glazed body** (primary or destructive capsule) uses the dark recipe in both themes: a
three-stop gradient of the tint (+10 % white, tint, +5.5 % black in light) and a gradient
rim. *(Extracted for danger; primary New)*

Pressed porcelain tightens its lift to 0 / 1.8 dp / blur 4.6 dp at 8 %. *(Extracted values,
first used by Sancai's button)*

### 7.2 Glazed track — `drawTrack`

Segmented tracks, switches, steppers, sliders, progress. *(Extracted)*

| Layer | Value |
|---|---|
| Outer lift | shadow 0 / 6.08 dp / blur 12.8 dp, `ink` at 12 %; pressed 0 / 2.5 / 5.3 at 11 % |
| Bed | the glaze colour, painted as is; depth is laid over it, never mixed into it |
| Top inner shade | black 16 % (dark theme 27 %), fading over 3.2 dp + 2 × 5.12 dp |
| Bottom inner light | white 34 % (dark theme 15 %), fading over 2.4 dp + 2 × 4 dp |
| Rim | 1 dp in the glaze's edge colour |

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

### 7.4 Well — `drawWell`, `SancaiWellDrawable`

Text fields, editors, media frames. A neutral recess: a body slightly darker at the top, and
a rim that runs from a dark top edge to a light bottom edge. Replacing the rim with a solid
colour is how a field shows focus (`ink`) or an error (`danger`). *(Extracted; solid rims
New)*

### 7.5 Gutters

A view draws its own shadow **inside its own bounds**. The visible body is therefore inset
from the view by a gutter, and a view is a little larger than what the eye sees: *(Extracted)*

| Body | Sides | Top | Bottom |
|---|---|---|---|
| Porcelain | 1.4 dp | 1 dp | 4.8 dp |
| Track (60 dp view, 52 dp capsule) | 1.5 dp | 1.5 dp | 6.5 dp |
| Hero switch (92 dp view, 80 dp capsule) | 2.5 dp | 2 dp | 10 dp |

Shadows measure the room the canvas clip leaves them and fade to zero inside it, so they are
never cut off, whatever clips the parent.

### 7.6 What depth means

| Depth | Meaning |
|---|---|
| Raised (porcelain, thumb) | something that holds content or can be moved |
| Recessed and glazed (track) | a state; its colour is the value |
| Recessed and neutral (well) | a place to put text or a picture |
| Flat on the canvas | text, icons, navigation |

---

## 8. States

| State | Treatment | Mark |
|---|---|---|
| Pressed | operated content sinks 1.5 dp in 55 ms and returns at once on release; tracks and thumbs sink their content 1 dp and tighten their shadow; no colour change | Extracted |
| Focused (keyboard, D-pad) | the rim of the focused body thickens to 2 dp of `ink`, drawn inside the body; shown only outside touch mode | New |
| Disabled | the whole control is drawn at 42 % alpha; shape and layout do not change | New |
| Selected | position of the thumb plus the glaze of the bed; in lists, `ink` instead of `muted` | Extracted / New |
| Error | danger-coloured rim on a well, a danger line below it, or a danger capsule inside a dialog | Extracted / Extended |
| Loading | an open ring turning (the ring of the power glyph), or a glazed fill travelling in a groove; content waits in place, nothing covers the page | New, after the original's habit |
| Empty | nothing is drawn in place of the missing content except one centred action; no empty card | Extracted |

---

## 9. Motion

`SancaiMotion` holds the tokens.

| Token | Value | Use | Mark |
|---|---|---|---|
| `glide()` | cubic-bezier(0.22, 0.78, 0.18, 1) | the one easing curve | Extracted |
| `CONTROL_MS` | 260 | thumb and glaze settle | Extracted |
| `PAGE_MS` | 420 | full-distance page move | Extracted |
| `settleDuration(f)` | 210 + 210 × f, at least 220, at most 420 | landing after a drag; f = remaining share | Extracted |
| `DIALOG_MS` | 320 | dialog and sheet enter and exit | Extracted |
| `FEEDBACK_MS` | 220 | toast, reveal row, expander, banner | Extracted |
| `PRESS_MS` | 55 | press sink | Extracted |
| `THEME_FADE_MS` | 300, cubic-bezier(0.2, 0, 0, 1) | old palette fading over the new one | Extracted |
| `COMMIT_FRACTION` | 0.28 | a drag past this share of the travel commits | Extracted |
| `COMMIT_VELOCITY_DP` | 320 dp/s | a flick faster than this commits | Extended: the original used a fixed 850 px/s, which is 310 to 325 dp/s on common phones; Sancai scales with density |
| `REDUCED_MS` / `FEEDBACK_REDUCED_MS` | 120 / 90 | every duration when motion is reduced | Extracted |

Rules:

- **Follow, then glide.** While the finger is down the element is exactly under it. After
  release it glides to rest; the duration depends on the distance left.
- **One curve.** `glide()` starts fast and lands softly. No bounce, no overshoot, no spring.
- **No fade for things that move.** Pages, sheets, toasts and rows enter by travelling.
  Fades are for light (the theme change, scrims) and for dialogs, which also rise 11.5 dp
  and grow from 97.8 %.
- **Interruptible.** A touch during a landing takes over at once; whichever page is at least
  half way in becomes the current one.
- **Colour moves with position.** The bed blends between glazes as the thumb travels, not
  after it arrives.
- **Reduced motion** shortens every animation to a brief settle and removes decorative
  movement. It follows the system animator scale unless the theme says otherwise.

---

## 10. Iconography

`SancaiIcon` is a set of 50 line icons drawn in code by `SancaiIconPainter`. No image or
vector file is shipped, so an icon takes any colour and any size.

| Property | Value | Mark |
|---|---|---|
| Grid | 24 × 24, ink kept inside 2..22 | Extracted |
| Stroke | one weight per icon, round caps and joins; 2 dp at 24 dp | Extracted |
| Fills | none, except small dots | Extracted |
| Default colour | `muted`; `ink` for an arrow that leads back | Extracted |
| Mirroring | chevrons and arrows left/right mirror in right-to-left layouts | New |

The original drew six glyphs (two chevrons, an arrow, the power ring, and the book, sliders
and "more" marks of its navigation row). They are kept with their geometry; the other
forty-four were drawn for Sancai in the same hand. Navigation icons use a lighter 1.7 dp
stroke, 2 dp when selected.

The power glyph's open ring (a 276° arc) doubles as the loading spinner.

Use icons sparingly. In this language a row does not need an icon to be a row, and a status
does not need an icon to be a status.

---

## 11. Theming

A view resolves its `SancaiTheme` once, when it is constructed, in this order:

1. a context made with `SancaiTheme.wrap(context, theme)`;
2. the process-wide `SancaiTheme.resolver`, if it returns a theme;
3. the `sancai*` attributes of the Android theme;
4. the built-in light or dark palette, chosen by the current night mode.

With nothing configured, step 4 applies: Sancai views work in any app theme and follow the
system's dark mode.

### 11.1 From XML

Every role and every glaze is a theme attribute. Set only the ones you want to change.

```xml
<style name="Theme.MyApp" parent="Theme.Sancai">
    <item name="sancaiColorAccent">#7A4B8F</item>
    <item name="sancaiGlazeCobalt">#9C8CF0</item>
    <item name="sancaiGlazeCobaltEdge">#7B69DA</item>
    <item name="sancaiTextScale">1.13</item>
    <item name="sancaiHighContrast">true</item>
    <item name="sancaiNightGlaze">0.3</item>
</style>
```

| Attribute | Type | Meaning |
|---|---|---|
| `sancaiIsDark` | boolean | force the dark or light palette |
| `sancaiHighContrast` | boolean | section 2.2 |
| `sancaiTextScale` | float | extra text scale |
| `sancaiReduceMotion` | `system` / `always` / `never` | section 9 |
| `sancaiHaptics` | boolean | ticks on committed changes |
| `sancaiNightGlaze` | float 0..1 | section 3.3 |
| `sancaiColorCanvas`, `…Surface`, `…Raised`, `…Ink`, `…Muted`, `…Hairline`, `…Divider`, `…Accent`, `…Danger`, `…Warning`, `…Success`, `…Shadow` | colour | section 2.1 |
| `sancaiGlaze<Name>`, `sancaiGlaze<Name>Edge`, `sancaiGlazeOn<Name>` | colour | section 3.1, for Terracotta, Ochre, Cobalt, Celadon, Mist |

`Theme.Sancai` (follows night mode), `Theme.Sancai.Light` and `Theme.Sancai.Dark` are
optional activity themes that also colour the window and the system bars.

### 11.2 From code

```kotlin
// One subtree with its own theme
val warm = SancaiTheme.light().copy(
    glazes = SancaiGlazes.default().copy(cobalt = SancaiGlaze(bed, edge, onBed)),
)
val view = SancaiSegmentedTrack(SancaiTheme.wrap(context, warm))

// The whole app, from your own settings
SancaiTheme.resolver = SancaiTheme.Resolver { context -> settings.toSancaiTheme(context) }
```

Because a view reads its theme once, change the theme by rebuilding the views: recreate the
activity, and use `SancaiThemeTransition` to fade the old appearance over the new one.

### 11.3 Making a palette of your own

- Keep canvas, surface and raised calm. In a light theme they may all be the same colour.
- A glaze needs all three values. Pick the edge 10 to 15 % darker than the bed, and an on-bed
  colour that reads on it; test the pair in the dark theme, where only 30 % of it remains.
- Do not reuse a text colour (`accent`, `danger`) as a bed. Beds sit under inner shading and
  need softer, lighter colours.
- Keep the order of the scale: the first glaze must read as "least" and the last as "most".

---

## 12. Accessibility

### 12.1 Contrast

Measured with the WCAG 2 formula on the default palettes.

| Pair | Light | Dark |
|---|---|---|
| `ink` on canvas | 17.1 : 1 | 14.7 : 1 |
| `muted` on canvas | 3.2 : 1 | 7.4 : 1 |
| `muted` on canvas, high contrast | 8.4 : 1 | 11.0 : 1 |
| `accent` on canvas | 6.9 : 1 | 4.0 : 1 |
| `danger` on canvas | 4.2 : 1 | 3.2 : 1 |
| `warning` on canvas | 4.7 : 1 | 4.5 : 1 |
| `success` on canvas | 3.9 : 1 | 4.0 : 1 |
| `accent`, `danger`, `warning`, `success` on canvas and on a card, high contrast | at least 4.6 : 1 | at least 4.6 : 1 |
| white on `accent` capsule | 6.9 : 1 | 4.2 : 1 |
| white on `danger` capsule | 4.2 : 1 | 5.4 : 1 |

Resting labels on a glazed bed:

| Glaze | Light | Light, high contrast | Dark | Dark, high contrast |
|---|---|---|---|---|
| Terracotta | 3.0 : 1 | 4.6 : 1 | 6.4 : 1 | 8.0 : 1 |
| Ochre | 8.9 : 1 | 7.0 : 1 | 6.1 : 1 | 6.7 : 1 |
| Cobalt | 2.3 : 1 | 5.8 : 1 | 6.2 : 1 | 7.2 : 1 |
| Celadon | 2.2 : 1 | 5.8 : 1 | 6.2 : 1 | 7.3 : 1 |
| Mist | 7.9 : 1 | 9.4 : 1 | 5.8 : 1 | 5.9 : 1 |

**Read this table before you ship.** The default light theme keeps the original's look:
white labels on cobalt, celadon and terracotta, and a light grey for secondary text. Those
pairs do **not** reach WCAG AA (4.5 : 1). The selected option is always `ink` on a white
bead (17 : 1) and the state is also carried by position, so no information depends on the
weak pairs alone, but small resting labels are hard to read for some people.

`sancaiHighContrast` brings every pair in the tables above to at least 4.5 : 1, in both
themes; this is checked by unit tests (`SancaiFoundationTest`). It switches on by itself
when the user has enabled high-contrast text in the system settings of Android 16 or later.
If your audience needs AA by default, set it in your theme.

### 12.2 Other commitments

- **Screen readers.** Every control reports a platform role (`Switch`, `CheckBox`,
  `RadioButton`, `SeekBar`, `Button`, `ProgressBar`) and its state. Controls with several
  zones expose each zone as its own element. Decorative views are hidden.
- **Keyboard and D-pad.** Controls with a direction answer arrow keys; controls with an
  action answer Space and Enter. A key that can do nothing is passed on, so focus is never
  trapped. Focus is visible (section 8).
- **Gestures have alternatives.** Whatever a swipe does can also be done by a tap, a key or
  an accessibility action: a reveal row offers its action directly, a page host answers
  Back, a sheet has a dismiss action.
- **Touch targets** are at least 44 dp (section 5.4).
- **Text scale.** Layouts are tested at the system font scale 1.3 and `textScale` 1.13.
- **Right to left.** Start and end are respected everywhere, including the direction pages
  lie in, the side a row reveals from, and directional icons.
- **Motion.** Reduced motion is honoured (section 9). Nothing flashes, and nothing loops
  except the loading indicators.
- **Haptics** confirm committed changes only, and can be switched off in the theme.
