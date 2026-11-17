# Writing a showcase page

The Showcase app (`:showcase`) is the proof that the library works. Every page uses the real
widgets, is fully interactive, and shows the code that produced what the reader is touching.

## How the app is put together

- `MainActivity` hosts a `SancaiSpatialHost`. `Pages.graph` is the map: Home in the middle,
  Foundations at its start side, Interactions at its end side, Components below, Appearance
  above. Detail pages are opened from rows of those four.
- A page is a Kotlin `object` extending `DemoPage(key, titleRes, summaryRes)` in
  `showcase/src/main/kotlin/io/github/avalonskyafar/sancai/showcase/pages/`. It implements
  `build(page: PageScope)`.
- `PageScope` builds the scrolling column with the rhythm of the design system and adds the
  way back by itself. You only add content.
- The theme comes from `ShowcaseSettings` through `SancaiTheme.resolver`; pages never choose
  colours for the theme. Read it with `page.theme`.

## `PageScope` in one glance

```kotlin
object TracksPage : DemoPage("c_tracks", R.string.page_c_tracks_title, R.string.page_c_tracks_summary) {
    override fun build(page: PageScope) {
        page.title()                                   // page title and summary

        page.section(R.string.tracks_scale_title, R.string.tracks_scale_detail) {
            // `this` is the SancaiSurface card of the section
            addView(SancaiSegmentedTrack(context).apply { setOptions("Off", "On") })
            addDivider(5f)
            addView(...)
        }
        page.code("""
            track.setOptions("Off", "Gentle", "Standard", "Strict")
            track.onSelectionChangedListener = ...
        """)
        page.caption(page.str(R.string.tracks_note))   // muted remark on the canvas
        page.loose(button, topDp = 22, sideDp = PageScope.BUTTON_SIDE)   // anything on the canvas
    }
}
```

| Helper | What it adds |
|---|---|
| `title(titleRes, detailRes)` | the page title; defaults to the page's own |
| `section(titleRes, detailRes = 0) { ... }` | heading + card; returns the card |
| `loose(view, topDp, sideDp, width, gravity)` | a view on the canvas |
| `caption(text, topDp)` | muted remark |
| `code(snippet, topDp)` | selectable monospace sample in a well |
| `text(value, role, tone)` | a `SancaiText` |
| `dp(n)`, `str(res)`, `context`, `theme`, `host` | utilities |
| `host.say(message)` | short message pill at the bottom |
| `host.open(key)` / `host.back()` | travel |

Inside a card use `LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP)`.

## Rules

1. **Interactive, not illustrative.** Every control on a page can be operated. Where a
   control has a listener, show its effect: a status line that changes, a second control that
   follows, a message through `host.say(...)`.
2. **Show states.** Each component appears in its important states (default, a second value,
   disabled; loading or error where it has them) and at least one glaze variation where it is
   glazed.
3. **Show the code.** After each section, a `page.code(...)` sample of at most about eight
   lines with the calls that built it, written the way a user of the library would write
   them. Keep the sample truthful: it must compile if pasted into an activity.
4. **Say where it comes from.** End the page with one caption that names what on it was
   extracted from the original app and what was designed for Sancai. Keep it to one or two
   sentences.
5. **Strings are resources**, in English and in Simplified Chinese:
   `showcase/src/main/res/values/strings_<page>.xml` and
   `showcase/src/main/res/values-zh/strings_<page>.xml`. Prefix every name with the page key
   stem (`tracks_...`, `follow_...`). The page title and summary strings already exist
   (`page_<key>_title`, `page_<key>_summary`): do not redefine them. Code samples are not
   translated and stay in the Kotlin file.
6. **Write like the design**: short, plain sentences. No exclamation marks, no marketing.
   Chinese copy is written as Chinese, not translated word for word.
7. **Only library widgets.** No `android.widget.Button`, `Switch`, `CheckBox`, `SeekBar`,
   `ProgressBar`, `AlertDialog`, `Toast`. Plain `LinearLayout`, `FrameLayout`, `View` and
   `TextView` are fine for arranging things; text should be `SancaiText` (`page.text`).
8. **One file per page**, plus private helper functions or a private view class in the same
   file when a page needs a custom drawing (colour swatches, a material cross-section).
   Do not edit `Demo.kt`, `Pages.kt`, `MainActivity.kt` or other pages. If you need something
   from the scaffold, say so in your report.
9. A page must look right in light and dark, at text scale 1.13, and in right-to-left.

## Building and looking

```
bash /d/DeepSeek/android-toolchain/work.sh pages <work-copy-key> page_<page-key>_
```

This compiles the library and the showcase and runs `ShowcasePagesTest`, which builds the
pages whose snapshot name matches the filter (a regular expression; leave it out to build
all pages), in light and in dark, with Robolectric. A page that throws or comes out blank
fails the test. The pictures are written to
`showcase/build/snapshots/page_<page-key>_light.png` and `..._dark.png` in the work copy.
Pages taller than 3200 dp are cut there. Open your two files with the Read tool and judge
them as a designer would.
