package com.callbundle.callbundle_android

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Persistent storage for cold-start call events.
 *
 * When the user taps "Accept" while the app is killed, the native
 * side processes the action (ConnectionService or notification
 * receiver), but the Dart side hasn't initialized yet. The event
 * is stored here and delivered deterministically after
 * [CallBundlePlugin.configure] is called.
 *
 * ## Deterministic Handshake Protocol
 *
 * ```
 * 1. User taps Accept on notification (app killed)
 * 2. ConnectionService.onAnswer() or BroadcastReceiver fires
 * 3. PendingCallStore.savePendingAccept(callId, extra)
 * 4. App launches, Flutter engine starts
 * 5. Dart calls CallBundle.configure()
 * 6. Plugin reads PendingCallStore
 * 7. Event delivered to Dart via MethodChannel
 * 8. Store cleared (single consumption)
 * ```
 *
 * **Key improvement:** Eliminates the hardcoded 3-second delay
 * used in the previous plugin. Events are delivered as soon as
 * the Dart side is ready, regardless of device speed.
 */
class PendingCallStore(context: Context) {

    companion object {
        private const val TAG = "PendingCallStore"
        private const val PREFS_NAME = "callbundle_pending_calls"
        private const val KEY_CALL_ID = "pending_call_id"
        private const val KEY_EXTRA = "pending_call_extra"
        private const val KEY_TIMESTAMP = "pending_call_timestamp"

        /** Events older than this are considered expired. */
        private const val TTL_MS = 60_000L // 60 seconds
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Saves a pending accept event for later delivery.
     *
     * Uses [SharedPreferences.Editor.commit] for synchronous write,
     * ensuring the data is persisted before the process might be killed.
     */
    fun savePendingAccept(callId: String, extra: Map<*, *>) {
        val editor = prefs.edit()
        editor.putString(KEY_CALL_ID, callId)
        editor.putString(KEY_EXTRA, mapToString(extra))
        editor.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
        editor.commit() // Synchronous — critical for cold-start reliability
        Log.d(TAG, "savePendingAccept: Saved callId=$callId")
    }

    /**
     * Reads and clears the pending accept event (single consumption).
     *
     * Returns `null` if no pending event exists or if the event has expired.
     */
    fun consumePendingAccept(): PendingAcceptEvent? {
        val callId = prefs.getString(KEY_CALL_ID, null) ?: return null
        val timestamp = prefs.getLong(KEY_TIMESTAMP, 0L)
        val extraStr = prefs.getString(KEY_EXTRA, null)

        // Clear immediately (single consumption)
        clearPending()

        // Check TTL
        if (isExpired(timestamp)) {
            Log.d(TAG, "consumePendingAccept: Event expired for callId=$callId")
            return null
        }

        val extra = stringToMap(extraStr)
        Log.d(TAG, "consumePendingAccept: Consumed callId=$callId")
        return PendingAcceptEvent(callId = callId, extra = extra)
    }

    /**
     * Whether a given timestamp has exceeded the TTL.
     */
    private fun isExpired(timestamp: Long): Boolean {
        return System.currentTimeMillis() - timestamp > TTL_MS
    }

    /**
     * Clears any pending data.
     */
    private fun clearPending() {
        prefs.edit().clear().commit()
    }

    /**
     * Simple map-to-string serialization using key=value pairs.
     * For production, consider using JSON serialization.
     */
    private fun mapToString(map: Map<*, *>): String {
        return map.entries.joinToString("|") { "${it.key}=${it.value}" }
    }

    /**
     * Reverse of [mapToString].
     */
    private fun stringToMap(str: String?): Map<String, String> {
        if (str.isNullOrEmpty()) return emptyMap()
        return str.split("|").mapNotNull { entry ->
            val parts = entry.split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
    }
}

/**
 * Data class for a pending accept event.
 */
data class PendingAcceptEvent(
    val callId: String,
    val extra: Map<String, String>
)
