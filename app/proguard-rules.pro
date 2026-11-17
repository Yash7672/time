# CHRONOS R8 / ProGuard rules.

# NotificationListenerService is referenced from the manifest by class name.
-keep class com.chronos.clock.notifications.NotificationListener { *; }
-keep class com.chronos.clock.services.OverlayService { *; }

# Kotlin / coroutines
-dontwarn org.jetbrains.annotations.**
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable

# Compose keeps its own rules via library consumer rules; nothing extra required.
