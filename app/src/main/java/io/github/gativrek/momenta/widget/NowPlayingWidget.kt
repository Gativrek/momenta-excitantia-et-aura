package io.github.gativrek.momenta.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.view.KeyEvent
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.graphics.scale
import androidx.core.net.toUri
import io.github.gativrek.momenta.MainActivity
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.playback.PlaybackService

/** What the widget shows. */
data class WidgetState(
    val title: String?,
    val artist: String?,
    val playing: Boolean,
)

/**
 * The home-screen widget: the cover, the song, and previous / play-pause / next plus the
 * Moment bolt. The playback service pushes every change to it ([NowPlayingWidgets.push]);
 * when the system asks for a redraw on its own (after a reboot, say) the last song shown is
 * drawn again, without its cover until the service is back.
 */
class NowPlayingWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val refresh = WidgetBridge.refresh
        if (refresh != null) refresh() else NowPlayingWidgets.push(context, NowPlayingWidgets.lastState(context), null)
    }
}

/** The widget's Moment bolt: saves a Moment in the playing song, if there is one. */
class WidgetMomentReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val save = WidgetBridge.saveMoment
        if (save != null) {
            save()
        } else {
            Toast.makeText(context, R.string.widget_nothing_to_save, Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Set by the playback service while it runs (same process), so the widget can reach it
 * without starting it.
 */
object WidgetBridge {
    var saveMoment: (() -> Unit)? = null
    var refresh: (() -> Unit)? = null
}

object NowPlayingWidgets {
    private const val PREFS = "widget"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"

    /** Covers are loaded at this size: sharp on the widget, small enough for RemoteViews. */
    private const val COVER_PX = 192

    fun hasAny(context: Context): Boolean =
        AppWidgetManager
            .getInstance(context)
            .getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
            .isNotEmpty()

    fun lastState(context: Context): WidgetState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return WidgetState(prefs.getString(KEY_TITLE, null), prefs.getString(KEY_ARTIST, null), playing = false)
    }

    /** Draws [state] (and [cover], or the logo without one) on every Momenta widget. */
    fun push(
        context: Context,
        state: WidgetState,
        cover: Bitmap?,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_TITLE, state.title)
            putString(KEY_ARTIST, state.artist)
        }
        val views =
            RemoteViews(context.packageName, R.layout.widget_now_playing).apply {
                setTextViewText(R.id.widget_title, state.title ?: context.getString(R.string.widget_idle))
                setTextViewText(R.id.widget_artist, state.artist.orEmpty())
                if (cover != null) {
                    setImageViewBitmap(R.id.widget_cover, cover)
                } else {
                    setImageViewResource(R.id.widget_cover, R.drawable.ic_launcher_foreground)
                }
                setImageViewResource(R.id.widget_play, if (state.playing) R.drawable.ic_pause else R.drawable.ic_play)
                setContentDescription(
                    R.id.widget_play,
                    context.getString(if (state.playing) R.string.action_pause else R.string.action_play),
                )
                setOnClickPendingIntent(R.id.widget_root, openApp(context))
                setOnClickPendingIntent(R.id.widget_previous, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
                setOnClickPendingIntent(R.id.widget_play, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                setOnClickPendingIntent(R.id.widget_next, mediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT))
                setOnClickPendingIntent(R.id.widget_moment, saveMoment(context))
            }
        AppWidgetManager
            .getInstance(context)
            .updateAppWidget(ComponentName(context, NowPlayingWidget::class.java), views)
    }

    /** The cover of a track for the widget, or null without one. Call off the main thread. */
    fun loadCover(
        context: Context,
        trackId: Long,
        albumId: Long,
    ): Bitmap? =
        try {
            val resolver = context.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId)
                resolver.loadThumbnail(uri, Size(COVER_PX, COVER_PX), null)
            } else {
                val uri = ContentUris.withAppendedId("content://media/external/audio/albumart".toUri(), albumId)
                resolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)?.scale(COVER_PX, COVER_PX)
            }
        } catch (_: Exception) {
            null // No art, or the file went away.
        }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * A media button press, delivered to the playback service the way a headset's would be:
     * Media3 handles it, including starting the service in the foreground when it plays.
     */
    private fun mediaButton(
        context: Context,
        keyCode: Int,
    ): PendingIntent {
        val intent =
            Intent(Intent.ACTION_MEDIA_BUTTON)
                .setComponent(ComponentName(context, PlaybackService::class.java))
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getForegroundService(context, keyCode, intent, flags)
    }

    private fun saveMoment(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WidgetMomentReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
