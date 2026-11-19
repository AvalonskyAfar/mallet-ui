# Integration guide

How to add Mallet UI to an Android app, use it from XML and from code, and make it yours.

Contents: [Requirements](#1-requirements) · [Add the library](#2-add-the-library) ·
[First screen](#3-first-screen) · [From XML](#4-from-xml) · [From Kotlin](#5-from-kotlin) ·
[From Java](#6-from-java) · [Theming](#7-theming) · [Windows and system
bars](#8-windows-and-system-bars) ·
[Living with other UI kits](#9-living-with-other-ui-kits) · [Custom
views](#10-custom-views-in-the-same-language) ·
[Testing](#11-testing) · [Shrinking](#12-shrinking) · [Troubleshooting](#13-troubleshooting)

---

## 1. Requirements

| | |
|---|---|
| Minimum Android version | 6.0 (API 23) |
| Compiled against | API 36 |
| Language | Kotlin or Java |
| UI stack | Android Views (XML layouts or views built in code). Not a Compose library. |
| Kotlin of your app | 2.0 or newer (the library is compiled with language and API level 2.0) |
| Java bytecode | 11 |
| AndroidX | required (`android.useAndroidX=true`) |

Run-time dependencies, all resolved by Gradle from the library's POM:
`androidx.annotation`, `androidx.core`, `androidx.customview` and the Kotlin standard
library. The library does **not** depend on AppCompat, Material Components or Compose, and
does not require your activities to extend any particular class.

Size: the AAR is about 0.8 MB and contains no images, fonts or native code. A screen like
the one in `samples/minimal`, shrunk with R8, makes an APK of about 170 KB.

---

## 2. Add the library

Mallet UI is not on Maven Central yet. Use one of the following.

### 2.1 From the release's Maven repository zip (recommended)

Each release has `mallet-<version>-maven-repo.zip`: a complete Maven repository with the
AAR, its POM, Gradle module metadata and sources. Unzip it into your project, for example
into `libs/mallet-repo/`, and point Gradle at it:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("libs/mallet-repo") }
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.github.avalonskyafar:mallet:0.1.0")
}
```

Gradle then resolves the AndroidX dependencies by itself and your IDE finds the sources.

### 2.2 From the AAR file

Copy `mallet-<version>.aar` into `app/libs/` and declare the dependencies yourself, because
an AAR by itself carries no dependency information:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(files("libs/mallet-0.1.0.aar"))
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.customview:customview:1.1.0")
}
```

### 2.3 As a source module

Copy the `mallet/` directory into your project (or add this repository as a Git submodule)
and include it:

```kotlin
// settings.gradle.kts
include(":mallet")
// app/build.gradle.kts
dependencies { implementation(project(":mallet")) }
```

The module uses the version catalog `gradle/libs.versions.toml` of this repository; copy
the entries its build script refers to (versions, libraries and the two plugins) into yours.

### 2.4 From JitPack

Once the repository is public and a version is tagged, JitPack can build it (the repository
contains a `jitpack.yml`):

```kotlin
repositories { maven { url = uri("https://jitpack.io") } }
dependencies { implementation("com.github.<owner>.<repository>:mallet:<tag>") }
```

---

## 3. First screen

No setup is needed. Put a Mallet view into any layout of any activity and it works: without
theme attributes it uses the built-in palette and follows the system's dark mode.

```xml
<!-- res/layout/activity_settings.xml -->
<io.github.avalonskyafar.mallet.widget.MalletPage
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/page"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <io.github.avalonskyafar.mallet.widget.MalletHeading
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:malletTitle="Protection"
        app:malletDetail="How strictly content is filtered" />

    <io.github.avalonskyafar.mallet.widget.MalletSurface
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="10dp">

        <io.github.avalonskyafar.mallet.widget.MalletControlRow
            android:id="@+id/strength"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:malletTitle="Strength"
            app:malletOptions="@array/strength_levels"
            app:malletSelectedIndex="2" />

        <io.github.avalonskyafar.mallet.widget.MalletControlRow
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:malletTitle="Only on Wi-Fi">

            <io.github.avalonskyafar.mallet.widget.MalletSwitch
                android:id="@+id/wifi"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                app:malletChecked="true" />
        </io.github.avalonskyafar.mallet.widget.MalletControlRow>
    </io.github.avalonskyafar.mallet.widget.MalletSurface>

    <io.github.avalonskyafar.mallet.widget.MalletButton
        android:id="@+id/restore"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginStart="30dp"
        android:layout_marginTop="22dp"
        android:layout_marginEnd="30dp"
        android:text="Restore defaults" />
</io.github.avalonskyafar.mallet.widget.MalletPage>
```

```kotlin
class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MalletWindow.apply(this)                       // optional: canvas-coloured window, edge to edge
        setContentView(R.layout.activity_settings)

        // The row made its track from app:malletOptions.
        val strength = findViewById<MalletControlRow>(R.id.strength)
        strength.track?.setOnSelectionChangeListener { _, index -> prefs.strength = index }

        findViewById<MalletSwitch>(R.id.wifi)
            .setOnCheckedChangeListener { _, checked -> prefs.wifiOnly = checked }

        findViewById<MalletButton>(R.id.restore).setOnClickListener {
            MalletDialog.confirm(this, "Restore all defaults?", "Every setting returns to its original value.",
                positiveLabel = "Restore", danger = true) { prefs.reset() }
        }
    }
}
```

The two settings are the rows of one card, and the button stands on the canvas below it;
section 4 says when a card is the right container.

The complete list of components, with their XML attributes and code APIs, is in the
[Component catalog](COMPONENT_CATALOG.md).

---

## 4. From XML

- Use the fully qualified class name as the tag
  (`io.github.avalonskyafar.mallet.widget.Mallet…`).
- Component attributes are in the app namespace and all start with `mallet`:
  `app:malletTitle`, `app:malletOptions`, `app:malletHue` and so on. Standard attributes
  such as `android:text`, `android:enabled`, `android:hint` and `android:src` are honoured
  where they make sense.
- A `MalletSurface` (card) is a vertical `LinearLayout`; put rows and a `MalletDivider`
  between row groups inside it. Dividers stretch from rim to rim by themselves. So does a
  row that slides aside: a `MalletRevealLayout` placed directly in the card is stretched
  over the cell of its row, from rim to rim and from divider to divider, so that the action
  it uncovers comes out from the rim of the card
  ([INTERACTION_GUIDE.md](INTERACTION_GUIDE.md#9-reveal-expand-page)).
- A card is a group: it holds at least two rows or blocks that belong together. A lone
  control never gets a card of its own: it joins the card of its relatives, or it stands
  on the canvas under its heading, directly in the `MalletPage`
  ([DESIGN_SYSTEM.md](DESIGN_SYSTEM.md#51-page-rhythm)).
- A card is slightly larger than it looks, because its shadow is drawn inside the view:
  1.4 dp at the sides, 1 dp above, 4.8 dp below. Leave vertical gaps as margins on the card,
  as in the example; the page rhythm is listed in
  [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md#5-space-and-layout).
- A card does not clip its children, so the shadows of the controls inside it can fade out
  around them. A child that scrolls (`ScrollView`, a list, a `RecyclerView`) is the
  exception and ends at its own edge; a row that slides aside ends at the edge of its cell,
  so that it leaves under the rim of the card. Plain content of your own, a line of text for
  instance, sits about 4 dp closer to the lower rim than to the upper one, because the
  lower gutter is the deeper; give it a bottom margin if that shows.
- Give every view whose state should survive rotation an `android:id`, as with any Android
  view.
- The layout editor preview draws the views, since they need nothing but their own code.

---

## 5. From Kotlin

Every component has a constructor that takes a `Context`, and properties for everything an
attribute can set.

```kotlin
val card = MalletSurface(context)

card.addView(MalletControlRow(context).apply {
    title = "Strength"
    setSegmented(listOf("Off", "Gentle", "Standard", "Strict"), 2) { _, index -> save(index) }
})
card.addDivider(verticalMarginDp = 5f)
card.addView(MalletEntryRow(context).apply {
    title = "Trusted accounts"
    detail = "Accounts that are never filtered"
    arrow = MalletEntryRow.Arrow.END
    setOnClickListener { host.navigate("accounts") }
})
```

The card holds a group: a control row, a divider between the row groups, an entry row. A
control without relatives gets no card (section 4). Hand it to the page as it is, and it
stands on the canvas under its heading:

```kotlin
val page = MalletPage(context)

val power = MalletPowerSwitch(context).apply {
    setChecked(true, animate = false)
    setOnCheckedChangeListener { _, on -> setProtected(on) }
}
page.addSection("Protection", null, power)                           // a lone control: no card
page.addSection("Filter", "How strictly content is filtered", card)  // a group: a card
```

Two conventions hold for every control and are worth knowing from the start:

1. **Listeners are called for user input only.** `select(...)`, `setChecked(...)`,
   `value = ...` never call back. You can therefore set initial values and roll a change
   back inside a listener without guard flags.
2. **A view reads its theme when it is constructed.** To change the theme at run time,
   rebuild the views (section 7.3).

---

## 6. From Java

The library is written in Kotlin with Java callers in mind: constructors and optional
parameters have overloads, companion functions are static, and listeners are single-method
interfaces.

```java
MalletSegmentedTrack track = new MalletSegmentedTrack(context);
track.setOptions("Off", "On");
track.select(1, false);
track.setOnSelectionChangeListener((source, index) -> prefs.setEnabled(index == 1));

MalletTheme theme = MalletTheme.of(context);
int ink = theme.getColors().getInk();

MalletHaptics.tick(view);
```

---

## 7. Theming

The tokens and what they mean are described in
[DESIGN_SYSTEM.md](DESIGN_SYSTEM.md#11-theming). This section is the how-to.

### 7.1 Nothing to do

With no configuration, Mallet views use the built-in light palette, or the dark one when the
device is in night mode, and honour the system's "remove animations" and (Android 16+)
high-contrast text settings.

### 7.2 Through your app theme

```xml
<!-- res/values/themes.xml -->
<style name="Theme.MyApp" parent="Theme.Mallet">
    <!-- change only what you need -->
    <item name="malletColorAccent">#7A4B8F</item>
    <item name="malletHueCobalt">#A997E6</item>
    <item name="malletHueCobaltEdge">#8874D0</item>
    <item name="malletHighContrast">true</item>
</style>
```

Each hue has three attributes, one for each of its colours: the bed (`malletHueCobalt`), the
edge of the bed (`malletHueCobaltEdge`) and the labels resting on the bed
(`malletHueOnCobalt`). Bronze, Ochre, Jade and Mist are named the same way. An attribute
you leave out keeps its built-in value.

`Theme.Mallet` follows night mode and also colours the window and the system bars (the light
navigation bar from Android 8.1);
`Theme.Mallet.Light` and `Theme.Mallet.Dark` are fixed. You do not have to inherit from
them: the `mallet*` attributes work in any theme, including AppCompat and Material themes.

Put night-mode overrides in `res/values-night/themes.xml` as usual.

### 7.3 From your own settings

Apps that let the user choose the appearance keep the choice in preferences and build the
theme from it:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MalletTheme.resolver = MalletTheme.Resolver { context ->
            val dark = when (settings.appearance) {
                Appearance.LIGHT -> false
                Appearance.DARK -> true
                Appearance.SYSTEM -> MalletTheme.systemIsDark(context)
            }
            MalletTheme(
                colors = if (dark) MalletColors.dark() else MalletColors.light(),
                textScale = if (settings.largeText) MalletTheme.LARGE_TEXT_SCALE else 1f,
                reduceMotion = settings.reduceMotion || MalletTheme.systemReducesMotion(context),
                haptics = settings.haptics,
            )
        }
    }
}
```

When the user changes a setting, recreate the activity. `MalletThemeTransition` hides the
cut:

```kotlin
appearanceTrack.setOnSelectionChangeListener { track, index ->
    settings.appearance = Appearance.values()[index]
    MalletThemeTransition.recreateAfter(track)        // waits for the thumb, then recreates
}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(...)
    MalletThemeTransition.play(this)                  // fades the old look over the new one
}
```

The Showcase app does exactly this; see `ShowcaseSettings.kt` and `MainActivity.kt`.

### 7.4 For one part of a screen

```kotlin
val themed = MalletTheme.wrap(context, MalletTheme.dark())
val panel = MalletSurface(themed)          // this card and what you build with `themed` are dark
```

### 7.5 Your own hues

```kotlin
val hues = MalletHues.default().copy(
    cobalt = MalletHue(bed = 0xFFA997E6.toInt(), edge = 0xFF8874D0.toInt(), onBed = Color.WHITE),
)
MalletTheme.light().copy(hues = hues)
```

A hue is always a triple: the bed, a darker edge of the same colour, and the colour of
labels resting on the bed. For a single control, set its hue directly (`switch.onHue = …`,
`track.setHues(…)`, `app:malletHue="jade"`).

---

## 8. Windows and system bars

```kotlin
MalletWindow.apply(activity)                 // canvas-coloured window; light or dark bar icons; edge to edge
MalletWindow.padForSystemBars(rootView)      // keep content clear of the bars (and optionally the keyboard)
MalletWindow.preferHighestRefreshRate(activity)   // optional, for screens built on finger-following
```

`MalletPage` pads itself for the system bars and the keyboard, so a screen whose root is a
`MalletPage` needs only the first call. The page reaches behind the bars, but what scrolls
in it ends at the status bar (and at a navigation bar with buttons) instead of sliding
under the clock. Android 15 and later draw every app edge to edge;
on earlier versions `MalletWindow.apply(activity, edgeToEdge = false)` keeps the classic
layout with canvas-coloured bars (a black navigation bar under a light theme before Android 8).

---

## 9. Living with other UI kits

Mallet views are ordinary `View`s. They can sit in the same layout as AppCompat or Material
widgets, inside a `RecyclerView`, a `Fragment`, a `ConstraintLayout`, or in a Compose screen
through `AndroidView`:

```kotlin
AndroidView(factory = { context ->
    MalletSegmentedTrack(context).apply { setOptions("Off", "On") }
})
```

Things to keep in mind:

- **Clipping.** A Mallet view draws its shadow inside its own bounds, so it is never cut off
  by a parent. You do not need `clipChildren="false"`.
- **Scrolling parents.** Horizontal controls hand vertical movement back to the parent, so
  they behave in `ScrollView`, `NestedScrollView` and `RecyclerView`. Inside a horizontally
  scrolling parent (`ViewPager2`, a horizontal list) a track claims horizontal drags that
  start on it, which is usually what you want.
- **Material themes.** Mallet reads only its own `mallet*` attributes. It neither needs nor
  disturbs `colorPrimary` and friends.
- **Dialogs and sheets** are plain platform `Dialog`s and `PopupWindow`s. They do not need a
  `FragmentManager`.

---

## 10. Custom views in the same language

The painters and tokens are public so that your own views can match.

```kotlin
class Gauge(context: Context) : MalletControl(context) {
    private val track = RectF()

    override fun onDraw(canvas: Canvas) {
        track.set(dp(1.5f), dp(1.5f), width - dp(1.5f), height - dp(6.5f))       // leave the shadow gutter
        val hue = theme.hues.jade
        material.drawTrack(canvas, track, theme.bedColor(hue), theme.bedEdgeColor(hue))
        // ... a thumb with material.drawThumb(...), labels with theme.labelOnBed(hue)
    }
}
```

| You need | Use |
|---|---|
| a card or capsule background | `MalletSurfaceDrawable(context, radiusDp, pill, tint)` |
| a recessed field | `MalletWellDrawable(context)` |
| a track, thumb, rim or shadow in `onDraw` | `MalletMaterial` (`drawTrack`, `drawThumb`, `drawSurface`, `drawWell`, `drawFocusRim`) |
| the press effect | `MalletPress.attach(host, content) { … }` |
| a glide | `MalletMotion.glide()` with `MalletMotion.CONTROL_MS`, or `MalletControl.settle(…)` |
| haptics | `MalletHaptics.tick(view)` |
| an icon | `MalletIconDrawable(context, MalletIcon.CHECK)` or `MalletIconPainter.draw(…)` |
| text in the type scale | `MalletText`, or `MalletTextRole.X.applyTo(textView, theme)` |

The rules such a view should follow are listed in [dev/CONVENTIONS.md](dev/CONVENTIONS.md).

---

## 11. Testing

Mallet views run under Robolectric with native graphics, which is how the library itself is
tested (on the JVM, no device needed):

```kotlin
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StrengthTest {
    @Test fun `selecting strict is saved`() {
        val context = MalletTheme.wrap(ApplicationProvider.getApplicationContext(), MalletTheme.light())
        val track = MalletSegmentedTrack(context).apply { setOptions("Off", "Gentle", "Standard", "Strict") }
        ...
    }
}
```

- `MalletTheme.wrap(context, theme)` gives a test a fixed theme, independent of the host
  machine; add `.copy(reduceMotion = true)` to make animations jump.
- Controls expose platform accessibility roles and states, so Espresso and UI Automator
  find them as switches, check boxes, seek bars and buttons.
- `performClick()` on a switch, check box, radio button, chip or button counts as user input
  and calls its listener; tracks, sliders and steppers change only through touch, keys or
  accessibility actions.

---

## 12. Shrinking

The library needs no ProGuard or R8 rules: it uses no reflection and loads no classes by
name. Views referenced from XML layouts are kept by the Android Gradle Plugin's generated
rules, as for any custom view. The Showcase release build is minified and resource-shrunk
with the default optimising configuration and no extra rules.

---

## 13. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| A card's shadow looks cut off at the bottom | Something sets a fixed height smaller than the content plus the 4.8 dp gutter. Use `wrap_content`, or add the gutter to the height. |
| Two cards touch | The gap between cards is a margin on the card (22 dp between sections); the gutter alone is not a gap. |
| One control looks lost in a card of its own | A card is a group of at least two rows or blocks (section 4). Move the control into the card of its relatives as one more row (a `MalletControlRow` gives it a title), or take the card away and put the control directly into the page, under its heading (`page.addSection(title, detail, control)` in code). |
| Something inside a card draws over the row below it | Cards do not clip their children. Scrolling views are clipped for you, and a row that slides aside (`MalletRevealLayout`) ends at its cell; for any other view that draws outside its bounds, set `clipBounds` on it or mark it with `isScrollContainer = true`. |
| The text of some rows is centred and of others not | Entry rows and control rows are centred by default. Set `alignment = Alignment.START` (`app:malletEntryAlignment`, `app:malletControlRowAlignment`) on every row of a card that should read from the edge. |
| The theme did not change after a setting changed | Views read the theme at construction. Recreate the activity (section 7.3) or rebuild the views. |
| A listener is not called after `select()` or `setChecked()` | By design: listeners are for user input. Call your own handler after setting the value. |
| A track inside a `ViewPager2` swallows page swipes | Swipes that start on the track belong to the track. Start the swipe outside it, or disable the track while paging. |
| A page host takes a drag that a custom scrolling view should have had, or never takes over at its end | Implement `canScrollVertically` / `canScrollHorizontally` on that view truthfully; the host asks it who should move. |
| Scrolling content stops short of the top edge of the screen | `MalletPage` ends what scrolls at the status bar on purpose (section 8). For a page inside a container that already keeps clear of the bars, set `fitSystemBars = false`. |
| A text field is not focused when the screen opens | A page does not hand the focus to a field merely because a window opened, which would scroll to it and raise the keyboard. On a screen that is about typing, call `field.requestFocus()` yourself. |
| Labels on blue or green tracks are hard to read | The default palette keeps the original look of white labels on these hues. Turn on `malletHighContrast`; see [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md#12-accessibility). |
| Manifest merger or duplicate-class errors | Mallet declares nothing in the manifest. Check that the three AndroidX dependencies are not excluded and that `android.useAndroidX=true`. |
| Build fails on Windows with "path contains non-ASCII characters" | An Android Gradle Plugin check, unrelated to the library. Move the project, or set `android.overridePathCheck=true` in `gradle.properties`. |
