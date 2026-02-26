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
                if (plugin != null) {
                    plugin.onCallAccepted(callId)
                } else {
                    // App killed: plugin not alive yet.
                    // Persist directly so deliverPendingEvents() picks it up
                    // after Flutter engine starts and configure() is called.
                    Log.d(TAG, "onReceive: Plugin null, persisting accept to PendingCallStore")
                    PendingCallStore(context).savePendingAccept(callId, emptyMap<String, Any>())
                }
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
}
