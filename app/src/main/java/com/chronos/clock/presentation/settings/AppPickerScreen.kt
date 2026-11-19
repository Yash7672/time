package com.chronos.clock.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronos.clock.presentation.theme.Chronos

/**
 * App allow-list picker: search, then check exactly which apps may show
 * their icons on the clock. Unchecking everything (or "Clear selection")
 * returns to showing all apps.
 */
@Composable
fun AppPickerScreen(
    apps: List<AppEntry>,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    onClearSelection: () -> Unit,
) {
    val palette = Chronos.palette
    var query by remember { mutableStateOf("") }

    val filtered = remember(query, apps) {
        if (query.isBlank()) {
            apps
        } else {
            apps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }
    val anySelected = remember(apps) { apps.any { it.selected } }

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
                    text = "Choose apps",
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
                    color = palette.textPrimary,
                    modifier = Modifier.padding(start = 52.dp),
                )
            }

            Text(
                text = if (anySelected) {
                    "Only apps switched ON show on the clock. Turn every " +
                        "switch off to return to all apps."
                } else {
                    "All apps currently show on the clock. Switch one or " +
                        "more on to show only those."
                },
                style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
                color = palette.textFaint,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            if (anySelected) {
                Text(
                    text = "Turn all switches off — show every app",
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    color = palette.accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClearSelection)
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                placeholder = {
                    Text(text = "Search apps…", color = palette.textFaint, fontSize = 14.sp)
                },
                textStyle = TextStyle(color = palette.textPrimary, fontSize = 14.sp),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = palette.surface,
                    unfocusedContainerColor = palette.surface,
                    focusedIndicatorColor = palette.accent,
                    unfocusedIndicatorColor = palette.surface,
                    cursorColor = palette.accent,
                ),
            )

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                filtered.forEach { entry ->
                    AppPickerRow(entry = entry, onToggle = { onToggle(entry.packageName) })
                }
                if (filtered.isEmpty()) {
                    Text(
                        text = "No apps match “$query”",
                        style = TextStyle(fontSize = 13.sp),
                        color = palette.textFaint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                Box(Modifier.fillMaxWidth().padding(bottom = 40.dp))
            }
        }
    }
}
