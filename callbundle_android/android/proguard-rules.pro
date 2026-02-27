# CallBundle — Consumer ProGuard Rules
# These rules are automatically applied to any app using this plugin.
# No app-level ProGuard configuration needed.

# Keep all CallBundle plugin classes (including inner/synthetic)
-keep class com.callbundle.** { *; }
-keep class com.callbundle.**$* { *; }
-dontwarn com.callbundle.**

# Prevent R8 from merging/inlining lambdas in the plugin.
# R8's horizontal class merging can incorrectly merge Kotlin lambdas
# (e.g. Handler.post Runnables) when they have the same signature,
# potentially breaking captured variable references in release builds.
-keep,allowoptimization class com.callbundle.callbundle_android.CallBundlePlugin {
    private final android.os.Handler mainHandler;
    private io.flutter.plugin.common.MethodChannel channel;
    private boolean isConfigured;
    private int nextEventId;
}

# Keep all members of CallStateManager (prevents field hoisting)
-keepclassmembers class com.callbundle.callbundle_android.CallStateManager { *; }
-keepclassmembers class com.callbundle.callbundle_android.CallStateManager$* { *; }

# Keep PendingCallStore members (prevents inlining of serialization methods)
-keepclassmembers class com.callbundle.callbundle_android.PendingCallStore { *; }

# Keep data classes (prevents R8 from removing unused fields)
-keep class com.callbundle.callbundle_android.CallInfo { *; }
-keep class com.callbundle.callbundle_android.PendingAcceptEvent { *; }
-keep class com.callbundle.callbundle_android.PendingDeclineEvent { *; }

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

# Keep Activity and BroadcastReceiver subclasses referenced in AndroidManifest
-keep class com.callbundle.callbundle_android.IncomingCallActivity { *; }
-keep class com.callbundle.callbundle_android.CallActionReceiver { *; }
-keep class com.callbundle.callbundle_android.CallConnectionService { *; }
