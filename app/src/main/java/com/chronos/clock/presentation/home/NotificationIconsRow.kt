package com.chronos.clock.presentation.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronos.clock.core.constants.AppConstants
import com.chronos.clock.domain.model.NotificationGroup
import com.chronos.clock.domain.model.NotificationSettings
import com.chronos.clock.presentation.theme.Chronos

/**
 * Notification app icons arranged around the centre.
 *
 * - Original source-app icons only (Android forbids recolouring another
 *   app's notification icon; CHRONOS never tries).
 * - One icon per app with an unread count indicator — this keeps multiple
 *   notifications organised and guarantees no overlap with the clock.
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
    modifier: Modifier = Modifier,
) {
    if (!settings.showAppIcons || groups.isEmpty()) return
    val palette = Chronos.palette

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
                onDismiss = onDismiss,
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationIcon(
    group: NotificationGroup,
    settings: NotificationSettings,
    loadIcon: suspend (String) -> ImageBitmap?,
    onOpen: (NotificationGroup) -> Unit,
    onDismiss: (NotificationGroup) -> Unit,
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
                        onLongClick = { onDismiss(group) },
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
