package com.callbundle.callbundle_android

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject

/**
 * Persistent storage for cold-start call events.
 *
 * When the user taps "Accept" or "Decline" while the app is killed,
 * the native side processes the action (notification receiver), but
 * the Dart side hasn't initialized yet. The event is stored here and
 * delivered deterministically after [CallBundlePlugin.configure] is called.
 *
 * ## Deterministic Handshake Protocol
 *
 * ```
 * 1. User taps Accept/Decline on notification (app killed)
 * 2. BroadcastReceiver fires → onCallAccepted/onCallDeclined
 * 3. PendingCallStore.savePendingAccept/savePendingDecline(callId, extra)
 * 4. App launches, Flutter engine starts
 * 5. Dart calls CallBundle.configure()
 * 6. Plugin reads PendingCallStore
 * 7. Events delivered to Dart via MethodChannel
 * 8. Store cleared (single consumption)
 * ```
 *
 * ## Why Decline Needs Storage Too
 *
 * In killed state, the background FCM engine (Instance B) shows the
 * notification and may still be alive when Decline is tapped. But
 * Instance B's Dart isolate has no [IncomingCallHandlerService] listener
 * on the event stream. Events sent through B's MethodChannel are added
 * to a broadcast stream with no subscribers → silently dropped.
 *
 * By storing the decline event, we ensure the reject API call is made
 * when the main engine starts and [configure] delivers pending events.
 */
class PendingCallStore(context: Context) {

    companion object {
        private const val TAG = "PendingCallStore"
        private const val PREFS_NAME = "callbundle_pending_calls"

        // Accept event keys
        private const val KEY_ACCEPT_CALL_ID = "pending_accept_call_id"
        private const val KEY_ACCEPT_EXTRA = "pending_accept_extra"
        private const val KEY_ACCEPT_TIMESTAMP = "pending_accept_timestamp"

        // Decline event keys
        private const val KEY_DECLINE_CALL_ID = "pending_decline_call_id"
        private const val KEY_DECLINE_EXTRA = "pending_decline_extra"
        private const val KEY_DECLINE_TIMESTAMP = "pending_decline_timestamp"

        /** Events older than this are considered expired. */
        private const val TTL_MS = 60_000L // 60 seconds
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ==========================================
    // ACCEPT EVENTS
    // ==========================================

    /**
     * Saves a pending accept event for later delivery.
     *
     * Uses [SharedPreferences.Editor.commit] for synchronous write,
     * ensuring the data is persisted before the process might be killed.
     */
    fun savePendingAccept(callId: String, extra: Map<*, *>) {
        val editor = prefs.edit()
        editor.putString(KEY_ACCEPT_CALL_ID, callId)
        editor.putString(KEY_ACCEPT_EXTRA, mapToString(extra))
        editor.putLong(KEY_ACCEPT_TIMESTAMP, System.currentTimeMillis())
        editor.commit() // Synchronous — critical for cold-start reliability
        Log.d(TAG, "savePendingAccept: Saved callId=$callId")
    }

    /**
     * Reads and clears the pending accept event (single consumption).
     *
     * Returns `null` if no pending event exists or if the event has expired.
     */
    fun consumePendingAccept(): PendingAcceptEvent? {
        val callId = prefs.getString(KEY_ACCEPT_CALL_ID, null) ?: return null
        val timestamp = prefs.getLong(KEY_ACCEPT_TIMESTAMP, 0L)
        val extraStr = prefs.getString(KEY_ACCEPT_EXTRA, null)

        // Clear accept keys (single consumption)
        clearAcceptKeys()

        // Check TTL
        if (isExpired(timestamp)) {
            Log.d(TAG, "consumePendingAccept: Event expired for callId=$callId")
            return null
        }

        val extra = stringToMap(extraStr)
        Log.d(TAG, "consumePendingAccept: Consumed callId=$callId")
        return PendingAcceptEvent(callId = callId, extra = extra)
    }

    // ==========================================
    // DECLINE EVENTS
    // ==========================================

    /**
     * Saves a pending decline event for later delivery.
     *
     * Used when the user declines from the notification while the app
     * is killed. The background engine can cancel the notification and
     * stop the ringtone, but cannot reach the main Dart isolate to call
     * the reject API. This event is delivered when [deliverPendingEvents]
     * runs after [configure].
     */
    fun savePendingDecline(callId: String, extra: Map<*, *>) {
        val editor = prefs.edit()
        editor.putString(KEY_DECLINE_CALL_ID, callId)
        editor.putString(KEY_DECLINE_EXTRA, mapToString(extra))
        editor.putLong(KEY_DECLINE_TIMESTAMP, System.currentTimeMillis())
        editor.commit()
        Log.d(TAG, "savePendingDecline: Saved callId=$callId")
    }

    /**
     * Reads and clears the pending decline event (single consumption).
     *
     * Returns `null` if no pending event exists or if the event has expired.
     */
    fun consumePendingDecline(): PendingDeclineEvent? {
        val callId = prefs.getString(KEY_DECLINE_CALL_ID, null) ?: return null
        val timestamp = prefs.getLong(KEY_DECLINE_TIMESTAMP, 0L)
        val extraStr = prefs.getString(KEY_DECLINE_EXTRA, null)

        // Clear decline keys (single consumption)
        clearDeclineKeys()

        // Check TTL
        if (isExpired(timestamp)) {
            Log.d(TAG, "consumePendingDecline: Event expired for callId=$callId")
            return null
        }

        val extra = stringToMap(extraStr)
        Log.d(TAG, "consumePendingDecline: Consumed callId=$callId")
        return PendingDeclineEvent(callId = callId, extra = extra)
    }

    // ==========================================
    // UTILITIES
    // ==========================================

    /**
     * Whether a given timestamp has exceeded the TTL.
     */
    private fun isExpired(timestamp: Long): Boolean {
        return System.currentTimeMillis() - timestamp > TTL_MS
    }

    /**
     * Clears accept-related keys only.
     */
    private fun clearAcceptKeys() {
        prefs.edit()
            .remove(KEY_ACCEPT_CALL_ID)
            .remove(KEY_ACCEPT_EXTRA)
            .remove(KEY_ACCEPT_TIMESTAMP)
            .commit()
    }

    /**
     * Clears decline-related keys only.
     */
    private fun clearDeclineKeys() {
        prefs.edit()
            .remove(KEY_DECLINE_CALL_ID)
            .remove(KEY_DECLINE_EXTRA)
            .remove(KEY_DECLINE_TIMESTAMP)
            .commit()
    }

    /**
     * JSON-based map serialization. Handles values containing any
     * characters (URLs with = and |, etc.) safely.
     */
    private fun mapToString(map: Map<*, *>): String {
        return try {
            val json = JSONObject()
            for ((key, value) in map) {
                json.put(key?.toString() ?: continue, value?.toString() ?: "")
            }
            json.toString()
        } catch (e: Exception) {
            Log.e(TAG, "mapToString: JSON serialization failed, using fallback", e)
            // Fallback: simple key=value pairs (legacy format)
            map.entries.joinToString("|") { "${it.key}=${it.value}" }
        }
    }

    /**
     * Reverse of [mapToString]. Supports both JSON format (new) and
     * legacy key=value|key=value format for backward compatibility.
     */
    private fun stringToMap(str: String?): Map<String, String> {
        if (str.isNullOrEmpty()) return emptyMap()
        return try {
            // Try JSON format first (new)
            if (str.startsWith("{")) {
                val json = JSONObject(str)
                val result = mutableMapOf<String, String>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    result[key] = json.optString(key, "")
                }
                result
            } else {
                // Legacy format: key=value|key=value
                str.split("|").mapNotNull { entry ->
                    val parts = entry.split("=", limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else null
                }.toMap()
            }
        } catch (e: Exception) {
            Log.e(TAG, "stringToMap: Deserialization failed for: $str", e)
            emptyMap()
        }
    }
}

/**
 * Data class for a pending accept event.
 */
data class PendingAcceptEvent(
    val callId: String,
    val extra: Map<String, String>
)

/**
 * Data class for a pending decline event.
 */
data class PendingDeclineEvent(
    val callId: String,
    val extra: Map<String, String>
)
