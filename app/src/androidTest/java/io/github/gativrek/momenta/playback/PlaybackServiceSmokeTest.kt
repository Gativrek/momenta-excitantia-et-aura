package io.github.gativrek.momenta.playback

import android.content.ComponentName
import android.os.Looper
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Smoke test (KICKOFF.md "Testing"): the service starts and a MediaController connects,
 * the same path Bluetooth and System UI take. Run with `./gradlew connectedDebugAndroidTest`
 * on an emulator or device; CI doesn't run it (no emulator there).
 */
@RunWith(AndroidJUnit4::class)
class PlaybackServiceSmokeTest {
    @Test
    fun controllerConnectsToPlaybackService() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        // A MediaController may only be used on its application looper. Tests run on their own
        // thread, so build it for the main looper and call into it from there.
        val future =
            MediaController
                .Builder(context, token)
                .setApplicationLooper(Looper.getMainLooper())
                .buildAsync()
        val controller = future.get(10, TimeUnit.SECONDS)
        instrumentation.runOnMainSync {
            assertTrue(controller.isConnected)
            MediaController.releaseFuture(future)
        }
    }
}
