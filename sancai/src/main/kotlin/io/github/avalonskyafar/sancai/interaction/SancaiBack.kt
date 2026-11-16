package io.github.avalonskyafar.sancai.interaction

import android.app.Activity
import android.os.Build
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi

/**
 * Connects the system back gesture to a [SancaiSpatialHost].
 *
 * Back leads to the parent of the current page (see [SancaiSpatialGraph.parentOf]). On
 * Android 14 and later the back swipe previews that move: the pages follow the swipe for up to
 * 40 % of the way, land when the gesture is completed and glide back when it is cancelled. On
 * the root page the registration steps aside, so the system handles back itself.
 *
 * ```kotlin
 * private lateinit var back: SancaiBack.Registration
 *
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     ...
 *     back = SancaiBack.attach(this, host)
 * }
 *
 * // Only needed below Android 13, or without android:enableOnBackInvokedCallback:
 * override fun onBackPressed() {
 *     if (!back.onBackPressed()) super.onBackPressed()
 * }
 * ```
 */
public object SancaiBack {

    /** Handle returned by [attach]. */
    public interface Registration {
        /** Forwards a legacy back press. Returns true when the host moved. */
        public fun onBackPressed(): Boolean

        /** Detaches from the host and from the system. */
        public fun remove()
    }

    /** Routes back for [activity] to [host] until the registration is removed. */
    @JvmStatic
    public fun attach(activity: Activity, host: SancaiSpatialHost): Registration = HostRegistration(activity, host)

    private class HostRegistration(
        private val activity: Activity,
        private val host: SancaiSpatialHost,
    ) : Registration, SancaiSpatialHost.Listener {
        private var callback: Any? = null

        init {
            host.addListener(this)
            sync()
        }

        override fun onPageSettled(key: String) {
            sync()
        }

        /** Registers with the system only while there is somewhere to go back to. */
        private fun sync() {
            if (Build.VERSION.SDK_INT < 33) return
            val wanted = host.canGoBack()
            val registered = callback
            if (wanted && registered == null) {
                callback = if (Build.VERSION.SDK_INT >= 34) Api34.register(activity, host) else Api33.register(activity, host)
            } else if (!wanted && registered != null) {
                Api33.unregister(activity, registered)
                callback = null
            }
        }

        override fun onBackPressed(): Boolean = host.back()

        override fun remove() {
            host.removeListener(this)
            val registered = callback
            if (registered != null && Build.VERSION.SDK_INT >= 33) Api33.unregister(activity, registered)
            callback = null
        }
    }

    @RequiresApi(33)
    private object Api33 {
        fun register(activity: Activity, host: SancaiSpatialHost): Any {
            val callback = OnBackInvokedCallback { host.back() }
            activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback,
            )
            return callback
        }

        fun unregister(activity: Activity, callback: Any) {
            activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback as OnBackInvokedCallback)
        }
    }

    @RequiresApi(34)
    private object Api34 {
        fun register(activity: Activity, host: SancaiSpatialHost): Any {
            val callback = object : OnBackAnimationCallback {
                private var previewing = false

                override fun onBackStarted(backEvent: BackEvent) {
                    previewing = host.beginBackPreview()
                }

                override fun onBackProgressed(backEvent: BackEvent) {
                    if (previewing) host.updateBackPreview(backEvent.progress)
                }

                override fun onBackCancelled() {
                    if (previewing) host.endBackPreview(commit = false)
                    previewing = false
                }

                override fun onBackInvoked() {
                    if (previewing && host.isMoving) host.endBackPreview(commit = true) else host.back()
                    previewing = false
                }
            }
            activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback,
            )
            return callback
        }
    }
}
