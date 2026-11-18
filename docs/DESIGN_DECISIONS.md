# Design decisions and provenance

Mallet UI grew out of one application. This document says, component by component, what was
**extracted** from it, what was **extended**, and what is **new**, and it records the reasons
for every place where Mallet deliberately differs from its source.

| Mark | Meaning |
|---|---|
| **Extracted** | Design and numbers taken from the original; re-implemented in Kotlin |
| **Extended** | An original idea, generalised or completed |
| **New** | Absent from the original; designed for Mallet from its materials and rules |

"Origin" cites the original source as `File.java:lines`, relative to
`Gravekeeper/app/src/main/java/com/gravekeeper/` in the original repository. The original
names its parts with internal codes (L1 canvas, S1 card, K1 track, K3 thumb, K4 capsule,
K5 compact switch, K8 stepper, D1 divider, M1 overlay material); they appear here only to
help anyone who reads that source.

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
- **Gaps.** One button variant in use; no text-field component, checkbox, radio, slider,
  menu, sheet, tab, app bar, progress or loading indicator; no disabled, focused or keyboard
  state anywhere; only content descriptions, no roles or actions, for screen readers on the
  custom controls; no right-to-left
  support; strings and product copy inside views; minimum Android 11; phone portrait only.

Mallet keeps the idea and the interaction model, states them as tokens and rules, makes the
parts usable from XML and code by any app, and fills the gaps in the same language.

---

## 2. Provenance of every part

### 2.1 Foundation

| Mallet | Mark | Origin | Kept | Changed or added |
|---|---|---|---|---|
| `MalletColors` | Extracted | `UiKit.java:82-115`, `460-465` | every neutral and semantic value, light and dark, and the high-contrast variants of muted, hairline and divider | role names; `warning` darkened in light ([D-07](#d-07)); semantic roles strengthened in high contrast ([D-08](#d-08)); `contrast()` |
| `MalletHue`, `MalletHues` | Extracted | `UiKit.java:54-66`, `2541-2609` | the five bed and edge colours; label colours on each bed; the 2 / 3 / 4-option scale | hues are data instead of label-count logic ([D-02](#d-02)); `uniform()`; mist as a general neutral hue |
| `MalletTheme` | Extended | `UiKit.java:126-142`, `196-212`; `AppPreferences.java` | text scale 1.13, high contrast, reduced motion, haptics as theme flags | resolution from context, resolver or XML attributes ([D-01](#d-01)); night hue ([D-03](#d-03)); system settings as defaults |
| `MalletTextRole`, `MalletTextTone` | Extended | `UiKit.java:284-315`, `ReadingLayoutSpec.java` | recurring sizes, medium weight for titles, no font padding, body leading | eleven named roles instead of about twenty literals ([D-05](#d-05)) |
| `MalletSpacing` | Extracted | `UiKit.java:267`, `329-334`, `MainActivity.java:402-460`, `1217-1227` | page padding 12 / 22 / 34, heading and section gaps, card padding 9 | names |
| `MalletMotion` | Extracted | `MotionSpec.java`, `UiKit.java:263-265`, `1071`, `1324`, `1459-1464`, `1622-1635`, `res/interpolator/gk_spatial.xml` | the curve, 260 / 420 / 320 / 220 / 55 ms, the 28 % commit rule, the settle formula | one settle formula and a density-based flick threshold ([D-06](#d-06)) |
| `MalletMaterial` | Extracted | `UiKit.java:1663-1937` | layer order, gradient stops, alphas, stroke widths, gutters of card, track and thumb, in both themes | one public painter instead of private statics; focus rim; pressed porcelain; repaint without lift |
| `MalletSurfaceDrawable`, `MalletWellDrawable` | Extracted | `UiKit.java:1939-2046`, `2185-2242` | card, capsule, dialog and well materials | split-corner cards from `2330-2448` folded in; solid rims for focus and error |
| `MalletIcon`, `MalletIconPainter` | Extended | `UiKit.java:847-927`, `2823-2831`; `MainActivity.java:357-399` | six glyphs: chevron, arrow, power ring, book, sliders, more | forty-four further icons drawn for Mallet; drawable and view; RTL mirroring ([D-10](#d-10)) |
| `MalletWindow` | Extracted | `UiKit.java:214-282` | canvas-coloured bars, light bar icons, insets as padding, refresh-rate preference | works from API 23; edge to edge on every version |

### 2.2 Controls

| Mallet | Mark | Origin | Kept | Changed or added |
|---|---|---|---|---|
| `MalletSegmentedTrack` | Extracted | `UiKit.java:506`, `2450-2720` | geometry (60 dp view, 52 dp capsule, 4 dp inset), finger-following thumb, hue blending, label rules, vertical abort, listener-then-haptic | any number of options, any hues; XML; RTL; keys; saved state; one accessibility element per option; disabled; shrink-to-fit labels |
| `MalletSwitch` | Extracted | `UiKit.java:525-684`, `WhitelistAccountsPage.java:234` | 61 × 34 dp, thumb and travel, tap and drag | hue properties; RTL; keys; saved state; `Switch` role |
| `MalletPowerSwitch` | Extracted | `UiKit.java:2722-2930`, `MainActivity.java:287` | 92 dp view, 80 dp capsule, thumb width rule, glyph and two labels that flip at mid-travel, deep shadow | labels as properties instead of product copy ([D-11](#d-11)); hues; RTL; keys; saved state |
| `MalletStepper` | Extended | `UiKit.java:686-793`, `DeveloperOptionsActivity.java:548-592` | three zones 1 : 1.35 : 1, fixed centre thumb with the value, marks on the bed | owns its value, range and step; hold to repeat; paled marks at the bounds; edit request; `SeekBar` role |
| `MalletButton` | Extended | `UiKit.java:977-989`, `1065-1084` | capsule, padding 22 × 13 dp, 14.5 sp medium, sink on press | primary, destructive and text variants; icon; loading; disabled; focus ([D-12](#d-12)) |
| `MalletCheckbox`, `MalletRadioButton`, `MalletRadioGroup` | New | — | — | a small bed that fills with hue; see [D-13](#d-13) |
| `MalletSlider` | New | — | — | a slim track glazed from the start to a neutral bead; see [D-14](#d-14) |
| `MalletProgressBar`, `MalletSpinner` | New | the ring of `UiKit.java:2823-2831` | the 276° open ring | see [D-15](#d-15) |
| `MalletTextField` | Extended | `UiKit.java:1488-1505`, `WhitelistAccountsPage.java:89-109` (13 × 11 dp, 15 sp); `ConfigEditorActivity.java:68-79` (well only) | the recessed well, 13 × 11 dp padding, label above | text on the scale (15.5 sp instead of 15); one component with label, helper, error, counter, end actions, focus and error rims |
| `MalletChip`, `MalletChipGroup`, `MalletTag`, `MalletBadge` | New | — | — | porcelain capsules that hue when checked; glazed status tags; see [D-16](#d-16) |

### 2.3 Layout and reading

| Mallet | Mark | Origin | Kept | Changed or added |
|---|---|---|---|---|
| `MalletSurface`, `MalletDivider` | Extracted | `UiKit.java:367-377`, `460-465`; `MainActivity.java:1221-1227` | card material, 9 dp padding, rim-to-rim divider at 14 % ink | the card stretches dividers itself instead of callers using negative margins; flat and split-corner variants |
| `MalletPage` | Extracted | `UiKit.java:269-282`, `329-365` | page padding, scroll configuration, insets as padding | XML children; keyboard inset; saved scroll position; a column no wider than 560 dp; content that ends at the system bars; a page that opens at its top ([D-22](#d-22)) |
| `MalletHeading` | Extracted | `UiKit.java:296-315` | 17 sp title, 12.5 sp muted detail, 10 dp inset | page-title level |
| `MalletEntryRow` | Extended | `UiKit.java:800-845` | 62 / 76 dp, centred title and detail, independent chevron, content-only sink | start alignment, leading icon, trailing value, RTL, focus |
| `MalletControlRow` | Extracted | `UiKit.java:467-508` | centred text column, weights 2 : 1, 1 : 3, 1 : 4 and 1 : 1.35, heights 64 / 72 dp | any control, not only a track |
| `MalletLinkRow` | Extracted | `UiKit.java:929-975` | 64 dp, muted medium label, chevron at the edge or beside the label | label on the scale (14.5 sp instead of 15); tone; RTL |
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
| `MalletRevealLayout` | Extracted | `WhitelistAccountsPage.java:206-280`; `UiKit.java:2244-2328` | 78 dp strip, 42 % threshold, flat row fill, recessed coloured bed, switch keeps its gesture | either edge; sibling closing; accessibility and keyboard access |
| `MalletPager`, `MalletPageIndicator` | Extracted | `FirstLaunchActivity.java:263-277`, `330-543` | finger-following pages, 22 % / 0.45-width commit, linked heading, dots | adapter; RTL; saved state; blending dots |
| `MalletExpandableLayout` | New | — | — | see [D-18](#d-18) |
| `MalletNavBar` | Extended | `MainActivity.java:291-400` | 72 dp row of outline icons with labels on the bare canvas | a selected state for tab use ([D-19](#d-19)); badges; keys |
| `MalletTopBar`, `MalletTabs` | New | — | — | see [D-19](#d-19) |

### 2.5 Overlays and states

| Mallet | Mark | Origin | Kept | Changed or added |
|---|---|---|---|---|
| `MalletDialog` | Extracted | `UiKit.java:1414-1428`, `1474-1640` | card radius 16, padding, centred title and message, full-bleed divider, 54 dp text actions, dim 0.27, width rule, 320 ms rise, closing guard, in-dialog error capsule | builder; one to three actions; stacking; scrolling message; input variant |
| `MalletToast` | Extended | `UiKit.java:1414-1472` | capsule, rise of 18 dp, 2600 ms, replacement rule, no fade | text on the `FOOTNOTE` role (13.5 sp instead of 13); success tone; one action; insets; announcement |
| `MalletFloatingPanel` | Extracted | `StatusOverlayController.java`; `UiKit.java:2048-2183` | draggable porcelain panel, radius 10 | usable inside any window; clamping; saved position |
| `MalletMenu`, `MalletBottomSheet` | New | — | — | see [D-20](#d-20) |
| `MalletStateView`, `MalletStateLayout`, `MalletSkeleton`, `MalletBanner` | New | habits in `MainActivity.java:211-217`, `1172-1195`; `WhitelistAccountsPage.java:57-87` | loading is a calm line in place; an empty list draws nothing but its one action; a failure says what happened | see [D-21](#d-21) |

---

## 3. Decisions

Each entry states a place where Mallet differs from its source, or adds to it, and why.

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
(4.7 : 1), in the hue of the ochre hue. The dark value is unchanged.

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
right-to-left layouts. The three marks of the original navigation row were drawn 24 dp wide,
where every other glyph keeps its ink inside 20 units; they are scaled to three quarters so
that one grid holds for all fifty, and the navigation row draws icons in a 32 dp box to give
them their first size back.

### D-11
**No product copy inside components.**
The original power switch drew "已开启", "开启保护" and so on itself. Labels are properties
with neutral defaults in string resources (English and Simplified Chinese).

### D-12
**Buttons: one material, four voices.**
Only the neutral capsule was in use; the destructive tint existed in the painter but was
reached only by error messages. Mallet adds a primary capsule (the same glazed-body recipe
with the accent colour), exposes the destructive one, and adds a text variant, which is what
the dialog actions already were. Loading reuses the open ring. A press sinks the label and
tightens the shadow, using pressed-shadow values that were present in the original painter
but unused.

### D-13
**Checkbox and radio: a small bed that fills with hue.**
The original expressed every choice as a track. For multiple selection and long option
lists that does not scale. A 26 dp well that turns into a glazed bed when chosen is the
smallest form of "colour is state"; the radio carries a small neutral bead, the checkbox a
check mark drawn as a stroke.

### D-14
**Slider: hue up to the bead.**
Continuous values needed dozens of stepper taps or a dialog. The slider is a slim groove
whose stretch from the start to the bead is glazed, so the amount of colour is the value. It
can run through the hue scale as the value rises: the stretch wears the hue nearest to the
bead and the next one is poured in when the bead passes half-way. Blending two hues by
position was tried first and dropped, because ochre and cobalt mix to grey and a slider can
come to rest there.

### D-15
**Loading borrows the power ring.**
The spinner is the 276° open ring of the power glyph, turning. Determinate progress is a
glazed fill growing in a groove. Both are quiet enough to sit inside a card, where the
original put its loading text; nothing covers the page.

### D-16
**Chips and tags.**
A chip is a small porcelain capsule that hues when checked, the button and the track in
one. A tag is a mini glazed capsule without a shadow, for status in lists, replacing the
coloured text chosen by string matching.

### D-17
**The system back gesture previews the move.**
On Android 14 and later a back swipe drives the same movement as a finger inside the page,
up to 40 % of the way, so the two gestures look alike. On the root page the callback is
unregistered and the system animates back-to-home itself.

### D-18
**Expanding is uncovering.**
The body grows from zero height while staying anchored at its top edge and clipped, as if a
cover slid away; content below is pushed. No fade and no scale, in keeping with "things that
move do not fade".

### D-19
**Navigation stays quiet.**
The original navigation is three muted outline icons on the bare canvas, with no bar and no
selected state, because each is a shortcut. For apps that need tabs, the selected item is
simply drawn in ink. The top bar has no shadow and centres its title independently of its
buttons, as rows do. Text tabs mark the selection with a short accent capsule under the
label. Hue is not used for navigation: hue means the state of a value, and the current
page is not a value.

### D-20
**Menus and sheets are porcelain.**
A menu is a small porcelain panel (radius 10, as the original floating panel) that grows
from its anchor; items sink when pressed. A sheet is a dialog card attached to the bottom
edge with only its top corners rounded, following the finger down; its shadow falls
downward like every other shadow, so separation from the page comes from the scrim and the
rim.

### D-21
**Empty, loading and error keep the original restraint.**
An empty area shows an outline icon, a line or two and at most one action, on the canvas,
never inside an empty card. A skeleton is a recessed neutral block with a slow light passing
over it, and all skeletons on a screen share one sweep. A banner is a flat card with the
accent bar of the callout; it pushes content down rather than covering it.

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

---

## 4. What was left behind

| Not taken | Why |
|---|---|
| The illustrations and launcher art | No explicit licence; at least one is AI-generated media with its own terms. See [LICENSE_AUDIT.md](LICENSE_AUDIT.md). |
| The mascot, product name and all copy | They identify the original product, not the design language. |
| Portrait-only layout | Mallet pages have a maximum content width and work in landscape and on tablets. |
| The dark first-launch card variant | Unreachable in the original (first launch was forced light); the split card is derived from the normal card instead. |
| Display-mode switching inside every screen | Offered as an explicit call, `MalletWindow.preferHighestRefreshRate`. |
| Stale comments and dead tokens | For example the edge colours listed in a comment at `UiKit.java:1882` differ from the ones drawn; Mallet follows the code, which is what the app's own audit confirmed on a device. |
