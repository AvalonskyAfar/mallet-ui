package io.github.avalonskyafar.mallet.widget

import android.view.View
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.mallet.testing.Snapshots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MalletBadgeTest {

    @Test
    fun `badge describes itself with its number and hides without one`() {
        val badge = MalletBadge(Snapshots.context())
        assertThat(badge.contentDescription).isNull()
        assertThat(badge.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
        badge.count = 5
        assertThat(badge.contentDescription.toString()).isEqualTo("5")
        assertThat(badge.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
        badge.count = 120
        assertThat(badge.contentDescription.toString()).isEqualTo("99+")
        badge.count = 0
        assertThat(badge.contentDescription).isNull()
        assertThat(badge.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
    }

    @Test
    fun `description and importance set by the developer are kept`() {
        val badge = MalletBadge(Snapshots.context())
        badge.contentDescription = "Unread messages"
        badge.count = 5
        badge.maxCount = 3
        badge.showDot = true
        assertThat(badge.contentDescription.toString()).isEqualTo("Unread messages")
        // Handing the description back: the badge writes its number again.
        badge.contentDescription = null
        badge.count = 2
        assertThat(badge.contentDescription.toString()).isEqualTo("2")

        // The parent row speaks the count: the badge stays hidden whatever it shows.
        val hidden = MalletBadge(Snapshots.context())
        hidden.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        hidden.count = 7
        assertThat(hidden.importantForAccessibility)
            .isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
        hidden.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        hidden.count = 8
        assertThat(hidden.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
    }

    @Test
    fun `developer value that equals what the badge wrote is still the developer's`() {
        // The badge without a number has hidden itself; the developer hides it for good.
        val hidden = MalletBadge(Snapshots.context())
        hidden.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        hidden.count = 7
        assertThat(hidden.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)

        val badge = MalletBadge(Snapshots.context())
        badge.count = 5
        badge.contentDescription = "5"
        badge.count = 6
        assertThat(badge.contentDescription.toString()).isEqualTo("5")
    }

    @Test
    fun `description and importance from XML survive the constructor`() {
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(android.R.attr.contentDescription, "Unread messages")
            .addAttribute(android.R.attr.importantForAccessibility, "no")
            .build()
        val badge = MalletBadge(Snapshots.context(), attrs)
        assertThat(badge.contentDescription.toString()).isEqualTo("Unread messages")
        badge.count = 4
        assertThat(badge.contentDescription.toString()).isEqualTo("Unread messages")
        assertThat(badge.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
    }
}
