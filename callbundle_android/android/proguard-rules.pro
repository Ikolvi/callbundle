# CallBundle — Consumer ProGuard Rules
# These rules are automatically applied to any app using this plugin.
# No app-level ProGuard configuration needed.

# Keep all CallBundle plugin classes
-keep class com.callbundle.** { *; }
-dontwarn com.callbundle.**

# ConnectionService loaded by Android Telecom framework via reflection
-keep class * extends android.telecom.ConnectionService { *; }
-keep class * extends android.telecom.Connection { *; }

# NotificationCompat.CallStyle (API 31+) — loaded via reflection
-keep class androidx.core.app.NotificationCompat$CallStyle { *; }
-keep class androidx.core.app.NotificationCompat$CallStyle$* { *; }
-keep class androidx.core.app.Person { *; }
-keep class androidx.core.app.Person$Builder { *; }

# Prevent stripping of R resource classes used in notifications
-keepclassmembers class **.R$layout { public static <fields>; }
-keepclassmembers class **.R$drawable { public static <fields>; }
-keepclassmembers class **.R$string { public static <fields>; }
-keepclassmembers class **.R$style { public static <fields>; }
