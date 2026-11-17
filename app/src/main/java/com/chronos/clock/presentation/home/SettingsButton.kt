package com.chronos.clock.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chronos.clock.presentation.theme.Chronos

/**
 * Bottom-left settings gear. Hidden during normal viewing; revealed with a
 * smooth fade when the user taps the screen, then auto-hidden after a short
 * idle period (handled by the caller via [visible]).
 */
@Composable
fun SettingsButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Chronos.palette
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = "Settings",
            tint = palette.textSecondary,
            modifier = Modifier
                .padding(20.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(palette.background.copy(alpha = 0.001f))
                .clickable(onClick = onClick)
                .semantics { contentDescription = "Settings" }
                .padding(6.dp),
        )
    }
}
