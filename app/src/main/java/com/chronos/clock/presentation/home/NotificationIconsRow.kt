package com.chronos.clock.presentation.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.chronos.clock.core.constants.AppConstants
import com.chronos.clock.domain.model.NotificationGroup
import com.chronos.clock.domain.model.NotificationSettings
import com.chronos.clock.notifications.FloatingAppLauncher
import com.chronos.clock.presentation.theme.Chronos

/**
 * Notification app icons shown along the bottom of the clock screen.
 *
 * - Original source-app icons only (Android forbids recolouring another
 *   app's notification icon; CHRONOS never tries).
 * - One icon per app with an unread count indicator — this keeps multiple
 *   notifications organised and guarantees no overlap with the clock.
 * - Tap opens the app beside CHRONOS in multi-window (split screen).
 * - Long-press opens the reply dialog when quick reply is on and the
 *   notification supports RemoteInput (Dismiss lives in the same dialog);
 *   otherwise long-press dismisses the notification directly.
 * - Bounded to [AppConstants.MAX_VISIBLE_ICONS] entries plus a "+N" chip.
 * - AMOLED friendly: dim circular backdrops, no bright surfaces.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationIconsRow(
    groups: List<NotificationGroup>,
    settings: NotificationSettings,
    loadIcon: suspend (String) -> ImageBitmap?,
    onOpen: (NotificationGroup) -> Unit,
    onDismiss: (NotificationGroup) -> Unit,
    onReply: (NotificationGroup, String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    if (!settings.showAppIcons || groups.isEmpty()) return
    val palette = Chronos.palette

    // Which icon's reply dialog is open (null = none).
    var replyTarget by remember { mutableStateOf<NotificationGroup?>(null) }

    val visible = groups.take(AppConstants.MAX_VISIBLE_ICONS)
    val overflow = groups.size - visible.size

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visible.forEach { group ->
            NotificationIcon(
                group = group,
                settings = settings,
                loadIcon = loadIcon,
                onOpen = onOpen,
                onLongClick = {
                    if (settings.quickReplyEnabled && group.latest.canReply) {
                        replyTarget = group
                    } else {
                        onDismiss(group)
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
        }
        if (overflow > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(palette.surface)
                    .padding(2.dp),
            ) {
                Text(
                    text = "+$overflow",
                    color = palette.textFaint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    replyTarget?.let { target ->
        ReplyDialog(
            group = target,
            onSend = { text ->
                val sent = onReply(target, text)
                if (sent) replyTarget = null
                sent
            },
            onDismissNotification = {
                onDismiss(target)
                replyTarget = null
            },
            onClose = { replyTarget = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationIcon(
    group: NotificationGroup,
    settings: NotificationSettings,
    loadIcon: suspend (String) -> ImageBitmap?,
    onOpen: (NotificationGroup) -> Unit,
    onLongClick: () -> Unit,
) {
    val palette = Chronos.palette
    var bitmap by remember(group.packageName) { mutableStateOf<ImageBitmap?>(null) }

    // Load off the main thread; result cached by the repository.
    LaunchedEffect(group.packageName) {
        bitmap = loadIcon(group.packageName)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(palette.surface)
                    .combinedClickable(
                        onClick = { onOpen(group) },
                        onLongClick = onLongClick,
                    )
                    .padding(5.dp),
            ) {
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = group.appLabel,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    Text(
                        text = group.appLabel.take(1).uppercase(),
                        color = palette.textFaint,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            if (settings.showIndicators) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(palette.accent),
                )
            }
        }

        if (settings.privacy ==
            com.chronos.clock.domain.model.NotificationPrivacy.SHOW_TITLES
        ) {
            Text(
                text = group.latest.title ?: group.appLabel,
                color = palette.textFaint,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(52.dp)
                    .padding(top = 3.dp),
            )
        } else if (settings.showIndicators && group.count > 1) {
            Text(
                text = "${group.count}",
                color = palette.textFaint,
                fontSize = 9.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/** Thin divider used between settings sections (no cards, no gradients). */
@Composable
fun SectionDivider(modifier: Modifier = Modifier) {
    val palette = Chronos.palette
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(palette.surface.copy(alpha = 0.6f)),
    )
}

/**
 * Bottom-centre prompt shown while notification access is missing — the
 * only state in which app icons can never appear. Tapping it opens
 * Android's own consent screen; the grant itself always happens there,
 * never inside CHRONOS.
 */
@Composable
fun NotificationAccessHint(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Chronos.palette
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Allow notification access",
            color = palette.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "so app icons appear here when messages arrive",
            color = palette.textFaint,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * Bottom status/diagnosis line shown whenever the icon row itself is not:
 *
 *  - feature switched off → accent line, one tap re-enables icons;
 *  - limited to chosen apps with none of them active → one tap returns
 *    to showing every app;
 *  - otherwise → faint "listening" line proving the pipeline is alive.
 *
 * It disappears the moment the first icon renders, so the bottom lane can
 * never be silently empty while something is wrong — the line always says
 * exactly why, and how to fix it in one tap.
 */
@Composable
fun NotificationStatusLine(
    settings: NotificationSettings,
    groupCount: Int,
    onEnableIcons: () -> Unit,
    onShowAllApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Chronos.palette
    val status: Triple<String, Boolean, (() -> Unit)?> = when {
        !settings.integrationEnabled || !settings.showAppIcons ->
            Triple("App icons are off — tap to turn on", true, onEnableIcons)
        settings.allowedPackages.isNotEmpty() && groupCount == 0 ->
            Triple(
                "Only ${settings.allowedPackages.size} chosen app(s) allowed — tap to show all apps",
                false,
                onShowAllApps,
            )
        else -> Triple("listening for notifications…", false, null)
    }
    val (text, accent, onTap) = status
    Text(
        text = text,
        color = if (accent) palette.accent else palette.textFaint,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/**
 * AMOLED reply sheet: type and send through the source app's own
 * RemoteInput (the officially supported Android mechanism), dismiss the
 * notification, or cancel. Every colour comes from the palette — no
 * default Material surfaces light up the panel.
 */
@Composable
private fun ReplyDialog(
    group: NotificationGroup,
    onSend: (String) -> Boolean,
    onDismissNotification: () -> Unit,
    onClose: () -> Unit,
) {
    val palette = Chronos.palette
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

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
            val preview = group.latest.title ?: group.latest.text
            if (preview != null) {
                Text(
                    text = preview,
                    color = palette.textFaint,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(text = "Type a reply…", color = palette.textFaint, fontSize = 14.sp)
                },
                textStyle = TextStyle(color = palette.textPrimary, fontSize = 14.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = palette.surface,
                    unfocusedContainerColor = palette.surface,
                    focusedIndicatorColor = palette.accent,
                    unfocusedIndicatorColor = palette.surface,
                    cursorColor = palette.accent,
                ),
                maxLines = 3,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
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
                    text = "Cancel",
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Send",
                    color = if (text.isBlank()) palette.textFaint else palette.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = text.isNotBlank()) { onSend(text) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }

    // Focus the field so the keyboard is up as soon as the sheet appears.
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
}

/**
 * Bottom-of-clock dialog for a notification-icon tap. Two honest modes:
 *
 *  - **Guided Small Window walkthrough** (vivo/iQOO — [HomeViewModel.FloatingWindowNotice.guided]):
 *    the native Small Window is the real floating window on these phones,
 *    but vivo exposes no public API to launch it — so the dialog states
 *    the exact Recents steps and only opens the app when the user taps
 *    "Open app" themselves. Nothing launches silently.
 *  - **Explanation** — a floating launch could not engage or nothing
 *    opened; the dialog reports what actually happened (never claims a
 *    floating success), what opened instead, and where to change it.
 *
 * It never blocks the clock: dismiss/back closes it and CHRONOS keeps
 * running. Shown only when Settings → Floating App Windows → "Show
 * instructions for unsupported devices" is on.
 */
@Composable
fun FloatingWindowNoticeDialog(
    notice: HomeViewModel.FloatingWindowNotice,
    onDismiss: () -> Unit,
    onOpenApp: () -> Unit = onDismiss,
) {
    val palette = Chronos.palette
    val guided = notice.guided
    val title = when {
        guided -> "Open as Small Window"
        notice.outcome == FloatingAppLauncher.Outcome.NONE -> "Couldn't open the app"
        else -> "Floating window unavailable"
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.background)
                .padding(18.dp),
        ) {
            Text(
                text = title,
                color = palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (guided) notice.guideSteps() else notice.explanation(),
                color = palette.textFaint,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (guided) {
                    Text(
                        text = "Cancel",
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Open app",
                        color = palette.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenApp)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "OK",
                        color = palette.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * The guided Small Window walkthrough — steps verified against vivo's own
 * support documentation (Recents → arrow next to the app name → Small
 * window) and iQOO's community Small Window guide, including the Mini
 * Small Window behaviour (minimize, tap to enlarge, drag to the edge to
 * hang, drag to the bottom to remove).
 */
private fun HomeViewModel.FloatingWindowNotice.guideSteps(): String = """
    CHRONOS opens $appLabel first so it appears in Recents — the clock keeps
    running behind it.

    1. Swipe up from the bottom and hold to open Recents.
    2. Tap the arrow next to $appLabel's name (or long-press its card).
    3. Tap Small window.

    The window then floats over CHRONOS with native iQOO controls: drag the
    top bar to move, drag the corners to resize, minimise for a Mini Small
    Window (tap to enlarge, drag to a screen edge to hang it, drag to the
    bottom to remove), and X to close. Closing it returns to CHRONOS
    untouched.

    Only apps that support Small window show that option — if it is missing,
    $appLabel simply opens normally instead.
""".trimIndent()

/** Honest, device-aware copy for the non-guided [FloatingWindowNoticeDialog]. */
private fun HomeViewModel.FloatingWindowNotice.explanation(): String {
    val opened = when (outcome) {
        FloatingAppLauncher.Outcome.FLOATING -> "a floating window"
        FloatingAppLauncher.Outcome.SPLIT -> "split screen"
        FloatingAppLauncher.Outcome.FULL_SCREEN -> "full screen"
        FloatingAppLauncher.Outcome.NOTIFICATION_HANDLE -> "its notification"
        FloatingAppLauncher.Outcome.NONE -> null
        FloatingAppLauncher.Outcome.SMALL_WINDOW_GUIDE -> null // guided mode never reaches this
    }
    val fallbackNote = if (outcome == FloatingAppLauncher.Outcome.NONE) {
        " CHRONOS kept running. If you want apps to open the normal " +
            "full-screen way, turn on “Allow normal full-screen " +
            "fallback” in Settings → Notifications → Floating App Windows."
    } else {
        ""
    }
    return when (floatBlockReason) {
        FloatingAppLauncher.FloatBlockReason.DEVICE_UNSUPPORTED ->
            "This device doesn't expose the standard Android freeform-window " +
                "launch, so $appLabel opened in ${opened ?: "the normal way"}" +
                " instead. Some phones provide their own floating-window " +
                "features in their own settings (varies by manufacturer) — " +
                "CHRONOS uses only public Android APIs and always falls back " +
                "to a normal launch." + fallbackNote
        FloatingAppLauncher.FloatBlockReason.REFUSED ->
            "Android refused the floating-window launch for $appLabel. It " +
                "opened in ${opened ?: "the normal way"} instead." + fallbackNote
        null ->
            if (outcome == FloatingAppLauncher.Outcome.NONE) {
                "Android couldn't open $appLabel right now — it may be " +
                    "disabled or the notification handle may be stale." +
                    fallbackNote
            } else {
                "$appLabel opened in ${opened ?: "a window"}. The clock keeps " +
                    "running behind it, and closing the window returns to " +
                    "CHRONOS with its state intact."
            }
    }
}
