# Interaction guide

How things move in Sancai, and how to use the parts that make them move.

The interaction system is a small set of rules applied everywhere, plus the classes in
`io.github.avalonskyafar.sancai.interaction`. Nothing in it knows about any particular app:
the same host that moved the original app's pages moves yours.

Contents: [The rules](#1-the-rules) · [Press](#2-press) · [Haptics](#3-haptics) ·
[Tracks under the finger](#4-tracks-under-the-finger) · [Pages as places](#5-pages-as-places) ·
[System back](#6-system-back) · [Activities as neighbours](#7-activities-as-neighbours) ·
[Changing the theme](#8-changing-the-theme) · [Reveal, expand, page](#9-reveal-expand-page) ·
[Who owns the gesture](#10-who-owns-the-gesture) · [Motion tokens](#11-motion-tokens) ·
[Reduced motion](#12-reduced-motion) · [Writing your own](#13-writing-your-own)

Marks: **Extracted** from the original app, **Extended** from an idea in it, **New**. See
[DESIGN_DECISIONS.md](DESIGN_DECISIONS.md) for the evidence.

---

## 1. The rules

1. **Follow, then glide.** While a finger is down, the element is exactly under it: a thumb,
   a page, a sheet, a row. On release it glides to rest along one curve
   (`SancaiMotion.glide()`); how long depends on how far it still has to go.
2. **Colour moves with position.** The bed of a track blends between glazes as the thumb
   travels, not after it lands.
3. **Decide late, commit clearly.** Nothing is decided inside the touch slop. A drag commits
   when it has gone 28 % of the way or is flicked; otherwise it returns. A cancelled gesture
   always returns.
4. **Everything can be interrupted.** A touch during a landing takes over at once. No
   animation ever makes the user wait.
5. **Pressing sinks; it does not tint.** The operated content moves down 1.5 dp at
   touch-down and is back in place before the click is delivered.
6. **One finger, one owner.** A gesture belongs to the innermost thing that can use it in
   that direction: a track for horizontal drags, a list for vertical ones, the page for what
   is left. Ownership is handed over when the inner thing reaches its limit.
7. **A gesture is never the only way.** Whatever a swipe does can also be done with a tap, a
   key, the Back button or an accessibility action.
8. **Feedback confirms, it does not decorate.** A haptic tick marks a committed change and
   nothing else.

---

## 2. Press

**Extracted.** Origin: `UiKit.attachPress`.

```kotlin
SancaiPress.attach(row, content = rowContent) { view -> openDetails() }
```

- `host` receives the touch; `content` is what sinks. For a row inside a card the host is
  the row and the content is its text and chevron: the card itself never moves.
- The sink (1.5 dp over 55 ms) starts on `ACTION_DOWN`, without the tap delay that scrolling
  containers add, so the response is immediate.
- On release or cancel the content jumps back **before** the click listener runs. A page
  that starts travelling in response never carries a half-pressed row with it, and a
  snapshot taken for a transition never shows one.
- The click ticks (see Haptics) and then calls your listener. `SancaiPress.attachSink` gives
  the sink alone, for views that manage their own click listener.

Controls that draw themselves (`SancaiButton`, tracks, switches) implement the same rule
internally: their label sinks, their shadow tightens, nothing changes colour.

There is no ripple and no pressed tint anywhere in the system. If you build a custom
pressable view, use `SancaiPress.attach`, or `SancaiPress.sink` and `SancaiPress.release`
from your own touch handling.

---

## 3. Haptics

**Extended.** The original had one tick; the vocabulary below is new.

`SancaiHaptics` wraps `View.performHapticFeedback`, needs no permission, and does nothing
when `SancaiTheme.haptics` is off.

| Call | Meaning | Platform effect |
|---|---|---|
| `tick(view)` | a state change was committed | `CLOCK_TICK` |
| `detent(view)` | the finger crossed into another option while dragging | `SEGMENT_FREQUENT_TICK` (34+), `TEXT_HANDLE_MOVE` (27+), none below |
| `confirm(view)` | a consequential action succeeded | `CONFIRM` (30+), else `CLOCK_TICK` |
| `reject(view)` | an action was refused or failed validation | `REJECT` (30+), else `LONG_PRESS` |
| `longPress(view)` | a long press was recognised | `LONG_PRESS` |

Rules, all from the original:

- **Controls that hold a value call your listener first and tick second.** A switch that
  turns haptics off therefore stays silent when it does so, and one that turns them on
  confirms with a tick.
- **Actions tick first and call your listener second.** Buttons, rows and links may start
  something slow (a page, a dialog); the confirmation must not wait for it.
- **Ticks are for commits.** Programmatic changes never tick.

---

## 4. Tracks under the finger

**Extracted.** Origin: `UiKit.SegmentControl`, `CompactToggle`, `PowerSwitch`.

All horizontal controls (`SancaiSegmentedTrack`, `SancaiSwitch`, `SancaiPowerSwitch`,
`SancaiSlider`, `SancaiStepper`) share one touch model:

| Phase | What happens |
|---|---|
| Down | the control draws pressed; any running glide is cancelled where it is |
| Move inside the slop | nothing is decided |
| Move, mostly horizontal | the control claims the gesture (`requestDisallowInterceptTouchEvent(true)`), the thumb follows the finger and the bed blends |
| Move, mostly vertical | the control lets go for good: the gesture belongs to the scrolling parent and the value is untouched |
| Up after a drag | commits the option under the finger (a switch commits the nearer side) and glides the last bit |
| Up without a drag | a tap: selects the tapped option, or toggles |
| Cancel | returns to the committed value |

Listeners:

```kotlin
track.setOnSelectionChangeListener { t, index ->
    if (!store.trySet(index)) t.select(previous)      // rolling back is safe
}
```

- Listeners fire for **user input only**: touch, keyboard, accessibility actions. Setting a
  value from code never calls back, which is what makes the rollback above safe and removes
  the need for "suppress listener" flags.
- A drag reports once, on release. Use a slider and its `setOnValueChangeListener` when
  you need continuous values.

Every control also answers the keyboard (arrows step, Space and Enter act), mirrors in
right-to-left layouts, saves its state, and exposes itself to accessibility services with a
platform role.

---

## 5. Pages as places

**Extracted and generalised.** Origin: `MainActivity.GestureHost`, where the page map was
hard-coded in `targetFor()`.

In Sancai an app is a map. Each page lies beside, above or below another, and you reach it
by dragging towards it. The page under the finger and the one it uncovers travel together,
edge to edge, at finger speed; nothing fades or scales. A chevron on a row points to where
its page lies, so the row and the motion tell the same story.

### 5.1 Describe the map

```kotlin
val graph = SancaiSpatialGraph.Builder(root = "home")
    .connect("home", Side.BELOW, "settings")       // drag up on home, drag down on settings
    .connect("home", Side.START, "guide")
    .connect("home", Side.END, "more")
    .connect("settings", Side.END, "advanced")
    .child("more", Side.END, "about")              // opened from a row; dragged back, not forth
    .child("more", Side.END, "licences")           // several children may share a side
    .build()
```

| Call | Meaning |
|---|---|
| `connect(a, side, b)` | `b` lies on that side of `a`, and `a` on the opposite side of `b`; both can be dragged to |
| `connect(a, side, b, reciprocal = false)` | only the move from `a` exists |
| `child(parent, side, child)` | `child` is opened from `parent` (a row, a button) and slides in from that side; it can be dragged back, but not dragged to |
| `parent(key, parentKey)` | overrides where Back leads from a page |

`Side.START` and `Side.END` follow the layout direction. `build()` rejects two draggable
pages on one side and pages that cannot get back to the root.

### 5.2 Host it

```kotlin
val host = SancaiSpatialHost(this)
host.setGraph(graph) { key -> createPageView(key) }
host.show("home")
setContentView(host)

row.setOnClickListener { host.navigate("about") }     // slides in from where it lies
```

| Member | Use |
|---|---|
| `show(key)` | jump to a page without movement |
| `navigate(key)` | glide to a page; it enters from the side where it lies |
| `navigate(key, from, animate)` | glide to any page from a chosen side |
| `back()` / `canGoBack()` | move to the parent page |
| `currentKey`, `isMoving` | state |
| `addListener(listener)` | `onPageSettled(key)` and `onPageScrolled(from, to, fraction)` |
| `setKeepAlive(keys)` | which pages stay alive off screen (default: all visited) |
| `invalidatePage(key)`, `invalidatePages()`, `refreshCurrent()` | rebuild pages after data changed |
| `prewarmNeighbours` | build neighbouring pages while idle, so the first drag is smooth |

Page views are kept while detached, so returning to a page restores its scroll position and
state exactly. The current page and the state of every kept page survive rotation and
process death.

### 5.3 What the host does with a finger

1. Inside the touch slop nothing is decided; then the axis is locked for the gesture.
2. A drag towards a side that has a draggable neighbour starts a move. Any other drag is
   left alone for the rest of the gesture.
3. Horizontal drags that begin within 24 dp of a screen edge are left to the system's own
   back gesture.
4. Both pages are translated by the finger offset. The offset cannot leave the travel and
   cannot go against the direction of the move.
5. Release commits past `SancaiMotion.COMMIT_FRACTION` (28 %) of the travel or with a flick
   faster than `COMMIT_VELOCITY_DP` (320 dp/s) in the direction of the move; otherwise the
   pages return.
6. The landing lasts `SancaiMotion.settleDuration(remaining)`: 220 ms for a short remainder,
   420 ms for the full distance.
7. A touch during the landing ends it at once. The page that is at least half way in becomes
   the current page.

Section 10 describes how the host shares the finger with scrolling content and controls.

---

## 6. System back

**Extended.** The original handled only the final back event; the preview is new.

```kotlin
private lateinit var back: SancaiBack.Registration

override fun onCreate(savedInstanceState: Bundle?) {
    ...
    back = SancaiBack.attach(this, host)
}

// Needed below Android 13, or without android:enableOnBackInvokedCallback="true"
override fun onBackPressed() {
    if (!back.onBackPressed()) super.onBackPressed()
}
```

- Back leads to `graph.parentOf(currentKey)`: the page the current one was connected from.
- **Android 14 and later:** the back swipe *previews* the move. The pages follow the swipe
  for up to 40 % of the way (`SancaiSpatialHost.BACK_PREVIEW_FRACTION`), land when the
  gesture completes and glide back when it is cancelled. It is the same movement a finger
  inside the page produces, so the system gesture and the in-page gesture look alike.
- **On the root page** the registration steps aside. The system handles back itself and can
  show its own back-to-home animation.

---

## 7. Activities as neighbours

**Extracted.** Origin: `UiKit.startSpatial`, `spatialRoot`, `finishSpatial` and
`SpatialActivityHost`.

A second activity can behave like a neighbouring page: it slides in from one side while the
screen below slides out the other way, and it can be dragged back out from anywhere inside
it.

```kotlin
// 1. In the first activity
SancaiSpatialTransition.start(this, Intent(this, DetailActivity::class.java), enterFrom = Side.END)

// 2. In the second activity
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(SancaiSpatialTransition.wrap(this, pageView))
}

// 3. Every way out
override fun onBackPressed() = SancaiSpatialTransition.finish(this)
```

How it works: before the second activity starts, the first one's content is drawn into a
bitmap that travels to the new activity by token. The wrapper shows that picture behind the
real page; the page glides in while the picture glides out, and during a swipe back the two
move together under the finger. When the swipe commits, the activity finishes without a
window animation, and the real screen below is exactly where the picture was.

- A swipe back starts anywhere inside the page except the 24 dp edge zones, text fields,
  horizontal controls, and content that can itself move the way the finger goes (a pager
  with a page left that way, a row that slides aside). Implement
  `SancaiSpatialTransition.SwipeGate` on the activity (or set one with `setSwipeGate`) to
  refuse it while the page is in the middle of something.
- Without a picture (a direct launch, a restored process, a screen larger than 16 MB of
  pixels) the page appears at once and leaving uses the plain slide animations in
  `res/anim`.
- Only `Side.START` and `Side.END` are supported between activities.

Prefer one activity with a `SancaiSpatialHost` when you can. Use this for a screen that has
to be its own activity.

---

## 8. Changing the theme

**Extracted.** Origin: `UiKit.prepareThemeTransition` and `playPreparedThemeTransition`.

A view reads its theme once, so a new palette means rebuilding the views. To hide the hard
cut, the old appearance is captured just before the activity is recreated, laid over the new
content and faded away. Nothing moves; only the light changes. This is the one place where a
fade is the right motion.

```kotlin
themeTrack.setOnSelectionChangeListener { track, index ->
    settings.appearance = index
    SancaiThemeTransition.recreateAfter(track)    // lets the thumb finish its glide first
}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(page)
    SancaiThemeTransition.play(this)              // fades the old look over the new one
}
```

`recreateAfter` waits one control glide plus two frames (292 ms) so the thumb that caused
the change is seen to arrive; repeated calls coalesce into one recreation. The fade lasts
300 ms. With reduced motion nothing is captured and the theme changes with a plain cut.

---

## 9. Reveal, expand, page

### 9.1 `SancaiRevealLayout` — a row that slides aside

**Extracted.** Origin: the account rows of `WhitelistAccountsPage`.

```kotlin
val reveal = SancaiRevealLayout(context)
reveal.addView(row)
reveal.setAction(getString(R.string.delete), SancaiTextTone.DANGER) { remove(item) }
```

- Dragging the row towards the open direction uncovers a 78 dp strip holding one action on a
  recessed coloured bed. The row follows the finger and stops at the strip width.
- Release opens the row when at least 42 % of the strip is uncovered, otherwise closes it.
- Opening one row closes its open siblings. A tap on an open row closes it.
- A switch or track inside the row keeps its own drag: a gesture that starts on it never
  reveals the action.
- The action is also offered as an accessibility action and through the keyboard, so nobody
  has to swipe to reach it.
- The row lives inside its card: it draws a flat fill and adds no rim or shadow of its own.

### 9.2 `SancaiExpandableLayout` — a section that opens

**New.** The first child is the header; the others form the body. Opening grows the body's
height while the body stays anchored at its top edge and is clipped, as if a cover slid
away. Nothing fades or scales, and content below is pushed, not covered. An indicator
(usually a chevron) turns with the progress. A change of mind mid-way reverses from where
it is.

### 9.3 `SancaiPager` and `SancaiPageIndicator` — a short flow

**Extracted.** Origin: the first-launch flow of `FirstLaunchActivity`.

A horizontal pager for a handful of pages: an introduction, a tutorial, a gallery. Pages
travel edge to edge under the finger. Paging is the main gesture of such a screen, so it
commits earlier than a page host does: at 22 % of the width, or with a flick of 0.45 widths
per second. `linkWith(other)` lets a second pager (a heading area, say) follow continuously,
as the original did. The indicator is a row of dots; the two dots involved in a move blend
with the offset, and tapping a dot goes there.

---

## 10. Who owns the gesture

The hard part of a finger-driven interface is not the animation, it is deciding who the
finger belongs to. Sancai uses one protocol everywhere. **Extracted** from `GestureHost`
and made generic.

| The finger lands on… | …and moves | Owner |
|---|---|---|
| a horizontal control | sideways | the control, for the whole gesture |
| a horizontal control | up or down | the control lets go; then the scrolling content, then the page |
| a reveal row, a pager, a sideways list | sideways, where it can move when the drag begins | the row, pager or list, **for the whole gesture** |
| the same, with nowhere left to go that way | sideways | the page host |
| scrolling content | up or down, where it can still scroll | the content |
| scrolling content at its top or bottom | further in the same direction | the page host takes over **within the same gesture** |
| anything else | towards a neighbouring page | the page host |
| within 24 dp of a screen edge | sideways | the system back gesture |

Three mechanisms implement this:

1. **`SancaiHorizontalDragOwner`.** A marker interface on tracks, switches, sliders and
   steppers. Hosts leave a gesture that starts on such a view to it, unless the gesture
   turns out to be vertical. Put it on your own horizontally draggable views.
2. **`canScrollHorizontally` / `canScrollVertically`.** Hosts ask the views under the finger
   whether they can still move in the drag direction. Lists and scroll views answer
   correctly by themselves; `SancaiRevealLayout` and `SancaiPager` answer for their own
   movement. Implement these on custom scrolling views and they take part automatically.
3. **Hand-over at the limit, up and down only.** When the content under the finger reaches
   its top or bottom, the page host re-anchors the gesture at that point and begins its own
   move. Pull down a list that is already at its top and the page above comes with it, in
   one motion. Sideways there is no hand-over: a drag that a row or a pager has taken stays
   with it until the finger lifts, however far the finger goes. A row slides only as far as
   its action is wide, and a page that took over there would shut the row with the very
   drag that opened it. *(Extended: the original hands over in one case only, a scrolled
   page pulled down at its top.)*

Only a drag owner may ask a host not to intercept
(`requestDisallowInterceptTouchEvent`). Requests from ordinary scrolling content are
ignored by the page host, because otherwise it could never take over at the limit.

---

## 11. Motion tokens

All in `SancaiMotion`. The design rules behind them are in
[DESIGN_SYSTEM.md](DESIGN_SYSTEM.md#9-motion).

| Token | Value |
|---|---|
| `glide()` | cubic-bezier(0.22, 0.78, 0.18, 1) |
| `fade()` | cubic-bezier(0.2, 0, 0, 1) |
| `CONTROL_MS` | 260 |
| `PAGE_MS` | 420 |
| `settleDuration(remaining, reduce)` | 210 + 210 × remaining, clamped to 220..420 |
| `DIALOG_MS` | 320 |
| `FEEDBACK_MS` | 220 |
| `PRESS_MS`, `PRESS_SINK_DP` | 55, 1.5 |
| `THEME_FADE_MS` | 300 |
| `COMMIT_FRACTION` | 0.28 |
| `COMMIT_VELOCITY_DP` | 320 |
| `REDUCED_MS`, `FEEDBACK_REDUCED_MS` | 120, 90 |

The original had two different settle formulas (one for its page host, another for
activity swipe-back and sub-pages) and two flick thresholds in raw pixels. Sancai uses one
of each. **Extended.**

---

## 12. Reduced motion

`SancaiTheme.reduceMotion` follows the system setting "remove animations" (animator
duration scale 0) unless the theme pins it (`sancaiReduceMotion`: `system`, `always`,
`never`).

With reduced motion:

- finger-following stays, because it is direct manipulation, not animation;
- every glide is shortened to 120 ms (90 ms for small feedback), and controls jump;
- the dialog appears without its rise, the theme changes without its fade;
- decorative movement stops: the skeleton shimmer becomes a slow breath, the indeterminate
  progress bar a still fill. The spinner keeps turning at half speed, because it is the
  only sign that work is going on.

---

## 13. Writing your own

A new interactive view fits in if it follows the same contract. In short:

- Resolve the theme once: `SancaiTheme.of(context)`; extend `SancaiControl` for a
  custom-drawn control and use its `settle()` for glides.
- Follow the finger exactly while it is down; glide with `SancaiMotion.glide()` after.
- Leave the decision until the touch slop is crossed; give vertical movement back to the
  parent; implement `SancaiHorizontalDragOwner` if you own horizontal drags.
- Call listeners for user input only. Value controls: listener, then tick. Actions: tick,
  then listener.
- Cancel animators when detached. Honour `theme.reduceMotion`.
- Offer a tap, key or accessibility action for everything a gesture does.

The full checklist is in [dev/CONVENTIONS.md](dev/CONVENTIONS.md).
