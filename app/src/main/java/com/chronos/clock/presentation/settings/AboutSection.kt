package com.chronos.clock.presentation.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronos.clock.core.constants.AppConstants
import com.chronos.clock.presentation.theme.Chronos
import com.chronos.clock.ui.components.NoteText
import com.chronos.clock.ui.components.SectionHeader

/** Section G — version, privacy, licences and repository link. */
@Composable
fun AboutSection(versionName: String) {
    val context = LocalContext.current
    val palette = Chronos.palette

    SectionHeader("About")

    InfoRow(label = "App version", value = versionName)

    SectionHeader("Privacy")
    NoteText(
        "• No account, no login, no onboarding.\n" +
            "• No analytics, no advertising SDK, no crash reporting.\n" +
            "• No internet permission is declared — CHRONOS cannot send data out.\n" +
            "• No contacts, location, microphone, camera or storage access.\n" +
            "• Notification content lives in process memory only; it is never " +
            "written to disk, never uploaded and cleared when the app exits.\n" +
            "• Settings are stored locally with Jetpack DataStore.",
    )

    SectionHeader("Open-source licences")
    NoteText(
        "CHRONOS is built with AndroidX, Jetpack Compose, Material 3, Kotlin, " +
            "Coroutines and Jetpack DataStore — all licensed under the Apache " +
            "License 2.0. Their respective copyright notices apply.\n\n" +
            "CHRONOS source code itself is released under the MIT licence.",
    )

    SectionHeader("GitHub repository")
    Text(
        text = AppConstants.GITHUB_URL,
        style = TextStyle(fontSize = 14.sp),
        color = palette.accent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.GITHUB_URL))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            .padding(horizontal = 20.dp, vertical = 6.dp),
    )

    SectionHeader("Known Android limitations")
    NoteText(
        "• Floating windows: whether WhatsApp (or any app) appears as a bubble " +
            "or floating window is controlled by Android and by that app. " +
            "CHRONOS can only forward the notification's own bubble/content " +
            "intent — it cannot force a third-party app into a floating window.\n" +
            "• Notification icons: Android does not allow one app to recolour " +
            "or replace another app's notification icon. CHRONOS always shows " +
            "the original application icon.\n" +
            "• Quick replies use the officially supported RemoteInput API and " +
            "work only where the source app declares a reply action.",
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    val palette = Chronos.palette
    Text(
        text = "$label:  $value",
        style = TextStyle(fontSize = 15.sp),
        color = palette.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}
