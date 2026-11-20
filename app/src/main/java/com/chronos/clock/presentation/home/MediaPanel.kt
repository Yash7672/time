package com.chronos.clock.presentation.home

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.chronos.clock.domain.model.NotificationGroup
import com.chronos.clock.notifications.NotificationListener
import com.chronos.clock.presentation.theme.Chronos

/**
 * The active [MediaController] for [packageName], or null when that app has
 * no session CHRONOS may see (playback stopped, or notification access is
 * missing — the system then refuses [MediaSessionManager.getActiveSessions]).
 *
 * Public API only: the notification-access grant is exactly what entitles
 * CHRONOS to *observe* sessions; it never touches one it cannot see.
 */
internal fun activeMediaController(context: Context, packageName: String): MediaController? {
    val manager = context.getSystemService(MediaSessionManager::class.java) ?: return null
    return runCatching {
        manager
            .getActiveSessions(ComponentName(context, NotificationListener::class.java))
            .firstOrNull { it.packageName == packageName }
    }.getOrNull()
}

/**
 * Tap-and-hold panel for a music app: previous, play/pause and next —
 * dispatched through the player's own media session via Android's public
 * transport controls. Live: play state and track metadata follow the
 * player through a controller callback, so the play/pause glyph always
 * reflects reality. Buttons the player doesn't support are dimmed (and a
 * transport call the session doesn't accept is a harmless no-op).
 * Dismiss clears the icon without touching playback.
 */
@Composable
internal fun MediaPanelDialog(
    group: NotificationGroup,
    controller: MediaController?,
    onDismissNotification: () -> Unit,
    onClose: () -> Unit,
) {
    val palette = Chronos.palette
    var playState by remember(controller) { mutableStateOf(controller?.playbackState) }
    var track by remember(controller) { mutableStateOf(controller?.metadata) }

    DisposableEffect(controller) {
        val c = controller
        val callback: MediaController.Callback? = if (c == null) {
            null
        } else {
            object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    playState = state
                }

                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    track = metadata
                }

                override fun onSessionDestroyed() {
                    playState = null
                    track = null
                }
            }
        }
        if (c != null && callback != null) {
            c.registerCallback(callback, Handler(Looper.getMainLooper()))
        }
        onDispose {
            if (c != null && callback != null) {
                runCatching { c.unregisterCallback(callback) }
            }
        }
    }

    val controls = controller?.transportControls
    val actions = playState?.actions ?: 0L
    // actions == 0 means the player reported no capability mask — try
    // anyway; the session owner simply ignores what it can't do.
    fun supported(bit: Long) = actions == 0L || (actions and bit) != 0L
    val playing = playState?.state == PlaybackState.STATE_PLAYING

    val title = track?.getString(MediaMetadata.METADATA_KEY_TITLE)
    val artist = track?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        ?: track?.getString(MediaMetadata.METADATA_KEY_ALBUM)

    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.background)
                .padding(18.dp),
        ) {
            Text(
                text = group.appLabel,
                color = palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            if (!title.isNullOrBlank()) {
                Text(
                    text = if (!artist.isNullOrBlank()) "$title — $artist" else title,
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportButton(
                    glyph = Glyph.PREV,
                    enabled = supported(PlaybackState.ACTION_SKIP_TO_PREVIOUS),
                    onClick = { controls?.skipToPrevious() },
                )
                Spacer(Modifier.width(18.dp))
                TransportButton(
                    glyph = if (playing) Glyph.PAUSE else Glyph.PLAY,
                    enabled = supported(
                        PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE,
                    ),
                    onClick = { if (playing) controls?.pause() else controls?.play() },
                )
                Spacer(Modifier.width(18.dp))
                TransportButton(
                    glyph = Glyph.NEXT,
                    enabled = supported(PlaybackState.ACTION_SKIP_TO_NEXT),
                    onClick = { controls?.skipToNext() },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Dismiss",
                    color = palette.textFaint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismissNotification)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Close",
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private enum class Glyph { PREV, PLAY, PAUSE, NEXT }

/** One circular transport button with a hand-drawn glyph (no icon library). */
@Composable
private fun TransportButton(
    glyph: Glyph,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = Chronos.palette
    val color = if (enabled) palette.accent else palette.textFaint.copy(alpha = 0.4f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(palette.surface)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        TransportGlyph(glyph = glyph, color = color, modifier = Modifier.size(24.dp))
    }
}

/** Draws one transport glyph with plain geometry — zero dependencies. */
@Composable
private fun TransportGlyph(glyph: Glyph, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        when (glyph) {
            Glyph.PLAY -> drawPath(
                Path().apply {
                    moveTo(w * 0.24f, h * 0.14f)
                    lineTo(w * 0.86f, h * 0.50f)
                    lineTo(w * 0.24f, h * 0.86f)
                    close()
                },
                color,
            )
            Glyph.PAUSE -> {
                val bar = w * 0.20f
                drawRect(
                    color,
                    topLeft = Offset(w * 0.26f, h * 0.16f),
                    size = Size(bar, h * 0.68f),
                )
                drawRect(
                    color,
                    topLeft = Offset(w * 0.54f, h * 0.16f),
                    size = Size(bar, h * 0.68f),
                )
            }
            Glyph.PREV -> {
                drawRect(
                    color,
                    topLeft = Offset(w * 0.14f, h * 0.16f),
                    size = Size(w * 0.12f, h * 0.68f),
                )
                drawPath(
                    Path().apply {
                        moveTo(w * 0.86f, h * 0.14f)
                        lineTo(w * 0.34f, h * 0.50f)
                        lineTo(w * 0.86f, h * 0.86f)
                        close()
                    },
                    color,
                )
            }
            Glyph.NEXT -> {
                drawRect(
                    color,
                    topLeft = Offset(w * 0.74f, h * 0.16f),
                    size = Size(w * 0.12f, h * 0.68f),
                )
                drawPath(
                    Path().apply {
                        moveTo(w * 0.14f, h * 0.14f)
                        lineTo(w * 0.66f, h * 0.50f)
                        lineTo(w * 0.14f, h * 0.86f)
                        close()
                    },
                    color,
                )
            }
        }
    }
}
