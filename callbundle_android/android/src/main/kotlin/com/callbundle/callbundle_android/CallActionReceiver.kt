package com.callbundle.callbundle_android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver for handling notification action buttons.
 *
 * Receives accept/decline/end intents from notification actions
 * and forwards them to [CallBundlePlugin] for event dispatch.
 *
 * This receiver must be registered in AndroidManifest.xml.
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
                    plugin.onCallDeclined(callId)
                } else {
                    // App killed: cancel the notification at minimum
                    Log.d(TAG, "onReceive: Plugin null, cancelling notification for declined call")
                    androidx.core.app.NotificationManagerCompat.from(context)
                        .cancel(callId.hashCode())
                }
            }
            ACTION_END -> {
                Log.d(TAG, "onReceive: End action for callId=$callId")
                if (plugin != null) {
                    plugin.onCallDeclined(callId)
                } else {
                    Log.d(TAG, "onReceive: Plugin null, cancelling notification for ended call")
                    androidx.core.app.NotificationManagerCompat.from(context)
                        .cancel(callId.hashCode())
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
                launchIntent.putExtra("callId", callId)
                launchIntent.putExtra("action", "call_accepted")
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
