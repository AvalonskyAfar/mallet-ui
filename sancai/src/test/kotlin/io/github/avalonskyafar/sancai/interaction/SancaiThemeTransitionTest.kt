package io.github.avalonskyafar.sancai.interaction

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.widget.SancaiButton
import io.github.avalonskyafar.sancai.widget.SancaiSegmentedTrack
import io.github.avalonskyafar.sancai.widget.SancaiSurface
import io.github.avalonskyafar.sancai.widget.SancaiText
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiThemeTransitionTest {

    /** Stands in for the app's palette setting: every activity resolves to this theme. */
    private var current: SancaiTheme = SancaiTheme.light()
    private val opened = ArrayList<Activity>()

    /** Counts recreations instead of performing them. */
    class RecordingActivity : Activity() {
        var recreations = 0

        override fun recreate() {
            recreations++
        }
    }

    @Before
    fun installTheme() {
        SancaiTheme.resolver = SancaiTheme.Resolver { current }
        // Frames only run as the clock moves, so a test can stop in the middle of the fade.
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(FRAME_MS))
    }

    @After
    fun cleanUp() {
        opened.forEach(SancaiThemeTransition::discard)
        SancaiTheme.resolver = null
    }

    /** Moves the clock frame by frame, the way a display would. */
    private fun advance(ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(left, FRAME_MS)
            ShadowLooper.idleMainLooper(step, TimeUnit.MILLISECONDS)
            left -= step
        }
    }

    /** Lays the content out now, without letting a frame (and so the fade) pass. */
    private fun layoutNow(activity: Activity) {
        val content = content(activity)
        content.measure(
            View.MeasureSpec.makeMeasureSpec(content.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(content.height, View.MeasureSpec.EXACTLY),
        )
        content.layout(content.left, content.top, content.right, content.bottom)
    }

    /** A small settings page painted in the theme that is current when it is built. */
    private fun page(activity: Activity): LinearLayout {
        val theme = SancaiTheme.of(activity)
        val d = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).roundToInt()
        fun rows() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
        val page = LinearLayout(activity)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(theme.colors.canvas)
        page.setPadding(dp(20), dp(22), dp(20), dp(24))
        val title = SancaiText.create(activity, "Appearance", SancaiTextRole.TITLE, SancaiTextTone.INK)
        title.gravity = Gravity.CENTER_HORIZONTAL
        page.addView(title, LinearLayout.LayoutParams(-1, -2))
        val card = SancaiSurface(activity)
        val label = SancaiText.create(activity, "Palette", SancaiTextRole.HEADING, SancaiTextTone.INK)
        label.gravity = Gravity.CENTER_HORIZONTAL
        card.addView(label, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        val track = SancaiSegmentedTrack(activity)
        track.options = listOf("Light", "Dark")
        track.setGlazes(theme.glazes.uniform(2, theme.glazes.mist))
        track.select(if (theme.isDark) 1 else 0, animate = false)
        track.id = TRACK_ID
        card.addView(track, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val hint = SancaiText.create(
            activity, "The page keeps its place while the light changes.",
            SancaiTextRole.CAPTION, SancaiTextTone.MUTED,
        )
        hint.gravity = Gravity.CENTER_HORIZONTAL
        card.addView(hint, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        page.addView(card, rows())
        val button = SancaiButton(activity)
        button.text = "Done"
        button.variant = SancaiButton.Variant.PRIMARY
        page.addView(button, rows())
        return page
    }

    /** Starts an activity in [theme], optionally with the intent of a recreated one. */
    private fun <T : Activity> open(type: Class<T>, theme: SancaiTheme, intent: Intent? = null): T {
        current = theme
        val activity = Robolectric.buildActivity(type, intent).setup().get()
        activity.setContentView(page(activity), ViewGroup.LayoutParams(-1, -1))
        advance(2 * FRAME_MS)
        opened += activity
        return activity
    }

    private fun open(theme: SancaiTheme, intent: Intent? = null): Activity =
        open(Activity::class.java, theme, intent)

    /** What the user sees right now: the content view drawn as it stands. */
    private fun frame(activity: Activity): Bitmap {
        val content = content(activity)
        val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
        content.draw(Canvas(bitmap))
        return bitmap
    }

    private companion object {
        const val TRACK_ID = 0x5A11
        const val FRAME_MS = 16L
    }

    @Test
    fun `prepare keeps the old appearance under a token in the intent`() {
        val before = SancaiThemeTransition.storedCount
        val activity = open(SancaiTheme.light())
        assertThat(SancaiThemeTransition.isPrepared(activity)).isFalse()

        assertThat(SancaiThemeTransition.prepare(activity)).isTrue()

        assertThat(activity.intent.getIntExtra(SancaiThemeTransition.EXTRA_SNAPSHOT, 0)).isNotEqualTo(0)
        assertThat(SancaiThemeTransition.isPrepared(activity)).isTrue()
        assertThat(SancaiThemeTransition.storedCount).isEqualTo(before + 1)
    }

    @Test
    fun `preparing again drops the stale picture`() {
        val before = SancaiThemeTransition.storedCount
        val activity = open(SancaiTheme.light())
        SancaiThemeTransition.prepare(activity)
        val first = activity.intent.getIntExtra(SancaiThemeTransition.EXTRA_SNAPSHOT, 0)

        SancaiThemeTransition.prepare(activity)

        assertThat(activity.intent.getIntExtra(SancaiThemeTransition.EXTRA_SNAPSHOT, 0)).isNotEqualTo(first)
        assertThat(SancaiThemeTransition.storedCount).isEqualTo(before + 1)

        SancaiThemeTransition.discard(activity)
        assertThat(SancaiThemeTransition.isPrepared(activity)).isFalse()
        assertThat(activity.intent.hasExtra(SancaiThemeTransition.EXTRA_SNAPSHOT)).isFalse()
        assertThat(SancaiThemeTransition.storedCount).isEqualTo(before)
    }

    @Test
    fun `capture paints the backdrop and refuses empty or oversized content`() {
        val activity = open(SancaiTheme.light())
        val bare = View(activity)
        assertThat(SancaiThemeTransition.capture(bare, Color.RED)).isNull()

        bare.layout(0, 0, 40, 30)
        val small = SancaiThemeTransition.capture(bare, Color.rgb(12, 34, 56))!!
        assertThat(small.width).isEqualTo(40)
        assertThat(small.height).isEqualTo(30)
        assertThat(small.getPixel(20, 15)).isEqualTo(Color.rgb(12, 34, 56))

        // 2048 x 2048 x 4 is exactly the limit; one more row is over it.
        bare.layout(0, 0, 2048, 2049)
        assertThat(SancaiThemeTransition.capture(bare, Color.RED)).isNull()
    }

    @Test
    fun `play lays the old appearance over the new page and removes it after the fade`() {
        val before = SancaiThemeTransition.storedCount
        val old = open(SancaiTheme.light())
        SancaiThemeTransition.prepare(old)
        val recreated = open(SancaiTheme.dark(), old.intent)
        val children = content(recreated).childCount

        assertThat(SancaiThemeTransition.play(recreated)).isTrue()

        val overlay = overlays(recreated).single()
        assertThat(content(recreated).getChildAt(children)).isSameInstanceAs(overlay)
        assertThat(overlay.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
        assertThat(overlay.isClickable).isFalse()
        assertThat(overlay.alpha).isEqualTo(1f)
        val bitmap = (overlay.drawable as android.graphics.drawable.BitmapDrawable).bitmap
        assertThat(recreated.intent.hasExtra(SancaiThemeTransition.EXTRA_SNAPSHOT)).isFalse()
        assertThat(SancaiThemeTransition.storedCount).isEqualTo(before)

        advance(SancaiMotion.THEME_FADE_MS / 2)
        assertThat(overlay.alpha).isGreaterThan(0f)
        assertThat(overlay.alpha).isLessThan(1f)
        assertThat(overlay.parent).isNotNull()

        advance(SancaiMotion.THEME_FADE_MS)
        assertThat(overlays(recreated)).isEmpty()
        assertThat(content(recreated).childCount).isEqualTo(children)
        assertThat(bitmap.isRecycled).isTrue()
    }

    @Test
    fun `the first frame is the old canvas and the last one the new canvas`() {
        val light = SancaiTheme.light().colors.canvas
        val dark = SancaiTheme.dark().colors.canvas
        val old = open(SancaiTheme.light())
        SancaiThemeTransition.prepare(old)
        val recreated = open(SancaiTheme.dark(), old.intent)
        assertThat(frame(recreated).getPixel(6, 6)).isEqualTo(dark)

        SancaiThemeTransition.play(recreated)
        layoutNow(recreated)
        assertThat(frame(recreated).getPixel(6, 6)).isEqualTo(light)

        advance(SancaiMotion.THEME_FADE_MS / 3)
        val between = frame(recreated).getPixel(6, 6)
        assertThat(between).isNotEqualTo(light)
        assertThat(between).isNotEqualTo(dark)
        assertThat(Color.red(between)).isIn(Color.red(dark)..Color.red(light))

        advance(SancaiMotion.THEME_FADE_MS)
        assertThat(frame(recreated).getPixel(6, 6)).isEqualTo(dark)
    }

    @Test
    fun `nothing happens without a prepared token`() {
        val activity = open(SancaiTheme.light())
        val children = content(activity).childCount

        assertThat(SancaiThemeTransition.play(activity)).isFalse()

        assertThat(content(activity).childCount).isEqualTo(children)
    }

    @Test
    fun `a token that outlived its process is dropped`() {
        val intent = Intent().putExtra(SancaiThemeTransition.EXTRA_SNAPSHOT, 7)
        val activity = open(SancaiTheme.light(), intent)

        assertThat(SancaiThemeTransition.isPrepared(activity)).isFalse()
        assertThat(SancaiThemeTransition.play(activity)).isFalse()

        assertThat(overlays(activity)).isEmpty()
        assertThat(activity.intent.hasExtra(SancaiThemeTransition.EXTRA_SNAPSHOT)).isFalse()
    }

    @Test
    fun `reduced motion neither captures nor fades`() {
        val reduced = SancaiTheme.light().copy(reduceMotion = true)
        val calm = open(reduced)
        assertThat(SancaiThemeTransition.prepare(calm)).isFalse()
        assertThat(SancaiThemeTransition.isPrepared(calm)).isFalse()

        // Prepared while motion was still allowed, played after it was switched off.
        val before = SancaiThemeTransition.storedCount
        val old = open(SancaiTheme.light())
        SancaiThemeTransition.prepare(old)
        val recreated = open(SancaiTheme.dark().copy(reduceMotion = true), old.intent)

        assertThat(SancaiThemeTransition.play(recreated)).isFalse()

        assertThat(overlays(recreated)).isEmpty()
        assertThat(SancaiThemeTransition.storedCount).isEqualTo(before)
        assertThat(frame(recreated).getPixel(6, 6)).isEqualTo(SancaiTheme.dark().colors.canvas)
    }

    @Test
    fun `leaving in the middle of the fade frees the picture`() {
        val old = open(SancaiTheme.light())
        SancaiThemeTransition.prepare(old)
        val recreated = open(SancaiTheme.dark(), old.intent)
        SancaiThemeTransition.play(recreated)
        val overlay = overlays(recreated).single()
        val bitmap = (overlay.drawable as android.graphics.drawable.BitmapDrawable).bitmap
        advance(SancaiMotion.THEME_FADE_MS / 3)

        content(recreated).removeView(overlay)

        assertThat(bitmap.isRecycled).isTrue()
        assertThat(overlay.drawable).isNull()
        advance(SancaiMotion.THEME_FADE_MS)
    }

    @Test
    fun `recreateAfter waits for the control and coalesces repeated requests`() {
        assertThat(SancaiThemeTransition.DEFAULT_DELAY_MS).isEqualTo(292L)
        val activity = open(RecordingActivity::class.java, SancaiTheme.light())
        val track = activity.findViewById<View>(TRACK_ID)

        assertThat(SancaiThemeTransition.recreateAfter(track)).isTrue()
        advance(200)
        assertThat(SancaiThemeTransition.recreateAfter(content(activity))).isTrue()
        advance(200)
        assertThat(activity.recreations).isEqualTo(0)
        assertThat(SancaiThemeTransition.isPrepared(activity)).isFalse()

        advance(100)
        assertThat(activity.recreations).isEqualTo(1)
        assertThat(SancaiThemeTransition.isPrepared(activity)).isTrue()

        advance(600)
        assertThat(activity.recreations).isEqualTo(1)
    }

    @Test
    fun `a requested recreation can be withdrawn and skips a finishing activity`() {
        val activity = open(RecordingActivity::class.java, SancaiTheme.light())
        val track = activity.findViewById<View>(TRACK_ID)
        assertThat(SancaiThemeTransition.cancelRecreate(track)).isFalse()

        SancaiThemeTransition.recreateAfter(track, 50)
        assertThat(SancaiThemeTransition.cancelRecreate(track)).isTrue()
        advance(100)
        assertThat(activity.recreations).isEqualTo(0)

        SancaiThemeTransition.recreateAfter(track, 50)
        activity.finish()
        advance(100)
        assertThat(activity.recreations).isEqualTo(0)
        assertThat(SancaiThemeTransition.isPrepared(activity)).isFalse()

        val loose = View(Snapshots.context())
        assertThat(SancaiThemeTransition.recreateAfter(loose)).isFalse()
    }

    @Test
    fun `content that cannot be drawn in software is not captured`() {
        val activity = open(SancaiTheme.light())
        var armed = false
        val broken = object : View(activity) {
            override fun onDraw(canvas: Canvas) {
                if (armed) throw IllegalStateException("hardware bitmap")
            }
        }
        broken.setWillNotDraw(false)
        content(activity).addView(broken, ViewGroup.LayoutParams(-1, -1))
        advance(2 * FRAME_MS)
        assertThat(SancaiThemeTransition.prepare(activity)).isTrue()
        armed = true

        assertThat(SancaiThemeTransition.prepare(activity)).isFalse()
        assertThat(SancaiThemeTransition.isPrepared(activity)).isFalse()
        assertThat(activity.intent.hasExtra(SancaiThemeTransition.EXTRA_SNAPSHOT)).isFalse()
    }

    @Test
    fun `pictures that are never played are dropped as stale`() {
        val activities = List(SancaiThemeTransition.MAX_WAITING + 2) { open(SancaiTheme.light()) }
        activities.forEach { assertThat(SancaiThemeTransition.prepare(it)).isTrue() }

        assertThat(SancaiThemeTransition.storedCount).isEqualTo(SancaiThemeTransition.MAX_WAITING)
        assertThat(SancaiThemeTransition.isPrepared(activities.first())).isFalse()
        assertThat(SancaiThemeTransition.isPrepared(activities.last())).isTrue()
        // The dropped token is still in the intent; playing it shows nothing and clears it.
        assertThat(SancaiThemeTransition.play(activities.first())).isFalse()
        assertThat(overlays(activities.first())).isEmpty()
    }

    @Test
    fun `the old picture also covers elevated content`() {
        val old = open(SancaiTheme.light())
        SancaiThemeTransition.prepare(old)
        val recreated = open(SancaiTheme.dark(), old.intent)
        val raised = View(recreated)
        raised.setBackgroundColor(Color.MAGENTA)
        raised.elevation = 48f
        content(recreated).addView(raised, ViewGroup.LayoutParams(40, 40))
        advance(2 * FRAME_MS)
        assertThat(frame(recreated).getPixel(6, 6)).isEqualTo(Color.MAGENTA)

        SancaiThemeTransition.play(recreated)
        layoutNow(recreated)

        assertThat(frame(recreated).getPixel(6, 6)).isEqualTo(SancaiTheme.light().colors.canvas)
    }

    @Test
    fun `snapshot of the theme transition in light and dark`() {
        // Each picture is the frame a third of the way into the fade towards that theme.
        for ((name, from, to) in listOf(
            Triple("themetransition_light", SancaiTheme.dark(), SancaiTheme.light()),
            Triple("themetransition_dark", SancaiTheme.light(), SancaiTheme.dark()),
        )) {
            val old = open(from)
            SancaiThemeTransition.prepare(old)
            val recreated = open(to, old.intent)
            SancaiThemeTransition.play(recreated)
            advance(SancaiMotion.THEME_FADE_MS / 3)
            val full = frame(recreated)
            val height = minOf(full.height, (full.width * 0.9f).roundToInt())
            Snapshots.save(Bitmap.createBitmap(full, 0, 0, full.width, height), name)
            advance(SancaiMotion.THEME_FADE_MS)
            assertThat(overlays(recreated)).isEmpty()
            Snapshots.save(frame(recreated).let { Bitmap.createBitmap(it, 0, 0, it.width, height) }, name + "_settled")
        }
    }

    @Test
    fun `the first frame repeats the old page exactly with large text and in RTL`() {
        val large = SancaiTheme.light().copy(textScale = 1.13f)
        val old = open(large)
        content(old).layoutDirection = View.LAYOUT_DIRECTION_RTL
        advance(2 * FRAME_MS)
        val seen = frame(old)
        SancaiThemeTransition.prepare(old)
        val recreated = open(SancaiTheme.dark().copy(textScale = 1.13f), old.intent)
        content(recreated).layoutDirection = View.LAYOUT_DIRECTION_RTL
        advance(2 * FRAME_MS)
        assertThat(frame(recreated).sameAs(seen)).isFalse()

        SancaiThemeTransition.play(recreated)
        layoutNow(recreated)

        assertThat(frame(recreated).sameAs(seen)).isTrue()
        advance(SancaiMotion.THEME_FADE_MS + 2 * FRAME_MS)
        assertThat(overlays(recreated)).isEmpty()
    }

    @Test
    fun `an activity without content yet is not captured and play is harmless`() {
        current = SancaiTheme.light()
        val empty = Robolectric.buildActivity(Activity::class.java).create().get()
        opened += empty

        assertThat(SancaiThemeTransition.prepare(empty)).isFalse()
        assertThat(SancaiThemeTransition.play(empty)).isFalse()
        assertThat(SancaiThemeTransition.isPrepared(empty)).isFalse()
    }

    private fun content(activity: Activity): ViewGroup = activity.findViewById(android.R.id.content)

    private fun overlays(activity: Activity): List<ImageView> {
        val content = content(activity)
        return (0 until content.childCount).map(content::getChildAt).filterIsInstance<ImageView>()
    }
}
