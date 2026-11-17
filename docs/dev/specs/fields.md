# Package `fields`

Text input.

## Files you own (all new)

- `widget/SancaiTextField.kt`
- `res/values/sancai_attrs_text_field.xml`
- `res/values/sancai_strings_text_field.xml`, `res/values-zh/sancai_strings_text_field.xml`
- tests: `widget/SancaiTextFieldTest.kt`

(Paths are under `sancai/src/main/kotlin/io/github/avalonskyafar/sancai/`,
`sancai/src/main/` and `sancai/src/test/kotlin/io/github/avalonskyafar/sancai/`.)

## `SancaiTextField` — extracted and completed

Origin: the app styled bare `EditText`s by hand. Read `UiKit.inputValidated`,
`UiKit.java` lines 1488-1505 (dialog input), `WhitelistAccountsPage.showAddDialog`,
lines 89-109 (label above a field), `DeveloperOptionsActivity.java` lines 290-307 (multi-line
list editor) and `ConfigEditorActivity.java` lines 68-79 (large monospace editor). What they
share is the recessed well: `SancaiWellDrawable`, radius 9 dp, padding 13 dp horizontal and
11 dp vertical, text 15 sp ink, hint in muted. The toolkit turns that into one component.

Anatomy: a vertical compound view.

1. **Label** (optional): 13 sp medium, ink, 7 dp above the well.
2. **Well**: an `EditText` on a `SancaiWellDrawable`. Optional leading icon (`SancaiIcon`,
   18 dp, muted) inside the well at the start. Optional end action inside the well at the
   end: `CLEAR` (CLOSE icon, shown only while there is text) or `PASSWORD_TOGGLE` (EYE /
   EYE_OFF). End actions are 40 dp touch targets with a content description.
3. **Support line** (optional): helper text in muted or, when there is an error, the error
   in the danger colour; CAPTION size, 6 dp below the well. A character counter
   (`12 / 64`) sits at the end of the same line when `counterMaxLength > 0`; it turns danger
   colour beyond the maximum.

States:

- Focused: the rim of the well becomes solid ink (`SancaiWellDrawable.rim = colors.ink`).
  This applies in touch mode too, since it shows where typing goes.
- Error: the rim becomes the danger colour and the error text replaces the helper. The
  support line appears and disappears by growing its height over the feedback duration
  (`SancaiMotion.feedbackDuration(theme.reduceMotion)`), not by popping. Setting an error
  announces it to accessibility services and gives a reject haptic.
- Disabled: the whole field pales to `SancaiControl.DISABLED_ALPHA`, the `EditText` is
  disabled.

API:

- `text`, `hint`, `label`, `helperText`, `error` (null clears), `counterMaxLength`,
  `leadingIcon`, `endAction`, `isMultiline` with `minLines` / `maxLines`, `inputType`,
  `isMonospace` (for code and config editors).
- `editText: EditText` for everything else (IME options, filters, selection).
- `fun validate(rule: (CharSequence) -> CharSequence?): Boolean` sets or clears the error
  and returns whether the text passed.
- `doAfterTextChanged { }`-style listener: `OnTextChangeListener(field, text)`.
- XML: `android:text`, `android:hint`, `android:inputType`, `android:maxLength`,
  `android:enabled`, `sancaiIcon` (common attribute, the leading icon) and new attributes
  `sancaiFieldLabel`, `sancaiFieldHelper`, `sancaiFieldError`, `sancaiFieldCounter`
  (integer), `sancaiFieldMultiline` (boolean), `sancaiFieldEndAction` (enum none / clear /
  passwordToggle), `sancaiFieldMonospace` (boolean).

Details that are easy to get wrong:

- Several fields on one screen must each restore their own text. The inner `EditText` has no
  stable id, so save and restore the children through a private container keyed by this
  view's id (`dispatchSaveInstanceState` / `dispatchRestoreInstanceState`), and save the
  error and password visibility too.
- Tapping anywhere on the well focuses the `EditText`. The label is linked to the field for
  accessibility (`labelFor`), and the error is exposed as the node's error.
- The cursor and selection handles take the accent colour where the platform allows it
  (API 29+ `textCursorDrawable`; leave older versions alone).
- RTL: icon, end action and counter swap sides; use relative padding.
- Large text must not clip: the well grows with its content.

## Tests

Text round trip; label / helper / error visibility; error wins over helper; `validate`;
counter text and over-limit colour; clear action appears and clears; password toggle changes
the transformation and keeps the selection; disabled; two fields restore independently;
rim colour pixel for normal, focused and error. Snapshots `text_field_light`,
`text_field_dark`: empty with hint, filled, focused, error, helper with counter, multi-line,
monospace, disabled, with leading icon and clear action.
