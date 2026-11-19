# Design decisions and provenance

Mallet UI grew out of one application. This document says, component by component, what was
**extracted** from it, what was **extended**, and what is **new**, and it records the reasons
for every place where Mallet deliberately differs from its source.

| Mark | Meaning |
|---|---|
| **Extracted** | Design and numbers taken from the original; re-implemented in Kotlin |
| **Extended** | An original idea, generalised or completed |
| **New** | Absent from the original; designed for Mallet from its materials and rules |

A row of the tables in section 2 carries the one mark that fits the part as a whole; its
last two columns say what was kept and what was changed or added. A decision can mix
origins, and then it says which of its parts is which.

"Origin" cites the original source as `File.java:lines`, relative to
`Gravekeeper/app/src/main/java/com/gravekeeper/` in the original repository. The original
names its parts with internal codes (L1 canvas, S1 card, K1 track, K3 thumb, K4 capsule,
K5 compact switch, K8 stepper, D1 divider, M1 overlay material); they appear here only to
help anyone who reads that source. Some decisions also cite the app's own records,
`UI_RENDER_FIDELITY_AUDIT_2026-08-12.md` and `UI_FROZEN_IMPLEMENTATION_MAP.md`, which lie in
the `Gravekeeper/` folder of that repository.

Contents: [What the original was](#1-what-the-original-was) ·
[Provenance of every part](#2-provenance-of-every-part) · [Decisions](#3-decisions) ·
[What was left behind](#4-what-was-left-behind)

---

## 1. What the original was

The source is a single-purpose Android app with an unusually deliberate interface:

- **No XML layouts.** Every screen is built in Java by one 2,900-line class, `UiKit`, tied to
  an `Activity` and to the app's preferences. Nothing could be inflated, previewed or reused
  outside that app.
- **A complete visual idea, incompletely stated.** White canvas; cards with one hairline and
  one short shadow; recessed capsule tracks whose *bed colour* is the state under a neutral
  thumb; centred text with independently placed chevrons; rim-to-rim dividers. The idea is
  consistent, but it exists as literals at call sites: about twenty text sizes, no named
  spacing, colours chosen by counting labels.
- **A real interaction model.** Pages arranged in space and dragged at finger speed, with
  careful arbitration between page, scroll view and track, interruption handling, and
  snapshot-based transitions between activities.
- **No navigation marks, and that is design.** The main page names the pages around it once,
  in a row of three entries at its bottom, and shows nothing else that points anywhere: no
  arrow, no page name at an edge, no hint. No page at any depth has a back control: a page
  is left by dragging it back the way it came, or with the system's Back. A page without a
  button to tap is how the drag becomes a habit. This is deliberate and does not belong to
  the gaps below; see [D-23](#d-23).
- **Gaps.** One button variant in use; no text-field component, checkbox, radio, slider,
  menu, sheet, tab, app bar, progress or loading indicator; no disabled, focused or keyboard
  state anywhere; only content descriptions, no roles or actions, for screen readers on the
  custom controls; no right-to-left support; strings and product copy inside views; minimum
  Android 11; phone portrait only.

Mallet keeps the idea and the interaction model, states them as tokens and rules, makes the
parts usable from XML and code by any app, and fills the gaps in the same language.

---

## 2. Provenance of every part

### 2.1 Foundation

| Mallet | Mark | Origin | Kept | Changed or added |
|---|---|---|---|---|
| `MalletColors` | Extracted | `UiKit.java:82-115`, `460-465` | every neutral and semantic value, light and dark, and the high-contrast variants of muted, hairline and divider | role names; `warning` darkened in light ([D-07](#d-07)); semantic roles strengthened in high contrast ([D-08](#d-08)); `contrast()` |
| `MalletHue`, `MalletHues` | Extracted | `UiKit.java:54-66`, `2541-2609` | the five bed and edge colours; label colours on each bed; the 2 / 3 / 4-option scale | hues are data instead of label-count logic ([D-02](#d-02)); `uniform()`; mist as a general neutral hue; the names bronze, ochre, cobalt, jade and mist, where the original names its track colours by level ([D-26](#d-26)) |
| `MalletTheme` | Extended | `UiKit.java:126-142`, `196-212`; `AppPreferences.java` | text scale 1.13, high contrast, reduced motion, haptics as theme flags | resolution from context, resolver or XML attributes ([D-01](#d-01)); night hue ([D-03](#d-03)); system settings as defaults |
| `MalletTextRole`, `MalletTextTone` | Extended | `UiKit.java:284-315`, `ReadingLayoutSpec.java` | recurring sizes, medium weight for titles, no font padding, body leading | eleven named roles instead of about twenty literals ([D-05](#d-05)) |
| `MalletSpacing` | Extracted | `UiKit.java:267`, `329-334`, `MainActivity.java:402-460`, `1217-1227` | page padding 12 / 22 / 34, heading and section gaps, card padding 9 | names |
| `MalletMotion` | Extracted | `MotionSpec.java`, `UiKit.java:263-265`, `1071`, `1324`, `1459-1464`, `1622-1635`, `res/interpolator/gk_spatial.xml` | the curve, 260 / 420 / 320 / 220 / 55 ms, the 28 % commit rule, the settle formula | one settle formula and a density-based flick threshold ([D-06](#d-06)) |
| `MalletMaterial` | Extracted | `UiKit.java:1663-1937` | layer order, gradient stops, alphas, stroke widths, gutters of card, track and thumb, in both themes | one public painter instead of private statics; focus rim; the pressed state of a raised surface; repaint without lift; a bed under a sliding row that takes the corners of its cell, and the short shade the row casts on it ([D-24](#d-24)) |
| `MalletSurfaceDrawable`, `MalletWellDrawable` | Extracted | `UiKit.java:1939-2046`, `2185-2242` | card, capsule, dialog and well materials | split-corner cards from `2330-2448` folded in; solid rims for focus and error |
| `MalletIcon`, `MalletIconPainter` | Extended | `UiKit.java:847-927`, `2823-2831`; `MainActivity.java:357-399` | six glyphs: chevron, arrow, power ring, book, sliders, more; the last three are the marks of the original's row of entries; chevron and arrow only point forward there, to the page a row or an action opens | forty-four further icons drawn for Mallet, among them three of the four on the showcase's Home (palette, grid, hand; [D-23](#d-23)); drawable and view; RTL mirroring ([D-10](#d-10)) |
| `MalletWindow` | Extracted | `UiKit.java:214-282` | canvas-coloured bars, light bar icons, insets as padding, refresh-rate preference | works from API 23; edge to edge on every version |

### 2.2 Controls

| Mallet | Mark | Origin | Kept | Changed or added |
|---|---|---|---|---|
| `MalletSegmentedTrack` | Extracted | `UiKit.java:506`, `2450-2720` | geometry (60 dp view, 52 dp capsule, 4 dp inset), finger-following thumb, hue blending, label rules, vertical abort, listener-then-haptic | any number of options, any hues; XML; RTL; keys; saved state; one accessibility element per option; disabled; shrink-to-fit labels |
| `MalletSwitch` | Extracted | `UiKit.java:525-684`, `WhitelistAccountsPage.java:234` | 61 × 34 dp, thumb and travel, tap and drag | hue properties; RTL; keys; saved state; `Switch` role |
| `MalletPowerSwitch` | Extracted | `UiKit.java:2722-2930`, `MainActivity.java:287` | 92 dp view, 80 dp capsule, thumb width rule, glyph and two labels that flip at mid-travel, deep shadow | labels as properties instead of product copy ([D-11](#d-11)); hues; RTL; keys; saved state |
| `MalletStepper` | Extended | `UiKit.java:686-793`, `DeveloperOptionsActivity.java:548-592` | three zones 1 : 1.35 : 1, fixed centre thumb with the value, marks on the bed | owns its value, range and step; hold to repeat; paled marks at the bounds; edit request; `SeekBar` role |
| `MalletButton` | Extended | `UiKit.java:977-989`, `1065-1084` | capsule, padding 22 × 13 dp, 14.5 sp medium, sink on press | primary, destructive and text variants; icon; loading; disabled; focus ([D-12](#d-12)) |
| `MalletCheckbox`, `MalletRadioButton`, `MalletRadioGroup` | New | — | — | a small bed that fills with its hue; see [D-13](#d-13) |
| `MalletSlider` | New | — | — | a slim track coloured from the start to a neutral bead; see [D-14](#d-14) |
| `MalletProgressBar`, `MalletSpinner` | New | the ring of `UiKit.java:2823-2831` | the 276° open ring | see [D-15](#d-15) |
| `MalletTextField` | Extended | `UiKit.java:1488-1505`, `WhitelistAccountsPage.java:89-109` (13 × 11 dp, 15 sp); `ConfigEditorActivity.java:68-79` (well only) | the recessed well, 13 × 11 dp padding, label above | text on the scale (15.5 sp instead of 15); one component with label, helper, error, counter, end actions, focus and error rims |
| `MalletChip`, `MalletChipGroup`, `MalletTag`, `MalletBadge` | New | — | — | neutral capsules that fill with their hue when checked; coloured status tags; see [D-16](#d-16) |

### 2.3 Layout and reading

| Mallet | Mark | Origin | Kept | Changed or added |
|---|---|---|---|---|
| `MalletSurface`, `MalletDivider` | Extracted | `UiKit.java:367-377`, `460-465`; `MainActivity.java:1221-1227` | card material, 9 dp padding, rim-to-rim divider at 14 % ink; a card that groups the rows of one headed section | the card stretches dividers itself instead of callers using negative margins, and stretches a row that slides aside over its cell ([D-24](#d-24)); flat and split-corner variants; a lone control gets no card of its own, where the original gives it one ([D-25](#d-25)) |
| `MalletPage` | Extracted | `UiKit.java:269-282`, `329-365` | page padding, scroll configuration, insets as padding | XML children; keyboard inset; saved scroll position; a column no wider than 560 dp; content that ends at the system bars; a page that opens at its top ([D-22](#d-22)) |
| `MalletHeading` | Extracted | `UiKit.java:296-315` | 17 sp title, 12.5 sp muted detail, 10 dp inset | page-title level |
| `MalletEntryRow` | Extended | `UiKit.java:800-845` | 62 / 76 dp, centred title and detail, independent chevron, content-only sink | start alignment, leading icon, trailing value, RTL, focus |
| `MalletControlRow` | Extracted | `UiKit.java:467-508` | centred text column, weights 2 : 1, 1 : 3, 1 : 4 and 1 : 1.35, heights 64 / 72 dp | any control, not only a track |
| `MalletLinkRow` | Extracted | `UiKit.java:929-975` | 64 dp, muted medium label, chevron at the edge or beside the label; the form with the chevron at the edge is in the original's code (`929-952`), but no page there calls it | label on the scale (14.5 sp instead of 15); tone; RTL |
| `MalletKeyValueList` | Extracted | `UiKit.java:1013-1063` | two fixed columns 1.22 : 1, 52 dp rows, non-interactive | tone per value instead of matching literal strings ([D-02](#d-02)) |
| `MalletMediaFrame` | Extracted | `UiKit.java:409-428`, `1162-1196` | recessed well, 4 dp inset, clipped image | aspect ratio; empty state |
| `MalletParagraph` | Extracted | `UiKit.java:1002-1011`; `MainActivity.java:700-729` | body leading, simple line breaking, CJK closing punctuation kept with its line | a reusable view |
| `MalletCallout`, `MalletFlowRow` | Extracted | `MainActivity.java:771-781`, `905-923`, `931-970` | 3 dp accent bar; flat step sequence with arrows | tones; RTL |

### 2.4 Navigation and movement

| Mallet | Mark | Origin | Kept | Changed or added |
|---|---|---|---|---|
| `MalletSpatialGraph` | Extended | `MainActivity.java:1867-1892`, `1603-1626` | the idea that pages lie on sides of one another and that Back follows the map | a declarative graph instead of a hard-coded `switch` ([D-04](#d-04)) |
| `MalletSpatialHost` | Extracted | `MainActivity.java:1628-2136`, `220-259` | axis lock, edge zones, both pages moving together, 28 % and flick commit, settle duration, interruption by adopting the nearer page, page cache, neighbour warm-up timings, hand-over from scrolling content | generic for any graph; hand-over at the bottom of scrolling content as well as at its top; pagers, sliding rows and sideways lists keep a sideways drag they can use; RTL; saved state; listeners |
| `MalletBack` | Extended | `BackNavigation.java` | Back handled by the page map | predictive back preview on Android 14+ ([D-17](#d-17)) |
| `MalletSpatialTransition` | Extracted | `UiKit.java:1090-1160`, `1198-1412`; `res/anim/*` | snapshot token, entry glide, interior swipe back, exclusions, XML fallback | one settle formula ([D-06](#d-06)); RTL; first-finger tracking |
| `MalletThemeTransition` | Extracted | `UiKit.java:144-194`; `MainActivity.java:1278-1289` | capture before recreation, 300 ms fade, delay until the control has settled | bounded store; survives undrawable content |
| `MalletPress` | Extracted | `UiKit.java:1065-1084` | 1.5 dp sink in 55 ms, instant return before the click | usable on any view |
| `MalletHaptics` | Extended | `UiKit.java:1086-1088` | tick on commit, honouring the preference | detent, confirm, reject, long press |
| `MalletHorizontalDragOwner` | Extended | `MainActivity.java:1841-1860`; `UiKit.java:1381-1397` | controls keep horizontal drags that start on them | a marker interface instead of `instanceof` lists of the four control classes |
| `MalletRevealLayout` | Extended | `WhitelistAccountsPage.java:206-280`; `UiKit.java:2244-2328` | 78 dp strip, 42 % threshold, the durations (220 ms, 90 ms with reduced motion), white centred label, the recessed paint of the bed, a tap closes, a switch in the row keeps its gesture | the bed starts at the rim of the card instead of inside its padding, a departure made on purpose ([D-24](#d-24)); either edge; an icon above the label; tones; sibling closing; accessibility and keyboard access |
| `MalletPager`, `MalletPageIndicator` | Extracted | `FirstLaunchActivity.java:263-277`, `330-543` | finger-following pages, 22 % / 0.45-width commit, linked heading, dots | adapter; RTL; saved state; blending dots |
| `MalletExpandableLayout` | New | — | — | see [D-18](#d-18) |
| `MalletNavBar` | Extended | `MainActivity.java:291-400` | the row of entries at the bottom of the original's main page: 72 dp tall, a muted outline icon above a bold 12.5 sp label, on the canvas with no bar behind it, nothing selected, a press that sinks. This is launcher mode, and it is what the showcase's Home uses ([D-23](#d-23)) | any number of entries; labels that shrink together, and give enlarged text back, before one of them is cut ([D-23](#d-23)); a selected state for tab use ([D-19](#d-19)); badges; keys; RTL |
| `MalletTopBar`, `MalletTabs` | New | — | — | see [D-19](#d-19) |

### 2.5 Overlays and states

| Mallet | Mark | Origin | Kept | Changed or added |
|---|---|---|---|---|
| `MalletDialog` | Extracted | `UiKit.java:1414-1428`, `1474-1640` | card radius 16, padding, centred title and message, full-bleed divider, 54 dp text actions, dim 0.27, width rule, 320 ms rise, closing guard, in-dialog error capsule | builder; one to three actions; stacking; scrolling message; input variant |
| `MalletToast` | Extended | `UiKit.java:1414-1472` | capsule, rise of 18 dp, 2600 ms, replacement rule, no fade | text on the `FOOTNOTE` role (13.5 sp instead of 13); success tone; one action; insets; announcement |
| `MalletFloatingPanel` | Extracted | `StatusOverlayController.java`; `UiKit.java:2048-2183` | draggable raised panel, radius 10 | usable inside any window; clamping; saved position |
| `MalletMenu`, `MalletBottomSheet` | New | — | — | see [D-20](#d-20) |
| `MalletStateView`, `MalletStateLayout`, `MalletSkeleton`, `MalletBanner` | New | habits in `MainActivity.java:211-217`, `1172-1195`; `WhitelistAccountsPage.java:57-87` | loading is a calm line in place; an empty list draws nothing but its one action; a failure says what happened | see [D-21](#d-21) |

---

## 3. Decisions

Each entry states a place where Mallet differs from its source, or adds to it, and why.
D-23 to D-26 were made when the first version was revised; of these, D-23 takes back
something the first version had added, and returns to the source.

### D-01
**Views resolve a theme from their context; nothing is tied to an Activity.**
The original `UiKit` needed an `Activity` and read one app's preferences, so its controls
could not be inflated from XML, previewed, or used by another app. A Mallet view asks
`MalletTheme.of(context)`, which looks at an explicit wrapper, then an app-supplied resolver,
then theme attributes, then night mode. With no setup at all a view still works.

### D-02
**Meaning is passed in as data, never inferred from content.**
The original chose track colours by counting labels, recognised its theme picker by matching
three Chinese strings, and coloured diagnostic values by comparing them with literals such as
"已开启". That welds components to one app and one language. In Mallet the hues of a track
are a list, a neutral choice says so (`uniform`), and a value carries its tone explicitly.

### D-03
**Dark tracks keep 30 % of their hue.**
The original drew every dark track as the same neutral groove and left state to thumb
position and tinted labels. That is restrained, but it gives up the central idea, colour as
state, exactly where a control has no labels: a dark switch looked the same on and off
except for the bead. Blending 30 % of the bed colour into the groove keeps the scale visible
without glare; label contrast on those beds is 5.5 : 1 or better. Setting `nightHue` to 0
restores the original.

### D-04
**The page map is declared, not coded.**
The original host knew its nine pages by name. `MalletSpatialGraph` states which page lies on
which side of which, and which pages are only opened from rows (`child`). Back follows the
graph. The gesture logic is unchanged; only its knowledge of the pages moved into data.

### D-05
**Eleven type roles replace about twenty sizes.**
Sizes within half a point of one another (19.2 and 20; 11.5, 11.8 and 12; 13 and 13.5; 14,
14.5 and 15)
were merged into the nearest recurring value. Only the label sizes that belong to the
geometry of the two signature controls stay inside them: 12.2 and 11.2 in the segmented
track, 14 in the power switch. The merge moves some text by up to half a point (the field
text from 15 to 15.5, the link row from 15 to 14.5, the detail of an entry row from 11.8 to
11.5); that is a deliberate trade of exact fidelity for a scale people can remember.

### D-06
**One settle formula, one flick threshold.**
The original used `210 + 210 x remaining` (220 to 420 ms) for its page host and
`max(130, 90 + 330 x remaining)` for activity swipe-back and sub-pages, and flick thresholds
of 850 and 620 px/s. The differences were incidental. Mallet uses the first formula
everywhere and a threshold of 320 dp/s. At the density of the original's screenshots (2.75)
850 px/s is 309 dp/s, so the feel is the same there, and the threshold now scales with
density instead of being easier to reach on sharper screens. The pager keeps its own, earlier
commit point (22 % of the width) because paging is the main gesture of such a screen.

### D-07
**`warning` is a deep ochre in the light theme.**
The original light `yellow` (#E0B33A) was declared and never drawn. As text on white it
reaches 2.0 : 1. Mallet uses the role for caution text and marks, so it is #9A6A14
(4.7 : 1), of the same colour family as the ochre hue. The dark value is unchanged.

### D-08
**High contrast reaches AA everywhere; the default keeps the original look.**
White labels on cobalt (2.3 : 1), jade (2.2 : 1) and bronze (3.0 : 1) and the grey
secondary text (3.2 : 1) are part of the visual identity and fail WCAG AA. Changing them
would change the design the project exists to preserve; leaving them unfixed would make the
library unusable for teams with accessibility requirements. So the default stays, the
shortfall is documented in the design system, and `highContrast` raises every text role and
every on-hue label to at least 4.5 : 1, verified by unit tests. It follows the system
high-contrast text setting on Android 16 and later.

### D-09
**Focus is a thicker rim; disabled is paler.**
The original had neither state. A focus ring outside the body would need room the layout
does not have and would look foreign next to hairlines, so focus thickens the existing rim
to 2 dp of ink, inside the body. Disabled draws the whole control at 42 % alpha: the hue
pales, shape and layout stay, and no separate grey palette is needed.

### D-10
**Icons are drawn in code, in one hand.**
Six glyphs existed. A toolkit needs more, and importing an icon set would add a second
visual voice and a licence. The forty-four new icons follow the originals: 24-unit grid,
one stroke weight, round caps and joins, no fills. Chevrons and arrows mirror in
right-to-left layouts. The three marks of the original's row of entries were drawn 24 dp
wide, where every other glyph keeps its ink inside 20 units; they are scaled to three
quarters so that one grid holds for all fifty, and the navigation row (`MalletNavBar`) draws
icons in a 32 dp box to give them their first size back.

### D-11
**No product copy inside components.**
The original power switch drew "已开启", "开启保护" and so on itself. Labels are properties
with neutral defaults in string resources (English and Simplified Chinese).

### D-12
**Buttons: one material, four voices.**
Only the neutral capsule was in use; the destructive tint existed in the painter but was
reached only by error messages. Mallet adds a primary capsule (the same recipe for a
coloured body, with the accent colour), exposes the destructive one, and adds a text
variant, which is what the dialog actions already were. Loading reuses the open ring. A
press sinks the label and tightens the shadow, using pressed-shadow values that were present
in the original painter but unused.

### D-13
**Checkbox and radio: a small bed that fills with its hue.**
The original expressed every choice as a track. For multiple selection and long option
lists that does not scale. A 26 dp well that turns into a coloured bed when chosen is the
smallest form of "colour is state"; the radio carries a small neutral bead, the checkbox a
check mark drawn as a stroke.

### D-14
**Slider: hue up to the bead.**
Continuous values needed dozens of stepper taps or a dialog. The slider is a slim groove
whose stretch from the start to the bead is coloured, so the amount of colour is the value.
It can run through the hue scale as the value rises: the stretch wears the hue nearest to
the bead, and the next one replaces it when the bead passes half-way. Blending two hues by
position was tried first and dropped, because ochre and cobalt mix to grey and a slider can
come to rest there.

### D-15
**Loading borrows the power ring.**
The spinner is the 276° open ring of the power glyph, turning. Determinate progress is a
coloured fill growing in a groove. Both are quiet enough to sit inside a card, where the
original put its loading text; nothing covers the page.

### D-16
**Chips and tags.**
A chip is a small neutral capsule that fills with its hue when checked, the button and the
track in one. A tag is a miniature coloured capsule without a shadow, for status in lists,
replacing the coloured text chosen by string matching.

### D-17
**The system back gesture previews the move.**
On Android 14 and later a back swipe drives the same movement as a finger inside the page,
up to 40 % of the way, so the two gestures look alike. On the root page the callback is
unregistered and the system animates back-to-home itself.

The system's Back is also the way back of a first-level page. Such a page shows no way back
of its own ([D-23](#d-23)): it is left by dragging back towards Home, or with Back. For a
keyboard, a switch or a screen reader the way back is the system's Back; nothing on screen
offers it.

### D-18
**Expanding is uncovering.**
The body grows from zero height while staying anchored at its top edge and clipped, as if a
cover slid away; content below is pushed. No fade and no scale, in keeping with "things that
move do not fade".

### D-19
**Navigation stays quiet.**
The original navigation is a row of three entries at the bottom of its main page: muted
outline icons above their labels, standing on the canvas itself, with no bar and no selected
state, because each entry is a shortcut to a page that lies beside or below. That row is
`MalletNavBar` in launcher mode, and it is the row the showcase's Home uses, with one entry
for each first-level page ([D-23](#d-23)). For apps that need tabs, the selected item is
simply drawn in ink. The top bar has no shadow and centres its title independently of its
buttons, as rows do. Its navigation button is for apps built as a stack and for separate
activities; a page that is a place on a map, Home or a first-level page, has no bar. Text
tabs mark the selection with a short accent capsule under the label. Hue is not used for
navigation: hue means the state of a value, and the current page is not a value.

### D-20
**Menus and sheets are raised surfaces.**
A menu is a small raised panel (radius 10, as the original floating panel) that grows
from its anchor; items sink when pressed. A sheet is a dialog card attached to the bottom
edge with only its top corners rounded, following the finger down; its shadow falls
downward like every other shadow, so separation from the page comes from the scrim and the
rim.

### D-21
**Empty, loading and error keep the original restraint.**
An empty area shows an outline icon, a line or two and at most one action, on the canvas,
never inside an empty card. [D-25](#d-25) says the same of controls: a card is a group, and
a lone control joins one or stands on the canvas, without a card of its own. A skeleton is a
recessed neutral block with a slow light passing over it, and all skeletons on a screen
share one sweep. A banner is a flat card with the accent bar of the callout; it pushes
content down rather than covering it.

### D-22
**A page keeps the system bars clear, and opens at its top.**
The original pads its root for the system bars on Android 15 and later and lets the system
draw opaque bars before that. Mallet draws behind the bars on every version, so a page has
to decide what happens there. Three things were found by running the showcase on devices
and are part of `MalletPage`: content that scrolls ends at the status bar, softened over
10 dp, instead of sliding under the clock where neither can be read (under the thin handle
of gesture navigation it shows through, because that covers almost nothing); a page asks
for the window insets whenever it is attached, because Android hands them down only when
they change and a page that a host attaches later would otherwise lie under the bar; and a
page does not give the focus to a text field merely because a window opened, which on
Android 7 and earlier scrolled a page to its first field and raised the keyboard. The
column is also held to 560 dp and centred, so the centred rows of the language do not
stretch across a tablet.

### D-23
**Pages carry no navigation marks.**
Mark: **Extracted**, in part **Extended**; the way-back row of the showcase is **New**.

Home is the root page of the map. A first-level page lies directly beside, above or below
Home. A deeper page is opened from a row of a first-level page, or lies further out.

**Rule.** A place carries no signpost. Home shows nothing that points at its neighbours: no
arrows, no page names at the edges. It names the pages around it once, in a row of entries
at its bottom (an outline icon above a short label, one entry for each first-level page). An
entry can be tapped; the page it names is also reached by dragging Home towards the side on
which that page lies. A first-level page shows no way back: no back row, no arrow, no bar.
It is left the way it was reached, by dragging back towards Home, or with the system's Back.

**Why.** A page without a button to tap is how the drag becomes a habit. This is the
original app's own design and it is deliberate; the first version of this toolkit read the
missing marks as a gap and added four edge markers on Home and a way-back row on every page.
Both are removed from Home and the first-level pages. The source nowhere says in words that
the absence is meant: it shows the absence on every page, and its acceptance record confirms
it. That it is meant is the statement of the app's author, who is also the author of this
toolkit.

**Evidence in the original.** `MainActivity.java:261-306` (the main page: a centre column
and a 72 dp bottom row of three entries, 教程 / 设置 / 更多, one per first-level page; no
arrow, hint or page name), `MainActivity.java:1584-1626, 1867-1892` (first-level pages are
left by the drag back or by system back), no back control on any page at any depth, and the
app's own acceptance record `UI_RENDER_FIDELITY_AUDIT_2026-08-12.md:45-90` (three entries,
finger-following moves in four directions, the row belongs to the main page alone, every
return path is system back).

**What is Extracted, what is Extended, what is New.**
- Extracted: no marks on Home; the bottom row with one entry per first-level page (72 dp
  tall, muted outline icon, bold 12.5 sp label, no bar, nothing selected, press sinks); a tap
  on an entry travels like a drag; first-level pages without a way back.
- Extended: four entries instead of three, because the showcase has four first-level pages
  (Foundations at the start side, Components below, Appearance above, Interactions at the
  end side); a page above Home (the original has none); start and end instead of left and
  right; the order of the row (start side first, end side last, the pages below and above
  between them); labels that give enlarged text back before one of them is cut
  (`MalletNavBar`).
- New, and only in the showcase: deeper pages keep a quiet way-back row that names their
  parent. The original has no back control at any depth; the row stays as an aid for readers
  of a demo with twenty-two deeper pages.

**In the code.** `showcase/.../pages/HomePage.kt` (the row is a `MalletNavBar` in
`Mode.LAUNCHER`, `fitNavigationBar = false` because the page is already padded for the
system bars), `showcase/.../Demo.kt` (`addWayBack` returns for children of Home), tests
`showcase/src/test/.../NavigationMarksTest.kt` and `NavigationDeviceTest`.

**Accessibility, stated honestly.** On a first-level page the way back for a keyboard, a
switch or a screen reader is the system's Back; nothing on screen offers it. Home's entries
are focusable and operable with keys. See also [D-17](#d-17) for Back and [D-19](#d-19) for
the navigation row.

### D-24
**A row that slides aside uncovers its action from the rim of the card.**
Mark: **Extended**; it departs from the original on purpose.

**Rule.** The coloured bed under a row that slides aside starts at the rim of the card, not
at the edge of the card's content. In a vertical `MalletSurface`, a `MalletRevealLayout`
that is a direct child is stretched over the *cell* of its row: from rim to rim, up to the
divider above it and down to the divider below it, or to the rim of the card where it is the
first or the last child, whose corners it then follows. The row keeps the place the card's
padding gave it. The bed shows exactly as far as the row has moved, and the action (icon
above label) comes out from the edge together with the row. A closed row shows no colour.

**Why.** A coloured box floating inside the card's padding is a box in a box, which rule 4
of the language ("dividers run from rim to rim; rows read as sections of one body", in
[DESIGN_SYSTEM.md](DESIGN_SYSTEM.md#1-principles)) already speaks against. The action should
look as if it had been lying under the card and the row had been pulled off it.

**The original.** It draws the bed as a square box inside the card's 9 dp padding
(`WhitelistAccountsPage.java:206-235`, `UiKit.java:2287-2315`): 78 dp wide, about 7.6 dp
inside the start rim. It was meant so: the code comment calls the bed "recessed" and
"visually subordinate to the parent" card (`UiKit.java:2287`), and the row passed the app's
acceptance (`UI_RENDER_FIDELITY_AUDIT_2026-08-12.md:41`). The look was faithfully extracted
in the first version and is now changed on purpose. What stays Extracted: the 78 dp strip,
the 42 % threshold, the durations, the white centred label, the recessed paint of the bed (a
vertical gradient with a dark line on top and a light line below), a tap closes, a switch in
the row keeps its own drag. The original has one precedent for content that meets the rim
and follows the corners: its first-launch picture (`UiKit.java:430-438`).

**Numbers.** The cell is the card's body inset by the hairline rim, which is
`MalletMaterial.RIM_DP` = 0.9 dp; its corner radius is the card's minus that (7.1 dp for the
default 8 dp card). Between rows the cell ends at a full-bleed horizontal `MalletDivider`;
beside any other neighbour it ends at the layout's own edge. A 5 dp shade lies on the bed
along the edge of the row. The sliding row is clipped to the cell, so it leaves under the
far rim. (In the original the card does not clip what is inside it, so the row is not cut
where the card's content ends; this is read from its code, `WhitelistAccountsPage.java:217`
and `UiKit.java:373-375`, and was not seen on a device.) All of the uncovered bed is the
touch target of the action. The layout no longer gives the row a fill of its own.

**Outside a card.** With any other parent, or in a horizontal surface, the layout is its own
cell: the bed is flush with the edges of the layout, and padding of the layout insets the
row, never the bed. Wrapped in another view inside a card, a row is not stretched.

**In the code.** `MalletRevealLayout` (`setCell`, `onLayout`, `onDraw`, `dispatchDraw`),
`MalletSurface.layoutRevealCells`,
`MalletMaterial.drawRevealBed(canvas, bounds, tint, topRadius, bottomRadius)`,
`MalletMaterial.drawRevealShade(...)`,
`MalletMaterial.drawFocusRim(canvas, bounds, topRadius, bottomRadius)`,
`MalletMaterial.RIM_DP`; tests in `MalletRevealLayoutTest` (section "inside a card").

### D-25
**A card is a group.**
Mark: the grouping is **Extracted**; the rule about lone controls is **New** and departs
from the original.

**Rule.** A card groups at least two rows or blocks that belong together. A lone control
never gets a card of its own: it joins the card of its relatives, or it stands on the canvas
under its heading, as a hero switch or a capsule button does. A caption or status line under
a control does not make a group. A card around one composite block (a list, a state view, a
pager with its dots, a paragraph, a picture, a stage with the knob that drives it) may stay.

**Why.** A raised surface says "these belong together". Around a single switch it says
nothing and only adds a frame, so the page reads as a stack of little boxes.

**The original.** It gives every headed section one card whatever its size, and so has two
cards with one lone control row: 状态通知 (`MainActivity.java:431-442`) and 本机技术状态
(`MainActivity.java:575-580`). The new rule goes beyond it. The means are the original's
own: rows stacked in one card, a full-width divider and an in-card label between sub-groups
(`MainActivity.java:509-523`), and controls that stand on the canvas itself, with no card
(the power switch, capsule buttons, page links).

**Details.** The rule is stated in full, with what goes inside a card and how a control
stands on the canvas, in [DESIGN_SYSTEM.md, section 5.1](DESIGN_SYSTEM.md#51-page-rhythm).
An empty area follows the same thought ([D-21](#d-21)).

### D-26
**The name Mallet and its vocabulary.**
Mark: **New**.

The toolkit is called **Mallet**, the project **Mallet UI**. What the name stands for is
told where the documents introduce it, in the [README](../README.md) and at the opening of
[DESIGN_SYSTEM.md](DESIGN_SYSTEM.md), and not again here.

The words Mallet uses are plain. A few of them are its own: *hue* for the colour in a
recessed bed, and *bronze*, *ochre*, *cobalt*, *jade* and *mist* for the five hues. None of
these comes from the original app, which names its track colours by level
(`UiKit.java:54-66`: `TRACK_OFF`, `TRACK_LIGHT`, `TRACK_STANDARD`, `TRACK_STRICT`,
`TRACK_THEME_LIGHT`). Other words are the original's: it calls its card material "surface"
(`UiKit.java:82-115, 367-402`), and its own records say 底色 for the colour of a track
(`UI_FROZEN_IMPLEMENTATION_MAP.md:55`), which is Mallet's Chinese word for a hue. The
Chinese names of the five hues (古铜, 赭黄, 钴蓝, 青玉, 雾白) and 浮层 for a raised surface
are Mallet's.

A name changes nothing that was extracted. The five colours, their edges and the materials
are the original's (section 2.1); what they are called is new.

| Level in the original | Mallet hue | Colour of the bed (light theme) |
|---|---|---|
| `TRACK_OFF` | bronze | `#CE8353` |
| `TRACK_LIGHT` | ochre | `#DEBE77` |
| `TRACK_STANDARD` | cobalt | `#86ACF7` |
| `TRACK_STRICT` | jade | `#80B897` |
| `TRACK_THEME_LIGHT` | mist | `#E2EDF7` |

What was searched about the name, and what was not, is recorded in
[LICENSE_AUDIT.md](LICENSE_AUDIT.md) under "The name". An unrelated library for the JVM is
called MALLET and is published as `cc.mallet:mallet`; this library has the coordinates
`io.github.avalonskyafar:mallet`. Whether to publish under another artifact id is left to
the maintainer.

---

## 4. What was left behind

| Not taken | Why |
|---|---|
| The illustrations and launcher art | No explicit licence; at least one is AI-generated media with its own terms. See [LICENSE_AUDIT.md](LICENSE_AUDIT.md). |
| The mascot, product name and all copy | They identify the original product, not the design language. |
| Portrait-only layout | Mallet pages have a maximum content width and work in landscape and on tablets. |
| The dark first-launch card variant | Unreachable in the original (first launch was forced light); the split card is derived from the normal card instead. |
| Display-mode switching inside every screen | Offered as an explicit call, `MalletWindow.preferHighestRefreshRate`. |
| The delete action as a box inside the card's padding | The bed under a row that slides aside starts at the rim of the card ([D-24](#d-24)). |
| A card of its own for one lone control | A card is a group ([D-25](#d-25)). |
| Stale comments and dead tokens | For example the edge colours listed in a comment at `UiKit.java:1882` differ from the ones drawn; Mallet follows the code, which is what the app's own audit confirmed on a device. |
