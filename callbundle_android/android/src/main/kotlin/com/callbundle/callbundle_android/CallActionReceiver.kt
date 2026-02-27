package com.callbundle.callbundle_android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver for handling notification action buttons.
 *
 * Receives accept/decline/end intents from [NotificationHelper]'s
 * PendingIntents and forwards them to [CallBundlePlugin] for event dispatch.
 *
 * ## Event Flow
 *
 * ```
 * User taps Decline → Android delivers PendingIntent.getBroadcast()
 *   → CallActionReceiver.onReceive()
 *     → CallBundlePlugin.instance?.onCallDeclined(callId, extra)
 *       → sendCallEvent("declined", ...) via MethodChannel
 *         → Dart _handleNativeCall → IncomingCallHandlerService._handleDeclined
 *           → PUT /v1/api/calls/{callId}/reject
 * ```
 *
 * ## Why We Pass `callExtra` on Decline
 *
 * The `callExtra` Bundle is embedded in the notification PendingIntent by
 * [NotificationHelper.createActionPendingIntent]. We extract and pass it
 * to [CallBundlePlugin.onCallDeclined] as a fallback because:
 *
 * - [CallBundlePlugin.callStateManager] is per-instance and wiped on
 *   engine recreation
 * - If the incoming call was shown by a background FCM engine (Instance B)
 *   but decline is handled by the main engine (Instance A), Instance A's
 *   callStateManager doesn't have the call data
 * - The PendingIntent Bundle is the only reliable source of call metadata
 *   across engine boundaries
 *
 * ## Accept vs Decline PendingIntent Types
 *
 * - **Accept**: Uses `PendingIntent.getActivity()` → launches MainActivity
 *   directly. This is NOT routed through CallActionReceiver.
 * - **Decline/End**: Uses `PendingIntent.getBroadcast()` → this receiver.
 *
 * ## Important: bringAppToForeground on Accept
 *
 * The Accept case calls [bringAppToForeground] but does NOT add
 * `"call_accepted"` action to the launch intent. This prevents a
 * duplicate `onNewIntent → onCallAccepted` event since `onCallAccepted()`
 * was already called directly above.
 *
 * This receiver must be registered in AndroidManifest.xml:
 * ```xml
 * <receiver android:name=".CallActionReceiver" android:exported="false" />
 * ```
 */
class CallActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallActionReceiver"
        const val ACTION_ACCEPT = "com.callbundle.ACTION_ACCEPT"
        const val ACTION_DECLINE = "com.callbundle.ACTION_DECLINE"
        const val ACTION_END = "com.callbundle.ACTION_END"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra("callId") ?: run {
            Log.e(TAG, "onReceive: Missing callId in intent")
            return
        }

        val plugin = CallBundlePlugin.instance

        when (intent.action) {
            ACTION_ACCEPT -> {
                Log.d(TAG, "onReceive: Accept action for callId=$callId")

                // Extract caller metadata from the notification PendingIntent.
                // The NotificationHelper embeds a "callExtra" Bundle with all
                // call info (callerName, callType, callerAvatar, etc.).
                val extraBundle = intent.getBundleExtra("callExtra")
                val extra = if (extraBundle != null) {
                    mutableMapOf<String, Any>().also { map ->
                        extraBundle.keySet().forEach { key ->
                            map[key] = extraBundle.getString(key) ?: ""
                        }
                    }
                } else null

                if (plugin != null) {
                    plugin.onCallAccepted(callId, extra)
                } else {
                    // App killed: plugin not alive yet.
                    // Persist directly so deliverPendingEvents() picks it up
                    // after Flutter engine starts and configure() is called.
                    // Extract call metadata from the PendingIntent's embedded Bundle
                    // so accepted events include caller info on cold-start.
                    val extraBundle = intent.getBundleExtra("callExtra")
                    val extra = mutableMapOf<String, Any>()
                    extraBundle?.keySet()?.forEach { key ->
                        extra[key] = extraBundle.getString(key) ?: ""
                    }
                    Log.d(TAG, "onReceive: Plugin null, persisting accept to PendingCallStore (extra keys: ${extra.keys})")
                    PendingCallStore(context).savePendingAccept(callId, extra)
                }

                // CRITICAL: Bring the app to the foreground after accepting.
                // Without this, the user taps Accept but the app stays in
                // the background — the call screen is never visible.
                // This works on Android 10+ because the PendingIntent was
                // triggered by a user-tapped notification action, which
                // grants a temporary background activity start exemption.
                bringAppToForeground(context, callId)
            }
            ACTION_DECLINE -> {
                Log.d(TAG, "onReceive: Decline action for callId=$callId")
                if (plugin != null) {
                    // Extract caller metadata from the notification PendingIntent
                    // as fallback when callStateManager doesn't have the call
                    // (e.g., call was shown by a background FCM engine instance).
                    val declineExtraBundle = intent.getBundleExtra("callExtra")
                    val declineExtra = if (declineExtraBundle != null) {
                        mutableMapOf<String, Any>().also { map ->
                            declineExtraBundle.keySet().forEach { key ->
                                map[key] = declineExtraBundle.getString(key) ?: ""
                            }
                        }
                    } else null
                    plugin.onCallDeclined(callId, declineExtra)
                } else {
                    // App killed and plugin is null: cancel notification
                    // and persist decline for delivery on next app start.
                    Log.d(TAG, "onReceive: Plugin null, persisting decline and cancelling notification")
                    androidx.core.app.NotificationManagerCompat.from(context)
                        .cancel(callId.hashCode())

                    // Persist decline so the reject API is called when
                    // the main engine starts and configure() runs.
                    val declineExtraForStore = intent.getBundleExtra("callExtra")
                    val declineMap = mutableMapOf<String, Any>()
                    declineExtraForStore?.keySet()?.forEach { key ->
                        declineMap[key] = declineExtraForStore.getString(key) ?: ""
                    }
                    PendingCallStore(context).savePendingDecline(callId, declineMap)
                }
            }
            ACTION_END -> {
                Log.d(TAG, "onReceive: End action for callId=$callId")
                if (plugin != null) {
                    val endExtraBundle = intent.getBundleExtra("callExtra")
                    val endExtra = if (endExtraBundle != null) {
                        mutableMapOf<String, Any>().also { map ->
                            endExtraBundle.keySet().forEach { key ->
                                map[key] = endExtraBundle.getString(key) ?: ""
                            }
                        }
                    } else null
                    plugin.onCallDeclined(callId, endExtra)
                } else {
                    Log.d(TAG, "onReceive: Plugin null, persisting end/decline and cancelling notification")
                    androidx.core.app.NotificationManagerCompat.from(context)
                        .cancel(callId.hashCode())
                    PendingCallStore(context).savePendingDecline(callId, emptyMap<String, Any>())
                }
            }
            else -> {
                Log.w(TAG, "onReceive: Unknown action ${intent.action}")
            }
        }
    }

    /**
     * Brings the app's main Activity to the foreground after the user
     * taps Accept on the incoming call notification.
     *
     * Uses the package manager's launch intent with [FLAG_ACTIVITY_NEW_TASK],
     * [FLAG_ACTIVITY_SINGLE_TOP], and [FLAG_ACTIVITY_REORDER_TO_FRONT] to
     * resume the existing Activity (or start a new one if killed).
     *
     * IMPORTANT: Does NOT add "call_accepted" action to avoid triggering
     * onNewIntent → onCallAccepted a second time (the event was already
     * sent by the plugin.onCallAccepted() call above).
     *
     * On Android 10+ (API 29+), this works because the call originates
     * from a user-tapped notification PendingIntent, which grants
     * a temporary exemption from background activity start restrictions.
     */
    private fun bringAppToForeground(context: Context, callId: String) {
        try {
            val launchIntent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)

            if (launchIntent != null) {
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
                // DO NOT add "call_accepted" action — onCallAccepted was
                // already called above and sent the event to Dart.
                // Adding it would cause onNewIntent to fire another
                // onCallAccepted → duplicate event.
                context.startActivity(launchIntent)
                Log.d(TAG, "bringAppToForeground: Launched activity for callId=$callId")
            } else {
                Log.w(TAG, "bringAppToForeground: Launch intent null for ${context.packageName}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "bringAppToForeground: Failed to bring app to foreground", e)
        }
    }
}
