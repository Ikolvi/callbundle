package com.callbundle.callbundle_android

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory call state storage.
 *
 * Manages the lifecycle of tracked calls using [ConcurrentHashMap]
 * for lock-free concurrent access from multiple threads
 * (MethodChannel handler, ConnectionService callbacks, etc.).
 */
class CallStateManager {

    private val calls = ConcurrentHashMap<String, CallInfo>()

    /**
     * Adds a call to the tracked state.
     *
     * If a call with the same ID already exists, it is replaced.
     */
    fun addCall(call: CallInfo) {
        calls[call.callId] = call
    }

    /**
     * Removes a call from tracked state.
     */
    fun removeCall(callId: String) {
        calls.remove(callId)
    }

    /**
     * Removes all tracked calls.
     */
    fun removeAllCalls() {
        calls.clear()
    }

    /**
     * Returns a specific call by ID, or null if not found.
     */
    fun getCall(callId: String): CallInfo? {
        return calls[callId]
    }

    /**
     * Returns a snapshot of all currently tracked calls.
     */
    fun getAllCalls(): List<CallInfo> {
        return calls.values.toList()
    }

    /**
     * Updates the state of a tracked call.
     *
     * @param callId The call to update.
     * @param state The new state (ringing, dialing, active, held, ended).
     * @param isAccepted Whether the call has been accepted.
     */
    fun updateCallState(
        callId: String,
        state: String,
        isAccepted: Boolean? = null
    ) {
        val existing = calls[callId] ?: return
        calls[callId] = existing.copy(
            state = state,
            isAccepted = isAccepted ?: existing.isAccepted
        )
    }
}

/**
 * Data class representing a tracked call's state.
 */
data class CallInfo(
    val callId: String,
    val callerName: String,
    val callType: Int,
    val state: String,
    val isAccepted: Boolean,
    val startTime: Long = System.currentTimeMillis(),
    val extra: Map<*, *> = emptyMap<String, Any>()
)
