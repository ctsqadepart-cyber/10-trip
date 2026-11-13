package tw.shilu.travel

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Headless Android lifecycle smoke test; not a device/permission/hardware acceptance test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=TravelApplication::class)
class ActivitySmokeTest {
    @Test fun activityCreatesResumesAndRecreates() {
        // Robolectric does not auto-grant the signature permission merged by androidx.core on API 28.
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions("tw.shilu.travel.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(controller.get().window.decorView)
        assertEquals("tw.shilu.travel",controller.get().packageName)
        controller.recreate()
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(controller.get().window.decorView)
        controller.pause().stop().destroy()
    }
}
