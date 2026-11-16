# Package `states`

What a screen shows while it has nothing, is waiting, or has failed.

## Files you own (all new)

- `widget/SancaiStateView.kt`, `widget/SancaiSkeleton.kt`, `widget/SancaiStateLayout.kt`,
  `widget/SancaiBanner.kt`
- `res/values/sancai_attrs_states.xml`
- `res/values/sancai_strings_states.xml`, `res/values-zh/sancai_strings_states.xml`
- tests: `widget/SancaiStatesTest.kt`

(Paths are under `sancai/src/main/kotlin/io/github/avalonskyafar/sancai/`,
`sancai/src/main/` and `sancai/src/test/kotlin/io/github/avalonskyafar/sancai/`.)

You may use published wave 1 widgets (`SancaiText`, `SancaiButton`, `SancaiSpinner`,
`SancaiIconView`) if they are present in your work copy; check before relying on them and
draw with `SancaiIconPainter` or plain views otherwise.

## What the original does

It has no dedicated components, only habits, and the habits are worth keeping:

- Loading is a calm sentence inside the card that will hold the result
  (`MainActivity.runPerformance`, `MainActivity.java` lines 1172-1195): no overlay, no
  blocking spinner over the page.
- An empty list draws nothing at all, not even an empty card, and leaves the single action
  that fills it, centred (`WhitelistAccountsPage.refreshList`, lines 57-87, and the
  "添加账户" capsule).
- A failed page replaces its content with a title and a plain card that says what happened
  and what the app did not do (`MainActivity.render` catch branch, lines 211-217).
- An inline status is a short coloured line: green when fine, red when not
  (`MainActivity.java` lines 685-696).

The components below make those habits reusable and fill the gaps.

## 1. `SancaiStateView` — new, in-language

A centred message for an empty, failed or finished area.

- Vertical, centred: an outline `SancaiIcon` 44 dp (stroke 1.6 dp at that size) in muted;
  16 dp; title SUBHEAD in ink, centred; 6 dp; detail CAPTION in muted, centred, max width
  280 dp; 20 dp; an optional action as a neutral capsule button (wrap content, min width
  160 dp).
- `Kind` presets set icon and tone: EMPTY (INBOX, muted), ERROR (ERROR icon in the danger
  colour), OFFLINE (CLOUD_OFF), SUCCESS (SUCCESS icon in the success colour), INFO. Every
  part can be overridden and each is optional.
- No card around it and no illustration slot background: it sits on the canvas or inside
  whatever card contains it. `compact` reduces the icon to 28 dp and the gaps for use inside
  a card row.
- `setAction(label) { }`; XML: `sancaiTitle`, `sancaiDetail`, `sancaiIcon`,
  `sancaiStateKind`, `sancaiStateAction`.
- Accessibility: title and detail read as one element; the action is separate.

## 2. `SancaiSkeleton` — new, in-language

A placeholder shape for content that is loading.

- A recessed neutral block: fill `blend(canvas, ink, 0.06)` in light and
  `blend(surface, ink, 0.08)` in dark, corner radius 6 dp (or a full capsule / circle via
  `shape`).
- Shimmer: a soft band, 40 % of the width, of `blend(fill, raised, 0.6)` travels from start
  to end in 1400 ms with a linear timeline, then pauses 300 ms. Reduced motion: no band, the
  block breathes between 70 % and 100 % alpha over 1600 ms.
- All skeletons in one window share one clock, so their bands move as a single sweep
  (derive the phase from `AnimationUtils.currentAnimationTimeMillis()` and the view's
  position on screen, not from one animator per view).
- `SancaiSkeleton.text(context, lines, lastLineFraction)` builds a column of text-line
  blocks (height 12 dp, 8 dp apart, last line shorter). Runs only while attached and
  visible. Not important for accessibility.

## 3. `SancaiStateLayout` — new, in-language

A `FrameLayout` that shows exactly one of four states: CONTENT, LOADING, EMPTY, ERROR.

- The first child declared is the content. The other three are created on first use:
  loading defaults to a centred spinner with a muted caption ("Loading" from strings);
  empty and error default to a `SancaiStateView` preset. Each can be replaced
  (`setLoadingView`, `setEmptyView`, `setErrorView`) or configured
  (`setEmpty(title, detail, actionLabel) { }`, `setError(title, detail, retryLabel) { }`).
- Transition: the outgoing view sinks 8 dp and is removed, the incoming view rises 8 dp
  into place, over the feedback duration with the glide; both overlap. No cross-fade of
  content: use alpha only on the outgoing view's last 40 %. Reduced motion swaps at once.
- `minimumLoadingMs` (default 400): once the loading state is shown it stays at least this
  long, so a fast response does not flash.
- `state` is saved. A state change is announced to accessibility services.

## 4. `SancaiBanner` — new, in-language

A persistent inline message at the top of a page or card, for conditions rather than events
(a toast is for events).

- A flat porcelain body (rim, no shadow), radius 8 dp, with a 3 dp bar in the tone colour
  at the start (the same bar as the callout); icon 20 dp in the tone colour; title SUBHEAD;
  detail CAPTION in muted; up to two text actions at the end of the text column; optional
  close icon.
- `tone`: INFO (accent), WARNING, DANGER, SUCCESS.
- Appears by growing its height from zero and disappears the same way (feedback duration,
  glide), pushing content rather than covering it. `show()`, `dismiss()`, `isShown`.

## Tests

State view: presets, optional parts collapse, action click, compact metrics. Skeleton:
draws its block; two skeletons side by side show one continuous band at a fixed time;
reduced motion draws no band; stops when detached. State layout: only one child visible per
state, lazily created defaults, custom views, minimum loading time (advance the looper),
save / restore. Banner: tones, actions, close, height animates to zero and the view becomes
GONE. Snapshots `states_light`, `states_dark`: empty, error with retry, offline, success,
compact in a card; skeleton rows at a fixed time; the four banner tones.
