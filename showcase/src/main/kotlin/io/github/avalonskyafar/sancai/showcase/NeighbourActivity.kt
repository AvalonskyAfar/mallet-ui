package io.github.avalonskyafar.sancai.showcase

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedDispatcher
import io.github.avalonskyafar.sancai.interaction.SancaiSpatialTransition
import io.github.avalonskyafar.sancai.theme.SancaiWindow
import io.github.avalonskyafar.sancai.widget.SancaiButton

/**
 * A separate activity that arrives and leaves like a neighbouring page.
 *
 * The three calls that make it so are marked below: the caller starts it with
 * [SancaiSpatialTransition.start], this activity wraps its content with
 * [SancaiSpatialTransition.wrap], and every way out goes through
 * [SancaiSpatialTransition.finish].
 *
 * The screen itself is declared in `res/layout/activity_neighbour.xml`: a `SancaiPage` with a
 * heading, a paragraph, a card and a button. Nothing of it is built in code.
 */
class NeighbourActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val settings = (application as ShowcaseApp).settings
        setTheme(if (settings.isDark(this)) R.style.Theme_Showcase_Dark else R.style.Theme_Showcase_Light)
        super.onCreate(savedInstanceState)
        SancaiWindow.apply(this)

        // (2) Wrap the page: it glides in over a picture of the screen below and can be
        // dragged back out.
        val screen = layoutInflater.inflate(R.layout.activity_neighbour, null)
        setContentView(SancaiSpatialTransition.wrap(this, screen))

        findViewById<SancaiButton>(R.id.neighbour_back).setOnClickListener {
            SancaiSpatialTransition.finish(this)
        }

        // (3) System back leaves the same way.
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                SancaiSpatialTransition.finish(this)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        SancaiSpatialTransition.finish(this)
    }
}
