package io.github.avalonskyafar.mallet.showcase

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import io.github.avalonskyafar.mallet.interaction.MalletBack
import io.github.avalonskyafar.mallet.interaction.MalletSpatialHost
import io.github.avalonskyafar.mallet.interaction.MalletThemeTransition
import io.github.avalonskyafar.mallet.material.MalletSurfaceDrawable
import io.github.avalonskyafar.mallet.theme.MalletMotion
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTheme
import io.github.avalonskyafar.mallet.theme.MalletWindow
import kotlin.math.roundToInt

/**
 * The whole showcase is one activity: a [MalletSpatialHost] showing the pages of [Pages.graph].
 *
 * Read this file for the pattern of a spatially navigated app: build the graph, hand the host a
 * page factory, attach system back, and recreate with a fade when the theme changes.
 */
class MainActivity : Activity(), ShowcaseHost {

    private lateinit var host: MalletSpatialHost
    private lateinit var back: MalletBack.Registration
    private val handler = Handler(Looper.getMainLooper())
    private var message: TextView? = null

    override val activity: Activity get() = this

    override val settings: ShowcaseSettings get() = (application as ShowcaseApp).settings

    override fun onCreate(savedInstanceState: Bundle?) {
        // The window background must match the palette before the first frame.
        setTheme(if (settings.isDark(this)) R.style.Theme_Showcase_Dark else R.style.Theme_Showcase_Light)
        super.onCreate(savedInstanceState)
        MalletWindow.apply(this)
        MalletWindow.preferHighestRefreshRate(this)

        host = MalletSpatialHost(this).apply {
            id = R.id.showcase_host
            setGraph(Pages.graph) { key -> Pages.create(key, this@MainActivity) }
            // The four pages around Home stay alive; detail pages are rebuilt on each visit.
            setKeepAlive(setOf(Pages.HOME, "foundations", "components", "interactions", "appearance"))
            prewarmNeighbours = true
        }
        setContentView(host)
        val start = intent.getStringExtra(EXTRA_PAGE)?.takeIf { Pages.graph.contains(it) }
        if (savedInstanceState == null) host.show(start ?: Pages.HOME)
        back = MalletBack.attach(this, host)

        // If this creation follows a theme change, fade the old look over the new one.
        MalletThemeTransition.play(this)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        if (host.currentKey == null) host.show(Pages.HOME)
    }

    override fun open(key: String) {
        host.navigate(key)
    }

    override fun back() {
        if (!host.back()) finish()
    }

    // Below Android 13 only. From 13 on, MalletBack registers an OnBackInvokedCallback.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!back.onBackPressed()) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun applyAppearance(source: View) {
        // Wait for the control that caused the change to finish gliding, then recreate.
        MalletThemeTransition.recreateAfter(source)
    }

    /**
     * A minimal message pill built from the porcelain material. (The library's own toast is
     * shown on the Dialogs and sheets page.)
     */
    override fun say(message: CharSequence) {
        val theme = MalletTheme.of(this)
        val density = resources.displayMetrics.density
        val content = findViewById<ViewGroup>(android.R.id.content)
        this.message?.let { old ->
            old.animate().cancel()
            content.removeView(old)
        }
        val pill = TextView(this).apply {
            text = message
            MalletTextRole.CAPTION.applyTo(this, theme)
            typeface = io.github.avalonskyafar.mallet.theme.MalletWeight.MEDIUM.typeface()
            setTextColor(theme.colors.ink)
            gravity = Gravity.CENTER
            setPadding((18 * density).roundToInt(), (12 * density).roundToInt(), (18 * density).roundToInt(), (14 * density).roundToInt())
            background = MalletSurfaceDrawable(this@MainActivity, pill = true)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        )
        params.bottomMargin = (64 * density).roundToInt()
        params.marginStart = (18 * density).roundToInt()
        params.marginEnd = (18 * density).roundToInt()
        content.addView(pill, params)
        this.message = pill
        val rise = 18 * density
        val duration = MalletMotion.feedbackDuration(theme.reduceMotion)
        pill.translationY = rise
        pill.animate().translationY(0f).setDuration(duration).setInterpolator(MalletMotion.glide()).start()
        handler.removeCallbacksAndMessages(MESSAGE_TOKEN)
        handler.postAtTime({
            if (this.message !== pill) return@postAtTime
            pill.animate().translationY(rise).setDuration(duration).setInterpolator(MalletMotion.glide())
                .withEndAction {
                    content.removeView(pill)
                    if (this.message === pill) this.message = null
                }.start()
        }, MESSAGE_TOKEN, android.os.SystemClock.uptimeMillis() + MESSAGE_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        back.remove()
        super.onDestroy()
    }

    companion object {
        /** Optional start page, for deep links from tests and screenshots. */
        const val EXTRA_PAGE: String = "page"

        private const val MESSAGE_MS = 2600L
        private val MESSAGE_TOKEN = Any()
    }
}
