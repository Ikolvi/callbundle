package com.callbundle.callbundle_android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
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
 * Key design decisions:
 * - Uses MethodChannel for BOTH directions (not EventChannel)
 * - Supports background isolates via strong BinaryMessenger reference
 * - Ships consumer ProGuard rules (no app-level changes needed)
 * - Ships permissions in AndroidManifest.xml (auto-merged)
 * - Implements PendingCallStore for cold-start event delivery
 */
class CallBundlePlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
    PluginRegistry.RequestPermissionsResultListener,
    PluginRegistry.NewIntentListener {

    companion object {
        private const val TAG = "CallBundlePlugin"
        private const val CHANNEL_NAME = "com.callbundle/main"
        private const val PERMISSION_REQUEST_CODE = 29741

        /** Singleton reference for ConnectionService to send events back. */
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

        Log.d(TAG, "onAttachedToEngine: Plugin attached (isMainInstance=${instance == this})")
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        if (instance == this) {
            instance = null
        }
        notificationHelper?.cleanup()
        Log.d(TAG, "onDetachedFromEngine: Plugin detached")
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
                Log.d(TAG, "onAttachedToActivity: call_accepted intent for callId=$callId")

                notificationHelper?.cancelNotification(callId)
                notificationHelper?.stopRingtone()

                val extraBundle = intent.getBundleExtra("callExtra")
                val extra = if (extraBundle != null) {
                    mutableMapOf<String, Any>().also { map ->
                        extraBundle.keySet().forEach { key ->
                            map[key] = extraBundle.getString(key) ?: ""
                        }
                    }
                } else emptyMap<String, Any>()

                // Not configured yet (app just starting) — save for
                // delivery when configure() calls deliverPendingEvents().
                pendingCallStore?.savePendingAccept(callId, extra)
                Log.d(TAG, "onAttachedToActivity: Saved pending accept for callId=$callId")
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
     * Safety net: when the Accept notification action (PendingIntent.getActivity)
     * or [bringAppToForeground] resumes the Activity, Android calls `onNewIntent`.
     *
     * If the plugin is configured, the accept event is sent immediately.
     * Otherwise, it's stored in [PendingCallStore] for delivery after configure().
     */
    override fun onNewIntent(intent: Intent): Boolean {
        val action = intent.getStringExtra("action")
        if (action == "call_accepted") {
            val callId = intent.getStringExtra("callId") ?: return false
            Log.d(TAG, "onNewIntent: call_accepted for callId=$callId")

            // Cancel the incoming call notification and ringtone
            notificationHelper?.cancelNotification(callId)
            notificationHelper?.stopRingtone()

            // Dismiss the native incoming call Activity if it was showing
            IncomingCallActivity.dismissIfShowing()

            // Extract caller metadata from the intent
            val extraBundle = intent.getBundleExtra("callExtra")
            val extra = if (extraBundle != null) {
                mutableMapOf<String, Any>().also { map ->
                    extraBundle.keySet().forEach { key ->
                        map[key] = extraBundle.getString(key) ?: ""
                    }
                }
            } else emptyMap<String, Any>()

            if (isConfigured) {
                callStateManager?.updateCallState(callId, "active", isAccepted = true)
                sendCallEvent(
                    type = "accepted",
                    callId = callId,
                    isUserInitiated = true,
                    extra = extra
                )
            } else {
                // App just starting — save for delivery after configure()
                pendingCallStore?.savePendingAccept(callId, extra)
                Log.d(TAG, "onNewIntent: Saved pending accept for callId=$callId")
            }
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
            "getVoipToken" -> handleGetVoipToken(result)
            "dispose" -> handleDispose(result)
            else -> result.notImplemented()
        }
    }

    // endregion

    // region Method handlers

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

            isConfigured = true

            // Deliver any pending cold-start events
            deliverPendingEvents()

            // Signal readiness to Dart
            sendReadySignal()

            result.success(null)
            Log.d(TAG, "configure: Plugin configured with appName=$appName")
        } catch (e: Exception) {
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
            "batteryOptimizationExempt" to false,
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
        Log.d(TAG, "dispose: Plugin disposed")
    }

    // endregion

    // region Event sending (Native → Dart)

    /**
     * Sends a call event to the Dart side via MethodChannel.
     *
     * This is the SINGLE path for all native→Dart communication.
     * Uses MethodChannel (not EventChannel) for reliable delivery
     * that survives Activity lifecycle and GC.
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

        mainHandler.post {
            try {
                channel.invokeMethod("onCallEvent", eventMap)
            } catch (e: Exception) {
                Log.e(TAG, "sendCallEvent: Failed to send event type=$type callId=$callId", e)
            }
        }
    }

    /**
     * Handles a user accept action from a notification or TelecomManager.
     *
     * Called by ConnectionService or notification action receiver.
     *
     * @param callId The unique call identifier.
     * @param intentExtra Optional extra metadata from the notification intent.
     *   Used as fallback when this instance's [callStateManager] doesn't
     *   have the call (e.g., call was registered by a background FCM engine).
     */
    fun onCallAccepted(callId: String, intentExtra: Map<String, Any>? = null) {
        callStateManager?.updateCallState(callId, "active", isAccepted = true)
        notificationHelper?.cancelNotification(callId)
        notificationHelper?.stopRingtone()

        // Dismiss the native IncomingCallActivity if it was showing
        IncomingCallActivity.dismissIfShowing()

        val extra = callStateManager?.getCall(callId)?.extra
            ?: intentExtra
            ?: emptyMap<String, Any>()

        if (isConfigured) {
            sendCallEvent(
                type = "accepted",
                callId = callId,
                isUserInitiated = true,
                extra = extra
            )
        } else {
            // Cold-start: store pending event for delivery after configure()
            pendingCallStore?.savePendingAccept(callId, extra)
            Log.d(TAG, "onCallAccepted: Stored pending accept for callId=$callId")
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
     */
    fun onCallDeclined(callId: String) {
        callStateManager?.updateCallState(callId, "ended")
        notificationHelper?.cancelNotification(callId)
        notificationHelper?.stopRingtone()

        // Dismiss the native IncomingCallActivity if it was showing
        IncomingCallActivity.dismissIfShowing()

        val extra = callStateManager?.getCall(callId)?.extra ?: emptyMap<String, Any>()
        sendCallEvent(
            type = "declined",
            callId = callId,
            isUserInitiated = true,
            extra = extra
        )
        callStateManager?.removeCall(callId)
    }

    // endregion

    // region Cold-start support

    /**
     * Delivers any pending cold-start events after configure() is called.
     *
     * This is the deterministic handshake protocol:
     * 1. Native receives accept event before Dart is ready → stores in PendingCallStore
     * 2. Dart calls configure() → this method delivers stored events
     * 3. No hardcoded delays needed
     */
    private fun deliverPendingEvents() {
        val pending = pendingCallStore?.consumePendingAccept()
        if (pending != null) {
            Log.d(TAG, "deliverPendingEvents: Delivering pending accept for callId=${pending.callId}")
            sendCallEvent(
                type = "accepted",
                callId = pending.callId,
                isUserInitiated = true,
                extra = pending.extra
            )
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
                context.startActivity(launchIntent)
                Log.d(TAG, "bringAppToForeground: Launched activity for callId=$callId")
            } else {
                Log.w(TAG, "bringAppToForeground: Launch intent null for ${context.packageName}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "bringAppToForeground: Failed", e)
        }
    }

    // endregion
}
