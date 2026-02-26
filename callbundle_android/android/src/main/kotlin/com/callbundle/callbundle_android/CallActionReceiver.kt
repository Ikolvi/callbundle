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
                plugin?.onCallAccepted(callId)
            }
            ACTION_DECLINE -> {
                Log.d(TAG, "onReceive: Decline action for callId=$callId")
                plugin?.onCallDeclined(callId)
            }
            ACTION_END -> {
                Log.d(TAG, "onReceive: End action for callId=$callId")
                plugin?.onCallDeclined(callId)
            }
            else -> {
                Log.w(TAG, "onReceive: Unknown action ${intent.action}")
            }
        }
    }
}
