package com.callbundle.callbundle_android

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.util.Log

/**
 * Android ConnectionService for TelecomManager integration.
 *
 * This service is registered in the plugin's AndroidManifest.xml and
 * handles incoming/outgoing call connections through Android's
 * Telecom framework.
 *
 * ## Design Decisions
 *
 * - Uses [CallBundlePlugin.instance] to communicate events back to Dart.
 * - Falls back gracefully if the plugin instance is null (background isolate
 *   may not have the plugin attached).
 * - Each call creates a new [CallBundleConnection] instance.
 */
class CallConnectionService : ConnectionService() {

    companion object {
        private const val TAG = "CallConnectionService"
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.d(TAG, "onCreateIncomingConnection")

        val extras = request?.extras
        val callId = extras?.getString("callId") ?: java.util.UUID.randomUUID().toString()

        val connection = CallBundleConnection(callId).apply {
            connectionProperties = Connection.PROPERTY_SELF_MANAGED
            setInitializing()
            setActive()
        }

        return connection
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.w(TAG, "onCreateIncomingConnectionFailed: Falling back to notification")
        // The notification path is already handled by NotificationHelper
        // This is the graceful degradation path for devices where
        // TelecomManager doesn't support our ConnectionService.
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.d(TAG, "onCreateOutgoingConnection")

        val extras = request?.extras
        val callId = extras?.getString("callId") ?: java.util.UUID.randomUUID().toString()

        val connection = CallBundleConnection(callId).apply {
            connectionProperties = Connection.PROPERTY_SELF_MANAGED
            setDialing()
        }

        return connection
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.w(TAG, "onCreateOutgoingConnectionFailed: Falling back to notification")
    }
}

/**
 * Represents a single call connection through Android's Telecom framework.
 *
 * Each instance tracks a single call and forwards user actions
 * (accept, reject, disconnect) to the plugin via [CallBundlePlugin].
 *
 * ## Key Improvement: isUserInitiated
 *
 * When the user taps accept/reject on the native UI, events are sent
 * with `isUserInitiated = true`. When the Dart side calls `endCall()`,
 * the plugin calls `programmaticEnd()` which sends with
 * `isUserInitiated = false`. This eliminates the
 * `_isEndingCallKitProgrammatically` flag pattern.
 */
class CallBundleConnection(
    private val callId: String
) : Connection() {

    companion object {
        private const val TAG = "CallBundleConnection"
    }

    override fun onAnswer() {
        Log.d(TAG, "onAnswer: callId=$callId")
        setActive()
        CallBundlePlugin.instance?.onCallAccepted(callId)
    }

    override fun onAnswer(videoState: Int) {
        Log.d(TAG, "onAnswer: callId=$callId videoState=$videoState")
        setActive()
        CallBundlePlugin.instance?.onCallAccepted(callId)
    }

    override fun onReject() {
        Log.d(TAG, "onReject: callId=$callId")
        setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.REJECTED))
        CallBundlePlugin.instance?.onCallDeclined(callId)
        destroy()
    }

    override fun onDisconnect() {
        Log.d(TAG, "onDisconnect: callId=$callId")
        setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.LOCAL))
        CallBundlePlugin.instance?.sendCallEvent(
            type = "ended",
            callId = callId,
            isUserInitiated = true
        )
        destroy()
    }

    override fun onAbort() {
        Log.d(TAG, "onAbort: callId=$callId")
        setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.UNKNOWN))
        CallBundlePlugin.instance?.sendCallEvent(
            type = "ended",
            callId = callId,
            isUserInitiated = false
        )
        destroy()
    }

    /**
     * Ends this connection programmatically (called from Dart's endCall()).
     *
     * The resulting event has `isUserInitiated = false`, eliminating
     * the need for the `_isEndingCallKitProgrammatically` flag.
     */
    fun programmaticEnd() {
        Log.d(TAG, "programmaticEnd: callId=$callId")
        setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.REMOTE))
        CallBundlePlugin.instance?.sendCallEvent(
            type = "ended",
            callId = callId,
            isUserInitiated = false
        )
        destroy()
    }
}
