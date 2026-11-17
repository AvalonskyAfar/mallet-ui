# Integration guide

How to add Sancai UI to an Android app, use it from XML and from code, and make it yours.

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

Sancai UI is not on Maven Central yet. Use one of the following.

### 2.1 From the release's Maven repository zip (recommended)

Each release has `sancai-<version>-maven-repo.zip`: a complete Maven repository with the
AAR, its POM, Gradle module metadata and sources. Unzip it into your project, for example
into `libs/sancai-repo/`, and point Gradle at it:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("libs/sancai-repo") }
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.github.avalonskyafar:sancai:0.1.0")
}
```

Gradle then resolves the AndroidX dependencies by itself and your IDE finds the sources.

### 2.2 From the AAR file

Copy `sancai-<version>.aar` into `app/libs/` and declare the dependencies yourself, because
a bare AAR carries no dependency information:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(files("libs/sancai-0.1.0.aar"))
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.customview:customview:1.1.0")
}
```

### 2.3 As a source module

Copy the `sancai/` directory into your project (or add this repository as a Git submodule)
and include it:

```kotlin
// settings.gradle.kts
include(":sancai")
// app/build.gradle.kts
dependencies { implementation(project(":sancai")) }
```

The module uses the version catalog `gradle/libs.versions.toml` of this repository; copy
the entries its build script refers to (versions, libraries and the two plugins) into yours.

### 2.4 From JitPack

Once the repository is public and a version is tagged, JitPack can build it (the repository
contains a `jitpack.yml`):

```kotlin
repositories { maven { url = uri("https://jitpack.io") } }
dependencies { implementation("com.github.<owner>.<repository>:sancai:<tag>") }
```

---

## 3. First screen

No setup is needed. Put a Sancai view into any layout of any activity and it works: without
theme attributes it uses the built-in palette and follows the system's dark mode.

```xml
<!-- res/layout/activity_settings.xml -->
<io.github.avalonskyafar.sancai.widget.SancaiPage
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/page"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <io.github.avalonskyafar.sancai.widget.SancaiHeading
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:sancaiTitle="Protection"
        app:sancaiDetail="How strictly content is filtered" />

    <io.github.avalonskyafar.sancai.widget.SancaiSurface
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="10dp">

        <io.github.avalonskyafar.sancai.widget.SancaiSegmentedTrack
            android:id="@+id/strength"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:sancaiOptions="@array/strength_levels"
            app:sancaiSelectedIndex="2" />
    </io.github.avalonskyafar.sancai.widget.SancaiSurface>

    <io.github.avalonskyafar.sancai.widget.SancaiButton
        android:id="@+id/restore"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginStart="30dp"
        android:layout_marginTop="22dp"
        android:layout_marginEnd="30dp"
        android:text="Restore defaults" />
</io.github.avalonskyafar.sancai.widget.SancaiPage>
```

```kotlin
class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SancaiWindow.apply(this)                       // optional: canvas-coloured window, edge to edge
        setContentView(R.layout.activity_settings)

        val strength = findViewById<SancaiSegmentedTrack>(R.id.strength)
        strength.setOnSelectionChangeListener { _, index -> prefs.strength = index }

        findViewById<SancaiButton>(R.id.restore).setOnClickListener {
            SancaiDialog.confirm(this, "Restore all defaults?", "Every setting returns to its original value.",
                positiveLabel = "Restore", danger = true) { prefs.reset() }
        }
    }
}
```

The complete list of components, with their XML attributes and code APIs, is in the
[Component catalog](COMPONENT_CATALOG.md).

---

## 4. From XML

- Use the fully qualified class name as the tag
  (`io.github.avalonskyafar.sancai.widget.Sancai…`).
- Component attributes are in the app namespace and all start with `sancai`:
  `app:sancaiTitle`, `app:sancaiOptions`, `app:sancaiGlaze` and so on. Standard attributes
  such as `android:text`, `android:enabled`, `android:hint` and `android:src` are honoured
  where they make sense.
- A `SancaiSurface` (card) is a vertical `LinearLayout`; put rows and a `SancaiDivider`
  between row groups inside it. Dividers stretch from rim to rim by themselves.
- A card is slightly larger than it looks, because its shadow is drawn inside the view:
  1.4 dp at the sides, 1 dp above, 4.8 dp below. Leave vertical gaps as margins on the card,
  as in the example; the page rhythm is listed in
  [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md#5-space-and-layout).
- A card does not clip its children, so the shadows of the controls inside it can fade out
  around them. A child that scrolls (`ScrollView`, a list, a `RecyclerView`) is the
  exception and ends at its own edge. Plain content of your own, a line of text for
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
val card = SancaiSurface(context)

val track = SancaiSegmentedTrack(context).apply {
    setOptions("Off", "Gentle", "Standard", "Strict")
    select(2, animate = false)
    setOnSelectionChangeListener { _, index -> save(index) }
}
card.addView(track, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
card.addDivider(verticalMarginDp = 5f)
card.addView(SancaiEntryRow(context).apply {
    title = "Trusted accounts"
    detail = "Accounts that are never filtered"
    arrow = SancaiEntryRow.Arrow.END
    setOnClickListener { host.navigate("accounts") }
})
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
SancaiSegmentedTrack track = new SancaiSegmentedTrack(context);
track.setOptions("Off", "On");
track.select(1, false);
track.setOnSelectionChangeListener((view, index) -> prefs.setEnabled(index == 1));

SancaiTheme theme = SancaiTheme.of(context);
int ink = theme.getColors().getInk();

SancaiHaptics.tick(view);
```

---

## 7. Theming

The tokens and what they mean are described in
[DESIGN_SYSTEM.md](DESIGN_SYSTEM.md#11-theming). This section is the how-to.

### 7.1 Nothing to do

With no configuration, Sancai views use the built-in light palette, or the dark one when the
device is in night mode, and honour the system's "remove animations" and (Android 16+)
high-contrast text settings.

### 7.2 Through your app theme

```xml
<!-- res/values/themes.xml -->
<style name="Theme.MyApp" parent="Theme.Sancai">
    <!-- change only what you need -->
    <item name="sancaiColorAccent">#7A4B8F</item>
    <item name="sancaiGlazeCobalt">#A997E6</item>
    <item name="sancaiGlazeCobaltEdge">#8874D0</item>
    <item name="sancaiHighContrast">true</item>
</style>
```

`Theme.Sancai` follows night mode and also colours the window and the system bars (the light
navigation bar from Android 8.1);
`Theme.Sancai.Light` and `Theme.Sancai.Dark` are fixed. You do not have to inherit from
them: the `sancai*` attributes work in any theme, including AppCompat and Material themes.

Put night-mode overrides in `res/values-night/themes.xml` as usual.

### 7.3 From your own settings

Apps that let the user choose the appearance keep the choice in preferences and build the
theme from it:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SancaiTheme.resolver = SancaiTheme.Resolver { context ->
            val dark = when (settings.appearance) {
                Appearance.LIGHT -> false
                Appearance.DARK -> true
                Appearance.SYSTEM -> SancaiTheme.systemIsDark(context)
            }
            SancaiTheme(
                colors = if (dark) SancaiColors.dark() else SancaiColors.light(),
                textScale = if (settings.largeText) SancaiTheme.LARGE_TEXT_SCALE else 1f,
                reduceMotion = settings.reduceMotion || SancaiTheme.systemReducesMotion(context),
                haptics = settings.haptics,
            )
        }
    }
}
```

When the user changes a setting, recreate the activity. `SancaiThemeTransition` hides the
cut:

```kotlin
appearanceTrack.setOnSelectionChangeListener { track, index ->
    settings.appearance = Appearance.values()[index]
    SancaiThemeTransition.recreateAfter(track)        // waits for the thumb, then recreates
}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(...)
    SancaiThemeTransition.play(this)                  // fades the old look over the new one
}
```

The Showcase app does exactly this; see `ShowcaseSettings.kt` and `MainActivity.kt`.

### 7.4 For one part of a screen

```kotlin
val themed = SancaiTheme.wrap(context, SancaiTheme.dark())
val panel = SancaiSurface(themed)          // this card and what you build with `themed` are dark
```

### 7.5 Your own glazes

```kotlin
val glazes = SancaiGlazes.default().copy(
    cobalt = SancaiGlaze(bed = 0xFFA997E6.toInt(), edge = 0xFF8874D0.toInt(), onBed = Color.WHITE),
)
SancaiTheme.light().copy(glazes = glazes)
```

A glaze is three colours: the bed, a darker edge of the same hue, and the colour of labels
resting on it. For a single control, set its glaze directly (`switch.onGlaze = …`,
`track.setGlazes(…)`, `app:sancaiGlaze="celadon"`).

---

## 8. Windows and system bars

```kotlin
SancaiWindow.apply(activity)                 // canvas-coloured window; light or dark bar icons; edge to edge
SancaiWindow.padForSystemBars(rootView)      // keep content clear of the bars (and optionally the keyboard)
SancaiWindow.preferHighestRefreshRate(activity)   // optional, for screens built on finger-following
```

`SancaiPage` pads itself for the system bars and the keyboard, so a screen whose root is a
`SancaiPage` needs only the first call. Android 15 and later draw every app edge to edge;
on earlier versions `SancaiWindow.apply(activity, edgeToEdge = false)` keeps the classic
layout with canvas-coloured bars (a black navigation bar under a light theme before Android 8).

---

## 9. Living with other UI kits

Sancai views are ordinary `View`s. They can sit in the same layout as AppCompat or Material
widgets, inside a `RecyclerView`, a `Fragment`, a `ConstraintLayout`, or in a Compose screen
through `AndroidView`:

```kotlin
AndroidView(factory = { context ->
    SancaiSegmentedTrack(context).apply { setOptions("Off", "On") }
})
```

Things to keep in mind:

- **Clipping.** A Sancai view draws its shadow inside its own bounds, so it is never cut off
  by a parent. You do not need `clipChildren="false"`.
- **Scrolling parents.** Horizontal controls hand vertical movement back to the parent, so
  they behave in `ScrollView`, `NestedScrollView` and `RecyclerView`. Inside a horizontally
  scrolling parent (`ViewPager2`, a horizontal list) a track claims horizontal drags that
  start on it, which is usually what you want.
- **Material themes.** Sancai reads only its own `sancai*` attributes. It neither needs nor
  disturbs `colorPrimary` and friends.
- **Dialogs and sheets** are plain platform `Dialog`s and `PopupWindow`s. They do not need a
  `FragmentManager`.

---

## 10. Custom views in the same language

The painters and tokens are public so that your own views can match.

```kotlin
class Gauge(context: Context) : SancaiControl(context) {
    private val track = RectF()

    override fun onDraw(canvas: Canvas) {
        track.set(dp(1.5f), dp(1.5f), width - dp(1.5f), height - dp(6.5f))       // leave the shadow gutter
        val glaze = theme.glazes.celadon
        material.drawTrack(canvas, track, theme.bedColor(glaze), theme.bedEdgeColor(glaze))
        // ... a thumb with material.drawThumb(...), labels with theme.labelOnBed(glaze)
    }
}
```

| You need | Use |
|---|---|
| a card or capsule background | `SancaiSurfaceDrawable(context, radiusDp, pill, tint)` |
| a recessed field | `SancaiWellDrawable(context)` |
| a track, thumb, rim or shadow in `onDraw` | `SancaiMaterial` (`drawTrack`, `drawThumb`, `drawSurface`, `drawWell`, `drawFocusRim`) |
| the press effect | `SancaiPress.attach(host, content) { … }` |
| a glide | `SancaiMotion.glide()` with `SancaiMotion.CONTROL_MS`, or `SancaiControl.settle(…)` |
| haptics | `SancaiHaptics.tick(view)` |
| an icon | `SancaiIconDrawable(context, SancaiIcon.CHECK)` or `SancaiIconPainter.draw(…)` |
| text in the type scale | `SancaiText`, or `SancaiTextRole.X.applyTo(textView, theme)` |

The rules such a view should follow are listed in [dev/CONVENTIONS.md](dev/CONVENTIONS.md).

---

## 11. Testing

Sancai views run under Robolectric with native graphics, which is how the library itself is
tested (on the JVM, no device needed):

```kotlin
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StrengthTest {
    @Test fun `selecting strict is saved`() {
        val context = SancaiTheme.wrap(ApplicationProvider.getApplicationContext(), SancaiTheme.light())
        val track = SancaiSegmentedTrack(context).apply { setOptions("Off", "Gentle", "Standard", "Strict") }
        ...
    }
}
```

- `SancaiTheme.wrap(context, theme)` gives a test a fixed theme, independent of the host
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
| Something inside a card draws over the row below it | Cards do not clip their children. Scrolling views are clipped for you; for any other view that draws outside its bounds, set `clipBounds` on it or mark it with `isScrollContainer = true`. |
| The text of some rows is centred and of others not | Entry rows and control rows are centred by default. Set `alignment = Alignment.START` (`app:sancaiEntryAlignment`, `app:sancaiControlRowAlignment`) on every row of a card that should read from the edge. |
| The theme did not change after a setting changed | Views read the theme at construction. Recreate the activity (section 7.3) or rebuild the views. |
| A listener is not called after `select()` or `setChecked()` | By design: listeners are for user input. Call your own handler after setting the value. |
| A track inside a `ViewPager2` swallows page swipes | Swipes that start on the track belong to the track. Start the swipe outside it, or disable the track while paging. |
| A page host does not move when dragging over a custom scrolling view | Implement `canScrollVertically` / `canScrollHorizontally` on that view; the host asks it who should move. |
| Labels on blue or green tracks are hard to read | The default palette keeps the original white-on-glaze look. Turn on `sancaiHighContrast`; see [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md#12-accessibility). |
| Manifest merger or duplicate-class errors | Sancai declares nothing in the manifest. Check that the three AndroidX dependencies are not excluded and that `android.useAndroidX=true`. |
| Build fails on Windows with "path contains non-ASCII characters" | An Android Gradle Plugin check, unrelated to the library. Move the project, or set `android.overridePathCheck=true` in `gradle.properties`. |
