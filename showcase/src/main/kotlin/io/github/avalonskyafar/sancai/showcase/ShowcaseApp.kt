package io.github.avalonskyafar.sancai.showcase

import android.app.Application
import io.github.avalonskyafar.sancai.theme.SancaiTheme

/**
 * Installs the app's own appearance settings as the source of every Sancai theme.
 *
 * An app that is happy with the built-in palettes and the system night mode needs none of
 * this: Sancai views work without any setup.
 */
class ShowcaseApp : Application() {

    lateinit var settings: ShowcaseSettings
        private set

    override fun onCreate() {
        super.onCreate()
        settings = ShowcaseSettings(this)
        SancaiTheme.resolver = SancaiTheme.Resolver { context -> settings.toTheme(context) }
    }
}
