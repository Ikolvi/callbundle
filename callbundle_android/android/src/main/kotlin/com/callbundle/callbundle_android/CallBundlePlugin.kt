package com.callbundle.callbundle_android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

/**
 * CallBundlePlugin — Main entry point for the Android implementation.
 *
 * Handles MethodChannel communication between Dart and native Android.
 * Manages ConnectionService, TelecomManager, notifications, and call state.
 *
 * ## Instance Management & the Background Engine Race
 *
 * Android's Flutter plugin system creates a **new plugin instance** every
 * time a new FlutterEngine is created. This means:
 *
 * - **Main engine** (app startup): Creates Instance A
 * - **Background FCM engine** (data message): Creates Instance B
 *
 * Each instance has its OWN [MethodChannel] bound to its engine's
 * [BinaryMessenger]. Events sent through Instance B's channel reach
 * the background Dart isolate, NOT the main app.
 *
 * ### The Race Condition (Fixed)
 *
 * The static [instance] field is used by [CallActionReceiver] to forward
 * accept/decline notifications to the plugin. There was a race condition:
 *
 * 1. Main engine creates Instance A → `instance = A`
 * 2. FCM background message arrives BEFORE [configure] is called
 * 3. Background engine creates Instance B → sees `A.isConfigured=false`
 *    → overwrites `instance = B`
 * 4. Main engine calls [configure] → `A.isConfigured = true`
 * 5. User taps Decline → [CallActionReceiver] reads `instance = B` (wrong!)
 * 6. Event goes through B's channel → background isolate → silently lost
 *
 * ### Fix: [handleConfigure] Reclaims the Instance
 *
 * After setting `isConfigured = true`, [handleConfigure] sets `instance = this`.
 * This guarantees the configured (main engine) instance is always the
 * canonical one. Since only the main engine calls [configure], this is safe.
 *
 * ## Key Design Decisions
 *
 * - Uses MethodChannel for BOTH directions (not EventChannel) to avoid
 *   WeakReference/GC issues that cause silent event drops
 * - Ships consumer ProGuard rules (no app-level changes needed)
 * - Ships permissions in AndroidManifest.xml (auto-merged)
 * - Implements [PendingCallStore] for cold-start event delivery
 * - [NotificationHelper] initialized eagerly with default app name for
 *   killed-state incoming calls (background FCM never calls [configure])
 */
class CallBundlePlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
    PluginRegistry.RequestPermissionsResultListener,
    PluginRegistry.NewIntentListener {

    companion object {
        private const val TAG = "CallBundlePlugin"
        private const val CHANNEL_NAME = "com.callbundle/main"
        private const val PERMISSION_REQUEST_CODE = 29741

        /**
         * Singleton reference for static callers ([CallActionReceiver],
         * [IncomingCallActivity]) to forward user actions to the plugin.
         *
         * ### Why @Volatile?
         * Multiple threads access this field:
         * - Main thread: [onAttachedToEngine], [handleConfigure]
         * - BroadcastReceiver thread: [CallActionReceiver.onReceive]
         * `@Volatile` ensures reads always see the latest write.
         *
         * ### Lifecycle
         * - Set in [onAttachedToEngine] (if no configured instance exists)
         * - RECLAIMED in [handleConfigure] (always, to fix race condition)
         * - Cleared in [onDetachedFromEngine] (if this was the instance)
         *
         * See class-level docs for the background engine race condition.
         */
        @Volatile
        var instance: CallBundlePlugin? = null
            private set
    }

    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private lateinit var messenger: BinaryMessenger
    private var activity: Activity? = null
    private var activityBinding: ActivityPluginBinding? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    // Managers initialized during configure()
    private var callStateManager: CallStateManager? = null
    private var pendingCallStore: PendingCallStore? = null
    private var oemDetector: OemDetector? = null
    private var notificationHelper: NotificationHelper? = null

    private var isConfigured = false
    private var nextEventId = 1

    // Pending permission result callback
    private var pendingPermissionResult: Result? = null

    // region FlutterPlugin lifecycle

    /**
     * Called when this plugin is attached to a FlutterEngine.
     *
     * This fires for EVERY engine — main app engine AND background FCM engines.
     * Each invocation creates a separate [MethodChannel] bound to that engine's
     * [BinaryMessenger], so events sent on one channel only reach that engine's
     * Dart isolate.
     *
     * ### Instance Preservation Logic
     *
     * The first instance to attach becomes [instance]. If a background engine
     * attaches while the main instance hasn't called [configure] yet, the
     * background instance temporarily becomes [instance]. This is corrected
     * when [handleConfigure] reclaims the instance.
     *
     * We can't simply "never override" because sometimes the main engine
     * IS the second one (e.g., app killed then restarted by FCM).
     *
     * ### Why NotificationHelper is Initialized Eagerly
     *
     * Background FCM engines call [handleShowIncomingCall] without ever
     * calling [configure]. If notificationHelper wasn't ready, the incoming
     * call notification would fail silently. We use a default app name ("Call")
     * which [configure] later replaces with the real app name.
     */
    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        messenger = binding.binaryMessenger
        channel = MethodChannel(messenger, CHANNEL_NAME)
        channel.setMethodCallHandler(this)

        // Preserve the main (configured) instance — when FCM spawns a
        // background FlutterEngine for data-only messages, a NEW
        // CallBundlePlugin is created and onAttachedToEngine fires again.
        // If we blindly overwrote `instance`, CallActionReceiver would
        // call onCallAccepted() on the unconfigured background instance,
        // hitting isConfigured=false and storing pending instead of
        // sending through the main engine's active MethodChannel.
        val existing = instance
        if (existing == null || !existing.isConfigured) {
            instance = this
            if (existing != null) {
                Log.w(TAG, "onAttachedToEngine: Overriding unconfigured instance ${existing.hashCode()} with ${this.hashCode()}")
            }
        }

        // Initialize core components that are needed immediately
        callStateManager = CallStateManager()
        pendingCallStore = PendingCallStore(context)
        oemDetector = OemDetector()

        // Initialize notificationHelper eagerly with a default app name.
        // This is CRITICAL for killed-state incoming calls: the background
        // FCM handler calls showIncomingCall() without calling configure()
        // first, so notificationHelper must already be available.
        // configure() will re-initialize it with the proper app name later.
        notificationHelper = NotificationHelper(context, "Call")
        notificationHelper?.ensureNotificationChannel()

        Log.d(TAG, "onAttachedToEngine: Plugin attached (hash=${this.hashCode()}, isMainInstance=${instance == this}, instanceHash=${instance?.hashCode()})")
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        if (instance == this) {
            instance = null
        }
        notificationHelper?.cleanup()
        Log.d(TAG, "onDetachedFromEngine: Plugin detached (hash=${this.hashCode()}, wasMainInstance=${instance == null}, isConfigured=$isConfigured)")
    }

    // endregion

    // region ActivityAware lifecycle

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        activityBinding = binding
        binding.addRequestPermissionsResultListener(this)
        binding.addOnNewIntentListener(this)

        // Only enable lock screen flags if there is an active accepted call.
        // This handles Activity recreation during an ongoing call (e.g.,
        // config change, memory pressure). During RINGING, the dedicated
        // IncomingCallActivity shows over the lock screen instead.
        if (callStateManager?.getAllCalls()?.any { it.isAccepted } == true) {
            applyLockScreenFlags(binding.activity)
        }

        // Check if Activity was launched by a notification Accept action
        // (killed-state: PendingIntent.getActivity starts the Activity
        // fresh, so onNewIntent does NOT fire — only onCreate).
        val intent = binding.activity.intent
        if (intent?.getStringExtra("action") == "call_accepted") {
            val callId = intent.getStringExtra("callId")
            if (callId != null) {
                Log.w(TAG, "onAttachedToActivity: call_accepted intent for callId=$callId isConfigured=$isConfigured hash=${this.hashCode()}")

                // CRITICAL: Apply lock screen flags NOW so the Activity
                // can show over the lock screen. Without this, the Activity
                // starts but stays behind the keyguard — invisible to the user.
                // This fixes killed+locked state where callStateManager is
                // empty (fresh start) so the generic isAccepted check above fails.
                applyLockScreenFlags(binding.activity)

                val extraBundle = intent.getBundleExtra("callExtra")
                val extra = if (extraBundle != null) {
                    mutableMapOf<String, Any>().also { map ->
                        extraBundle.keySet().forEach { key ->
                            map[key] = extraBundle.getString(key) ?: ""
                        }
                    }
                } else emptyMap<String, Any>()

                if (isConfigured) {
                    // Dart engine is already running (Activity was destroyed
                    // but process stayed alive). Send the event directly.
                    // If we saved to PendingCallStore instead, configure()
                    // would never be called again and the event would be
                    // stuck forever.
                    Log.w(TAG, "onAttachedToActivity: isConfigured=true, calling onCallAccepted directly")
                    onCallAccepted(callId, extra)
                } else {
                    // Cold-start: save for delivery after configure()
                    // calls deliverPendingEvents().
                    Log.w(TAG, "onAttachedToActivity: isConfigured=FALSE — saving pending accept for callId=$callId (extraKeys=${extra.keys})")
                    notificationHelper?.cancelNotification(callId)
                    notificationHelper?.stopRingtone()
                    IncomingCallActivity.dismissIfShowing()
                    pendingCallStore?.savePendingAccept(callId, extra)
                }
            }

            // Clear the intent extras to prevent re-processing on
            // config changes (rotation, theme change, etc.).
            intent.removeExtra("action")
        }

        Log.d(TAG, "onAttachedToActivity")
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activityBinding?.removeRequestPermissionsResultListener(this)
        activityBinding?.removeOnNewIntentListener(this)
        activityBinding = null
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        activityBinding = binding
        binding.addRequestPermissionsResultListener(this)
        binding.addOnNewIntentListener(this)
        // Re-apply lock screen flags only during an active accepted call
        if (callStateManager?.getAllCalls()?.any { it.isAccepted } == true) {
            applyLockScreenFlags(binding.activity)
        }
    }

    override fun onDetachedFromActivity() {
        activityBinding?.removeRequestPermissionsResultListener(this)
        activityBinding?.removeOnNewIntentListener(this)
        activityBinding = null
        activity = null
    }

    // endregion

    // region NewIntentListener

    /**
     * Called when the Activity receives a new intent via
     * [PendingIntent.getActivity] from the notification Accept button
     * or from [IncomingCallActivity.proceedWithAccept].
     *
     * Delegates to [onCallAccepted] for consistent handling across all
     * accept paths (notification button, IncomingCallActivity, BroadcastReceiver).
     *
     * [onCallAccepted] handles:
     * - Notification cancellation
     * - Ringtone/vibration stop
     * - IncomingCallActivity dismissal
     * - Event delivery to Dart (or PendingCallStore for cold-start)
     * - Lock screen flags
     * - Bringing the app to the foreground
     */
    override fun onNewIntent(intent: Intent): Boolean {
        val action = intent.getStringExtra("action")
        Log.w(TAG, "onNewIntent: action=$action isConfigured=$isConfigured hash=${this.hashCode()}")
        if (action == "call_accepted") {
            val callId = intent.getStringExtra("callId") ?: return false
            Log.w(TAG, "onNewIntent: call_accepted for callId=$callId")

            // Extract caller metadata from the intent
            val extraBundle = intent.getBundleExtra("callExtra")
            val extra = if (extraBundle != null) {
                mutableMapOf<String, Any>().also { map ->
                    extraBundle.keySet().forEach { key ->
                        map[key] = extraBundle.getString(key) ?: ""
                    }
                }
            } else null

            // Delegate to the centralized accept handler.
            // This ensures: notification cancelled, ringtone stopped,
            // event sent to Dart, and app brought to foreground.
            onCallAccepted(callId, extra)

            // Clear the intent to prevent re-processing on config changes
            intent.removeExtra("action")
            return true
        }
        return false
    }

    // endregion

    /**
     * Applies window flags to show the activity over the lock screen
     * and turn the screen on for active call display.
     *
     * IMPORTANT: This is only called when a call is ACCEPTED, not during
     * ringing. During ringing, [IncomingCallActivity] handles lock screen
     * display with its own manifest-declared flags.
     *
     * The flags are cleared by [clearLockScreenFlags] when the call ends.
     *
     * - API 27+: Uses `Activity.setShowWhenLocked()` and `setTurnScreenOn()`
     * - API < 27: Uses legacy `WindowManager.LayoutParams` flags
     */
    private fun applyLockScreenFlags(activity: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                activity.window.addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
            Log.d(TAG, "applyLockScreenFlags: Applied for API ${Build.VERSION.SDK_INT}")
        } catch (e: Exception) {
            Log.w(TAG, "applyLockScreenFlags: Failed to apply", e)
        }
    }

    /**
     * Clears the lock screen flags from the main Activity.
     *
     * Called when a call ends to prevent the app from being accessible
     * over the lock screen between calls.
     */
    private fun clearLockScreenFlags() {
        try {
            val act = activity ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                act.setShowWhenLocked(false)
                act.setTurnScreenOn(false)
            } else {
                @Suppress("DEPRECATION")
                act.window.clearFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
            Log.d(TAG, "clearLockScreenFlags: Cleared")
        } catch (e: Exception) {
            Log.w(TAG, "clearLockScreenFlags: Failed", e)
        }
    }

    // endregion

    // region MethodCallHandler

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "configure" -> handleConfigure(call, result)
            "showIncomingCall" -> handleShowIncomingCall(call, result)
            "showOutgoingCall" -> handleShowOutgoingCall(call, result)
            "endCall" -> handleEndCall(call, result)
            "endAllCalls" -> handleEndAllCalls(result)
            "setCallConnected" -> handleSetCallConnected(call, result)
            "getActiveCalls" -> handleGetActiveCalls(result)
            "checkPermissions" -> handleCheckPermissions(result)
            "requestPermissions" -> handleRequestPermissions(result)
            "requestBatteryOptimizationExemption" -> handleRequestBatteryOptimization(result)
            "getVoipToken" -> handleGetVoipToken(result)
            "dispose" -> handleDispose(result)
            else -> result.notImplemented()
        }
    }

    // endregion

    // region Method handlers

    /**
     * Handles the `configure` MethodChannel call from Dart.
     *
     * This is the FIRST Dart→native call after the main engine starts.
     * It performs three critical operations:
     *
     * 1. **Re-initializes [NotificationHelper]** with the real app name
     *    (replacing the default "Call" set in [onAttachedToEngine]).
     *
     * 2. **Reclaims [instance]** — fixes the background engine race
     *    condition where a background FCM instance may have stolen the
     *    static reference. Since only the main engine calls configure(),
     *    `instance = this` is always correct here. This ensures
     *    [CallActionReceiver] routes events through the main engine's
     *    [MethodChannel], which reaches the main Dart isolate.
     *
     * 3. **Delivers pending events** — if accept was triggered during
     *    cold-start (before configure), [PendingCallStore] holds it.
     *    [deliverPendingEvents] sends it now via [sendCallEvent].
     *
     * Called by: `CallKitService.initialize()` → `CallBundle.configure()`
     */
    private fun handleConfigure(call: MethodCall, result: Result) {
        try {
            val configMap = call.arguments as? Map<*, *> ?: run {
                result.error("INVALID_ARGS", "Configuration map is required", null)
                return
            }

            val appName = configMap["appName"] as? String ?: "CallBundle"

            // Initialize notification helper
            notificationHelper = NotificationHelper(context, appName)
            notificationHelper?.ensureNotificationChannel()

            // Store background reject config (BackgroundRejectConfig)
            // so BackgroundCallRejectHelper can make native HTTP calls
            // when the user declines in killed state.
            val backgroundReject = configMap["backgroundReject"] as? Map<*, *>
            if (backgroundReject != null) {
                BackgroundCallRejectHelper.storeConfig(context, backgroundReject)
            }

            isConfigured = true

            // CRITICAL: Reclaim the singleton instance.
            //
            // Race condition fix: when FCM spawns a background engine
            // BEFORE configure() is called on the main engine, the
            // background instance steals `instance` because the main
            // instance's isConfigured is still false at that point.
            //
            // By setting instance=this here, we guarantee the configured
            // (main engine) instance is always the canonical one that
            // CallActionReceiver and other static callers use. Events
            // sent through this instance's channel reach the main Dart
            // isolate where IncomingCallHandlerService is listening.
            val previousInstance = instance
            instance = this

            // Deliver any pending cold-start events
            deliverPendingEvents()

            // Signal readiness to Dart
            sendReadySignal()

            result.success(null)
            Log.w(TAG, "configure: SUCCESS appName=$appName isConfigured=$isConfigured instance=${this.hashCode()} previousInstance=${previousInstance?.hashCode()} reclaimed=${previousInstance != this}")
        } catch (e: Exception) {
            Log.e(TAG, "configure: FAILED — isConfigured will remain FALSE, all accept events will be stored in PendingCallStore instead of sent to Dart!", e)
            result.error("CONFIGURE_ERROR", e.message, e.stackTraceToString())
        }
    }

    private fun handleShowIncomingCall(call: MethodCall, result: Result) {
        try {
            val paramsMap = call.arguments as? Map<*, *> ?: run {
                result.error("INVALID_ARGS", "Call params map is required", null)
                return
            }

            val callId = paramsMap["callId"] as? String ?: run {
                result.error("INVALID_ARGS", "callId is required", null)
                return
            }
            val callerName = paramsMap["callerName"] as? String ?: "Unknown"
            val callType = (paramsMap["callType"] as? Number)?.toInt() ?: 0
            val handle = paramsMap["handle"] as? String
            val duration = (paramsMap["duration"] as? Number)?.toLong() ?: 60000L
            val extra = paramsMap["extra"] as? Map<*, *> ?: emptyMap<String, Any>()
            val callerAvatar = paramsMap["callerAvatar"] as? String

            // Store call in state manager
            callStateManager?.addCall(
                CallInfo(
                    callId = callId,
                    callerName = callerName,
                    callType = callType,
                    state = "ringing",
                    isAccepted = false,
                    extra = extra
                )
            )

            // Show notification (OEM-adaptive)
            notificationHelper?.showIncomingCallNotification(
                callId = callId,
                callerName = callerName,
                callType = callType,
                handle = handle,
                callerAvatar = callerAvatar,
                duration = duration,
                isOemAdaptive = oemDetector?.isBudgetOem() ?: false,
                extra = extra
            )

            result.success(null)
            Log.d(TAG, "showIncomingCall: callId=$callId, caller=$callerName")
        } catch (e: Exception) {
            result.error("SHOW_CALL_ERROR", e.message, e.stackTraceToString())
        }
    }

    private fun handleShowOutgoingCall(call: MethodCall, result: Result) {
        try {
            val paramsMap = call.arguments as? Map<*, *> ?: run {
                result.error("INVALID_ARGS", "Call params map is required", null)
                return
            }

            val callId = paramsMap["callId"] as? String ?: run {
                result.error("INVALID_ARGS", "callId is required", null)
                return
            }
            val callerName = paramsMap["callerName"] as? String ?: "Unknown"
            val callType = (paramsMap["callType"] as? Number)?.toInt() ?: 0
            val extra = paramsMap["extra"] as? Map<*, *> ?: emptyMap<String, Any>()

            callStateManager?.addCall(
                CallInfo(
                    callId = callId,
                    callerName = callerName,
                    callType = callType,
                    state = "dialing",
                    isAccepted = false,
                    extra = extra
                )
            )

            notificationHelper?.showOngoingCallNotification(
                callId = callId,
                callerName = callerName,
                callType = callType
            )

            result.success(null)
            Log.d(TAG, "showOutgoingCall: callId=$callId")
        } catch (e: Exception) {
            result.error("SHOW_CALL_ERROR", e.message, e.stackTraceToString())
        }
    }

    private fun handleEndCall(call: MethodCall, result: Result) {
        try {
            val callId = call.arguments as? String ?: run {
                result.error("INVALID_ARGS", "callId is required", null)
                return
            }

            callStateManager?.updateCallState(callId, "ended")
            notificationHelper?.cancelNotification(callId)
            notificationHelper?.stopRingtone()

            // Dismiss IncomingCallActivity and clear lock screen flags
            IncomingCallActivity.dismissIfShowing()
            clearLockScreenFlags()

            // Send event to Dart with isUserInitiated = false (programmatic)
            sendCallEvent(
                type = "ended",
                callId = callId,
                isUserInitiated = false,
                extra = callStateManager?.getCall(callId)?.extra ?: emptyMap<String, Any>()
            )

            callStateManager?.removeCall(callId)

            result.success(null)
            Log.d(TAG, "endCall: callId=$callId (programmatic)")
        } catch (e: Exception) {
            result.error("END_CALL_ERROR", e.message, e.stackTraceToString())
        }
    }

    private fun handleEndAllCalls(result: Result) {
        try {
            val calls = callStateManager?.getAllCalls() ?: emptyList()
            for (call in calls) {
                notificationHelper?.cancelNotification(call.callId)
                sendCallEvent(
                    type = "ended",
                    callId = call.callId,
                    isUserInitiated = false,
                    extra = call.extra
                )
            }
            callStateManager?.removeAllCalls()
            notificationHelper?.stopRingtone()

            // Dismiss IncomingCallActivity and clear lock screen flags
            IncomingCallActivity.dismissIfShowing()
            clearLockScreenFlags()

            result.success(null)
            Log.d(TAG, "endAllCalls: ended ${calls.size} calls")
        } catch (e: Exception) {
            result.error("END_ALL_ERROR", e.message, e.stackTraceToString())
        }
    }

    private fun handleSetCallConnected(call: MethodCall, result: Result) {
        try {
            val callId = call.arguments as? String ?: run {
                result.error("INVALID_ARGS", "callId is required", null)
                return
            }

            callStateManager?.updateCallState(callId, "active")
            notificationHelper?.stopRingtone()

            // Dismiss IncomingCallActivity if still showing (race condition safety)
            IncomingCallActivity.dismissIfShowing()

            // Update notification to "ongoing call" style
            val callInfo = callStateManager?.getCall(callId)
            if (callInfo != null) {
                notificationHelper?.showOngoingCallNotification(
                    callId = callInfo.callId,
                    callerName = callInfo.callerName,
                    callType = callInfo.callType
                )
            }

            result.success(null)
            Log.d(TAG, "setCallConnected: callId=$callId")
        } catch (e: Exception) {
            result.error("SET_CONNECTED_ERROR", e.message, e.stackTraceToString())
        }
    }

    private fun handleGetActiveCalls(result: Result) {
        try {
            val calls = callStateManager?.getAllCalls() ?: emptyList()
            val callMaps = calls.map { call ->
                mapOf(
                    "callId" to call.callId,
                    "callerName" to call.callerName,
                    "callType" to call.callType,
                    "state" to call.state,
                    "isAccepted" to call.isAccepted,
                    "extra" to call.extra
                )
            }
            result.success(callMaps)
        } catch (e: Exception) {
            result.error("GET_CALLS_ERROR", e.message, e.stackTraceToString())
        }
    }

    /**
     * Returns current permission status without prompting.
     * Used by Dart to check status before showing custom dialogs.
     */
    private fun handleCheckPermissions(result: Result) {
        try {
            result.success(buildPermissionInfo())
        } catch (e: Exception) {
            result.error("PERMISSIONS_ERROR", e.message, e.stackTraceToString())
        }
    }

    private fun handleRequestPermissions(result: Result) {
        try {
            val currentActivity = activity
            if (currentActivity == null) {
                // No activity: just return current status without requesting
                result.success(buildPermissionInfo())
                return
            }

            // Check if POST_NOTIFICATIONS permission needs to be requested (API 33+)
            if (Build.VERSION.SDK_INT >= 33) {
                val hasNotifPerm = context.checkSelfPermission(
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (!hasNotifPerm) {
                    // Request the permission — result will be delivered via onRequestPermissionsResult
                    pendingPermissionResult = result
                    ActivityCompat.requestPermissions(
                        currentActivity,
                        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                        PERMISSION_REQUEST_CODE
                    )
                    return
                }
            }

            // Check full screen intent permission (API 34+)
            if (Build.VERSION.SDK_INT >= 34) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                if (!nm.canUseFullScreenIntent()) {
                    // Open system settings for full screen intent permission
                    try {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:${context.packageName}")
                        )
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        currentActivity.startActivity(intent)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to open full screen intent settings", e)
                    }
                }
            }

            // All permissions already granted or requested
            result.success(buildPermissionInfo())
        } catch (e: Exception) {
            result.error("PERMISSIONS_ERROR", e.message, e.stackTraceToString())
        }
    }

    /**
     * Builds the current permission info map.
     */
    private fun buildPermissionInfo(): Map<String, Any> {
        val permissionInfo = mutableMapOf<String, Any>(
            "manufacturer" to Build.MANUFACTURER.lowercase(),
            "model" to Build.MODEL,
            "osVersion" to Build.VERSION.SDK_INT.toString(),
            "phoneAccountEnabled" to true,
            "batteryOptimizationExempt" to isBatteryOptimizationExempt(),
            "diagnosticInfo" to mapOf(
                "isBudgetOem" to (oemDetector?.isBudgetOem() ?: false),
                "oemStrategy" to (oemDetector?.getRecommendedStrategy() ?: "standard")
            )
        )

        // Check notification permission (API 33+)
        if (Build.VERSION.SDK_INT >= 33) {
            val hasNotifPerm = context.checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            permissionInfo["notificationPermission"] = if (hasNotifPerm) "granted" else "denied"
        } else {
            permissionInfo["notificationPermission"] = "granted"
        }

        // Check full screen intent permission (API 34+)
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            permissionInfo["fullScreenIntentPermission"] =
                if (nm.canUseFullScreenIntent()) "granted" else "denied"
        } else {
            permissionInfo["fullScreenIntentPermission"] = "granted"
        }

        return permissionInfo
    }

    /**
     * Checks if the app is exempt from battery optimization (Doze mode).
     *
     * Uses [PowerManager.isIgnoringBatteryOptimizations] (API 23+).
     * On API < 23, returns `true` (Doze didn't exist).
     */
    private fun isBatteryOptimizationExempt(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Handles the `requestBatteryOptimizationExemption` MethodChannel call.
     *
     * Shows the system dialog asking the user to disable battery
     * optimization for this app. This is a separate method from
     * [handleRequestPermissions] so the app can control the UX —
     * e.g., show a custom explanation dialog before prompting.
     *
     * Returns `true` if already exempt, or `false` after launching
     * the system dialog (the user may or may not grant it).
     */
    private fun handleRequestBatteryOptimization(result: Result) {
        try {
            if (isBatteryOptimizationExempt()) {
                result.success(true)
                return
            }

            val currentActivity = activity
            if (currentActivity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                result.success(false)
                return
            }

            try {
                @SuppressWarnings("BatteryLife")
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                currentActivity.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to request battery optimization exemption", e)
            }

            // Return false — the dialog is shown but user hasn't responded yet.
            // App should re-check with checkPermissions() after resuming.
            result.success(false)
        } catch (e: Exception) {
            result.error("BATTERY_OPT_ERROR", e.message, e.stackTraceToString())
        }
    }

    // region PluginRegistry.RequestPermissionsResultListener

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode != PERMISSION_REQUEST_CODE) return false

        val pending = pendingPermissionResult
        pendingPermissionResult = null

        if (pending != null) {
            // Also check full screen intent after notification permission
            if (Build.VERSION.SDK_INT >= 34) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                if (!nm.canUseFullScreenIntent()) {
                    try {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:${context.packageName}")
                        )
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        activity?.startActivity(intent)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to open full screen intent settings", e)
                    }
                }
            }

            pending.success(buildPermissionInfo())
        }

        return true
    }

    // endregion

    private fun handleGetVoipToken(result: Result) {
        // VoIP tokens are iOS-only (PushKit)
        result.success(null)
    }

    private fun handleDispose(result: Result) {
        notificationHelper?.cleanup()
        callStateManager?.removeAllCalls()
        IncomingCallActivity.dismissIfShowing()
        clearLockScreenFlags()
        isConfigured = false
        result.success(null)
        Log.d(TAG, "dispose: Plugin disposed (hash=${this.hashCode()}, isMainInstance=${instance == this})")
    }

    // endregion

    // region Event sending (Native → Dart)

    /**
     * Sends a call event to the Dart side via MethodChannel.
     *
     * This is the SINGLE path for all native→Dart communication.
     * Uses MethodChannel (not EventChannel) for reliable delivery
     * that survives Activity lifecycle and GC.
     *
     * ## Threading
     *
     * Events are posted to [mainHandler] (main thread) because
     * MethodChannel.invokeMethod MUST be called on the main thread.
     * The caller may be on any thread (BroadcastReceiver, ConnectionService).
     *
     * ## Does NOT Check isConfigured
     *
     * This method always sends, regardless of [isConfigured]. The check
     * for whether to send vs store is done by the CALLER:
     * - [onCallAccepted]: Checks isConfigured → sends or stores in PendingCallStore
     * - [onCallDeclined]: Always sends (no store — decline on cold-start
     *   just cancels the notification, no Dart handling needed)
     *
     * ## Dart Reception
     *
     * The Dart handler ([MethodChannelCallBundle._handleNativeCall]) is
     * registered in the constructor, so it's ready before [configure].
     * Events land in `_eventController` (broadcast stream), consumed by
     * [IncomingCallHandlerService].
     */
    fun sendCallEvent(
        type: String,
        callId: String,
        isUserInitiated: Boolean,
        extra: Map<*, *> = emptyMap<String, Any>()
    ) {
        val eventId = nextEventId++
        val eventMap = mapOf(
            "type" to type,
            "callId" to callId,
            "isUserInitiated" to isUserInitiated,
            "extra" to extra,
            "timestamp" to System.currentTimeMillis(),
            "eventId" to eventId
        )

        Log.w(TAG, "sendCallEvent: SENDING type=$type callId=$callId eventId=$eventId isConfigured=$isConfigured extraKeys=${extra.keys}")

        mainHandler.post {
            try {
                channel.invokeMethod("onCallEvent", eventMap)
                Log.w(TAG, "sendCallEvent: SUCCESS type=$type callId=$callId eventId=$eventId")
            } catch (e: Throwable) {
                // CRITICAL: Catch Throwable (not just Exception) to prevent
                // silent crashes from R8-optimized code that may throw Error
                // subclasses like NoSuchMethodError or NoClassDefFoundError.
                Log.e(TAG, "sendCallEvent: FAILED to send event type=$type callId=$callId eventId=$eventId", e)
            }
        }
    }

    /**
     * Handles a user accept action from a notification or TelecomManager.
     *
     * Called by:
     * - [CallActionReceiver] (notification Accept button, though currently
     *   Accept uses PendingIntent.getActivity → onNewIntent path instead)
     * - [onNewIntent] (when Accept launches/brings forward MainActivity)
     * - [onAttachedToActivity] (when Activity is recreated with accept intent)
     * - [IncomingCallActivity.proceedWithAccept] (native lock screen UI)
     *
     * ## Event Routing
     *
     * - **isConfigured=true**: Sends `"accepted"` event via [sendCallEvent]
     *   → Dart receives immediately → [IncomingCallHandlerService] navigates
     *   to ActiveCallScreen.
     * - **isConfigured=false** (cold-start): Stores in [PendingCallStore]
     *   → delivered when [handleConfigure] calls [deliverPendingEvents].
     *
     * ## Extra Resolution Chain
     *
     * Call metadata (`callerName`, `callType`, etc.) is resolved in order:
     * 1. [callStateManager] (if this instance showed the call)
     * 2. [intentExtra] (from notification PendingIntent Bundle)
     * 3. Empty map (fallback)
     *
     * @param callId The unique call identifier.
     * @param intentExtra Optional metadata from the notification PendingIntent.
     *   Used as fallback when this instance's [callStateManager] doesn't
     *   have the call (e.g., call was shown by a background FCM engine).
     */
    fun onCallAccepted(callId: String, intentExtra: Map<String, Any>? = null) {
        Log.w(TAG, "onCallAccepted: START callId=$callId isConfigured=$isConfigured hash=${this.hashCode()} instanceHash=${instance?.hashCode()} hasCallState=${callStateManager?.getCall(callId) != null} hasIntentExtra=${intentExtra != null}")
        callStateManager?.updateCallState(callId, "active", isAccepted = true)
        notificationHelper?.cancelNotification(callId)
        notificationHelper?.stopRingtone()

        // Dismiss the native IncomingCallActivity if it was showing
        IncomingCallActivity.dismissIfShowing()

        val extra = callStateManager?.getCall(callId)?.extra
            ?: intentExtra
            ?: emptyMap<String, Any>()

        Log.w(TAG, "onCallAccepted: extraSource=${if (callStateManager?.getCall(callId)?.extra != null) "callStateManager" else if (intentExtra != null) "intentExtra" else "empty"} extraKeys=${extra.keys}")

        if (isConfigured) {
            Log.w(TAG, "onCallAccepted: isConfigured=true, sending event via sendCallEvent")
            sendCallEvent(
                type = "accepted",
                callId = callId,
                isUserInitiated = true,
                extra = extra
            )
        } else {
            // Cold-start: store pending event for delivery after configure()
            Log.w(TAG, "onCallAccepted: isConfigured=FALSE — storing to PendingCallStore (THIS IS THE BUG if configure() never ran)")
            pendingCallStore?.savePendingAccept(callId, extra)
        }

        // Temporarily enable lock screen flags on the main Activity so
        // the Flutter call screen is visible if the device is locked.
        // These flags are cleared when the call ends (handleEndCall /
        // handleEndAllCalls) to prevent full app access over lock screen.
        activity?.let { applyLockScreenFlags(it) }

        // CRITICAL: Bring the app to the foreground so the user sees
        // the in-app call screen. Without this, accepting from a
        // background notification or lock screen leaves the app invisible.
        bringAppToForeground(callId)
    }

    /**
     * Handles a user decline action from a notification or TelecomManager.
     *
     * ## Killed-State Handling
     *
     * When the app is killed, the background FCM engine (Instance B) shows
     * the notification. If the user taps Decline:
     * - Instance B handles it (may still be alive)
     * - B.sendCallEvent sends through B's channel → background Dart isolate
     * - Background isolate has NO IncomingCallHandlerService listener
     * - Event is silently dropped → caller never sees rejection
     *
     * Fix: when `isConfigured=false`, we persist the decline in
     * [PendingCallStore]. When the main engine starts and [configure] is
     * called, [deliverPendingEvents] sends the decline → Dart processes
     * it → reject API is called → caller sees rejection.
     *
     * The notification cancellation and ringtone stop happen immediately
     * (they don't need Dart). Only the API reject is deferred.
     *
     * @param callId The unique call identifier.
     * @param intentExtra Optional extra metadata from the notification intent.
     *   Used as fallback when this instance's [callStateManager] doesn't
     *   have the call (e.g., call was shown by a background FCM engine).
     */
    fun onCallDeclined(callId: String, intentExtra: Map<String, Any>? = null) {
        Log.d(TAG, "onCallDeclined: callId=$callId, isConfigured=$isConfigured, hash=${this.hashCode()}, instanceHash=${instance?.hashCode()})")
        callStateManager?.updateCallState(callId, "ended")
        notificationHelper?.cancelNotification(callId)
        notificationHelper?.stopRingtone()

        // Dismiss the native IncomingCallActivity if it was showing
        IncomingCallActivity.dismissIfShowing()

        val extra = callStateManager?.getCall(callId)?.extra
            ?: intentExtra
            ?: emptyMap<String, Any>()

        // Always send the event regardless of isConfigured.
        //
        // When isConfigured=true: the main engine's Dart isolate receives
        //   it and IncomingCallHandlerService calls the reject API.
        //
        // When isConfigured=false (killed state): the background FCM
        //   engine's Dart isolate may still be alive with a listener
        //   set up by _waitForCallActionInBackground(). If it IS alive,
        //   the listener catches the decline and calls the reject API
        //   immediately from the background isolate. If the background
        //   engine is DEAD (channel.invokeMethod fails), the pending
        //   store below ensures the reject happens on next app start.
        Log.d(TAG, "onCallDeclined: sending declined event (extra keys: ${extra.keys}, isConfigured=$isConfigured)")
        sendCallEvent(
            type = "declined",
            callId = callId,
            isUserInitiated = true,
            extra = extra
        )

        if (!isConfigured) {
            // Also persist as fallback — if the background engine is dead,
            // sendCallEvent above would fail silently. The pending decline
            // is delivered when configure() runs on next app start.
            pendingCallStore?.savePendingDecline(callId, extra)
            Log.d(TAG, "onCallDeclined: Also stored pending decline for callId=$callId (fallback)")

            // CRITICAL: Make a direct native HTTP call to reject the call
            // immediately. This bypasses Dart entirely and ensures the
            // caller side sees the rejection within seconds, even when
            // the Dart isolate is not available.
            val callData = mutableMapOf("callId" to callId)
            for ((key, value) in extra) {
                val k = key?.toString() ?: continue
                callData[k] = value?.toString() ?: ""
            }
            BackgroundCallRejectHelper.rejectCall(context, callData)
        }

        callStateManager?.removeCall(callId)
    }

    // endregion

    // region Cold-start support

    /**
     * Delivers any pending cold-start events after configure() is called.
     *
     * This is the deterministic handshake protocol:
     * 1. Native receives accept/decline event before Dart is ready
     *    → stores in [PendingCallStore]
     * 2. Dart calls configure() → this method delivers stored events
     * 3. No hardcoded delays needed
     *
     * ## Accept vs Decline
     *
     * - **Accept**: Sends `"accepted"` event → Dart navigates to call screen
     * - **Decline**: Sends `"declined"` event → Dart calls reject API
     *   so the caller side stops ringing
     *
     * Both are single-consumption (cleared after reading).
     */
    private fun deliverPendingEvents() {
        Log.w(TAG, "deliverPendingEvents: Checking for pending cold-start events...")
        // Deliver pending accept (if any)
        val pendingAccept = pendingCallStore?.consumePendingAccept()
        if (pendingAccept != null) {
            Log.w(TAG, "deliverPendingEvents: DELIVERING pending accept for callId=${pendingAccept.callId} extraKeys=${pendingAccept.extra.keys}")
            sendCallEvent(
                type = "accepted",
                callId = pendingAccept.callId,
                isUserInitiated = true,
                extra = pendingAccept.extra
            )
        } else {
            Log.w(TAG, "deliverPendingEvents: No pending accept")
        }

        // Deliver pending decline (if any)
        // This ensures the reject API is called even when the user
        // declined from a killed-state notification. The notification
        // and ringtone were already stopped natively; this sends the
        // API request so the caller side sees the rejection.
        val pendingDecline = pendingCallStore?.consumePendingDecline()
        if (pendingDecline != null) {
            Log.w(TAG, "deliverPendingEvents: DELIVERING pending decline for callId=${pendingDecline.callId}")
            sendCallEvent(
                type = "declined",
                callId = pendingDecline.callId,
                isUserInitiated = true,
                extra = pendingDecline.extra
            )
        } else {
            Log.w(TAG, "deliverPendingEvents: No pending decline")
        }
    }

    private fun sendReadySignal() {
        mainHandler.post {
            try {
                channel.invokeMethod("onReady", null)
            } catch (e: Exception) {
                Log.e(TAG, "sendReadySignal: Failed", e)
            }
        }
    }

    /**
     * Brings the app's main Activity to the foreground.
     *
     * Called after the user accepts an incoming call. Without this,
     * accepting from a background notification or lock screen leaves
     * the app invisible — the Dart side navigates to the call screen
     * but the user never sees it.
     *
     * Uses [FLAG_ACTIVITY_SINGLE_TOP] to avoid creating duplicate activities
     * and [FLAG_ACTIVITY_REORDER_TO_FRONT] to bring an existing activity
     * to the front of the task.
     *
     * On Android 10+ (API 29+) this relies on the background activity
     * start exemption granted by the user-tapped notification PendingIntent.
     * Uses the Activity context when available for stronger BAL exemption.
     */
    private fun bringAppToForeground(callId: String) {
        try {
            val launchIntent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)

            if (launchIntent != null) {
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
                // DO NOT put "call_accepted" action here — onCallAccepted
                // already sent the event (or saved to PendingCallStore).
                // Adding it would cause onNewIntent to send a duplicate event.

                // Prefer Activity context for BAL exemption on Android 10+.
                // Application context may be blocked as "background start"
                // if no foreground Activity exists.
                val startContext = activity ?: context
                startContext.startActivity(launchIntent)
                Log.d(TAG, "bringAppToForeground: Launched activity for callId=$callId (fromActivity=${activity != null})")
            } else {
                Log.w(TAG, "bringAppToForeground: Launch intent null for ${context.packageName}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "bringAppToForeground: Failed", e)
        }
    }

    // endregion
}
