# Package `spatial`

Pages arranged in space, moved by the finger.

## Files you own (all new)

- `interaction/SancaiSpatialGraph.kt`, `interaction/SancaiSpatialHost.kt`,
  `interaction/SancaiBack.kt`
- tests: `interaction/SancaiSpatialHostTest.kt`

(Paths are under `sancai/src/main/kotlin/io/github/avalonskyafar/sancai/` and
`sancai/src/test/kotlin/io/github/avalonskyafar/sancai/`.)

## The idea

In the original app pages are not a stack, they are places. The settings page lies below the
main page, the tutorial to its left, "more" to its right; the advanced settings lie to the
right of the settings. A page is reached by dragging towards it, and the page under the
finger and its neighbour move together, edge to edge, at finger speed. There is no fade, no
scale and no parallax. A chevron on an entry row points to where its page lies.

Origin, to be read completely before designing: `MainActivity.GestureHost`,
`MainActivity.java` lines 1628-2136, with `show`, `backTarget` and `backDirection` (lines
1229-1239 and 1589-1626), the page cache (lines 220-259) and `MotionSpec.java`.

## 1. `SancaiSpatialGraph`

An immutable description of where pages lie. Pages are identified by `String` keys.

```kotlin
val graph = SancaiSpatialGraph.Builder(root = "main")
    .connect("main", Side.BELOW, "settings")      // also records: main is ABOVE settings
    .connect("main", Side.START, "tutorial")
    .connect("main", Side.END, "more")
    .connect("settings", Side.END, "advanced")
    .build()
```

- `Side`: START, END, ABOVE, BELOW. START and END follow the layout direction.
- `neighbour(key, side): String?`, `sideOf(from, to): Side?`, `contains(key)`.
- `parentOf(key)`: the next page on the shortest path to the root; this is where "back"
  goes. The builder can override it with `parent(key, parentKey)`.
- Validation at `build()`: unknown keys, two neighbours on one side, and pages unreachable
  from the root throw `IllegalArgumentException` with a clear message.

## 2. `SancaiSpatialHost`

A `FrameLayout` that shows one page of a graph and moves between pages.

Setup: `host.setGraph(graph, factory)` where `factory` is
`fun interface PageFactory { fun createPage(key: String): View }`, then `host.show(key)`.

Navigation:

- `navigate(key, animate = true)`: the destination must be a neighbour; it enters from the
  side where it lies. `navigate(key, from: Side, animate)` moves to any page, entering from
  the given side (for pages that are not neighbours).
- `back(): Boolean`: moves to `parentOf(current)`; false at the root.
- `currentKey`, `isMoving`.
- `Listener`: `onPageSettled(key)`, `onPageScrolled(from, to, fraction)` (0..1, for linked
  effects such as an indicator).

Gesture rules, all from the original and all to be kept:

1. Nothing is decided inside the touch slop. The axis is then locked for the gesture.
2. A drag towards a side with a neighbour starts a move; any other drag is left alone for
   the rest of the gesture.
3. Horizontal drags that start within 24 dp of the left or right edge are left to the
   system back gesture.
4. A gesture that starts on a `SancaiHorizontalDragOwner` belongs to that control. If it
   turns out to be vertical, the reservation is dropped and normal arbitration resumes.
5. Vertical moves give way to scrolling content: when the page content can still scroll
   towards the finger's origin (for example a list not at its top, for a pull down), the
   host waits; when the content reaches its limit during the same gesture, the host
   re-anchors the gesture there and may begin a move.
6. While dragging, both pages translate by the finger offset; the offset is clamped to the
   travel and cannot go against the move direction.
7. Release commits when the offset passes `SancaiMotion.COMMIT_FRACTION` of the travel, or
   the velocity towards the destination exceeds `SancaiMotion.COMMIT_VELOCITY_DP`;
   otherwise the pages return. Cancel always returns.
8. Settle with `SancaiMotion.settleDuration(remaining, reduceMotion)` and
   `SancaiMotion.glide()`, one `ValueAnimator` driving both pages.
9. Interruption: a touch that lands during a settle ends it at once. The page that is at
   least half way in becomes the current page; the other is removed. Navigation calls made
   during a drag or settle first stabilise the same way.

Pages:

- Views are created through the factory and cached while detached, so returning to a page
  keeps its scroll position. `setKeepAlive(keys)` limits the cache; other pages are dropped
  when they leave. `invalidate(key)` / `invalidateAll()` discard cached views (after a
  theme or data change). A cached view is reused only for the key it was created for.
- `prewarmNeighbours` (default false): after a settle, neighbour pages are created on idle
  loops with the original pacing (250 ms after an animated settle, then 120 ms apart; at
  once after a plain `show`), and any touch-down cancels pending warm-ups.
- The current key and the cached keys are saved; views are recreated through the factory.
- RTL: START and END swap sides. Accessibility: only the current page is exposed; a settle
  announces the page if the factory view has a pane title.

## 3. `SancaiBack`

Connects the system back gesture to a host (addition; the original only handled the final
event, see `BackNavigation.java`).

- `SancaiBack.attach(activity, host): Registration`.
- API 34 and later: an `OnBackAnimationCallback`. Back progress drives the same move as a
  finger would, towards `parentOf(current)`, up to 40 % of the travel; cancel returns,
  invoke commits with a settle. The callback is enabled only while `back()` could succeed,
  so at the root the system handles back (and can show its own animation).
- API 33: an `OnBackInvokedCallback` calling `host.back()`.
- Below 33 the activity forwards `onBackPressed`: `if (!registration.onBackPressed())
  super.onBackPressed()`.
- `Registration.remove()`.

## Tests

Graph: reciprocity, parent path, validation errors. Host, with coloured test pages:
programmatic navigation ends on the right page; drag past and short of the threshold; fling;
drag against a missing neighbour does nothing; edge zone ignored; a drag starting on a
`SancaiSegmentedTrack` moves the track, not the page; vertical drag over scrolled content
scrolls it, and continues into a page move at the top; interruption adopts the nearer page;
both pages are offset by the same amount while dragging (assert translations); keep-alive
and invalidation; save / restore; RTL. `SancaiBack`: `back()` path to the root. Snapshot
`spatial_drag_light`: a frame captured mid-drag showing two pages edge to edge.
