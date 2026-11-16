# Package `dialogs`

Modal questions and passing feedback.

## Files you own (all new)

- `overlay/SancaiDialog.kt`, `overlay/SancaiToast.kt`
- `res/values/sancai_strings_dialogs.xml`, `res/values-zh/sancai_strings_dialogs.xml`
- tests: `overlay/SancaiDialogsTest.kt`

(Paths are under `sancai/src/main/kotlin/io/github/avalonskyafar/sancai/`,
`sancai/src/main/` and `sancai/src/test/kotlin/io/github/avalonskyafar/sancai/`.)

You may use the published wave 1 widgets (`SancaiTextField`, `SancaiButton`, `SancaiText`,
`SancaiDivider`) if they are present in your work copy.

## 1. `SancaiDialog` — extracted

Origin: `UiKit.dialog`, `confirm`, `input`, `inputValidated`, `form`, `error` and
`dismissDialog`, `UiKit.java` lines 1414-1428 and 1474-1640. Read all of it; the layout
numbers and the closing guard are the design.

Card:

- Porcelain, corner radius 16 dp (`SancaiSurfaceDrawable`), padding 18 dp sides, 19 dp top,
  none at the bottom. Kept on a hardware layer while it animates.
- Title: HEADLINE (18 sp medium, ink), centred.
- Message: CAPTION (12.5 sp, muted), centred, 7 dp below the title.
- Custom content (optional): 15 dp below.
- Validation slot: hidden until an error is shown, 10 dp above the divider. The error is a
  capsule glazed in the danger colour with white 12.5 sp medium text, padding 16 dp by 10 dp,
  as wide as the card content.
- A full-bleed divider 18 dp below the content, then the action row, 54 dp high: text
  actions of equal width separated by vertical dividers. Dismissive action in muted,
  confirming action in accent, destructive confirmation in danger; 14 sp medium, centred.
  Pressing sinks the label. One, two or three actions.

Window:

- A platform `Dialog` without title, transparent window background, dim amount 0.27, width
  `min(352 dp, screen width - 32 dp)`.
- `cancelable` (default true): back closes with the exit animation. Touching outside does
  not close by default (`canceledOnTouchOutside = false`), as in the original.
- Enter: alpha 0 to 1, rise from 11.5 dp below, scale from 0.978, `SancaiMotion.DIALOG_MS`,
  glide. Exit: the reverse, then `dismiss()`. Reduced motion: no animation.
- Closing guard: once closing has begun, further clicks and back presses are ignored, so an
  action can never be committed twice.

API (Kotlin and Java friendly builder):

```kotlin
SancaiDialog.Builder(context)
    .title("Restore all defaults?")
    .message("Every setting returns to its original value.")
    .positive("Restore", tone = SancaiTextTone.DANGER) { dialog -> store.reset(); true }
    .negative("Cancel")
    .show()
```

- Action callbacks return `true` to close, `false` to keep the dialog open. With no
  callback the action just closes.
- `dialog.showError(message)` fills the validation slot and gives a reject haptic;
  `clearError()`. Showing an error from inside a positive callback implies "stay open".
- `.view(customView)` for forms.
- `.input(initial, hint, inputType, multiline) { dialog, text -> Boolean }`: an input
  dialog. The field is a `SancaiTextField` when available, otherwise an `EditText` on a
  `SancaiWellDrawable` (radius 9 dp, padding 13 by 11 dp, 15 sp); the keyboard opens with
  the dialog and IME "done" triggers the positive action.
- Default labels come from the library strings (`sancai_action_ok`, `sancai_action_cancel`
  and friends already exist in `sancai_strings.xml`).
- `OnDismissListener` with a flag telling whether the positive action closed it (the
  original app needed this to roll a control back when a confirmation was cancelled).
- Convenience: `SancaiDialog.confirm(context, title, message, positiveLabel, danger) { }`
  and `SancaiDialog.message(context, title, message)`.

Also required: RTL swaps the action order; the title is the accessibility pane title; long
messages scroll inside the card (max card height 80 % of the window) while title and actions
stay fixed; text scale 1.13 and long action labels must not clip (labels may wrap to two
lines).

## 2. `SancaiToast` — extracted and extended

Origin: `UiKit.message`, `error` and `showFeedback`, `UiKit.java` lines 1430-1472. A pill
that rises at the bottom of the activity, says one thing, and sinks away. It is a view inside
the activity content, not a system toast and not a window.

- Pill: porcelain capsule for neutral messages; glazed in the danger colour with white text
  for errors (`tone`: NEUTRAL, DANGER; SUCCESS glazed in the success colour is an addition).
  Text 13 sp medium, centred, padding 18 dp by 12 dp. Hardware layer for its lifetime.
- Position: bottom centre of `android.R.id.content`, 18 dp side margins, 28 dp above the
  bottom plus the navigation-bar and IME insets.
- Motion: starts 18 dp lower and glides up over the feedback duration; after 2600 ms it
  glides back down 18 dp and is removed. No fade.
- A new toast removes the current one at once and takes its place.
- Addition: one optional text action inside the pill at the end (for example "Undo"),
  separated by a vertical hairline; tapping it runs the callback and dismisses. With an
  action the toast stays 5000 ms.
- `SancaiToast.show(activity, message, tone)` / `.error(activity, message)` /
  `Builder(activity).message().action(label) { }.duration(ms).show()`; `dismiss()`.
- Accessibility: the message is announced (polite live region); the action is reachable;
  under a screen reader the toast stays until dismissed or 10 s.

## Tests

Dialog: structure for one, two, three actions; callbacks and the true / false contract;
`showError` keeps it open and shows the capsule; closing guard (two rapid clicks commit
once); back key; dismiss listener flag; input dialog returns the text and validates; width
rule; RTL order. Toast: appears in the content view, replaces a previous one, leaves after
its duration (advance the looper), action callback, tones. Snapshots `dialogs_light`,
`dialogs_dark`: confirm, destructive confirm, input with an error capsule, a three-action
dialog; neutral, danger and action toasts (render the card and pill views directly).
