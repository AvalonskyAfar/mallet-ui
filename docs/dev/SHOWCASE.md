# Writing a showcase page

The Showcase app (`:showcase`) is the proof that the library works. Every page uses the real
widgets, is fully interactive, and shows the code that produced what the reader is touching.

## How the app is put together

- `MainActivity` hosts a `MalletSpatialHost`. `Pages.graph` is the map: Home in the middle
  and four first-level pages around it, Foundations at its start side, Interactions at its
  end side, Components below, Appearance above. Deeper pages are opened from rows of
  Foundations, Components and Interactions.
- Home shows nothing that points at its neighbours: no arrows, no page names at the edges.
  It names the pages around it once, in a row of entries at its bottom: a `MalletNavBar` in
  `Mode.LAUNCHER` with one entry (an outline icon above a short label) for each first-level
  page, the start side first, the end side last, the pages below and above between them. An
  entry can be tapped; the page it names is also reached by dragging Home towards the side
  on which that page lies. A new first-level page needs its place in `Pages.graph` and its
  entry in `HomePage`.
- A first-level page shows no way back: no back row, no arrow, no bar. It is left the way it
  was reached, by dragging back towards Home, or with the system's Back. For a keyboard, a
  switch or a screen reader the system's Back is the way back there; nothing on screen
  offers it.
- A page is a Kotlin `object` extending `DemoPage(key, title, summary)` (the title and the
  summary are string resources) in
  `showcase/src/main/kotlin/io/github/avalonskyafar/mallet/showcase/pages/`. It implements
  `build(page: PageScope)`.
- `PageScope` builds the page (a `MalletPage`) with the rhythm of the design system. It adds
  a way-back row only to deeper pages: a quiet row at the top that names the parent, with a
  chevron towards the side on which the parent lies (a parent that lies below would be named
  at the bottom instead). Home and the first-level pages get none, on purpose. A page only
  adds content.
- The theme comes from `ShowcaseSettings` through `MalletTheme.resolver`; pages never choose
  colours for the theme. Read it with `page.theme`.

Pages that carry no navigation marks are the original app's own design, extracted: no marks
on its main page, a bottom row with one entry for each first-level page, first-level pages
without a way back. Four entries instead of three, a page above Home, and start and end
instead of left and right extend it. The way-back row of deeper pages is new and exists only
in the showcase, as an aid for readers of a demo with many deeper pages; the original has no
back control at any depth. The rule and its reasons are recorded as D-23 in
[DESIGN_DECISIONS.md](../DESIGN_DECISIONS.md); `NavigationMarksTest` holds the showcase to it.

## `PageScope` in one glance

```kotlin
object TracksPage : DemoPage("c_tracks", R.string.page_c_tracks_title, R.string.page_c_tracks_summary) {
    override fun build(page: PageScope) {
        fun track(vararg labels: Int) =
            MalletSegmentedTrack(page.context).apply { options = labels.map { page.str(it) } }
        val hues = page.theme.hues

        page.title()                                   // page title and summary

        // A card is a group: this one holds two tracks that belong together.
        val view = track(R.string.tracks_list, R.string.tracks_grid)
        view.setHues(hues.uniform(2, hues.mist))
        val order = track(R.string.tracks_newest, R.string.tracks_oldest, R.string.tracks_by_name)
        order.setHues(hues.uniform(3, hues.jade))
        page.section(R.string.tracks_plain_title, R.string.tracks_plain_detail) {
            // `this` is the MalletSurface card of the section
            addView(view)
            addDivider(5f)
            addView(order)
        }
        page.code("""
            val hues = MalletTheme.of(this).hues
            val view = MalletSegmentedTrack(this)
            view.setOptions("List", "Grid")
            view.setHues(hues.uniform(2, hues.mist))
        """)

        // A lone control gets no card: it stands on the canvas under its heading.
        val level = track(
            R.string.tracks_off, R.string.tracks_gentle, R.string.tracks_standard, R.string.tracks_strict,
        )
        page.heading(R.string.tracks_refuse_title, R.string.tracks_refuse_detail)
        page.loose(level, topDp = PageScope.HEADING_GAP)

        page.caption(page.str(R.string.tracks_origin)) // muted remark on the canvas
    }
}
```

| Helper | What it adds |
|---|---|
| `title(title, detail)` | the page title; defaults to the page's own |
| `section(title, detail = 0) { ... }` | a heading and a card under it, for a group of at least two rows or blocks; returns the card |
| `heading(title, detail = 0)` | a heading alone; the page adds what stands under it, such as a lone control |
| `loose(view, topDp, sideDp, width, gravity)` | a view on the canvas: a lone control under its heading (`topDp = PageScope.HEADING_GAP`), a capsule button (`sideDp = PageScope.BUTTON_SIDE`) |
| `caption(text, topDp)` | muted remark |
| `code(snippet, topDp, wrap = true)` | selectable monospace sample in a well; `wrap = false` for XML |
| `text(value, role, tone)` | a `MalletText` |
| `inflate(layout, topDp)` | a layout file inflated into the column; returns its root view |
| `dp(n)`, `str(res)`, `context`, `theme`, `host` | utilities |
| `host.say(message)` | short message pill at the bottom |
| `host.open(key)` / `host.back()` | travel |

Inside a card use `LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP)`. A control on
the canvas is as wide as the cards of the page.

## Rules

1. **Interactive, not illustrative.** Every control on a page can be operated. Where a
   control has a listener, show its effect: a status line that changes, a second control that
   follows, a message through `host.say(...)`.
2. **Show states.** Each component appears in its important states (default, a second value,
   disabled; loading or error where it has them) and, where it has a coloured bed, with at
   least one other hue. The states of one component are rows or blocks of one card, not a
   card each.
3. **A card is a group.** `page.section { }` makes a card, and a card holds at least two
   rows or blocks that belong together. A lone control never gets a card of its own, and a
   caption or status line under it does not make a group.
   - Merge first. Variants of one component, and controls that are relatives, share one
     card: one block for each control, with a short label above it, and the status line
     of a control moves into its block.
   - Otherwise the control stands on the canvas under its heading:
     `page.heading(title, detail)`, then
     `page.loose(control, topDp = PageScope.HEADING_GAP)`.
   - A card around one composite block may stay: a list of rows, a state view, a pager with
     its dots, a paragraph, a picture, a stage with the knob that drives it. If it reads as
     a box around a box, take the card away.
   - Inside a card, a full-bleed divider (`addDivider()`; `addDivider(5f)` between
     full-width tracks) separates blocks of different kinds and the rows of a list. Sibling
     controls of one kind get no hairline between them.

   Grouping rows in cards is extracted from the original app; that a lone control gets no
   card is new in Mallet. The decision and its reasons are recorded as D-25 in
   [DESIGN_DECISIONS.md](../DESIGN_DECISIONS.md).
4. **Show the code.** After each section, a `page.code(...)` sample of at most about eight
   lines with the calls that built it, written the way a user of the library would write
   them. Keep the sample truthful: it must compile if pasted into an activity, and when the
   way a demo is built changes, its sample changes with it. Lines too long for the screen
   are wrapped for you at safe places (`CodeWrap`); still, prefer short lines, and never
   rely on a trailing comment staying on its line. Set listeners the way the library is
   meant to be used, with a trailing lambda:
   `switch.setOnCheckedChangeListener { _, isChecked -> ... }`.
5. **Say where it comes from.** End the page with one caption that names what on it was
   extracted from the original app and what was designed for Mallet. Keep it to one or two
   sentences.
6. **Strings are resources**, in English and in Simplified Chinese:
   `showcase/src/main/res/values/strings_<page>.xml` and
   `showcase/src/main/res/values-zh/strings_<page>.xml`. Prefix every name with the page key
   stem (`tracks_...`, `follow_...`). The page title and summary strings already exist
   (`page_<key>_title`, `page_<key>_summary`): do not redefine them. Code samples are not
   translated and stay in the Kotlin file.
7. **Write like the design**: short, plain sentences. No exclamation marks, no marketing.
   The design is told in plain words (raised, recessed, sinks, follows, glides); build no
   images on top of them. Sample texts need no story: a notebook, a workshop, a device
   setting. Chinese copy is written as Chinese, not translated word for word.
8. **Only library widgets.** No `android.widget.Button`, `Switch`, `CheckBox`, `SeekBar`,
   `ProgressBar`, `AlertDialog`, `Toast`. Plain `LinearLayout`, `FrameLayout`, `View` and
   `TextView` are fine for arranging things; text should be `MalletText` (`page.text`).
9. **One file per page**, plus private helper functions or a private view class in the same
   file when a page needs a custom drawing (colour swatches, a material cross-section).
   A new page is listed in `Pages.kt`, which also gives it its place on the map.
10. A page must look right in light and dark, at text scale 1.13, and in right-to-left.

## Words (English and Chinese)

Use the same word for the same thing on every page, in demo texts, comments and code
samples alike. The Chinese follows the vocabulary of the original app where it had one
(底色, 凹槽 and 滑块 are its own words).

| English | 中文 | | English | 中文 |
|---|---|---|---|---|
| Mallet | Mallet | | hue | 底色 |
| surface (the raised material) | 浮层 | | track | 轨道 |
| thumb | 滑块 | | well | 凹槽 |
| bed (of a track) | 槽底 | | bead (slider, radio) | 圆珠 |
| bronze / ochre / cobalt / jade / mist | 古铜 / 赭黄 / 钴蓝 / 青玉 / 雾白 | | to colour / plain | 着色 / 素底 |
| card | 卡片 | | capsule | 胶囊 |
| rim | 描边 | | divider | 分隔线 |
| shadow gutter | 阴影留边 | | canvas (page background) | 页面背景 |
| press / sink | 按压 / 下沉 | | glide / settle | 滑行 / 归位 |
| Off / On | 关闭 / 开启 | | Gentle / Standard / Strict | 轻度 / 标准 / 严格 |
| segmented track | 分段轨道 | | switch / power switch | 开关 / 总开关 |
| stepper / slider | 步进器 / 滑杆 | | progress bar / spinner | 进度条 / 加载环 |
| checkbox / radio | 复选框 / 单选框 | | text field | 文本框 |
| button / chip / tag / badge | 按钮 / 筛选片 / 状态标签 / 角标 | | entry row / control row / link row | 入口行 / 控制行 / 链接行 |
| dialog / sheet / menu / toast | 对话框 / 底部面板 / 菜单 / 轻提示 | | page / neighbour | 页面 / 邻页 |
| Home | 首页 | | first-level page / deeper page | 一级页面 / 更深的页面 |
| entry (of Home's row) | 入口 | | navigation row / way-back row | 导航行 / 返回行 |
| cell (of a row that slides aside) | 行格 | | start side / end side | 起始侧 / 末端侧 |
| extracted from the original app | 取自原应用 | | new in Mallet | Mallet 新增 |

Some of these words have rules of their own:

- **Mallet** is written the same in every language. In Chinese text it stays Latin, with a
  space on each side where Chinese characters touch it: `为 Mallet 新设计的`.
- **hue** is a noun, and it means one of the five state colours of a recessed bed. A hue
  fills a bed; a track is coloured with one hue or with a scale of hues. Where any other
  colour is meant (the colour of a button, the danger colour), say colour.
- In Chinese the hue is 底色. The bed that carries it is 槽底 and the canvas is 页面背景, so
  that neither is taken for the hue.
- A card is 卡片. As a material, the raised surface of cards, capsules and dialogs is 浮层.
- An **entry** (入口) is one item of the row at the bottom of Home; an **entry row** (入口行)
  is the component `MalletEntryRow`. The **navigation row** (导航行) is the component
  `MalletNavBar`, which Home uses in launcher mode.
- A **first-level page** lies directly beside, above or below Home. A **deeper page** is
  opened from a row of a first-level page, or lies further out; only deeper pages carry the
  **way-back row**.

## Building and looking

```
./gradlew :showcase:testDebugUnitTest --tests "*ShowcasePagesTest" -PmalletPages=page_c_tracks_
```

`ShowcasePagesTest` builds every page the way the app does, on the JVM with Robolectric, and
writes a picture of each to `showcase/build/snapshots`: `page_<key>_light.png`, `_dark.png`,
`_zh.png` (Chinese), `_rtl.png` (right to left, dark) and `_large.png` (system font scale 1.3
with the app's larger text). `-PmalletPages=<regex>` limits the run to the pictures whose
name matches; leave it out to draw all of them. A page that throws or comes out blank fails
the test. Pages taller than 3200 dp are cut in the picture.

Whether a page looks right is not something the test can say: open the pictures and judge
them as a designer would, in every variant. Then try the page on a device
(`./gradlew :showcase:installDebug`), where gestures and motion can be felt.

`ShowcaseFilmTest` (`-PmalletFilms`) starts the real activity on the JVM, touches it and
draws it frame by frame into `showcase/build/films`; `python tools/films.py` turns the frames
into GIFs. Add a film when a page shows a gesture worth seeing.
