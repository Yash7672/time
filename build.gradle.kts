plugins {
    id("com.android.application") version "9.5.1" apply false
    // AGP 9 provides built-in Kotlin; only the Compose compiler plugin is needed.
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
}
