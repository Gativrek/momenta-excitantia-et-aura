package io.github.gativrek.momenta.playback

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.gativrek.momenta.MainActivity
import io.github.gativrek.momenta.R

/**
 * Owns the one ExoPlayer and its MediaSession (KICKOFF.md: "the service owns the player").
 *
 * Everything that controls playback, our own UI included, goes through the session:
 * the notification, lock screen, Bluetooth buttons and car displays all use the same path.
 * MediaSessionService handles the foreground-service lifecycle and the media notification
 * for us: it goes foreground while playing and drops back when paused.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class) // DefaultMediaNotificationProvider.setSmallIcon
    override fun onCreate() {
        super.onCreate()

        val player =
            ExoPlayer
                .Builder(this)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    // handleAudioFocus = true: ExoPlayer requests focus, pauses for calls and
                    // other media, ducks for navigation prompts and resumes when appropriate.
                    // No hand-written OnAudioFocusChangeListener anywhere (KICKOFF.md rule 5).
                    true,
                )
                // Pause when headphones are unplugged or Bluetooth audio disconnects (criterion 5).
                .setHandleAudioBecomingNoisy(true)
                // Hold a partial wake lock while playing so the CPU keeps decoding with the screen off.
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()

        // Tapping the notification (or the lock-screen player) opens the app.
        val openApp =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        mediaSession =
            MediaSession
                .Builder(this, player)
                .setSessionActivity(openApp)
                .setCallback(SessionCallback())
                .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            },
        )
    }

    /** Every controller (our UI, System UI, Bluetooth) gets the same session. */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * The user swiped the app away from Recents (criterion 8). Keep playing if music is
     * playing; otherwise there's nothing to keep the service for, so stop it.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null ||
            !player.playWhenReady ||
            player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback {
        /**
         * Items arriving from a controller may have lost their URI on the way (Media3 strips
         * `localConfiguration` when items cross process boundaries, e.g. from System UI).
         * Our mediaId is the MediaStore ID, so the playable URI can always be rebuilt from it.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.mapNotNullTo(mutableListOf(), ::resolve))

        private fun resolve(item: MediaItem): MediaItem? {
            if (item.localConfiguration != null) return item
            val id = item.mediaId.toLongOrNull() ?: return null
            return item
                .buildUpon()
                .setUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id))
                .build()
        }
    }
}
