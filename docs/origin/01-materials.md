# Origin spec 01 — Materials, depth and colour

Source-accurate extraction from the Gravekeeper / 守目人 Android app (`com.gravekeeper`, versionName `2.1`, versionCode `23`, minSdk 30, targetSdk 36 — app/build.gradle:12-15). This file seeds the Sancai UI toolkit; it describes what the original code does, not what Sancai should do.

## 0. Scope and reading conventions

| Item | Value |
|---|---|
| In scope | `UiKit.Palette`; `TRACK_*` constants; `blend()` / `alphaColor()`; painters `drawBoundedOuterShadow`, `drawSoftLift`, `drawInnerDepth`, `drawSurfaceInnerDepth`, `drawThumbInnerDepth`, `drawRecessedTrack`, `drawNeutralThumb`; drawables `MaterialSurfaceDrawable`, `ContextMaterialDrawable`, `InsetFieldDrawable`, `SurfaceFillDrawable`, `DestructiveRevealDrawable`, `OnboardingSectionDrawable`; `ClippedImageView`; `divider()`; `res/values/colors.xml`, `icon_colors.xml`, `styles.xml`; `applyWindow()` bar colours |
| Out of scope (other slices) | Typography, row/page layout, control gestures and motion, dialogs/toast behaviour, page hosts. They are referenced here only where they fix the bounds a painter receives |
| Citation form | `File.java:LINE` against the tree under `Gravekeeper-master/Gravekeeper/app/src/main/` (Java in `java/com/gravekeeper/`, resources in `res/`) |
| Derived values | A hex next to a `blend(...)` expression was computed for this document with the exact `blend()` arithmetic of UiKit.java:1650-1656 (float32, `Math.round`). The expression is the source of truth |
| `INFERRED` | Marks anything deduced rather than read from a source line |
| `OBSERVED` | Marks a pixel value sampled from `Gravekeeper-master/screenshots/*.png` (1080 px wide; geometry matches density 2.75, see §20) |

### 0.1 Unit rules that affect every number below

| Rule | Detail | Cite |
|---|---|---|
| `ui.dp(v)` rounds to whole pixels | `Math.round(value * density)` returned as `int`. Every `ui.dp(...)` term in a painter is rounded on its own before it is added or subtracted, so 0.45dp, 0.5dp, 0.85dp, 1.4dp are not sub-pixel values on screen | UiKit.java:249-251 |
| `ContextMaterialDrawable.px(v)` does not round | `dp * density` as `float` | UiKit.java:2072 |
| `ui.sp(v)` is not rounded | `value * scaledDensity` | UiKit.java:254-256 |
| Alpha values | Integers 0-255 passed to `Color.argb` / `alphaColor`. Where a comment gives a CSS percentage the code literal is `round(pct * 255)` (9% -> 23, 12% -> 31, 16% -> 41, 13% -> 33, 23% -> 59, 19% -> 48, 4% -> 10, 5% -> 13, 8% -> 20, 10% -> 26, 34% -> 87). INFERRED relation; the literals are what is cited | UiKit.java:1658-1661, 1722-1725 |
| rem -> dp | Comments quote the approved CSS in `rem`; the adjacent literals equal `rem * 16` in dp (0.22rem -> 3.52, 0.5rem -> 8, 0.38rem -> 6.08, 0.8rem -> 12.8, 0.55rem -> 8.8, 1.1rem -> 17.6). INFERRED: 1rem = 16dp | UiKit.java:1722-1725, 1838-1842 |

Rounded pixel results of the fractional dp literals used by the painters (computed with the UiKit.java:250 formula):

| dp literal | d=1.0 | d=1.5 | d=2.0 | d=2.625 | d=2.75 | d=3.0 | d=3.5 | d=4.0 |
|---|---|---|---|---|---|---|---|---|
| 0.45 | 0 | 1 | 1 | 1 | 1 | 1 | 2 | 2 |
| 0.5 | 1 | 1 | 1 | 1 | 1 | 2 | 2 | 2 |
| 0.7 | 1 | 1 | 1 | 2 | 2 | 2 | 2 | 3 |
| 0.85 | 1 | 1 | 2 | 2 | 2 | 3 | 3 | 3 |
| 0.9 | 1 | 1 | 2 | 2 | 2 | 3 | 3 | 4 |
| 1 | 1 | 2 | 2 | 3 | 3 | 3 | 4 | 4 |
| 1.1 | 1 | 2 | 2 | 3 | 3 | 3 | 4 | 4 |
| 1.2 | 1 | 2 | 2 | 3 | 3 | 4 | 4 | 5 |
| 1.28 | 1 | 2 | 3 | 3 | 4 | 4 | 4 | 5 |
| 1.4 | 1 | 2 | 3 | 4 | 4 | 4 | 5 | 6 |
| 1.5 | 2 | 2 | 3 | 4 | 4 | 5 | 5 | 6 |
| 1.6 | 2 | 2 | 3 | 4 | 4 | 5 | 6 | 6 |
| 2.4 | 2 | 4 | 5 | 6 | 7 | 7 | 8 | 10 |
| 2.56 | 3 | 4 | 5 | 7 | 7 | 8 | 9 | 10 |
| 3.2 | 3 | 5 | 6 | 8 | 9 | 10 | 11 | 13 |
| 4.8 | 5 | 7 | 10 | 13 | 13 | 14 | 17 | 19 |
| 5.76 | 6 | 9 | 12 | 15 | 16 | 17 | 20 | 23 |
| 6.4 | 6 | 10 | 13 | 17 | 18 | 19 | 22 | 26 |
| 6.5 | 7 | 10 | 13 | 17 | 18 | 20 | 23 | 26 |
| 7.68 | 8 | 12 | 15 | 20 | 21 | 23 | 27 | 31 |
| 10.4 | 10 | 16 | 21 | 27 | 29 | 31 | 36 | 42 |
| 13.44 | 13 | 20 | 27 | 35 | 37 | 40 | 47 | 54 |

### 0.2 Internal code names used by the source

| Code | Meaning in this slice | Implemented by |
|---|---|---|
| L1 | Page canvas (flat `p.bg`) | `pageColumn()` UiKit.java:329-334 |
| S1 | Surface card | `MaterialSurfaceDrawable(8, false, true)` UiKit.java:379-381 |
| M1 | Overlay / dialog material | `ContextMaterialDrawable` (service overlay) UiKit.java:394-396; dialog card `MaterialSurfaceDrawable(16, false, true)` UiKit.java:1519 |
| K1 | Recessed coloured track | `drawRecessedTrack` UiKit.java:1832-1893 |
| UI-001 | Main power switch (K1 with its own larger outer shadow) | `drawRecessedTrack(..., mainPower=true)` UiKit.java:2785 |
| K3 | Neutral thumb | `drawNeutralThumb` UiKit.java:1896-1937 |
| K4 | Capsule button | `MaterialSurfaceDrawable(999, danger, true)` UiKit.java:985 |
| K5 | Compact toggle (K1 + K3 at small size) | UiKit.java:562-586 |
| K8 | Precise stepper (K1 + fixed K3) | UiKit.java:714-751 |
| K10 | Bulk editor field | `InsetFieldDrawable(9)` UiKit.java:405-407 |
| D1 | Divider | `divider()` UiKit.java:460-465 |
| E3 | Error capsule | `MaterialSurfaceDrawable(999, true, true)` UiKit.java:1421 |
| R2 | Embedded account row | `SurfaceFillDrawable` UiKit.java:451-453 |

## 1. Colour utilities

### 1.1 `blend(first, second, secondAmount)` — UiKit.java:1650-1656

| Aspect | Value | Cite |
|---|---|---|
| Signature | `public static int blend(int first, int second, float secondAmount)` | UiKit.java:1650 |
| Clamp | `a = max(0f, min(1f, secondAmount))` | UiKit.java:1651 |
| Per channel | `Math.round(c1 * (1 - a) + c2 * a)` in float32 for R, G, B | UiKit.java:1653-1655 |
| Alpha | Dropped. Result is `Color.rgb(...)`, always opaque (0xFF) regardless of input alphas | UiKit.java:1652 |
| Colour space | Plain sRGB-encoded channel interpolation (no gamma handling) | UiKit.java:1653-1655 |
| Rounding | `Math.round(float)` = floor(x + 0.5); results are 8-bit ints, so small amounts can round back to the first colour (see §13, §16) | UiKit.java:1653 |

### 1.2 `alphaColor(color, alpha)` — UiKit.java:1658-1661

| Aspect | Value | Cite |
|---|---|---|
| Signature | `private static int alphaColor(int color, int alpha)` | UiKit.java:1658 |
| Behaviour | `Color.argb(clamp(alpha, 0, 255), R, G, B)` — replaces the alpha, never multiplies with the existing alpha | UiKit.java:1659-1660 |

## 2. `UiKit.Palette` — UiKit.java:82-115

Constructor `Palette(boolean dark, boolean contrast)` is package-private; all fields are `public final int` (UiKit.java:83-84). It is built from `prefs.resolveDark(activity)` and `prefs.highContrast()` (UiKit.java:208-209), and again inside `ContextMaterialDrawable` and `StatusOverlayController` for service contexts (UiKit.java:2065-2066, StatusOverlayController.java:150-151).

| Token | Light | Dark | Cite (light / dark) |
|---|---|---|---|
| `bg` | `rgb(255,255,255)` `#FFFFFF` | `rgb(24,29,36)` `#181D24` | UiKit.java:101 / 86 |
| `surface` | `rgb(255,255,255)` `#FFFFFF` | `rgb(31,37,46)` `#1F252E` | UiKit.java:102 / 87 |
| `raised` | `rgb(255,255,255)` `#FFFFFF` | `rgb(38,45,56)` `#262D38` | UiKit.java:103 / 88 |
| `ink` | `rgb(26,28,31)` `#1A1C1F` | `rgb(235,239,244)` `#EBEFF4` | UiKit.java:104 / 89 |
| `muted` | `rgb(142,143,144)` `#8E8F90` | `rgb(161,172,187)` `#A1ACBB` | UiKit.java:105 / 90 |
| `muted` (high contrast) | `rgb(76,78,80)` `#4C4E50` | `rgb(201,208,217)` `#C9D0D9` | UiKit.java:105 / 90 |
| `border` | `rgb(237,237,237)` `#EDEDED` | `rgb(54,64,77)` `#36404D` | UiKit.java:106 / 91 |
| `border` (high contrast) | `rgb(209,210,210)` `#D1D2D2` | `rgb(88,101,118)` `#586576` | UiKit.java:106 / 91 |
| `blue` | `rgb(49,90,155)` `#315A9B` | `rgb(91,126,164)` `#5B7EA4` | UiKit.java:108 / 92 |
| `red` | `rgb(200,88,88)` `#C85858` | `rgb(151,89,96)` `#975960` | UiKit.java:109 / 93 |
| `yellow` | `rgb(224,179,58)` `#E0B33A` | `rgb(157,128,75)` `#9D804B` | UiKit.java:110 / 94 |
| `green` | `rgb(46,145,104)` `#2E9168` | `rgb(89,133,105)` `#598569` | UiKit.java:111 / 95 |
| `shadow` | `argb(23,26,28,31)` `#171A1C1F` | `argb(100,0,0,0)` `#64000000` | UiKit.java:112 / 96 |

| Fact | Detail | Cite |
|---|---|---|
| Light L1 = S1 = raised | All three are pure white. Source comment: "both --background and --secondary resolve to white. L1/S1 depth comes from the approved short shadow and inner shading, not from tinting the material blue-grey" | UiKit.java:98-103 |
| High contrast scope | Changes only `muted` and `border` (plus the D1 rule, §18). No other token branches on `contrast` | UiKit.java:90-91, 105-106 |
| Semantic comment | Light `blue/red/yellow/green` are "Frozen UI-002 semantic values from the approved HTML/CSS sample" | UiKit.java:107 |
| `shadow` is never read | No painter or page reads `p.shadow`; outer shadows use `p.ink`, black, or `rgb(30,39,50)` literals (§6, §7, §12) | whole-tree search: only UiKit.java:96, 112 assign it |
| `yellow` is never read | Declared in both themes, no reader anywhere in `app/src/main` | whole-tree search: only UiKit.java:94, 110 |
| `green` readers | Status values only: UiKit.java:1057, MainActivity.java:689, 838 | — |
| First-launch palette | `UiKit.forFirstLaunch()` forces `dark=false`; the high-contrast preference still applies | UiKit.java:201-203, 208-209 |
| INFERRED relations | Light `border` `#EDEDED` equals `blend(bg, ink, 0.08)` (the unused `edgeBase` of UiKit.java:2008-2009 computes exactly `#EDEDED`); light high-contrast `border` `#D1D2D2` equals `blend(#FFFFFF, #1A1C1F, 0.20)` | computed |

## 3. `TRACK_*` constants — UiKit.java:51-66

Light-theme K1 / UI-001 bed colours and their 1dp edge colours. Source comment: these are "the final rendered beds confirmed in the approved More-page screenshot, not the darker semantic source colours previously (and incorrectly) painted directly onto Android Canvas" (UiKit.java:51-53).

| Constant | Bed | Cite | Edge constant | Edge | Cite |
|---|---|---|---|---|---|
| `TRACK_OFF` | `rgb(206,131,83)` `#CE8353` | UiKit.java:54 | `TRACK_OFF_EDGE` | `rgb(190,105,56)` `#BE6938` | UiKit.java:62 |
| `TRACK_LIGHT` | `rgb(222,190,119)` `#DEBE77` | UiKit.java:58 | `TRACK_LIGHT_EDGE` | `rgb(194,158,82)` `#C29E52` | UiKit.java:63 |
| `TRACK_STANDARD` | `rgb(134,172,247)` `#86ACF7` | UiKit.java:59 | `TRACK_STANDARD_EDGE` | `rgb(105,144,224)` `#6990E0` | UiKit.java:64 |
| `TRACK_STRICT` | `rgb(128,184,151)` `#80B897` | UiKit.java:60 | `TRACK_STRICT_EDGE` | `rgb(91,151,116)` `#5B9774` | UiKit.java:65 |
| `TRACK_THEME_LIGHT` | `rgb(226,237,247)` `#E2EDF7` | UiKit.java:61 | `TRACK_THEME_LIGHT_EDGE` | `rgb(183,204,224)` `#B7CCE0` | UiKit.java:66 |

Edge hex values are confirmed by UI_FROZEN_IMPLEMENTATION_MAP.md:46-50.

### 3.1 Which state gets which bed (light theme)

| Control | Position -> bed | Cite |
|---|---|---|
| `SegmentControl`, 2 labels | 0 `TRACK_OFF`, 1 `TRACK_STANDARD` | UiKit.java:2546 |
| `SegmentControl`, 3 labels | 0 `TRACK_OFF`, 1 `TRACK_LIGHT`, 2 `TRACK_STANDARD` | UiKit.java:2547-2548 |
| `SegmentControl`, any other count (4 in the app) | 0 `TRACK_OFF`, 1 `TRACK_LIGHT`, 2 `TRACK_STANDARD`, 3 and above `TRACK_STRICT` | UiKit.java:2549-2551 |
| `SegmentControl`, theme selector (labels exactly `"跟随系统"`, `"浅色"`, `"深色"`) | 0 `TRACK_OFF`, 1 `TRACK_THEME_LIGHT`, 2 `TRACK_STANDARD` | UiKit.java:2542-2545, 2604-2609 |
| Edges | Same mapping with the `*_EDGE` constants | UiKit.java:2554-2565 |
| Between positions | `blend(semantic(low), semantic(high), position - low)` with `low = floor(position)`, `high = low + 1`, both clamped to the label range; the edge colour is interpolated the same way | UiKit.java:2567-2574 |
| `PowerSwitch` | `blend(TRACK_OFF, TRACK_STANDARD, trackPosition)`; edge `blend(TRACK_OFF_EDGE, TRACK_STANDARD_EDGE, trackPosition)` | UiKit.java:2778-2784 |
| `CompactToggle` | Same two blends as `PowerSwitch` | UiKit.java:568-574 |
| `PreciseValueControl` | Fixed `TRACK_STANDARD` / `TRACK_STANDARD_EDGE` | UiKit.java:719-723 |
| Half-way example | `blend(TRACK_OFF, TRACK_STANDARD, 0.5)` = `rgb(170,152,165)` `#AA98A5`; edge `rgb(148,125,140)` `#947D8C` | computed |
| Mapping is positional, not semantic | `semantic(index)` looks only at the label count and index (plus the exact theme-selector strings). The label arrays the app passes: 2 labels `{"关闭", "开启"}` and `{"抖音", "快手"}`; 3 labels `{"关闭", "提醒", "划走"}`, `{"跟随系统", "浅色", "深色"}` and `{"MAX", "AVERAGE", "LATEST"}`; 4 labels `{"关闭", "轻度", "标准", "严格"}`. The developer row `"证据聚合"` (`MAX / AVERAGE / LATEST`) and the platform picker (`抖音 / 快手`) therefore get orange / yellow / blue and orange / blue beds although their options are not off / on levels | MainActivity.java:86-88, 1047; DeveloperOptionsActivity.java:38, 195-196; WhitelistAccountsPage.java:101 |

### 3.2 Dark theme: one neutral bed for every state

| Element | Expression | Result | Cite |
|---|---|---|---|
| Bed (all controls, all states) | `blend(p.bg, p.surface, 0.14f)` | `rgb(25,30,37)` `#191E25` | UiKit.java:570, 720, 2582, 2781 |
| Edge (all controls, all states) | `blend(p.ink, p.bg, 0.93f)` | `rgb(39,44,51)` `#272C33` | UiKit.java:573, 722, 2589, 2783 |
| Rule | "深色模式仍使用已确认的中性暗槽，不直接铺浅色主题的彩色轨道" | — | UI_FROZEN_IMPLEMENTATION_MAP.md:51 |

In dark the selected state is carried only by thumb position and by label tint (§3.3); the `TRACK_*` constants never fill a dark bed.

### 3.3 Foreground colours drawn on a bed (cross-reference)

| Element | Light | Dark | Cite |
|---|---|---|---|
| Segment label on the thumb (candidate) | `p.ink` | `p.ink` | UiKit.java:2536 |
| Segment label off the thumb, default | `Color.WHITE` `#FFFFFF` | `blend(semantic(index), p.ink, 0.62f)` for 2 labels, `0.70f` otherwise | UiKit.java:2598, 2600-2601 |
| ... theme selector while candidate is 1 (`浅色`) | `rgb(52,72,92)` `#34485C` | as above | UiKit.java:2594 |
| ... candidate is 1 and at least 3 labels (yellow bed) | `rgb(44,33,4)` `#2C2104` | as above | UiKit.java:2596 |
| ... candidate is 3 and exactly 4 labels (green bed) | `rgb(244,255,249)` `#F4FFF9` | as above | UiKit.java:2597 |
| Dark label tints, computed | — | 2 labels: OFF `#E0C6B7`, STANDARD `#C5D6F5`. Otherwise: OFF `#E2CFC4`, LIGHT `#E7E0CF`, STANDARD `#CDDBF5`, STRICT `#CBDFD8`, THEME_LIGHT `#E8EEF5` | computed from UiKit.java:2600-2601 |
| `PowerSwitch` hint text (`"开启保护"` / `"关闭保护"`) | `blend(track, p.ink, 0.76f)`: off `#45352B`, on `#343F53` | `blend(track, p.ink, 0.82f)` = `#C5C9CF` | UiKit.java:2815-2818 |
| `PowerSwitch` thumb glyph and text | `p.ink` | `p.ink` | UiKit.java:2802, 2808 |
| `PreciseValueControl` `−` / `+` | `Color.WHITE` | `p.muted` | UiKit.java:741 |
| `PreciseValueControl` value | `p.ink` | `p.ink` | UiKit.java:749 |

## 4. Resource colours, themes and window bars

### 4.1 `res/values/colors.xml` and `icon_colors.xml`

| Resource | Value | Cite | Used by |
|---|---|---|---|
| `guard_primary` | `#315A9B` | colors.xml:3 | `android:colorAccent` of every theme (styles.xml:5, 13, 23, 50). Equals light `p.blue`. The dark theme also uses this value, not dark `p.blue` `#5B7EA4` |
| `guard_background` | `#FFFFFF` | colors.xml:4 | Bars of the base `Theme.Gravekeeper` (styles.xml:6-7) |
| `guard_text` | `#202124` | colors.xml:5 | No reference in styles, manifest or Java. Differs from light `p.ink` `#1A1C1F` |
| `guard_background_native` | `#FFFFFF` | colors.xml:6 | `Theme.Gravekeeper.Light` window background and bars (styles.xml:14-16) |
| `guard_background_dark` | `#181D24` | colors.xml:7 | `Theme.Gravekeeper.Dark` window background and bars (styles.xml:24-26). Equals dark `p.bg` |
| `guard_text_dark` | `#EBEFF4` | colors.xml:8 | `android:textColorPrimary` of the dark theme (styles.xml:29). Equals dark `p.ink` |
| `icon_background` | `#FFFFFF` | icon_colors.xml:3 | Launcher icon background in all three variants: legacy layer-list `<solid>` (mipmap-anydpi/ic_gravekeeper_launcher.xml:6), adaptive `<background>` (mipmap-anydpi-v26/ic_gravekeeper_launcher.xml:3) and adaptive + monochrome (mipmap-anydpi-v33/ic_gravekeeper_launcher.xml:3) |

Literal colours in `res/drawable/*.xml` (not routed through `colors.xml`; no Java code reads `R.color.*`, the only resource colour used from Java is `android.R.color.transparent` at UiKit.java:1603):

| File | Value | Cite | Role |
|---|---|---|---|
| `ic_gravekeeper_launcher_monochrome.xml` | `#FF000000` (both paths) | ic_gravekeeper_launcher_monochrome.xml:10, 13 | Themed-icon silhouette, 108 x 108dp viewport 108 |
| `ic_notification.xml` | `#FFFFFFFF` (both paths) | ic_notification.xml:8, 11 | Status-bar small icon, 24 x 24dp, viewport 108 |
| `ic_gravekeeper_launcher_foreground.xml` | no colour; `<inset>` of `@drawable/ic_launcher_art` with `android:inset="9%"` | ic_gravekeeper_launcher_foreground.xml:2-5 | Adaptive foreground |

### 4.2 `res/values/styles.xml`

| Theme | Parent | Items | Cite |
|---|---|---|---|
| `Theme.Gravekeeper` | `android:style/Theme.Material.Light.NoActionBar` | `fontFamily=sans`; `colorAccent=@color/guard_primary`; `navigationBarColor` and `statusBarColor` `=@color/guard_background`; `windowLightStatusBar=true`. No `windowBackground`, no `windowLightNavigationBar` | styles.xml:3-9 |
| `Theme.Gravekeeper.Light` | `android:style/Theme.Material.Light.NoActionBar` (explicit parent) | `fontFamily=sans`; `colorAccent=@color/guard_primary`; `windowBackground`, `navigationBarColor`, `statusBarColor` `=@color/guard_background_native`; `windowLightStatusBar=true`; `windowLightNavigationBar=true` | styles.xml:11-19 |
| `Theme.Gravekeeper.Dark` | `android:style/Theme.Material.NoActionBar` | `fontFamily=sans`; `colorAccent=@color/guard_primary`; `windowBackground`, `navigationBarColor`, `statusBarColor` `=@color/guard_background_dark`; `windowLightStatusBar=false`; `windowLightNavigationBar=false`; `textColorPrimary=@color/guard_text_dark` | styles.xml:21-30 |
| `Theme.Gravekeeper.Spatial.Light` | `Theme.Gravekeeper.Light` | `windowIsTranslucent=true`; `windowBackground=@android:color/transparent`; `windowIsFloating=false`; `backgroundDimEnabled=false`; `windowDisablePreview=true` | styles.xml:32-38 |
| `Theme.Gravekeeper.Spatial.Dark` | `Theme.Gravekeeper.Dark` | Same five items | styles.xml:40-46 |
| `Theme.Gravekeeper.FirstLaunch` | `android:style/Theme.Material.Light.NoActionBar` | `fontFamily=sans`; `colorAccent=@color/guard_primary`; `windowBackground`, `navigationBarColor`, `statusBarColor` `=@android:color/white`; `windowLightStatusBar=true`; `windowLightNavigationBar=true`; `forceDarkAllowed=false` | styles.xml:48-57 |

### 4.3 Which theme is applied where

| Rule | Detail | Cite |
|---|---|---|
| Manifest default | `android:theme="@style/Theme.Gravekeeper"` on `<application>` | AndroidManifest.xml:38 |
| First launch | `Theme.Gravekeeper.FirstLaunch` in the manifest and again through `UiKit.applyFirstLaunchTheme()` before `super.onCreate` | AndroidManifest.xml:63; UiKit.java:140-142; FirstLaunchActivity.java:50 |
| All other activities | `UiKit.applyPreferredTheme()` before `super.onCreate`: dark -> `Theme_Gravekeeper_Dark`, otherwise `Theme_Gravekeeper_Light` | UiKit.java:126-130; MainActivity.java:112; DeveloperOptionsActivity.java:64; ConfigEditorActivity.java:39; RecoveryActivity.java:12 |
| Dark resolution | `theme()==THEME_DARK(2)` -> dark; `THEME_LIGHT(1)` -> light; `THEME_SYSTEM(0)`, the default, follows `uiMode & UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES` | AppPreferences.java:8-10, 24, 41-47 |
| Spatial themes | `applySpatialOverlayTheme()` exists but has no caller; the two `Spatial` themes are never applied at runtime | UiKit.java:133-137 (definition only) |
| XML vs Java | Themes only fix the first frame (window background, bar colours, accent). All component colour set by app code comes from `Palette` in Java; no app code reads a theme attribute. INFERRED from platform behaviour: framework-drawn parts of stock widgets still take the theme's `colorAccent` (`#315A9B` in every theme), i.e. the `EditText` cursor, selection handles and selection highlight of the five input fields in §13.1, because the app replaces only their background and text/hint colours | UiKit.java:205-212, 1490-1502; styles.xml:5, 13, 23, 50 |
| Theme switch | Activity recreation with a bitmap of the old palette cross-faded out over the new one: alpha 1 -> 0, 300 ms, `PathInterpolator(0.2f, 0f, 0f, 1f)`. No reduced-motion branch. Details belong to the motion slice | UiKit.java:145-184; MainActivity.java:143, 1286 |

### 4.4 `applyWindow()` — UiKit.java:214-247 (runs in every `UiKit` constructor, UiKit.java:211)

| Step | Light | Dark | Cite |
|---|---|---|---|
| `window.setStatusBarColor(p.bg)` | `#FFFFFF` | `#181D24` | UiKit.java:237 |
| `window.setNavigationBarColor(p.bg)` | `#FFFFFF` | `#181D24` | UiKit.java:238 |
| `SYSTEM_UI_FLAG_LIGHT_STATUS_BAR` (SDK >= 23) | set | not set | UiKit.java:240 |
| `SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR` (SDK >= 26) | set | not set | UiKit.java:241 |
| `decorView.setSystemUiVisibility(flags)` | both flags | `0` | UiKit.java:242 |
| SDK >= 35 override | both bar colours become `Color.TRANSPARENT` | same | UiKit.java:243-246 |
| SDK >= 35 insets | `applySystemInsets(root)` adds `WindowInsets.Type.systemBars()` to the root's original padding; it returns immediately below SDK 35 | same | UiKit.java:269-282 |
| Roots that call `applySystemInsets` | `GestureHost` (MainActivity.java:124), developer stage (DeveloperOptionsActivity.java:71), config editor page column (ConfigEditorActivity.java:55), first-launch root (FirstLaunchActivity.java:70). `RecoveryActivity` does not call it | — | — |
| Refresh rate (not colour) | Chooses the supported `Display.Mode` with the current physical size and the highest refresh rate; sets `preferredDisplayModeId` and `preferredRefreshRate` | same | UiKit.java:216-236 |
| First launch | `forceLight` -> light column regardless of preference | — | UiKit.java:201-208 |

Net effect: on SDK 30-34 the bars are opaque `p.bg`; on SDK 35 and later they are transparent over a root whose own background is `p.bg` and which is padded by the bar insets.

### 4.5 Flat `p.bg` fills (L1)

| Host | Cite |
|---|---|
| `pageColumn()` (padding `dp(12), dp(22), dp(12), dp(34)`) | UiKit.java:329-334 |
| `scroll()`; `embeddedScroll()` uses `Color.TRANSPARENT` instead | UiKit.java:362 |
| `GestureHost` | MainActivity.java:1658 |
| Main page root | MainActivity.java:266 |
| Developer stage | DeveloperOptionsActivity.java:70 |
| First-launch root | FirstLaunchActivity.java:69 |
| `SpatialActivityHost`, and its parent placeholder when no snapshot exists | UiKit.java:1228, 1233 |

## 5. Depth model overview

The app never uses `View` elevation for these materials: `setElevation(0)` is set explicitly on S1, K4, error capsule and toast (UiKit.java:372, 401, 986, 1422, 1447). Depth is painted in four layers, always in this order: outer shadow, body, inner depth, edge stroke.

### 5.1 Shadow gutters (material rect inside the host bounds)

A drawable or control view is larger than the visible material; the difference is the room in which the outer shadow is painted.

| Host | Left / right | Top | Bottom | Cite |
|---|---|---|---|---|
| `MaterialSurfaceDrawable`, `shadow=true` (S1, K4, E3, toast, dialog) | `dp(1.4)` | `dp(1)` | `dp(4.8)` | UiKit.java:1957-1964 |
| `MaterialSurfaceDrawable`, `shadow=false` | `dp(0.5)` | `dp(0.5)` | `dp(0.5)` | UiKit.java:1959-1961 |
| `ContextMaterialDrawable` | `px(1.4)` | `px(1)` | `px(4.8)` | UiKit.java:2076-2077 |
| `InsetFieldDrawable` | `dp(0.5)` | `dp(0.5)` | `dp(0.5)` | UiKit.java:2201-2202 |
| `SurfaceFillDrawable`, `DestructiveRevealDrawable` | 0 | 0 | 0 | UiKit.java:2256, 2270; 2298-2299 |
| `OnboardingSectionDrawable`, top section | `dp(0.7)` | `dp(0.7)` | 0 | UiKit.java:2355-2359 |
| `OnboardingSectionDrawable`, bottom section | `dp(0.7)` | 0 | `dp(4.8)` | UiKit.java:2355-2359 |
| `SegmentControl` track (K1) | `dp(1.5)` | `dp(1.5)` | `dp(6.5)` | UiKit.java:2496-2502 |
| `PreciseValueControl` track (K8) | `dp(1.5)` | `dp(1.5)` | `dp(6.5)` | UiKit.java:716-717 |
| `CompactToggle` track (K5) | `dp(1.5)` | `dp(1)` | `dp(3)` | UiKit.java:564-566, 576 |
| `PowerSwitch` track (UI-001) | `dp(2.5)` | `dp(2)` | `dp(10)` | UiKit.java:2769-2775 |

### 5.2 Host sizes used by the app (they determine pill radius = track height / 2)

| Control | View size | Resulting track | Cite |
|---|---|---|---|
| `SegmentControl` in a control row | height `dp(60)` | 52dp high ("The 52dp frozen track keeps an 8dp internal shadow gutter") | UiKit.java:504-506 |
| `SegmentControl` in the add-account form | height `dp(60)` | 52dp high | WhitelistAccountsPage.java:102 |
| `PowerSwitch` | height `dp(92)`, full width | 80dp high ("UI-001 remains an 80dp capsule; the larger view only reserves room for its soft shadow") | MainActivity.java:286-287 |
| `CompactToggle` | `dp(61)` x `dp(34)` | 58 x 30dp | WhitelistAccountsPage.java:234 |
| `PreciseValueControl` | height `dp(61)` | 53dp high | DeveloperOptionsActivity.java:590 |

### 5.3 Thumb placement inside a track

| Control | Inset from track | Thumb size | Clipped to track pill? | Cite |
|---|---|---|---|---|
| `SegmentControl` | `dp(4)` all sides | one segment wide: `(track.width - 2*dp(4)) / labels.length`; height `track.height - 2*dp(4)` | Yes, `canvas.clipPath(trackClip)` wraps `drawNeutralThumb` | UiKit.java:2500, 2508-2522 |
| `PowerSwitch` | `dp(5.6)` all sides | width `max(dp(112), track.width * 0.56f - dp(5.6))`; height `track.height - 2*dp(5.6)` | Yes | UiKit.java:2773, 2787-2798 |
| `CompactToggle` | `dp(2.8)` | square `min(dp(26.4), track.height - 2*dp(2.8))`; pressed adds `dp(1)` to its top | No clip | UiKit.java:567, 580-585 |
| `PreciseValueControl` | `dp(4)` top and bottom | width `track.width * 0.42f`, centred | No clip | UiKit.java:726-730 |

### 5.4 Other depth cues outside the painters

| Cue | Value | Cite |
|---|---|---|
| Dialog scrim | `FLAG_DIM_BEHIND`, `dimAmount = 0.27f`, window background transparent | UiKit.java:1603-1607 |
| Dialog and toast layers | `View.LAYER_TYPE_HARDWARE` for the whole card / toast lifetime so the material is rasterised once | UiKit.java:1450, 1521 |
| Overlay panel layer | `View.LAYER_TYPE_SOFTWARE`; view alpha default `0.88f` (configurable) | StatusOverlayController.java:22, 50, 157 |
| S1 clipping | `setClipToOutline(false)`, `setClipChildren(false)`, `setClipToPadding(false)`; padding `dp(9)` all sides (`surfacePaddingDp()`) | UiKit.java:267, 367-377 |
| No blur | No `FLAG_BLUR_BEHIND` / `setBlurBehindRadius` anywhere (asserted by a test) | UiInteractionContractTest.java:170-176 |

## 6. `drawBoundedOuterShadow` — UiKit.java:1672-1713

Signature: `private static void drawBoundedOuterShadow(Canvas canvas, Paint paint, RectF bounds, float radius, UiKit ui, float blurDp, float offsetDp, int opacity)`.

Purpose stated in source (UiKit.java:1663-1671): "Draws the frozen CSS outer shadow in the finite gutter owned by an Android View. Android clips a View's display list, while CSS shadows may extend outside the element. A native mask blur therefore ends as a rectangular band when the frozen shadow is wider than the View's finite gutter. Use enough low-alpha shells to reproduce a continuous falloff that reaches zero before that boundary. The material body drawn by the caller covers the shells' centres, leaving only the soft outer lift."

It is only called on light-theme paths (§6.2). There is no `MaskFilter`, no `setShadowLayer` and no canvas translation: the downward bias comes from the three different reaches.

### 6.1 Algorithm

| Step | Operation | Cite |
|---|---|---|
| 1 | Remember `paint` colour, alpha and colour filter; restore all three on both exits | UiKit.java:1674-1676, 1687-1689, 1710-1712 |
| 2 | `clip = canvas.getClipBounds()` | UiKit.java:1677 |
| 3 | `availableLeft = max(0, bounds.left - clip.left)`; `availableTop = max(0, bounds.top - clip.top)`; `availableRight = max(0, clip.right - bounds.right)`; `availableBottom = max(0, clip.bottom - bounds.bottom)` | UiKit.java:1678-1681 |
| 4 | `sideReach = min(min(availableLeft, availableRight), ui.dp(blurDp * 0.48f))` — one value for both sides, limited by the tighter side | UiKit.java:1683 |
| 5 | `topReach = min(availableTop, ui.dp(max(0f, blurDp - offsetDp) * 0.22f))` | UiKit.java:1684 |
| 6 | `bottomReach = min(availableBottom, ui.dp(offsetDp + blurDp * 0.72f))` | UiKit.java:1685 |
| 7 | Return without drawing when `sideReach <= 0.1f && topReach <= 0.1f && bottomReach <= 0.1f` | UiKit.java:1686-1691 |
| 8 | `paint.setStyle(FILL)`; `paint.setShader(null)` | UiKit.java:1693-1694 |
| 9 | `layers = 18` | UiKit.java:1695 |
| 10 | `layerAlpha = max(1, Math.round(opacity * 0.76f / layers))` | UiKit.java:1699 |
| 11 | For `i = 0 .. 17`: `factor = 1f - (float) i / layers` (1.0 first, 1/18 last) | UiKit.java:1700-1701 |
| 12 | Shell rect = `(bounds.left - sideReach*factor, bounds.top - topReach*factor, bounds.right + sideReach*factor, bounds.bottom + bottomReach*factor)` | UiKit.java:1702-1705 |
| 13 | Shell colour = `alphaColor(ui.p.ink, layerAlpha)` (light ink `#1A1C1F`) | UiKit.java:1706 |
| 14 | `canvas.drawRoundRect(shell, r, r)` with `r = radius + sideReach * factor * 0.38f` | UiKit.java:1707-1708 |

Source note on step 10 (UiKit.java:1696-1698): "Total accumulated opacity is intentionally slightly below the CSS source alpha. The available native gutter is narrower than an unconstrained browser blur, so preserving the full alpha would make the compact shadow look heavier."

### 6.2 Call sites and parameters

`layerAlpha` and the nominal reaches are derived from the cited literals with the formulas of §6.1.

| Caller | State | `blurDp` | `offsetDp` | `opacity` | `layerAlpha` | Nominal reach side / top / bottom (dp) | CSS quoted by the source comment | Cite |
|---|---|---|---|---|---|---|---|---|
| `drawSoftLift`, `compact=false` (S1, K4, E3, toast, dialog, onboarding action) | rest | 8 | 3.52 | 23 | 1 | 3.84 / 0.9856 / 9.28 | "S1 0 .22rem .5rem 9%" | UiKit.java:1722-1725 |
| `drawSoftLift`, `compact=false` | pressed | 4.6 | 1.8 | 20 | 1 | 2.208 / 0.616 / 5.112 | none | UiKit.java:1726-1729 |
| `drawSoftLift`, `compact=true` (K1 tracks except light UI-001) | rest | 12.8 | 6.08 | 31 | 1 | 6.144 / 1.4784 / 15.296 | "K1 0 .38rem .8rem 12%" | UiKit.java:1722-1725 |
| `drawSoftLift`, `compact=true` | pressed | 5.3 | 2.5 | 28 | 1 | 2.544 / 0.616 / 6.316 | none | UiKit.java:1726-1729 |
| `drawRecessedTrack`, `mainPower=true` (UI-001) | rest | 17.6 | 8.8 | 41 | 2 | 8.448 / 1.936 / 21.472 | "Frozen UI-001: 0 .55rem 1.1rem 16%" | UiKit.java:1838-1842 |
| `drawRecessedTrack`, `mainPower=true` | pressed | 9.92 | 4.48 | 33 | 1 | 4.7616 / 1.1968 / 11.6224 | "pressed .28rem .62rem 13%" | UiKit.java:1838-1842 |
| `drawNeutralThumb` (K3) | rest | 5.12 | 4.48 | 59 | 2 | 2.4576 / 0.1408 / 8.1664 | "Frozen K3: 0 .28rem .32rem 23%" | UiKit.java:1910-1913 |
| `drawNeutralThumb` | pressed | 2.72 | 1.92 | 48 | 2 | 1.3056 / 0.176 / 3.8784 | "pressed 0 .12rem .17rem 19%" | UiKit.java:1910-1913 |

The `compact=false` pressed row is unreachable: both callers pass `pressed=false` (UiKit.java:1990, 2375).

### 6.3 What the numbers produce

| Fact | Detail | Basis |
|---|---|---|
| `opacity` is quantised | Only two per-shell alphas occur: 1 (for 20, 23, 28, 31, 33) and 2 (for 41, 48, 59). S1 (23) and K1 (31) therefore use identical shell alpha and differ only in reach | derived from UiKit.java:1699 |
| Peak accumulated alpha | 18 stacked shells: alpha 1 -> 6.83% (17.41/255); alpha 2 -> 13.21% (33.70/255). That is below the CSS 9% / 12% / 16% / 23% the comments quote | derived |
| Falloff shape | Linear in distance: the shell count covering a point falls from 18 at the body edge to 1 at the outer reach. No Gaussian profile | derived from UiKit.java:1700-1705 |
| Innermost shell | `factor` never reaches 0; the smallest shell still exceeds the body by reach/18 | UiKit.java:1701 |
| Gutter wins on the sides and bottom at rest | For every track and surface host at rest the nominal side and bottom reach is larger than the gutter (§6.4), so the visible shadow size is the gutter size, not the CSS blur. Only some pressed states and the top reach fall below the gutter | derived |
| The clip is the host view | `getClipBounds()` of a view's own display list is the view rect. INFERRED from platform behaviour; consistent with OBSERVED S1 shading starting exactly at the view edge (x=33 = `dp(12)` page padding at density 2.75) and reaching the border at x=37 = `+dp(1.4)` in screenshot_settings.png row 1300 | UiKit.java:1677 |
| The clip is not always the host view | INFERRED from platform behaviour, not verified on device: the reach depends on whatever `getClipBounds()` returns for the canvas in use. The app also draws the whole content tree into a software `Canvas(bitmap)` for the spatial parent snapshot and the theme cross-fade. On a software canvas a child of a parent with `setClipChildren(false)` is not clipped to its own bounds, so tracks hosted in a control row (row and S1 both disable child clipping) and the power switch (its column disables child clipping) can receive a larger clip and paint up to their nominal reach in those bitmaps. A re-implementation that wants the gutter-sized shadow everywhere must clamp to the host bounds explicitly | UiKit.java:186-194, 374, 485, 1677-1685; MainActivity.java:270 |
| OBSERVED S1 bottom shadow | screenshot_settings.png, column 540, rows 603-615 (13 px = `dp(4.8)` at density 2.75): `#EDEDED` directly under the body (white minus 18 levels = 18 shells x 1 level; the same value as the border colour), then `#EFEFEF` fading by about one level per pixel to `#FEFEFE`; white from row 616 | sampled |
| OBSERVED UI-001 bottom shadow | screenshot_main.png, column 900, rows 1196-1221: `#DDDDDD` fading to `#FDFDFD`, white from row 1222; inside the `dp(10)` = 28 px gutter | sampled |

### 6.4 Effective reach per host = `min(gutter, nominal)`

Gutters from §5.1; valid when the clip equals the host view (INFERRED, see §6.3). All terms are rounded to px individually by `ui.dp`.

| Host | State | Side (dp) | Top (dp) | Bottom (dp) |
|---|---|---|---|---|
| S1 / K4 / E3 / toast / dialog (gutter 1.4 / 1 / 4.8) | rest | 1.4 (gutter) | 0.9856 (nominal) | 4.8 (gutter) |
| Onboarding action section (gutter 0.7 / 0 / 4.8) | rest | 0.7 (gutter) | 0 | 4.8 (gutter) |
| `SegmentControl`, `PreciseValueControl` track (gutter 1.5 / 1.5 / 6.5) | rest | 1.5 (gutter) | 1.4784 (nominal) | 6.5 (gutter) |
| same | pressed | 1.5 (gutter) | 0.616 (nominal) | 6.316 (nominal) |
| `CompactToggle` track (gutter 1.5 / 1 / 3) | rest | 1.5 (gutter) | 1 (gutter) | 3 (gutter) |
| same | pressed | 1.5 (gutter) | 0.616 (nominal) | 3 (gutter) |
| `PowerSwitch` track, light (gutter 2.5 / 2 / 10) | rest | 2.5 (gutter) | 1.936 (nominal) | 10 (gutter) |
| same | pressed | 2.5 (gutter) | 1.1968 (nominal) | 10 (gutter) |

K3 thumb shadow (clip = track pill bounding box where the control clips, otherwise the host view; §5.3):

| Host | Side (dp) | Top (dp) | Bottom (dp) | Note |
|---|---|---|---|---|
| `SegmentControl` | 2.4576 (nominal; the gap to the track is at least `dp(4)`) | 0.1408 (nominal; `ui.dp` rounds it to 0 px while `0.1408 * density < 0.5`) | 4 (track inset) | Shells are additionally cut by the round track clip |
| `PowerSwitch` | 2.4576 | 0.1408 | 5.6 (track inset) | Same clip |
| `CompactToggle` at 61x34dp | 2.4576 | 0.1408 | 5.8 (distance from thumb bottom to view bottom) | Not clipped: the shells cross the track's bottom edge and stroke and end at the view edge |
| `PreciseValueControl` at 61dp height | 2.4576 | 0.1408 | 8.1664 (nominal) | Not clipped: 4.1664dp (nominal) of the shadow lies below the track's bottom edge |

## 7. `drawSoftLift` — UiKit.java:1716-1763

Signature: `private static void drawSoftLift(Canvas canvas, Paint paint, RectF bounds, float radius, UiKit ui, boolean pressed, boolean compact)`. Doc comment: "Draws the frozen single soft outer shadow; dark mode retains its approved treatment." It saves and restores the caller's paint colour, alpha and colour filter (UiKit.java:1718-1720, 1733-1735, 1760-1762); source note: "setColor() also changes Paint alpha. Restore the caller's material state so the following gradient is opaque instead of being tinted by the colored track below." (UiKit.java:1758-1759).

| Theme | Behaviour | Cite |
|---|---|---|
| Light | Delegates to `drawBoundedOuterShadow` with the §6.2 rows, then returns | UiKit.java:1721-1737 |
| Dark | Four offset black shells, described below. Not bounded by the gutter: no clip query | UiKit.java:1738-1757 |

### 7.1 Dark shells

| Parameter | `compact=true` | `compact=false` | Cite |
|---|---|---|---|
| `spread` (dp), index 0..3 | 2.8, 2.05, 1.35, 0.72 | 4.8, 3.55, 2.3, 1.15 | UiKit.java:1739-1741 |
| `opacity` (0-255), index 0..3 | 16, 20, 25, 30 | 10, 14, 19, 25 | UiKit.java:1742-1744 |
| `scale` | `pressed ? 0.52f : 1f` | same | UiKit.java:1738 |
| Base colour | `Color.BLACK` (the `rgb(30,39,50)` arm of the ternary is dead code because light returned earlier) | same | UiKit.java:1745 |
| Draw order | `i = 3` down to `0` (smallest, most opaque first) | same | UiKit.java:1748 |
| `amount` | `ui.dp(spread[i]) * scale` | same | UiKit.java:1749 |
| Shell rect | `bounds` then `inset(-amount * 0.42f, -amount * 0.18f)` then `offset(0, amount * 0.56f)` | same | UiKit.java:1750-1752 |
| Shell colour | `Color.argb(Math.round(opacity[i] * scale), 0, 0, 0)` | same | UiKit.java:1753-1754 |
| Shell corner radius | `radius + amount * 0.32f` | same | UiKit.java:1755-1756 |

Derived extents per shell (nominal dp, before the px rounding of `ui.dp(spread)`): side overhang = `0.42 * amount`; bottom overhang = `(0.18 + 0.56) * amount = 0.74 * amount`; the shell's top edge sits `(0.56 - 0.18) * amount = 0.38 * amount` below the body top, so nothing shows above the body.

| Variant | Index | Spread | Alpha | Side overhang | Bottom overhang | Radius add |
|---|---|---|---|---|---|---|
| `compact=false`, rest | 0 | 4.8 | 10 | 2.016 | 3.552 | 1.536 |
| | 1 | 3.55 | 14 | 1.491 | 2.627 | 1.136 |
| | 2 | 2.3 | 19 | 0.966 | 1.702 | 0.736 |
| | 3 | 1.15 | 25 | 0.483 | 0.851 | 0.368 |
| `compact=true`, rest | 0 | 2.8 | 16 | 1.176 | 2.072 | 0.896 |
| | 1 | 2.05 | 20 | 0.861 | 1.517 | 0.656 |
| | 2 | 1.35 | 25 | 0.567 | 0.999 | 0.432 |
| | 3 | 0.72 | 30 | 0.3024 | 0.5328 | 0.2304 |

| Derived fact | Value |
|---|---|
| Pressed alphas `round(opacity * 0.52f)` | compact 8, 10, 13, 16; non-compact 5, 7, 10, 13 (non-compact pressed is unreachable, §6.2) |
| Peak stacked alpha (all four shells) | compact 31.26%; compact pressed 17.22%; non-compact 24.20% |
| Dark S1 side overhang vs gutter | The largest non-compact shell overhangs 2.016dp per side but the S1 side gutter is `dp(1.4)`; the excess is cut wherever the parent clips the card (INFERRED) |

### 7.2 Who calls it

| Caller | `pressed` | `compact` | Cite |
|---|---|---|---|
| `MaterialSurfaceDrawable.draw` when `shadow` | `false` | `false` | UiKit.java:1990 |
| `OnboardingSectionDrawable.draw`, bottom section only | `false` | `false` | UiKit.java:2375 |
| `drawRecessedTrack` unless light and `mainPower` | caller's `pressed` | `true` | UiKit.java:1837-1845 |
| `drawNeutralThumb` in dark | caller's `pressed` | `true` | UiKit.java:1907-1908 |

## 8. Inner depth — `drawInnerDepth`, `drawSurfaceInnerDepth`, `drawThumbInnerDepth`

### 8.1 `drawInnerDepth` — UiKit.java:1773-1810

Signature: `private static void drawInnerDepth(Canvas canvas, Paint paint, RectF bounds, float radius, UiKit ui, float topOffsetDp, float topBlurDp, int topAlpha, float bottomOffsetDp, float bottomBlurDp, int bottomAlpha)`.

Source note (UiKit.java:1765-1772): "Draws a CSS-style inset shadow as two short edge fades. The approved CSS specifies absolute offsets and blur radii. The old native port converted those shadows to percentages of the component height, so a 1.6dp shadow could become a 30-50dp grey band on a tall S1. Keep the affected depth tied to the frozen CSS metrics instead: offset + two blur radii is the complete local falloff."

| Step | Operation | Cite |
|---|---|---|
| Clip | `canvas.save()`; `clipPath(roundRect(bounds, radius, radius))`; restored at the end | UiKit.java:1776-1779, 1808 |
| Top depth | `topDepth = min(bounds.height() / 2f, ui.dp(topOffsetDp + topBlurDp * 2f))`; drawn only if `topAlpha > 0 && topDepth > 0` | UiKit.java:1782-1784 |
| Top gradient | Vertical `LinearGradient` from `bounds.top` to `bounds.top + topDepth`, `CLAMP`; colours `ink@topAlpha`, `ink@round(topAlpha * 0.36f)`, `Color.TRANSPARENT`; positions `0`, `min(0.72f, ui.dp(topOffsetDp) / topDepth)`, `1` | UiKit.java:1785-1791 |
| Top fill | `drawRect(bounds.left, bounds.top, bounds.right, bounds.top + topDepth)` | UiKit.java:1792 |
| Bottom depth | `bottomDepth = min(bounds.height() / 2f, ui.dp(bottomOffsetDp + bottomBlurDp * 2f))`; drawn only if `bottomAlpha > 0 && bottomDepth > 0` | UiKit.java:1795-1797 |
| Bottom gradient | From `bounds.bottom - bottomDepth` to `bounds.bottom`; colours `Color.TRANSPARENT`, `ink@round(bottomAlpha * 0.36f)`, `ink@bottomAlpha`; positions `0`, `max(0.28f, 1f - ui.dp(bottomOffsetDp) / bottomDepth)`, `1` | UiKit.java:1798-1804 |
| Bottom fill | `drawRect(bounds.left, bounds.bottom - bottomDepth, bounds.right, bounds.bottom)` | UiKit.java:1805-1806 |
| Tint | Both bands use `ui.p.ink`: the bottom band darkens too (it is not a highlight) | UiKit.java:1786-1787, 1800-1801 |
| Cleanup | `paint.setShader(null)` | UiKit.java:1809 |

### 8.2 Presets

| Preset | CSS quoted by the source | Top offset / blur / alpha | Top mid alpha | Top depth | Top mid stop (nominal) | Bottom offset / blur / alpha | Bottom mid alpha | Bottom depth | Bottom mid stop (nominal) | Cite |
|---|---|---|---|---|---|---|---|---|---|---|
| `drawSurfaceInnerDepth` | "Frozen S1/M1/K4: inset 0 0.1rem 0.1rem 4%, 0 -0.08rem 0.14rem 5%." | 1.6 / 1.6 / 10 | 4 | 4.8dp | 0.3333 | 1.28 / 2.24 / 13 | 5 | 5.76dp | 0.7778 | UiKit.java:1812-1817 |
| `drawThumbInnerDepth` | "Frozen K3: inset 0 0.16rem 0.12rem 8%, 0 -0.12rem 0.18rem 10%." | 2.56 / 1.92 / 20 | 7 | 6.4dp | 0.4 | 1.92 / 2.88 / 26 | 9 | 7.68dp | 0.75 | UiKit.java:1819-1824 |

Mid alphas are `round(alpha * 0.36f)`; depths are `offset + 2 * blur`; mid stops are `offset / depth` and `1 - offset / depth`. On device the stops use px-rounded terms (density 2.75: S1 top `4/13 = 0.3077`, S1 bottom `1 - 4/16 = 0.75`).

| Caller | Condition | Cite |
|---|---|---|
| `MaterialSurfaceDrawable.draw` -> `drawSurfaceInnerDepth` | `!ui.dark && !danger` | UiKit.java:2004 |
| `drawNeutralThumb` -> `drawThumbInnerDepth` | `!ui.dark` | UiKit.java:1919 |

Dark theme and danger materials get no inner depth from these functions; their volume comes from the body gradient and the gradient edge (§10, §11).

## 9. `drawRecessedTrack` (K1 / UI-001) — UiKit.java:1826-1893

Signatures: `drawRecessedTrack(Canvas, Paint, RectF bounds, UiKit ui, int color, int edge, boolean pressed)` delegates to the 8-argument form with `mainPower=false` (UiKit.java:1827-1830). Doc comment: "Shared K1/UI-001 material: soft outer lift, recessed colored bed and rounded edge." Only `PowerSwitch` passes `mainPower=true` (UiKit.java:2785).

### 9.1 Layer order

| # | Layer | Geometry | Light | Dark | Cite |
|---|---|---|---|---|---|
| 0 | Setup | `radius = bounds.height() / 2f` (full pill); `FILL`; shader cleared | — | — | UiKit.java:1834-1836 |
| 1a | Outer shadow, `mainPower=true` | `drawBoundedOuterShadow(bounds, radius, blur, offset, opacity)` | rest `17.6 / 8.8 / 41`; pressed `9.92 / 4.48 / 33` | not taken; falls to 1b | UiKit.java:1837-1842 |
| 1b | Outer shadow, otherwise | `drawSoftLift(bounds, radius, pressed, compact=true)` | bounded shells: rest `12.8 / 6.08 / 31`; pressed `5.3 / 2.5 / 28` (§6.2) | 4 black shells, spread 2.8 / 2.05 / 1.35 / 0.72dp, alpha 16 / 20 / 25 / 30, x0.52 when pressed (§7.1) | UiKit.java:1843-1845 |
| 2 | Bed | `drawRoundRect(bounds, radius, radius)` with the caller's `color`, flat | state colour from §3.1 | `#191E25` (§3.2) | UiKit.java:1848-1849 |
| 3 | Clip | `clipPath(roundRect(bounds, radius))` for layers 4-5 | — | — | UiKit.java:1851-1854 |
| 4 | Top inner shadow | Band `bounds.top .. bounds.top + topDepth`, `topDepth = min(bounds.height() / 2f, ui.dp(3.2f + 5.12f * 2f))` (13.44dp). Vertical gradient, stops `0`, `min(0.72f, ui.dp(3.2f) / topDepth)` (nominal 0.2381), `1` | `argb(41,0,0,0)` `#29000000` -> `argb(15,0,0,0)` `#0F000000` -> transparent | `argb(70,0,0,0)` `#46000000` -> `argb(30,0,0,0)` `#1E000000` -> transparent | UiKit.java:1856-1867 |
| 5 | Bottom inner highlight | Band `bounds.bottom - bottomDepth .. bounds.bottom`, `bottomDepth = min(bounds.height() / 2f, ui.dp(2.4f + 4f * 2f))` (10.4dp). Stops `0`, `max(0.28f, 1f - ui.dp(2.4f) / bottomDepth)` (nominal 0.7692), `1` | transparent -> `argb(14,255,255,255)` `#0EFFFFFF` -> `argb(87,255,255,255)` `#57FFFFFF` | transparent -> `argb(18,255,255,255)` `#12FFFFFF` -> `argb(38,255,255,255)` `#26FFFFFF` | UiKit.java:1869-1879 |
| 6 | Restore clip | `canvas.restoreToCount(save)` | — | — | UiKit.java:1880 |
| 7 | Edge | `border = bounds` inset `dp(0.5)` on all sides; `STROKE`, width `dp(1f)`; solid caller `edge`; corner radius `border.height() / 2f` | state edge from §3.1 | `#272C33` (§3.2) | UiKit.java:1884-1890 |
| 8 | Reset | shader cleared, style `FILL` | — | — | UiKit.java:1891-1892 |

### 9.2 Source comments that define the material

| Comment | Cite |
|---|---|
| "Frozen UI-001: 0 .55rem 1.1rem 16%; pressed .28rem .62rem 13%. Use the same bounded native translation as S1/K1 so its edge cannot be cut." | UiKit.java:1838-1839 |
| "Preserve the frozen semantic colour as the actual bed colour. Depth is layered on top, never created by mixing the whole track with S1 or by painting it twice." | UiKit.java:1846-1847 |
| "Frozen K1: inset 0 0.2rem 0.32rem 16%. Its native falloff is local to 3.2dp + 2 * 5.12dp, never a percentage of the entire track height." | UiKit.java:1856-1857 |
| "Frozen K1: inset 0 -0.15rem 0.25rem background 34%." | UiKit.java:1869 |
| "The one-dp border is the frozen per-state edge colour (#984040/#ad8215/#244879/#1f6f4e). It must not become a generic grey or a vertical colour ramp." | UiKit.java:1882-1883 |

| Fact | Detail |
|---|---|
| Stale hex in the edge comment | The four hex values quoted at UiKit.java:1882-1883 are not the values drawn. The drawn edges are the `TRACK_*_EDGE` constants `#BE6938 / #C29E52 / #6990E0 / #5B9774` (UiKit.java:62-65), confirmed by UI_FROZEN_IMPLEMENTATION_MAP.md:46-49 |
| Inner shadow tint | Layer 4 is pure black and layer 5 pure white in both themes. This differs from S1/K3 inner depth, which tints both bands with `p.ink` (§8) |
| Pressed | Changes only the outer shadow parameters. Bed, inner bands and edge are identical when pressed |
| Dark UI-001 | Uses the same compact dark shells as every other track (branch 1b); the larger UI-001 shadow exists only in light |
| OBSERVED (screenshot_main.png, column 900) | Bed `#CD8352` (constant: `#CE8353`); top inner shadow rows 975-1010; bottom highlight rows 1167-1190 peaking at `#DB9A70`; edge rows 1191-1193 |
| OBSERVED (screenshot_settings.png, column 500) | Bed `#86ACF7` on rows 284-362 (equals `TRACK_STANDARD`); top inner shadow rows 248-283; bottom highlight rows 363-387 peaking at `#9BBDFD`; edge rows 388-390 |

## 10. `drawNeutralThumb` (K3) — UiKit.java:1895-1937

Signature: `private static void drawNeutralThumb(Canvas canvas, Paint paint, RectF bounds, UiKit ui, boolean pressed)`. Doc comment: "Shared neutral K3 material. Its edge is deliberately tonal rather than a hard outline."

| # | Layer | Geometry | Light | Dark | Cite |
|---|---|---|---|---|---|
| 0 | Setup | `radius = bounds.height() / 2f`; `FILL` | — | — | UiKit.java:1898, 1906 |
| 1 | Outer shadow | around `bounds` | `drawBoundedOuterShadow`: rest `5.12 / 4.48 / 59`; pressed `2.72 / 1.92 / 48` | `drawSoftLift(pressed, compact=true)` (§7.1) | UiKit.java:1907-1914 |
| 2 | Body | `drawRoundRect(bounds, radius, radius)` with a vertical gradient `bounds.top -> bounds.bottom`, stops `0`, `0.56f`, `1` | `p.raised`, `p.raised`, `p.raised` = flat `#FFFFFF` | `blend(p.raised, p.ink, 0.055f)` `#313842` -> `p.raised` `#262D38` -> `blend(p.raised, p.bg, 0.19f)` `#232A34` | UiKit.java:1899-1905, 1915-1918 |
| 3 | Inner depth | `drawThumbInnerDepth` (§8.2): top 6.4dp ink@20 -> ink@7 -> 0; bottom 7.68dp 0 -> ink@9 -> ink@26 | drawn | not drawn | UiKit.java:1919 |
| 4 | Edge | `edge = bounds` inset `dp(0.45)`; `STROKE`, width `dp(0.9f)`; corner radius `edge.height() / 2f` | solid `blend(p.bg, p.ink, 0.24f)` = `rgb(200,201,201)` `#C8C9C9` | vertical gradient `edge.top -> edge.bottom`, stops `0`, `0.60f`, `1`: `argb(105,255,255,255)` `#69FFFFFF` -> `alphaColor(p.border, 142)` `#8E36404D` (high contrast `#8E586576`) -> `argb(88,30,39,50)` `#581E2732` | UiKit.java:1921-1934 |
| 5 | Reset | shader cleared, style `FILL` | — | — | UiKit.java:1935-1936 |

| Fact | Detail | Cite |
|---|---|---|
| CSS quoted | "Frozen K3: 0 .28rem .32rem 23%; pressed 0 .12rem .17rem 19%." | UiKit.java:1910 |
| Audit description | "白色主体、24% 前景边、单层短影、轻内影" | UI_RENDER_FIDELITY_AUDIT_2026-08-12.md:23 |
| High contrast | The light edge does not use `p.border`, so it is unchanged in high contrast. The dark edge mid stop does use `p.border` | UiKit.java:1928, 1932 |
| Clipping | The caller decides whether the thumb (and its shadow) is clipped to the track pill (§5.3) | UiKit.java:2516-2522, 2792-2798 |
| OBSERVED (screenshot_settings.png, column 737) | Edge pixel `#C8CAC9` at row 260; top inner depth rows 261-275 `#F2F2F2 -> #FEFEFE`; body `#FFFFFF`; bottom inner depth rows 360-377 `#FEFEFE -> #EFF1F0` | sampled |

## 11. `MaterialSurfaceDrawable` (S1 / K4 / E3 / toast / dialog) — UiKit.java:1939-2046

Doc comment: "M1/S1/K4/dialog neutral material without Android's hard elevation silhouette." Constructor `MaterialSurfaceDrawable(UiKit ui, float cornerDp, boolean danger, boolean shadow)` (UiKit.java:1950-1955). Private; reachable through the factories below.

### 11.1 Instances

| Use | `cornerDp` | `danger` | `shadow` | Host padding (measured from the view bounds, not from the material) | Cite |
|---|---|---|---|---|---|
| S1 `surface()` / `surfaceDrawable()` | `8f` | `false` | `true` | `dp(9)` all sides | UiKit.java:367-381 |
| K4 `capsule(title, danger, listener)` | `999f` | parameter; every call site in the app passes `false` | `true` | `dp(22), dp(13), dp(22), dp(13)` | UiKit.java:977-989 |
| E3 validation error capsule (inside a dialog) | `999f` | `true` | `true` | `dp(16), dp(10), dp(16), dp(10)` | UiKit.java:1418-1422 |
| Feedback toast (`message()` / `error()`) | `999f` | `false` for `message`, `true` for `error` | `true` | `dp(18), dp(12), dp(18), dp(12)` | UiKit.java:1443-1447 |
| Dialog card | `16f` | `false` | `true` | `dp(18), dp(19), dp(18), 0` | UiKit.java:1517-1519 |
| `applySurfaceMaterial(view)` | `8f` | `false` | `true` | — (no caller in the app) | UiKit.java:399-402 |
| `drawSurfaceMaterial(canvas, bounds, cornerDp, danger, shadow)` | parameter | parameter | parameter | — (no caller in the app; the only route to `shadow=false`) | UiKit.java:384-391 |

### 11.2 Geometry

| Item | Value | Cite |
|---|---|---|
| Material rect, `shadow=true` | view bounds inset: left/right `dp(1.4f)`, top `dp(1f)`, bottom `dp(4.8f)` | UiKit.java:1957-1964 |
| Material rect, `shadow=false` | inset `dp(0.5f)` on all sides | UiKit.java:1959-1961 |
| Corner radius | `cornerDp >= 900f ? rect.height() / 2f : ui.dp(cornerDp)` — the pill radius is half the material height, not half the view height | UiKit.java:1966-1968 |
| Empty guard | nothing is drawn when `rect.width() <= 0 || rect.height() <= 0` | UiKit.java:1972 |
| Edge rect | material rect inset `dp(0.45f)`; stroke width `dp(0.85f)`; corner radius `max(0, radius - dp(0.45f))` | UiKit.java:2006-2011, 2022-2023 |
| Outline | `getOutline` reports the material rect (rounded to int) with the same radius, not the view bounds | UiKit.java:2040-2045 |
| Opacity | `PixelFormat.TRANSLUCENT` | UiKit.java:2038 |
| Optical offset | The material centre sits `(4.8 - 1) / 2 = 1.9dp` above the view centre, so content centred in the view sits 1.9dp below the centre of the visible shape. INFERRED from the insets; OBSERVED on the `"恢复全部默认值"` capsule in screenshot_settings.png (view rows 1988-2112, border rows 1991-1992 and 2098-2099) | UiKit.java:1960-1961 |

### 11.3 Layer order

| # | Layer | Condition | Operation | Cite |
|---|---|---|---|---|
| 1 | Paint setup | always | `FILL`, shader cleared, `setColor(fill)`, `setAlpha(drawableAlpha)`, colour filter | UiKit.java:1985-1989 |
| 2 | Outer shadow | `shadow` | `drawSoftLift(rect, radius, pressed=false, compact=false)`: light = bounded shells `8 / 3.52 / 23` (§6.2); dark = 4 black shells, spread 4.8 / 3.55 / 2.3 / 1.15dp, alpha 10 / 14 / 19 / 25 (§7.1) | UiKit.java:1990 |
| 3 | Base fill | always | `drawRoundRect(rect, radius, radius)` flat `fill` | UiKit.java:1991 |
| 4a | Body gradient | `ui.dark || danger` | vertical gradient `rect.top -> rect.bottom`, colours `top, middle, bottom`, stops `0`, `0.56f`, `1` | UiKit.java:1993-1998 |
| 4b | Body repeat | light and not danger | the flat `fill` round rect is drawn a second time (no visual change at alpha 255) | UiKit.java:1999-2003 |
| 5 | Inner depth | `!ui.dark && !danger` | `drawSurfaceInnerDepth` (§8.2): top 4.8dp ink@10 -> ink@4 -> 0; bottom 5.76dp 0 -> ink@5 -> ink@13 | UiKit.java:2004 |
| 6a | Edge gradient | `ui.dark || danger` | vertical gradient `edge.top -> edge.bottom`, stops `0`, `0.60f`, `1`: `argb(dark ? 66 : 172, 255,255,255)` -> `alphaColor(edgeBase, dark ? 142 : 126)` -> `argb(dark ? 78 : 48, 30,39,50)` | UiKit.java:2012-2017 |
| 6b | Edge solid | light and not danger | solid `p.border` | UiKit.java:2018-2021 |
| 7 | Reset | always | shader cleared, style `FILL` | UiKit.java:2024-2025 |

### 11.4 Colours per variant

`fill = danger ? p.red : p.surface`; `middle = fill` (UiKit.java:1974, 1979).

| Value | Neutral light | Neutral dark | Danger light | Danger dark | Cite |
|---|---|---|---|---|---|
| `fill` / `middle` | `#FFFFFF` | `#1F252E` | `#C85858` | `#975960` | UiKit.java:1974 |
| `top` | `p.surface` `#FFFFFF` | `blend(p.surface, p.ink, 0.028f)` `#252B34` | `blend(fill, WHITE, 0.10f)` `#CE6969` | `blend(fill, WHITE, 0.055f)` `#9D6269` | UiKit.java:1975-1978 |
| `bottom` | `p.surface` `#FFFFFF` | `blend(p.surface, p.bg, 0.15f)` `#1E242D` | `blend(fill, BLACK, 0.055f)` `#BD5353` | `blend(fill, BLACK, 0.10f)` `#885056` | UiKit.java:1980-1983 |
| Body | flat | gradient | gradient | gradient | UiKit.java:1993-2003 |
| Inner depth | yes | no | no | no | UiKit.java:2004 |
| `edgeBase` | `blend(p.bg, p.ink, 0.08f)` `#EDEDED` (computed but not drawn) | `blend(p.bg, p.ink, 0.082f)` `#292E35` | `blend(p.red, p.ink, 0.20f)` `#A54C4D` | `blend(p.red, p.ink, 0.20f)` `#A8777E` | UiKit.java:2008-2009 |
| Edge | solid `p.border` `#EDEDED` (high contrast `#D1D2D2`) | `#42FFFFFF` -> `#8E292E35` -> `#4E1E2732` | `#ACFFFFFF` -> `#7EA54C4D` -> `#301E2732` | `#42FFFFFF` -> `#8EA8777E` -> `#4E1E2732` | UiKit.java:2012-2021 |
| Outer shadow | ink shells (bounded) | black shells | ink shells (bounded) | black shells | UiKit.java:1990 |
| Text drawn on it | `p.ink` | `p.ink` | `Color.WHITE` | `Color.WHITE` | UiKit.java:978, 1418, 1443 |

| Fact | Detail | Cite |
|---|---|---|
| Source comment | "The approved light S1 is a white body. Keep its material depth in the short inner shadow and outer lift instead of tinting the whole surface." | UiKit.java:2000-2001 |
| No pressed material | The drawable always passes `pressed=false`; press feedback on K4 and rows is a content translation handled elsewhere, the shadow does not change | UiKit.java:1990 |
| Dark high contrast | The dark edge derives from `edgeBase` (bg/ink), not from `p.border`, so dark S1 / K4 / dialog edges do not change in high contrast | UiKit.java:2008-2016 |
| `setAlpha` coverage | `drawableAlpha` modulates the body, inner depth and gradient edge. The outer-shadow shells (absolute alpha) and the light solid edge (`setColor` resets alpha) ignore it. The app never sets a drawable alpha; it fades the host `View` on a hardware layer instead | UiKit.java:1988, 1706, 2020, 1521, 1617-1622 |
| Elevation | Hosts call `setElevation(0)`; S1 additionally disables outline clipping | UiKit.java:372-375, 986, 1422, 1447 |
| OBSERVED S1 (screenshot_settings.png) | Left edge: view starts at x=33, shell shading x=33-36 (`#FCFCFC, #F8F8F8, #F3F3F3, #EFEFEF`), border x=37-38 `#EDEDED`, interior `#FFFFFF`. Top (column 540): shading rows 214-216, border rows 217-218 `#EDEDED`, inner depth rows 219-228. Bottom: inner depth rows 588-600, border rows 601-602, outer shadow rows 603-615 | sampled |

## 12. `ContextMaterialDrawable` (service overlay M1) — UiKit.java:2048-2183

Factory: `public static Drawable overlaySurfaceDrawable(Context context)` (UiKit.java:394-396), documented as "Service/overlay counterpart of M1. It uses the same palette and drawable formula." The formula is close to, but not the same as, `MaterialSurfaceDrawable`; §12.3 lists every difference.

### 12.1 Construction and host

| Item | Value | Cite |
|---|---|---|
| Inputs | `density` from the context; `dark = AppPreferences.resolveDark(context)`; `Palette(dark, highContrast())`; only `bg`, `surface`, `border` are kept | UiKit.java:2059-2070 |
| Why not system `uiMode` | "Overlays run from the accessibility service, whose configuration does not necessarily reflect the app's explicit Light/Dark choice. Resolve the same preference used by Activities instead of reading only system uiMode." | UiKit.java:2062-2064 |
| Units | `px(dp) = dp * density`, not rounded | UiKit.java:2072 |
| Only user | `StatusOverlayController.applyStyle`: a `TextView` with padding `dp(12), dp(8), dp(12), dp(8)`, text colour `palette.ink`, `12f * (largeText ? 1.13f : 1f)` sp, `sans-serif-medium`, `LAYER_TYPE_SOFTWARE`, view alpha `0.88f` by default (configurable) | StatusOverlayController.java:22, 50, 95, 148-158 |
| Material rect | bounds inset left/right `px(1.4f)`, top `px(1f)`, bottom `px(4.8f)`; nothing drawn if empty | UiKit.java:2075-2078 |
| Corner radius | `px(10f)` | UiKit.java:2079 |
| Outline | not overridden | UiKit.java:2048-2183 |

### 12.2 Layer order

| # | Layer | Light | Dark | Cite |
|---|---|---|---|---|
| 1 | Outer shadow | `paint.setShadowLayer(px(7f), 0, px(3.1f), argb(23 * drawableAlpha / 255, 30, 39, 50))` (`#171E2732` at full alpha) on a round rect filled with `surface`; then `clearShadowLayer()` | 4 shells, `index = 3 .. 0`: `amount = px({4.8f, 3.55f, 2.3f, 1.15f}[index])`; rect inset `(-amount * 0.42f, -amount * 0.18f)`, offset `(0, amount * 0.56f)`; colour `argb({10, 14, 19, 25}[index] * drawableAlpha / 255, 0, 0, 0)`; radius `radius + amount * 0.32f` | UiKit.java:2084-2110 |
| 2 | Body | gradient `top, surface, bottom` at `0`, `0.56f`, `1`; all three are `surface` `#FFFFFF` | `top = blend(surface, WHITE, 0.028f)` `#252B34` -> `surface` `#1F252E` -> `bottom = blend(surface, bg, 0.15f)` `#1E242D` | UiKit.java:2080-2083, 2111-2116 |
| 3 | Inner depth (clipped to the round rect) | top band `min(rect.height() / 2f, px(4.8f))`: `argb(10,26,28,31)` `#0A1A1C1F` -> `argb(4,26,28,31)` `#041A1C1F` -> transparent, stops `0`, `px(1.6f) / topDepth`, `1`. Bottom band `min(rect.height() / 2f, px(5.76f))`: transparent -> `argb(5,26,28,31)` `#051A1C1F` -> `argb(13,26,28,31)` `#0D1A1C1F`, stops `0`, `1f - px(1.28f) / bottomDepth`, `1` | not drawn | UiKit.java:2118-2139 |
| 4 | Edge | `edge = rect` inset `px(0.45f)`; `STROKE` width `px(0.85f)`; solid `border` `#EDEDED` (high contrast `#D1D2D2`); radius `radius - px(0.45f)` | same geometry; gradient stops `0`, `0.60f`, `1`: `argb(66,255,255,255)` `#42FFFFFF` -> `alphaColor(edgeBase, 142)` `#8E2B3036` -> `argb(78,30,39,50)` `#4E1E2732`, with `edgeBase = blend(bg, WHITE, 0.082f)` `#2B3036` | UiKit.java:2141-2157 |
| 5 | Second inner stroke | not drawn | `inner = rect` inset `px(1.25f)`; width `px(0.65f)`; gradient `inner.top -> inner.bottom`, stops `0`, `0.43f`, `1`: `argb(40,255,255,255)` `#28FFFFFF` -> transparent -> `argb(36,30,39,50)` `#241E2732`; radius `radius - px(1.25f)` | UiKit.java:2159-2169 |

### 12.3 Differences from `MaterialSurfaceDrawable`

| Aspect | `MaterialSurfaceDrawable` | `ContextMaterialDrawable` | Cite |
|---|---|---|---|
| Pixel rounding | every term through `ui.dp()` (integer px) | `px()` float | UiKit.java:249-251, 2072 |
| Radius | 8dp (S1), 16dp (dialog), pill | 10dp | UiKit.java:380, 1519, 2079 |
| Light outer shadow | 18 bounded ink shells | one `setShadowLayer` blur, radius 7dp, dy 3.1dp, `rgb(30,39,50)` at alpha 23. INFERRED: it is cut at the view bounds because the gutters (1.4 / 1 / 4.8dp) are smaller than the blur | UiKit.java:1731, 2104-2107 |
| Shadow vs drawable alpha | shells ignore `drawableAlpha` | shadow alpha is multiplied by `drawableAlpha / 255` (integer arithmetic) | UiKit.java:1706, 2095, 2105 |
| Dark `top` | `blend(surface, ink, 0.028f)` | `blend(surface, WHITE, 0.028f)`; both compute to `#252B34` | UiKit.java:1977, 2080 |
| Dark `edgeBase` | `blend(bg, ink, 0.082f)` `#292E35` | `blend(bg, WHITE, 0.082f)` `#2B3036` | UiKit.java:2009, 2143-2144 |
| Dark second stroke | none | present (layer 5) | UiKit.java:2159-2169 |
| Inner-depth tint | `p.ink` of the active palette | hard-coded `rgb(26,28,31)` | UiKit.java:1786, 2125-2132 |
| Inner-depth stop clamps | `min(0.72f, ...)` / `max(0.28f, ...)` | none | UiKit.java:1790, 1803, 2127, 2133 |
| Edge radius guard | `max(0, radius - dp(0.45f))` | `radius - px(0.45f)` | UiKit.java:2022, 2156 |
| Danger variant | yes | no | UiKit.java:1953 |
| Required layer type | none (hardware-safe primitives) | host sets `LAYER_TYPE_SOFTWARE` | StatusOverlayController.java:157 |

## 13. `InsetFieldDrawable` (input field, K10 editor, media well) — UiKit.java:2185-2242

Doc comment: "K10/dialog input well: a true recessed field with soft internal edge transitions." Constructor `InsetFieldDrawable(UiKit ui, float cornerDp)` (UiKit.java:2194-2197).

### 13.1 Instances

| Factory | `cornerDp` | Used by | Cite |
|---|---|---|---|
| `inputFieldDrawable()` | `9f` | Dialog `EditText` (padding `dp(13), dp(11), dp(13), dp(11)`) | UiKit.java:405-407, 1501-1502 |
| | | Add-account field (same padding) | WhitelistAccountsPage.java:96-97 |
| | | Config JSON editor (padding `dp(12)` all sides, 24 min lines) | ConfigEditorActivity.java:75-77 |
| | | Reset-path field (padding `dp(12), dp(11), dp(12), dp(11)`) | ConfigEditorActivity.java:109-110 |
| | | Ignored-overlay packages editor (padding `dp(13), dp(11), dp(13), dp(11)`) | DeveloperOptionsActivity.java:300-301 |
| `mediaWellDrawable()` | `7f` | `recessedMediaFrame`: `FrameLayout` with padding `dp(4)` all sides (`MEDIA_WELL_INSET_DP`), child image clipped to `dp(4f)` (`MEDIA_WELL_IMAGE_RADIUS_DP`); used at height `dp(152)` (`TUTORIAL_MEDIA_HEIGHT_DP`) | UiKit.java:410-428; ReadingLayoutSpec.java:43-45; MainActivity.java:615-618 |

### 13.2 Layers

| # | Layer | Geometry | Light | Dark | Cite |
|---|---|---|---|---|---|
| 0 | Rect | bounds inset `dp(0.5f)` on all sides; `radius = ui.dp(cornerDp)` | — | — | UiKit.java:2200-2203 |
| 1 | Body | `drawRoundRect(rect, radius, radius)`, vertical gradient `rect.top -> rect.bottom`, colours `top, fill, bottom`, stops `0`, `0.48f`, `1` | `fill = blend(p.bg, p.surface, 0.35f)` `#FFFFFF`; `top = blend(fill, p.ink, 0.045f)` `#F5F5F5`; `bottom = blend(fill, p.raised, 0.16f)` `#FFFFFF` | `fill = blend(p.bg, p.surface, 0.22f)` `#1A1F26`; `top = blend(fill, p.ink, 0.07f)` `#292E34`; `bottom = blend(fill, p.raised, 0.025f)` `#1A1F26` | UiKit.java:2204-2214 |
| 2 | Edge | `edge = rect` inset `dp(0.45f)`; `STROKE` width `dp(0.9f)`; radius `max(0, radius - dp(0.45f))`; vertical gradient `edge.top -> edge.bottom`, stops `0`, `0.58f`, `1` | `argb(74,0,0,0)` `#4A000000` -> `alphaColor(p.border, 156)` `#9CEDEDED` (high contrast `#9CD1D2D2`) -> `argb(96,255,255,255)` `#60FFFFFF` | `argb(104,0,0,0)` `#68000000` -> `alphaColor(p.border, 132)` `#8436404D` (high contrast `#84586576`) -> `argb(30,255,255,255)` `#1EFFFFFF` | UiKit.java:2216-2228 |

| Fact | Detail | Cite |
|---|---|---|
| No outer shadow, no inner-depth call | The recess is the darker top of the body gradient plus an edge that is dark at the top and light at the bottom | UiKit.java:2199-2229 |
| Body ramp scales with height | The mid stop is a fraction (`0.48f`) of the field height, so the `#F5F5F5 -> #FFFFFF` ramp covers 48% of the inset rect of any field (nominally `0.48 x (152 - 2 x 0.5)` = 72.48dp of the 152dp media well, and the same 48% of the config editor, whose height is set by its 24 minimum lines). This is the proportional behaviour the `drawInnerDepth` comment says was removed from S1 | UiKit.java:2210-2212, 1765-1772 |
| Blend collapse | Light `fill` and `bottom` are pure white because bg = surface = raised. Dark `bottom` rounds back to `fill` (`#1A1F26`), so the dark body has no lighter lower half | computed from UiKit.java:2204-2206 |
| No state | The class does not override `isStateful` / `onStateChange`; focus, error and disabled look identical | UiKit.java:2186-2242 |
| Text colours on it (set by callers) | text `p.ink`, hint `p.muted` | UiKit.java:1492-1493; WhitelistAccountsPage.java:93-94 |
| Paint style after `draw` | left as `STROKE`; the next `draw` resets it to `FILL` first | UiKit.java:2207, 2218 |

## 14. `SurfaceFillDrawable` (embedded row R2) — UiKit.java:2244-2285

Doc comments: "S1-owned row fill: the parent's edge and shadow stay authoritative." and, at the factory, "Neutral row material inside a parent S1; it does not add a second outer shadow." (UiKit.java:450-453).

| Item | Light | Dark | Cite |
|---|---|---|---|
| Shape | `canvas.drawRect(bounds)`: full bounds, square corners, no gutter | same | UiKit.java:2256, 2270 |
| Fill | solid `p.surface` `#FFFFFF` | vertical gradient `bounds.top -> bounds.bottom`, stops `0`, `0.56f`, `1`: `blend(p.surface, p.ink, 0.028f)` `#252B34` -> `p.surface` `#1F252E` -> `blend(p.surface, p.bg, 0.15f)` `#1E242D` | UiKit.java:2260-2269 |
| Edge / shadow / inner depth | none | none | UiKit.java:2255-2272 |
| Alpha | `setColor` after `setAlpha` resets alpha to 255 (drawable alpha ignored) | drawable alpha applied | UiKit.java:2258, 2268 |
| Only user | Whitelist account row foreground (`dp(68)` high, padding `dp(14), dp(8), dp(7), dp(8)`), which slides over the destructive reveal. Source: "R2 rows share their parent S1. Do not add a second card edge or shadow per row." | same | WhitelistAccountsPage.java:79, 226-228 |
| Dark gradient scope | — | The gradient restarts on every row (0 to 1 over the row height) while the parent S1 gradient spans the whole card, so the row does not equal the card colour behind it. INFERRED from the two formulas; no dark screenshot exists | UiKit.java:2263-2265, 1994-1996 |

## 15. `DestructiveRevealDrawable` (swipe-to-delete bed) — UiKit.java:2287-2328

Doc comments: "A recessed destructive bed that stays visually subordinate to the parent S1." and, at the factory, "Destructive reveal material used behind the frozen right-swipe whitelist row." (UiKit.java:455-458).

| # | Layer | Geometry | Light | Dark | Cite |
|---|---|---|---|---|---|
| 0 | Rect | full bounds, square corners | — | — | UiKit.java:2298-2299 |
| 1 | Body | `drawRect(rect)`, vertical gradient `rect.top -> rect.bottom`, colours `top, base, bottom`, stops `0`, `0.54f`, `1` | `base = p.red` `#C85858`; `top = blend(base, p.ink, 0.09f)` `#B85353`; `bottom = blend(base, WHITE, 0.12f)` `#CF6C6C` | `base = blend(p.red, p.bg, 0.26f)` `#764950`; `top = blend(base, p.ink, 0.13f)` `#855F65`; `bottom = blend(base, WHITE, 0.035f)` `#7B4F56` | UiKit.java:2300-2310 |
| 2 | Top line | `drawRect(left, top, right, top + dp(1.2f))` | `argb(42,0,0,0)` `#2A000000` | `argb(52,0,0,0)` `#34000000` | UiKit.java:2311-2312 |
| 3 | Bottom line | `drawRect(left, bottom - dp(1.1f), right, bottom)` | `argb(52,255,255,255)` `#34FFFFFF` | `argb(26,255,255,255)` `#1AFFFFFF` | UiKit.java:2313-2314 |

| Fact | Detail | Cite |
|---|---|---|
| Dark `top` is lighter than `base` | `top` blends toward `p.ink`, which is light in dark theme (`#855F65` vs `#764950`); in light theme the same expression darkens. The gradient direction therefore inverts between themes | UiKit.java:2301 |
| Host | `TextView` `"删除"`, 13.5sp, `Color.WHITE`, medium, centred; width `dp(78)` (`revealWidth`), full row height, `Gravity.START`; the row container clips children | WhitelistAccountsPage.java:207, 217-222 |
| No edge, no radius, no outer shadow | The parent S1 supplies them | UiKit.java:2297-2315 |

## 16. `OnboardingSectionDrawable` (split S1 of the first-launch pager) — UiKit.java:2330-2448

Doc comment: "Exact native counterpart of first-launch-flow-animation's split S1 sections." One S1 is drawn as two independent halves so the upper half can slide while the lower half stays fixed.

| Factory | `topSection` | Doc comment | Host | Cite |
|---|---|---|---|---|
| `onboardingContentDrawable()` | `true` | "Moving D1-above first-launch section: top corners, no lower shadow or border." | Each sliding content panel (`FrameLayout`) inside `contentStage`, which has `bottomMargin = dp(68)` | UiKit.java:440-443; FirstLaunchActivity.java:85-89, 212-217 |
| `onboardingActionDrawable()` | `false` | "Fixed first-launch action section: bottom corners and the shared short outer shadow." | `fixedAction` column, `dp(68)` high at `Gravity.BOTTOM`: a `dp(1)` D1 followed by the `dp(67)` action row | UiKit.java:445-448; FirstLaunchActivity.java:91-93, 116-118 |

### 16.1 Geometry

| Item | Top section | Bottom section | Cite |
|---|---|---|---|
| Rect inset from bounds | left/right `dp(0.7f)`, top `dp(0.7f)`, bottom `0` | left/right `dp(0.7f)`, top `0`, bottom `dp(4.8f)` | UiKit.java:2355-2359 |
| Corner radius | `dp(8f)` | `dp(8f)` | UiKit.java:2360 |
| Rounded corners | top-left and top-right only (`{r,r,r,r,0,0,0,0}`) | bottom-right and bottom-left only (`{0,0,0,0,r,r,r,r}`) | UiKit.java:2345-2351 |
| Side gutter vs S1 | `dp(0.7f)`, half of the S1 `dp(1.4f)` | same | UiKit.java:2355, 1959 |

### 16.2 Layer order

| # | Layer | Top section | Bottom section | Cite |
|---|---|---|---|---|
| 1 | Outer shadow + base | none | `setColor(fillMiddle)`; `drawSoftLift(rect, radius, pressed=false, compact=false)`; `drawPath(path)` | UiKit.java:2373-2377 |
| 2 | Body gradient | `drawPath(path)`, vertical gradient `rect.top -> rect.bottom`, colours `fillTop, fillMiddle, fillBottom`, stops `0`, `0.56f`, `1` | same | UiKit.java:2378-2382 |
| 3 | Inner depth (light only, clipped to the path) | band `min(rect.height(), dp(4.8f))` at the top: `ink@10` -> `ink@4` -> transparent, stops `0`, `dp(1.6f) / depth`, `1` | band `min(rect.height(), dp(5.76f))` at the bottom: transparent -> `ink@5` -> `ink@13`, stops `0`, `1f - dp(1.28f) / depth`, `1` | UiKit.java:2384-2406 |
| 4 | Edge | path rebuilt on `rect` inset `dp(0.45f)` with radius `max(0, radius - dp(0.45f))`; `STROKE` width `dp(0.85f)`; the whole outline is stroked, including the flat joining side | same | UiKit.java:2408-2423 |
| 5 | Joining-edge eraser | `FILL` `fillMiddle`, `drawRect(rect.left, rect.bottom - dp(1.5f), rect.right, rect.bottom + dp(1))` | `FILL` `fillMiddle`, `drawRect(rect.left, rect.top - dp(1), rect.right, rect.top + dp(1.5f))` | UiKit.java:2425-2434 |

Source comment on layer 5: "The shared D1 owns the joining edge between sections." (UiKit.java:2425). The eraser spans the full rect width, so it also removes the last `dp(1.5f)` of both side strokes next to the join.

### 16.3 Colours

| Value | Light | Dark | Cite |
|---|---|---|---|
| `fillTop` | `p.surface` `#FFFFFF` | top section: `blend(p.bg, p.surface, 0.22f)` `#1A1F26`; bottom section: `p.surface` `#1F252E` | UiKit.java:2363-2364 |
| `fillMiddle` | `p.surface` `#FFFFFF` | `blend(p.bg, p.surface, 0.14f)` `#191E25` | UiKit.java:2365-2366 |
| `fillBottom` | `p.surface` `#FFFFFF` | `blend(p.bg, p.surface, 0.30f)` `#1A1F27` | UiKit.java:2367-2368 |
| Edge | solid `p.border` `#EDEDED` (high contrast `#D1D2D2`) | gradient stops `0`, `0.60f`, `1`: `argb(56,255,255,255)` `#38FFFFFF` -> `alphaColor(p.border, 120)` `#7836404D` -> `argb(64,30,39,50)` `#401E2732` | UiKit.java:2413-2421 |
| Outer shadow (bottom section) | bounded ink shells `8 / 3.52 / 23`; effective reach side `dp(0.7)`, top 0, bottom `dp(4.8)` (§6.4) | 4 black shells (§7.1) | UiKit.java:2375 |

| Fact | Detail | Cite |
|---|---|---|
| Dark branch is unreachable in the app | Both factories are used only by `FirstLaunchActivity`, whose `UiKit` is created with `forFirstLaunch()` (`forceLight`). The dark values above have never been rendered by the shipped app | FirstLaunchActivity.java:52, 92, 214; UiKit.java:139-142, 201-208 |
| Light halves add up to one S1 | The top half carries only the S1 top inner shade (10 / 4), the bottom half only the S1 bottom inner shade (5 / 13) and the S1 outer shadow | UiKit.java:2387-2403, 1815-1816 |
| Inner-depth copies differ from `drawInnerDepth` | Depth is capped by the full rect height (not half) and the mid stops are not clamped | UiKit.java:2388, 2392, 2395, 2399 |
| Media inside the top half | `onboardingMediaImage`: `ClippedImageView`, radius `dp(7f)` (`FIRST_LAUNCH_MEDIA_RADIUS_DP`), top corners only, margins `dp(1)` left / top / right and `0` bottom (`FIRST_LAUNCH_MEDIA_EDGE_INSET_DP`), layout height `dp(220)` (`FIRST_LAUNCH_MEDIA_MIN_HEIGHT_DP`) with weight `1f`, so 220dp is the floor and the image takes the spare height of the fill-viewport scroll column. INFERRED: 7 = 8 - 1 keeps the image corner concentric with the section corner | UiKit.java:430-438; FirstLaunchActivity.java:157-162, 172-174; ReadingLayoutSpec.java:18-20 |
| Image overlaps the section edge | INFERRED from the two insets: the section stroke occupies 0.7dp to 1.55dp from the panel edge (rect inset `dp(0.7f)`, stroke `dp(0.85f)` centred on a further `dp(0.45f)` inset) while the image, a child drawn above the background, starts at `dp(1)`. At density 2.75 that is stroke px 2-3 and image from px 3, so only the outer 1 px of the 2 px border stays visible beside and above the image | UiKit.java:2355-2356, 2409-2412; FirstLaunchActivity.java:160-161 |
| Side shadow exists only beside the action section | The top section never calls `drawSoftLift`, so the sliding content half has no outer shadow on any side. The bottom section passes the full `rect` and `radius` to the shadow painter, whose shells are ordinary round rects (all four corners), although the body path has square top corners; the side shading therefore tapers off over the top `dp(8)` of the action section, towards the join. Top reach is 0 because the rect starts at the view top | UiKit.java:2373-2377, 1702-1708, 2345-2348 |
| Action row vs visible material | The `dp(67)` action row covers the `dp(4.8)` shadow gutter, so its centred label sits 2.4dp below the centre of the visible white area. INFERRED from the two sizes | FirstLaunchActivity.java:116-118; UiKit.java:2357 |

## 17. `ClippedImageView` — UiKit.java:1162-1196

Doc comment: "Deterministic bitmap clipping; unlike an outline, this supports top-only corners." `private static final class ClippedImageView extends ImageView`.

| Item | Value | Cite |
|---|---|---|
| Constructor | `ClippedImageView(Context context, float radiusPx, boolean topOnly)`; the radius is already in px | UiKit.java:1169-1173 |
| Clip path | Rebuilt in `onSizeChanged` over `RectF(0, 0, width, height)` | UiKit.java:1175-1178 |
| `topOnly = true` | `addRoundRect(bounds, {r, r, r, r, 0, 0, 0, 0}, CW)`: top-left and top-right rounded, bottom square | UiKit.java:1179-1184 |
| `topOnly = false` | `addRoundRect(bounds, r, r, CW)`: all four corners | UiKit.java:1185-1187 |
| Draw | `canvas.save()`; `canvas.clipPath(clipPath)`; `super.onDraw(canvas)`; `restoreToCount` | UiKit.java:1190-1195 |
| No border, shadow or background | The surrounding well or section supplies the frame | UiKit.java:1164-1196 |
| Instance: media well image | radius `dp(4f)`, all corners, `CENTER_CROP`, content description from the caller | UiKit.java:421-426 |
| Instance: onboarding image | radius `dp(7f)`, top only, `CENTER_CROP` | UiKit.java:432-437 |
| Edge quality | `clipPath` is a hard clip; the rounded edge is not blended against the well. INFERRED from platform behaviour, not stated in source | UiKit.java:1192 |

## 18. `divider()` (D1) — UiKit.java:460-465

| Item | Value | Cite |
|---|---|---|
| Implementation | A plain `View` with `setBackgroundColor(...)`; no drawable, no gradient | UiKit.java:461-464 |
| Colour rule | `prefs.highContrast() ? p.border : blend(p.bg, p.ink, 0.14f)` | UiKit.java:463 |
| Source comment | "Frozen D1: foreground 14% composited on the current L1/S1 background." | UiKit.java:462 |
| Light | `blend(#FFFFFF, #1A1C1F, 0.14)` = `rgb(223,223,224)` `#DFDFE0` | computed |
| Dark | `blend(#181D24, #EBEFF4, 0.14)` = `rgb(54,58,65)` `#363A41` | computed |
| Light, high contrast | `p.border` = `#D1D2D2` (the same colour as the high-contrast S1 edge) | UiKit.java:106, 463 |
| Dark, high contrast | `p.border` = `#586576` | UiKit.java:91, 463 |
| Blend base | Always `p.bg`, also when the divider lies on S1. In dark that is `#181D24`, not the S1 body `#1F252E` | UiKit.java:463 |
| Thickness and margins | Set by each caller through `LayoutParams` (below); the view itself has no size | UiKit.java:460-465 |

### 18.1 Call-site geometry

| Site | Size | Margins (left, top, right, bottom) | Cite |
|---|---|---|---|
| `MainActivity.fullDivider()` (settings, advanced, tutorial card, info groups) | match x `dp(1)` | `-dp(9), dp(5), -dp(9), dp(5)` | MainActivity.java:1221-1227; used at 425, 520, 523, 569, 619, 1097 |
| `WhitelistAccountsPage.fullDivider()` | match x `dp(1)` | `-dp(9), 0, -dp(9), 0` | WhitelistAccountsPage.java:199-204; used at 76 |
| `DeveloperOptionsActivity.fullDivider()` | match x `dp(1)` | `-dp(9), 0, -dp(9), 0` | DeveloperOptionsActivity.java:1077-1082; used at 124, 451, 455, 458 |
| `ConfigEditorActivity.fullDivider()` | match x `dp(1)` | `-dp(9), 0, -dp(9), 0` | ConfigEditorActivity.java:332-338; used at 122 |
| Read-only key/value surface (R5) | match x `dp(1)` | `-dp(9), 0, -dp(9), 0` | UiKit.java:1045-1049 |
| Dialog, horizontal (above the actions) | match x `dp(1)` | `-dp(18), dp(18), -dp(18), 0` | UiKit.java:1543-1546 |
| Dialog, vertical (between the two actions) | `dp(1)` x match | none | UiKit.java:1554-1555 |
| First launch: top of the fixed action section | match x `dp(1)` | none | FirstLaunchActivity.java:93 |
| First launch: under the privacy state block | match x `dp(1)` | none | FirstLaunchActivity.java:234 |
| Tutorial child pages (on L1, not inside S1) | match x `dp(1)` | `dp(10), dp(38), dp(10), 0`; `dp(48), 0, dp(10), 0`; `dp(48), dp(2), dp(10), 0`; `0, dp(4), 0, dp(4)` | MainActivity.java:795-797, 880-883, 888-891, 998-1001 |

| Fact | Detail | Cite |
|---|---|---|
| D1 overhangs the card edge | The negative horizontal margin equals the host padding (`dp(9)` for S1, `dp(18)` for the dialog), so the divider spans the full view width. The material is inset `dp(1.4)` inside that view, so the line extends `dp(1.4)` past the border on both sides. This works because S1 disables child and padding clipping | UiKit.java:267, 373-375, 1047-1048, 1545 |
| OBSERVED | screenshot_settings.png row 1216: divider `#DFDFDF` from x=33 to x=1046, while the S1 border is at x=37-38 and x=1041-1042; thickness rows 1215-1217 (3 px) | sampled |
| Audit wording | "左右贯穿所属 S1" and "D1 必须贯穿" | UI_RENDER_FIDELITY_AUDIT_2026-08-12.md:24, 90 |

## 19. CSS values quoted by source comments, and their native literals

The approved HTML/CSS samples named in UI_FROZEN_IMPLEMENTATION_MAP.md:9-25 are not in the repository; these comments are the only record of them. Native literal = CSS length x 16 (INFERRED 1rem = 16dp) and CSS percentage x 255, rounded.

| Material | CSS as quoted | Native literals | Tint used natively | Cite |
|---|---|---|---|---|
| S1 outer shadow | `0 .22rem .5rem 9%` | offset 3.52, blur 8, alpha 23 | `p.ink` | UiKit.java:1722-1725 |
| K1 outer shadow | `0 .38rem .8rem 12%` | offset 6.08, blur 12.8, alpha 31 | `p.ink` | UiKit.java:1722-1725 |
| UI-001 outer shadow | `0 .55rem 1.1rem 16%` | offset 8.8, blur 17.6, alpha 41 | `p.ink` | UiKit.java:1838-1842 |
| UI-001 outer shadow, pressed | `.28rem .62rem 13%` | offset 4.48, blur 9.92, alpha 33 | `p.ink` | UiKit.java:1838-1842 |
| K3 outer shadow | `0 .28rem .32rem 23%` | offset 4.48, blur 5.12, alpha 59 | `p.ink` | UiKit.java:1910-1913 |
| K3 outer shadow, pressed | `0 .12rem .17rem 19%` | offset 1.92, blur 2.72, alpha 48 | `p.ink` | UiKit.java:1910-1913 |
| S1 / M1 / K4 inset, top | `inset 0 0.1rem 0.1rem 4%` | offset 1.6, blur 1.6, alpha 10 | `p.ink` | UiKit.java:1812-1816 |
| S1 / M1 / K4 inset, bottom | `0 -0.08rem 0.14rem 5%` | offset 1.28, blur 2.24, alpha 13 | `p.ink` | UiKit.java:1812-1816 |
| K3 inset, top | `inset 0 0.16rem 0.12rem 8%` | offset 2.56, blur 1.92, alpha 20 | `p.ink` | UiKit.java:1819-1823 |
| K3 inset, bottom | `0 -0.12rem 0.18rem 10%` | offset 1.92, blur 2.88, alpha 26 | `p.ink` | UiKit.java:1819-1823 |
| K1 inset, top | `inset 0 0.2rem 0.32rem 16%` | offset 3.2, blur 5.12, alpha 41 (mid 15) | black | UiKit.java:1856-1866 |
| K1 inset, bottom | `inset 0 -0.15rem 0.25rem background 34%` | offset 2.4, blur 4, alpha 87 (mid 14) | white | UiKit.java:1869-1877 |
| S1 / K1 outer shadow, pressed | no CSS quoted | S1: offset 1.8, blur 4.6, alpha 20; K1: offset 2.5, blur 5.3, alpha 28 | `p.ink` | UiKit.java:1726-1729 |
| K5 size (context only) | "3.8rem by 2.15rem red/blue switch with a 1.65rem neutral thumb" | host 61 x 34dp; thumb cap `dp(26.4f)` | — | UiKit.java:525, 580; WhitelistAccountsPage.java:234 |

## 20. Screenshot cross-check (OBSERVED)

Files: `Gravekeeper-master/screenshots/screenshot_main.png` and `screenshot_settings.png` (1080 x 2247, light theme, default text scale). The S1 view starts at x=33 = `dp(12)` page padding, the D1 is 3 px thick and the S1 gutter is 4 px, which matches density 2.75 with the UiKit.java:250 rounding. `screenshot_more.png` and `screenshot_tutorial.png` are downscaled (324 x 666) and were used for layout confirmation only. No dark or high-contrast screenshot exists in the repository.

| Element | Sampled | Code value | Agreement |
|---|---|---|---|
| L1 | `#FFFFFF` | `p.bg` `#FFFFFF` (UiKit.java:101) | exact |
| S1 edge | `#EDEDED`, 2 px, at x=37-38 | `p.border` `#EDEDED`, `dp(0.85)` = 2 px (UiKit.java:106, 2011) | exact |
| S1 side gutter | shading x=33-36: `#FCFCFC, #F8F8F8, #F3F3F3, #EFEFEF` | `dp(1.4)` = 4 px (UiKit.java:1959) | exact width |
| S1 bottom shadow | rows 603-615 (13 px), first row `#EDEDED`, then `#EFEFEF ... #FEFEFE` | gutter `dp(4.8)` = 13 px; 18 shells of alpha 1 (UiKit.java:1961, 1695-1699) | exact height; peak = white minus 18 levels |
| S1 top inner depth | rows 219-228 `#F9FBFA -> #FEFEFE` | 4.8dp band, ink@10 -> 4 -> 0 (UiKit.java:1815-1816) | consistent |
| D1 | `#DFDFDF`, 3 px, x=33-1046 | `#DFDFE0`, `dp(1)` = 3 px, full view width (UiKit.java:463; MainActivity.java:1221-1227) | 1 level off in blue |
| K1 standard bed | `#86ACF7` | `TRACK_STANDARD` `#86ACF7` (UiKit.java:59) | exact |
| K1 off bed (UI-001) | `#CD8352` | `TRACK_OFF` `#CE8353` (UiKit.java:54) | 1 level off in red and blue |
| K1 top inner shadow | 36 px ramp (rows 248-283, settings; rows 975-1010, main) | `dp(13.44)` = 37 px (UiKit.java:1859) | consistent |
| K1 bottom highlight | peak `#9BBDFD` (blue bed), `#DB9A70` (orange bed) | white@87 over the bed (UiKit.java:1875) | consistent |
| K3 edge | `#C8CAC9` | `#C8C9C9` (UiKit.java:1932) | 1 level off in green |
| K3 body | `#FFFFFF` | `p.raised` `#FFFFFF` (UiKit.java:1905) | exact |
| UI-001 bottom shadow | rows 1196-1221, first row `#DDDDDD` | gutter `dp(10)` = 28 px; 18 shells of alpha 2 (UiKit.java:2772, 1842) | consistent |
| K4 capsule | view rows 1988-2112; border rows 1991-1992 and 2098-2099; shadow rows 2100-2112 | insets `dp(1)` = 3 px top, `dp(4.8)` = 13 px bottom (UiKit.java:1960-1961) | exact |

## 21. Preference and accessibility variants in this slice

| Preference | Key / default | Effect on materials and colour | Cite |
|---|---|---|---|
| Theme | `theme`, default `THEME_SYSTEM` (0) | Selects the light or dark column of every table in this file | AppPreferences.java:24, 41-47 |
| High contrast | `high_contrast`, default `false` | Changes `muted`, `border` and the D1 rule only (table below) | AppPreferences.java:28; UiKit.java:90-91, 105-106, 463 |
| Large text | `large_text`, default `false`; `textScale = 1.13f` | No painter or colour branches on it. Indirect only: wrap-content hosts (K4, E3, toast) grow with their text, and a pill radius is half the material height. Track heights are fixed dp and do not grow | AppPreferences.java:26; UiKit.java:210, 1967 |
| Reduce motion | `reduce_motion`, default `false` | No painter or colour branches on it. Pressed shadows are state switches, not animations. The theme cross-fade (300 ms) has no reduced branch | AppPreferences.java:30; UiKit.java:177-178 |
| System font scale | — | `ui.sp()` uses `scaledDensity`; canvas labels only | UiKit.java:254-256 |
| Force dark | `android:forceDarkAllowed=false` on the first-launch theme only | Prevents system auto-darkening of the light-only onboarding | styles.xml:56 |

High-contrast deltas (everything not listed is identical with high contrast on):

| Element | Light: normal -> high contrast | Dark: normal -> high contrast | Cite |
|---|---|---|---|
| `muted` text | `#8E8F90` -> `#4C4E50` | `#A1ACBB` -> `#C9D0D9` | UiKit.java:105, 90 |
| `border` token | `#EDEDED` -> `#D1D2D2` | `#36404D` -> `#586576` | UiKit.java:106, 91 |
| D1 | `#DFDFE0` -> `#D1D2D2` | `#363A41` -> `#586576` | UiKit.java:463 |
| Neutral S1 / K4 / toast / dialog edge | `#EDEDED` -> `#D1D2D2` | unchanged (gradient built from `edgeBase`) | UiKit.java:2008-2021 |
| Danger edge (E3, error toast) | unchanged | unchanged | UiKit.java:2008, 2012-2017 |
| K3 edge | unchanged `#C8C9C9` | mid stop `#8E36404D` -> `#8E586576` | UiKit.java:1928, 1932 |
| Inset field edge, mid stop | `#9CEDEDED` -> `#9CD1D2D2` | `#8436404D` -> `#84586576` | UiKit.java:2223 |
| Overlay edge | `#EDEDED` -> `#D1D2D2` | unchanged | UiKit.java:2143-2155 |
| Onboarding section edge | `#EDEDED` -> `#D1D2D2` | mid stop `#7836404D` -> `#78586576` (unreachable, §16.3) | UiKit.java:2416, 2420 |
| Track beds and edges, all shadows, inner depth, body fills | unchanged | unchanged | UiKit.java:54-66, 1672-1893 |

## 22. Who reads each palette token

| Token | Readers | Cite |
|---|---|---|
| `bg` | L1 fills (§4.5); system bars; blend base for D1, dark track bed and edge (all four controls), light K3 edge, inset field fill, edge bases, onboarding dark fills; blend partner for the dark `bottom` stops of K3, S1 and the embedded row and for the dark destructive base; kept by the overlay drawable | UiKit.java:237-238, 332, 362, 463, 570, 573, 720, 722, 1228, 1233, 1903, 1932, 1982, 2009, 2067, 2204, 2262, 2300, 2364-2368, 2582, 2589, 2781, 2783; MainActivity.java:266, 1658; DeveloperOptionsActivity.java:70; FirstLaunchActivity.java:69 |
| `surface` | S1 / dialog / toast / K4 body; embedded row fill; onboarding section fills; overlay body; blend partner for the dark track bed and the inset field fill | UiKit.java:1974-1983, 2068, 2204, 2261-2268, 2364-2368, 570, 720, 2582, 2781 |
| `raised` | K3 body; inset field `bottom` blend | UiKit.java:1901-1905, 2206 |
| `ink` | Primary text; shadow shells; S1 / K3 / onboarding inner depth; light K3 edge blend; D1 blend; left chevron of `"←"` entries; power glyph and thumb text; on-thumb segment label and stepper value; blend partner for the dark track edge, the dark `top` stops (K3, S1, embedded row), both `edgeBase` values, the inset field `top`, the destructive `top`, the dark segment label tints and the power-switch hint text; overlay text | UiKit.java:1706, 1786-1801, 1932, 463, 827, 2802, 2808, 2536, 749, 573, 722, 2589, 2783, 1901, 1977, 2261, 2008-2009, 2205, 2301, 2390, 2397-2398, 2601, 2816-2817; StatusOverlayController.java:152 |
| `muted` | Secondary text; default chevron; page-link text; main-nav icons and labels; inactive pager dot; dark stepper glyphs | UiKit.java:857, 938, 964, 741; MainActivity.java:340; FirstLaunchActivity.java:269 |
| `border` | Light solid edges (S1, overlay, onboarding); gradient mid stop of the dark K3 edge, the inset field edge and the dark onboarding edge; D1 in high contrast | UiKit.java:2020, 2154, 2420, 1928, 2223, 2416, 463 |
| `blue` | Dialog positive action text; first-launch action text and arrow, privacy headings, active pager dot; tutorial accent bars, eyebrow, section titles, `"→"` arrow | UiKit.java:1551; FirstLaunchActivity.java:101-105, 256, 269, 541; MainActivity.java:775, 865, 909, 917, 966, 1011 |
| `red` | Danger material fill and edge base; destructive reveal base; dialog destructive action text; negative status values; tutorial warning title | UiKit.java:1974, 2008, 2300, 1551, 1059-1060; MainActivity.java:689, 838, 897 |
| `green` | Positive status values only | UiKit.java:1056-1057; MainActivity.java:689, 838 |
| `yellow` | No reader | UiKit.java:94, 110 |
| `shadow` | No reader | UiKit.java:96, 112 |

## 23. Re-implementation pitfalls

Ordered roughly by how likely each is to be reproduced wrongly.

### 23.1 Colour

| # | Likely mistake | What the source does | Cite |
|---|---|---|---|
| 1 | Giving the light page a grey or blue-grey background so cards stand out | Light `bg`, `surface` and `raised` are all `#FFFFFF`. Separation comes only from the 2 px `#EDEDED` edge, the short shadow and the inner depth | UiKit.java:98-103 |
| 2 | Filling tracks with the palette semantics (`p.red`, `p.blue`, `p.yellow`, `p.green`) | Tracks use the separate `TRACK_*` screen colours. "Off" is orange `#CE8353`, not `p.red` `#C85858`; "on / standard" is `#86ACF7`, not `p.blue` `#315A9B` | UiKit.java:51-66 |
| 3 | Copying the edge hex from the code comment | The comment at UiKit.java:1882-1883 lists `#984040 / #ad8215 / #244879 / #1f6f4e`; the drawn edges are `#BE6938 / #C29E52 / #6990E0 / #5B9774` | UiKit.java:62-65 |
| 4 | Colouring dark-theme tracks per state | Every dark track is the same neutral bed `#191E25` with edge `#272C33`; state is shown by thumb position and label tint only | UiKit.java:570-573, 2582, 2589 |
| 5 | Snapping the bed colour when the state commits | Bed and edge are interpolated continuously from the thumb position during drag and animation (`blend` between neighbouring states) | UiKit.java:2567-2574, 2780-2784 |
| 6 | Treating the theme selector as an ordinary 3-way segment | Its middle bed is `#E2EDF7` / edge `#B7CCE0` (not yellow) and its off-thumb labels become `#34485C`; the special case is detected by the exact labels `"跟随系统"`, `"浅色"`, `"深色"` | UiKit.java:2542-2545, 2594, 2604-2609 |
| 7 | Using white off-thumb labels on every light bed | Yellow bed (candidate 1, 3 or more labels): `#2C2104`; green bed (candidate 3, 4 labels): `#F4FFF9`; otherwise white. The colour depends on the candidate, not on the label's own index | UiKit.java:2592-2598 |
| 8 | Building tokens for `Palette.shadow` and `Palette.yellow` and wiring shadows to them | Both tokens are declared and never read. Light shadows are tinted with `p.ink`, dark shadows are pure black | UiKit.java:96, 112, 1706, 1745 |
| 9 | Treating `blend()` as alpha-aware or gamma-correct | It is an 8-bit sRGB lerp that drops alpha and rounds; several results collapse to an input (light inset fill and bottom = white; dark inset bottom = fill) | UiKit.java:1650-1656, 2204-2206 |
| 10 | Assuming high contrast restyles everything | Only `muted`, `border` and D1 change. Dark S1 / K4 / dialog edges, the light K3 edge, tracks and shadows do not change | UiKit.java:90-91, 105-106, 463, 2008-2016 |
| 11 | Computing D1 against the surface it sits on | D1 is always `blend(p.bg, p.ink, 0.14f)` (`#DFDFE0` / `#363A41`), and `p.border` in high contrast | UiKit.java:463 |
| 12 | Using dark `p.blue` as the dark accent in XML | `colorAccent` is `#315A9B` in every theme; `guard_text` `#202124` is unused and differs from `p.ink` | styles.xml:5, 13, 23, 50; colors.xml:3, 5 |

### 23.2 Shadows and depth

| # | Likely mistake | What the source does | Cite |
|---|---|---|---|
| 13 | Reproducing the outer shadow with elevation, `setShadowLayer` or a blur filter using the CSS numbers | Light shadows are 18 stacked rounded rects of `p.ink` with a linear falloff, no blur. The CSS blur/offset only set the nominal reach (`0.48 x blur` sideways, `0.22 x (blur - offset)` up, `offset + 0.72 x blur` down) | UiKit.java:1683-1708 |
| 14 | Using the CSS opacity as the shadow opacity | Per-shell alpha is `max(1, round(opacity * 0.76 / 18))`, which is 1 for 20-33 and 2 for 41-59. Peak darkness is 6.83% or 13.21%, and S1 (23) and K1 (31) have the same density | UiKit.java:1695-1699 |
| 15 | Letting the shadow extend to its nominal reach | Each reach is clamped to the room between the material and the canvas clip, so the visible shadow is exactly the host gutter (S1 1.4 / 1 / 4.8dp; K1 1.5 / 1.5 / 6.5dp; UI-001 2.5 / 2 / 10dp) and never touches a clip edge | UiKit.java:1677-1685 |
| 16 | Sizing a control view to its visible material | Views are larger than the material: 60dp for a 52dp track, 92dp for the 80dp power capsule, 61 x 34dp for the 58 x 30dp toggle. Layout and centring use the view box | UiKit.java:504-506; MainActivity.java:286-287; WhitelistAccountsPage.java:234 |
| 17 | Re-centring content on the visible capsule or card | Content is centred in the view box; the material sits 1.9dp above the view centre (insets 1dp top, 4.8dp bottom), so labels sit slightly low. This is the shipped appearance | UiKit.java:1960-1961 |
| 18 | Using one shadow algorithm for both themes | Dark uses a different painter: 4 black shells with per-shell spread and alpha, shifted down, not clamped to the gutter | UiKit.java:1738-1757 |
| 19 | Tinting all inner shadows the same way | S1 and K3 inner depth use `p.ink` for both bands (both darken). K1 uses black at the top and a white highlight at the bottom | UiKit.java:1786-1801, 1862-1875 |
| 20 | Making inner depth proportional to height | Band depth is absolute: `offset + 2 x blur` dp, capped at half the height. Only `InsetFieldDrawable` and the body gradients are proportional | UiKit.java:1782-1783, 1859, 1870, 2211 |
| 21 | Giving dark or danger materials the light inner depth | Inner depth is drawn only for light neutral materials and the light thumb. Dark and danger get a 3-stop body gradient (stops 0 / 0.56 / 1) and a 3-stop gradient edge (stops 0 / 0.60 / 1) instead | UiKit.java:1993-2004, 2012-2017 |
| 22 | Clipping every thumb shadow to its track, or none | The thumb is clipped to the track pill in `SegmentControl` and `PowerSwitch` only; in `CompactToggle` and `PreciseValueControl` the thumb shadow crosses the track edge | UiKit.java:2516-2522, 2792-2798, 585, 730 |
| 23 | Animating or changing the card shadow on press | `MaterialSurfaceDrawable` always draws the rest shadow. Pressed shadow parameters exist only for tracks and thumbs, and switch instantly | UiKit.java:1990, 1726-1729, 1912-1913 |
| 24 | Enabling elevation or outline clipping on these views | Elevation is forced to 0 and S1 disables outline, child and padding clipping; the reported outline is the inset material rect | UiKit.java:372-375, 2040-2045 |

### 23.3 Geometry and edges

| # | Likely mistake | What the source does | Cite |
|---|---|---|---|
| 25 | Treating 0.45dp / 0.85dp / 1.4dp as sub-pixel values | `ui.dp()` rounds every term to whole pixels before use (0.45dp is 1 px from density 1.5 to 3.0 and 0 px at 1.0; 0.85dp is 1-3 px). Only the overlay drawable uses unrounded floats | UiKit.java:249-251, 2072 |
| 26 | Deriving a pill radius from the view height | `cornerDp >= 900` means half of the material rect height; tracks use `bounds.height() / 2` of the track rect | UiKit.java:1966-1968, 1834 |
| 27 | Drawing one edge style everywhere | Light neutral: solid `p.border`, 0.85dp. Dark and danger: vertical 3-stop gradient stroke. K1: solid per-state colour, 1dp, inset 0.5dp. K3 light: solid `#C8C9C9`, 0.9dp. Inset field: gradient, dark at the top and light at the bottom, 0.9dp | UiKit.java:2006-2023, 1884-1890, 1921-1934, 2216-2227 |
| 28 | Clipping D1 to the card interior | D1 spans the full view width through negative margins and overhangs the card edge by the 1.4dp gutter on both sides; vertical margins are 5dp on main pages and 0 elsewhere | UiKit.java:1047-1048; MainActivity.java:1221-1227; WhitelistAccountsPage.java:199-204 |
| 29 | Drawing the onboarding card as one S1 | It is two drawables (top half without shadow, bottom half with shadow), side gutter 0.7dp, each stroking its full outline and then erasing 2.5dp at the join so the D1 owns that line | UiKit.java:2355-2359, 2425-2434 |
| 30 | Implementing the onboarding dark values as verified design | The dark branch exists but the first-launch flow is forced light, so it has never been rendered | UiKit.java:201-208; FirstLaunchActivity.java:52 |
| 31 | Reusing the S1 drawable for the service overlay | The overlay drawable has radius 10dp, a real `setShadowLayer` blur (7dp, dy 3.1dp, `rgb(30,39,50)` alpha 23), a second inner stroke in dark, a different dark edge base and needs a software layer | UiKit.java:2079, 2104-2107, 2143-2144, 2159-2169; StatusOverlayController.java:157 |
| 32 | Adding a focus ring or error state to the input well | `InsetFieldDrawable` is stateless; validation errors are shown as a separate danger capsule (E3) | UiKit.java:2186-2242, 1414-1426 |
| 33 | Rounding the embedded row or the delete bed | `SurfaceFillDrawable` and `DestructiveRevealDrawable` are square, edge-less and shadow-less; the parent S1 owns edge and shadow | UiKit.java:2255-2272, 2297-2315 |
| 34 | Clipping images with an outline provider | `ClippedImageView` uses `canvas.clipPath` because the onboarding image needs top-only corners (7dp inside an 8dp section with a 1dp inset; 4dp inside a 7dp well with a 4dp inset) | UiKit.java:1162-1196, 421-423, 432-434 |
| 35 | Assuming the K5 thumb is 26.4dp | The size is `min(dp(26.4), trackHeight - 2 x dp(2.8))`; in the shipped 61 x 34dp host the track is 30dp high, so the thumb is 24.4dp (67 px at density 2.75) | UiKit.java:580; WhitelistAccountsPage.java:234 |

### 23.4 Window and theme

| # | Likely mistake | What the source does | Cite |
|---|---|---|---|
| 36 | Relying on XML themes for component colour | XML themes fix only the first frame (window background, bars, accent). Every component colour is computed in Java from `Palette` | styles.xml:3-57; UiKit.java:205-212 |
| 37 | Setting bar colours the same way on every API level | Below SDK 35: opaque `p.bg` bars with light-bar flags in light theme. From SDK 35: transparent bars, and the root view is padded by the system-bar insets | UiKit.java:237-246, 269-282 |
| 38 | Reading system night mode in a service or overlay | Dark is resolved from the app preference first (`theme` 0 / 1 / 2), and only `THEME_SYSTEM` falls back to `uiMode` | AppPreferences.java:41-47; UiKit.java:2062-2065 |
| 39 | Letting first launch follow the dark preference | First launch uses its own light theme, a forced-light palette and `forceDarkAllowed=false`; the high-contrast preference still applies | styles.xml:48-57; UiKit.java:139-142, 201-209 |
| 40 | Shipping the `Spatial` translucent themes as part of the design | They are defined but never applied | styles.xml:32-46; UiKit.java:133-137 |
