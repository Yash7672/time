package com.chronos.clock.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronos.clock.core.utils.TimeUtils
import com.chronos.clock.domain.model.ClockSettings
import com.chronos.clock.presentation.theme.Chronos
import java.util.Locale

/**
 * Date + day-of-week block shown directly beneath the clock. Secondary to
 * the time but sized to stay clearly readable at a glance.
 */
@Composable
fun DateView(
    nowMillis: Long,
    settings: ClockSettings,
    modifier: Modifier = Modifier,
) {
    val palette = Chronos.palette
    val locale = Locale.getDefault()

    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (settings.showDate) {
            Text(
                text = TimeUtils.formatDate(nowMillis, locale = locale),
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.6.sp,
                ),
                color = palette.textSecondary.copy(alpha = 0.9f),
                maxLines = 1,
            )
        }
        if (settings.showDay) {
            Text(
                text = TimeUtils.formatDay(nowMillis, locale = locale)
                    .uppercase(locale),
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 2.4.sp,
                ),
                color = palette.textFaint,
                maxLines = 1,
                modifier = Modifier.padding(top = if (settings.showDate) 2.dp else 0.dp),
            )
        }
    }
}
