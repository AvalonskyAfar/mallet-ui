package io.github.avalonskyafar.mallet.theme

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Display
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Window-level helpers: the part of a Mallet screen that lies outside any view.
 *
 * ```kotlin
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     MalletWindow.apply(this)                 // canvas-coloured window, matching bar icons
 *     setContentView(root)
 *     MalletWindow.padForSystemBars(root)      // keep content clear of the bars
 * }
 * ```
 */
public object MalletWindow {

    /**
     * Paints the window of [activity] in the canvas colour of [theme] and gives the status and
     * navigation bars dark or light icons to match.
     *
     * With [edgeToEdge] (the default) the content is laid out behind transparent system bars,
     * which is also what Android 15 and later enforce; pad your root view with
     * [padForSystemBars]. Without it the bars take the canvas colour instead (before Android 8
     * the navigation bar of a light theme stays black, because its icons cannot be darkened).
     */
    @JvmStatic
    @JvmOverloads
    public fun apply(
        activity: Activity,
        theme: MalletTheme = MalletTheme.of(activity),
        edgeToEdge: Boolean = true,
    ) {
        val window = activity.window
        val canvas = theme.colors.canvas
        val light = !theme.isDark
        window.setBackgroundDrawable(ColorDrawable(canvas))
        WindowCompat.setDecorFitsSystemWindows(window, !edgeToEdge)
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = if (edgeToEdge) Color.TRANSPARENT else canvas
            window.navigationBarColor = when {
                // Before Android 8 the navigation icons are always light: keep them readable.
                Build.VERSION.SDK_INT < 26 && light -> if (edgeToEdge) LEGACY_NAV_SCRIM else Color.BLACK
                edgeToEdge -> Color.TRANSPARENT
                else -> canvas
            }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
    }

    /**
     * Adds the system-bar and display-cutout insets to the padding [view] has right now, and
     * keeps doing so when the insets change and whenever the view is attached to a window.
     * With [includeIme] the on-screen keyboard is treated the same way, so the content stays
     * above it. Call it once per view.
     */
    @JvmStatic
    @JvmOverloads
    public fun padForSystemBars(view: View, includeIme: Boolean = false) {
        val left = view.paddingLeft
        val top = view.paddingTop
        val right = view.paddingRight
        val bottom = view.paddingBottom
        var types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        if (includeIme) types = types or WindowInsetsCompat.Type.ime()
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val bars = insets.getInsets(types)
            target.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
            insets
        }
        // Android hands the insets down when they change, not when a view arrives: a view that
        // is attached later, or attached again, has to ask for them each time.
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(attached: View) {
                ViewCompat.requestApplyInsets(attached)
            }

            override fun onViewDetachedFromWindow(detached: View) = Unit
        })
        ViewCompat.requestApplyInsets(view)
    }

    /**
     * Asks for the fastest refresh rate the display offers at its current resolution. Finger-
     * following movement is where a high refresh rate shows most, so screens built around a
     * page host or large tracks benefit from it. The system may still decide otherwise.
     */
    @JvmStatic
    public fun preferHighestRefreshRate(activity: Activity) {
        val display: Display? = try {
            if (Build.VERSION.SDK_INT >= 30) activity.display else legacyDisplay(activity)
        } catch (_: UnsupportedOperationException) {
            // An activity that is not attached to a display yet.
            legacyDisplay(activity)
        }
        val current = display?.mode ?: return
        var best: Display.Mode = current
        for (candidate in display.supportedModes) {
            if (candidate.physicalWidth != current.physicalWidth) continue
            if (candidate.physicalHeight != current.physicalHeight) continue
            if (candidate.refreshRate > best.refreshRate) best = candidate
        }
        val attributes = activity.window.attributes
        attributes.preferredDisplayModeId = best.modeId
        activity.window.attributes = attributes
    }

    @Suppress("DEPRECATION")
    private fun legacyDisplay(activity: Activity): Display? = activity.windowManager.defaultDisplay

    private const val LEGACY_NAV_SCRIM = 0x66000000
}
