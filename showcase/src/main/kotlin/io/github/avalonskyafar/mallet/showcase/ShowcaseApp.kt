package io.github.avalonskyafar.mallet.showcase

import android.app.Application
import io.github.avalonskyafar.mallet.theme.MalletTheme

/**
 * Installs the app's own appearance settings as the source of every Mallet theme.
 *
 * An app that is happy with the built-in palettes and the system night mode needs none of
 * this: Mallet views work without any setup.
 */
class ShowcaseApp : Application() {

    lateinit var settings: ShowcaseSettings
        private set

    override fun onCreate() {
        super.onCreate()
        settings = ShowcaseSettings(this)
        MalletTheme.resolver = MalletTheme.Resolver { context -> settings.toTheme(context) }
    }
}
