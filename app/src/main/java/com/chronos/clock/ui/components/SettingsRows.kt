package com.chronos.clock.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronos.clock.presentation.theme.Chronos

/** Section header used by every settings group (no cards, minimal). */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    val palette = Chronos.palette
    Text(
        text = title.uppercase(),
        style = TextStyle(fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium),
        color = palette.textFaint,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 8.dp),
    )
}

/** One switch row: label, optional description, AMOLED-friendly switch. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val palette = Chronos.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
                color = palette.textPrimary,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                    color = palette.textFaint,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = palette.accent,
                checkedThumbColor = if (palette.isLight) palette.background else palette.textPrimary,
                uncheckedTrackColor = palette.surface,
                uncheckedThumbColor = palette.textFaint,
                uncheckedBorderColor = palette.surface,
            ),
        )
    }
}

/**
 * Single-choice row group rendered as a wrapping set of text "chips"
 * (plain text + selection colour — no bright filled surfaces on AMOLED).
 */
@Composable
fun <T> OptionRow(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val palette = Chronos.palette
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            text = title,
            style = TextStyle(fontSize = 15.sp),
            color = palette.textPrimary,
        )
        if (description != null) {
            Text(
                text = description,
                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                color = palette.textFaint,
                modifier = Modifier.padding(top = 3.dp, bottom = 6.dp),
            )
        } else {
            Spacer(Modifier.padding(top = 4.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            options.forEach { option ->
                val isSelected = option == selected
                Text(
                    text = optionLabel(option),
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    ),
                    color = if (isSelected) palette.accent else palette.textSecondary,
                    modifier = Modifier
                        .clickable { onSelect(option) }
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}

/** Explanatory fine print under a section (privacy / battery notes). */
@Composable
fun NoteText(text: String, modifier: Modifier = Modifier) {
    val palette = Chronos.palette
    Text(
        text = text,
        style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
        color = palette.textFaint,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}
