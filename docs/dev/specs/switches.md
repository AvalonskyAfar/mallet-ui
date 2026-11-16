# Package `switches`

Two-state controls: the compact switch, the hero power switch, the checkbox and the radio.

## Files you own (all new)

- `widget/SancaiSwitch.kt`, `widget/SancaiPowerSwitch.kt`, `widget/SancaiCheckbox.kt`,
  `widget/SancaiRadioButton.kt` (the last file also holds `SancaiRadioGroup`)
- `res/values/sancai_attrs_switches.xml`
- `res/values/sancai_strings_switches.xml`, `res/values-zh/sancai_strings_switches.xml`
- tests: `widget/SancaiSwitchesTest.kt`

(Paths are under `sancai/src/main/kotlin/io/github/avalonskyafar/sancai/`,
`sancai/src/main/` and `sancai/src/test/kotlin/io/github/avalonskyafar/sancai/`.)

All four extend `SancaiControl`, implement `android.widget.Checkable`, save their state, and
follow the listener rules of CONVENTIONS section 3. Shared listener shape:

```kotlin
public fun interface OnCheckedChangeListener { fun onCheckedChanged(view: View, checked: Boolean) }
public fun setChecked(checked: Boolean, animate: Boolean)   // never calls the listener
```

## 1. `SancaiSwitch` — extracted

Origin: `UiKit.CompactToggle`, `UiKit.java` lines 525-684; used at 61 x 34 dp
(`WhitelistAccountsPage.java` line 234). Port the geometry and the touch state machine
exactly, on top of `HorizontalDragGate`, `material.drawTrack` and `material.drawThumb`.

- Default size 61 x 34 dp. Track gutters: 1.5 dp sides, 1 dp top, 3 dp bottom. Thumb: a
  circle of 26.4 dp (never larger than track height minus twice the 2.8 dp padding) that
  travels the padded width; it sinks 1 dp while pressed.
- Bed: terracotta when off, cobalt when on, blended by thumb position while it moves
  (`theme.bedColor` / `theme.bedEdgeColor`, then `SancaiColors.blend`). `offGlaze` and
  `onGlaze` are properties.
- Tap toggles; a horizontal drag follows the finger and commits to the nearer side on
  release; a vertical drag aborts. Implements `SancaiHorizontalDragOwner`.
- Added to the original: RTL mirroring, Space / Enter, focus rim, disabled state, saved
  state, accessibility class `android.widget.Switch` with checked state and an On / Off
  state description from string resources.

## 2. `SancaiPowerSwitch` — extracted

Origin: `UiKit.PowerSwitch`, `UiKit.java` lines 2722-2930; used full width at 92 dp
(`MainActivity.java` line 287). The single hero control of a screen.

- Default height 92 dp (an 80 dp capsule). Gutters: 2.5 dp sides, 2 dp top, 10 dp bottom.
  Inset 5.6 dp. Thumb width `max(112 dp, trackWidth * 0.56 - inset)`.
  `material.drawTrack(..., hero = true)`; the thumb is clipped to the track.
- On the thumb, centred as a group: the POWER icon (draw with `SancaiIconPainter`, about
  17 dp, stroke 1.8 dp, ink) and the state label, 14 sp medium, ink.
- On the bed, in the free half (centre at 22 % or 78 % of the track width): the action
  label, 14 sp medium, colour `blend(bed, ink, 0.76)` in light and `0.82` in dark.
- Both labels switch when the thumb crosses the middle, not on release.
- Labels are properties with string-resource defaults: `onLabel` "On" / 已开启, `offLabel`
  "Off" / 已关闭, `turnOnLabel` "Turn on" / 开启, `turnOffLabel` "Turn off" / 关闭.
- Same touch, key, RTL, state and accessibility behaviour as `SancaiSwitch`.

## 3. `SancaiCheckbox` — new, in-language

A small recessed bed that fills with glaze when checked.

- Box 26 x 26 dp, corner radius 7 dp, drawn with the gutters of the switch so its shadow
  fits. Unchecked: neutral well (`material.drawWell`). Checked: glazed bed
  (`material.drawTrack(..., radius = 7 dp)`, cobalt by default, `glaze` property) with a
  white check mark (CHECK icon, stroke 2.2 dp, colour `theme.labelOnBed(glaze)`).
- Transition over `SancaiMotion.CONTROL_MS`: the glaze fades in and the check is drawn
  progressively along its path; reduced motion jumps.
- Optional label text (`text` property, `android:text`) to the end of the box, SUBHEAD
  size, regular weight, 10 dp gap; the whole view is the touch target (min height 44 dp).
  Pressing sinks the box content by 1 dp.
- Accessibility class `android.widget.CheckBox`.

## 4. `SancaiRadioButton` and `SancaiRadioGroup` — new, in-language

- Same anatomy as the checkbox with a round 26 dp bed. Checked: glazed bed with a 12 dp
  neutral bead in the centre (`material.drawThumb`), which scales up from nothing during
  the transition.
- A checked radio cannot be unchecked by the user.
- `SancaiRadioGroup` is a vertical `LinearLayout` that keeps exactly one of its
  `SancaiRadioButton` children checked, exposes `checkedId`, `check(id)` and an
  `OnCheckedChangeListener`, and saves its state.

## Tests

Per control: tap, drag past and short of the middle, vertical abort, programmatic set does
not notify, listener rollback, keyboard, disabled, RTL, save / restore, accessibility state;
exact bed pixels for off and on in light. Snapshots `switches_light`, `switches_dark`: every
control in both states, plus disabled, with labels.
