package io.github.avalonskyafar.sancai.showcase.device

import org.junit.After
import org.junit.Before

/** Every device test starts from a fresh, light-themed app and leaves nothing behind. */
abstract class DeviceTest {

    @Before
    fun freshApp() {
        Device.prepareScreen()
        Device.finishAll()
        Device.resetSettings()
    }

    @After
    fun leaveNothing() {
        Device.ui.setOrientationNatural()
        Device.finishAll()
        Device.resetSettings()
    }
}
