package com.chronos.clock.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronos.clock.presentation.theme.Chronos

/** Simple package allow-list picker (no icons, no cards, plain rows). */
@Composable
fun AppPickerScreen(
    apps: List<AppEntry>,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
) {
    val palette = Chronos.palette
    Box(modifier = Modifier.fillMaxSize().background(palette.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = palette.textSecondary,
                    )
                }
                Text(
                    text = "Select applications",
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
                    color = palette.textPrimary,
                    modifier = Modifier.padding(start = 52.dp),
                )
            }

            Text(
                text = if (apps.any { it.selected }) {
                    "Only checked apps are shown on the clock. Uncheck everything " +
                        "to return to “all apps”."
                } else {
                    "All apps are currently shown. Check apps to limit the display."
                },
                style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
                color = palette.textFaint,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                apps.forEach { entry ->
                    AppPickerRow(entry = entry, onToggle = { onToggle(entry.packageName) })
                }
                Box(Modifier.fillMaxWidth().padding(bottom = 40.dp))
            }
        }
    }
}
