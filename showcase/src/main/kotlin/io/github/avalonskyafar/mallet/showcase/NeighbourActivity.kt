package io.github.avalonskyafar.mallet.showcase

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedDispatcher
import io.github.avalonskyafar.mallet.interaction.MalletSpatialTransition
import io.github.avalonskyafar.mallet.theme.MalletWindow
import io.github.avalonskyafar.mallet.widget.MalletButton

/**
 * A separate activity that arrives and leaves like a neighbouring page.
 *
 * The three calls that make it so are marked below: the caller starts it with
 * [MalletSpatialTransition.start], this activity wraps its content with
 * [MalletSpatialTransition.wrap], and every way out goes through
 * [MalletSpatialTransition.finish].
 *
 * The screen itself is declared in `res/layout/activity_neighbour.xml`: a `MalletPage` with a
 * page heading, a paragraph, a segmented track on the canvas under a heading of its own, and a
 * button. Nothing of it is built in code.
 */
class NeighbourActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val settings = (application as ShowcaseApp).settings
        setTheme(if (settings.isDark(this)) R.style.Theme_Showcase_Dark else R.style.Theme_Showcase_Light)
        super.onCreate(savedInstanceState)
        MalletWindow.apply(this)

        // (2) Wrap the page: it glides in over a picture of the screen below and can be
        // dragged back out.
        val screen = layoutInflater.inflate(R.layout.activity_neighbour, findViewById(android.R.id.content), false)
        setContentView(MalletSpatialTransition.wrap(this, screen))

        findViewById<MalletButton>(R.id.neighbour_back).setOnClickListener {
            MalletSpatialTransition.finish(this)
        }

        // (3) System back leaves the same way.
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                MalletSpatialTransition.finish(this)
            }
        }
    }

    // Below Android 13 only; from 13 on the callback registered in onCreate is called.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        MalletSpatialTransition.finish(this)
    }
}
