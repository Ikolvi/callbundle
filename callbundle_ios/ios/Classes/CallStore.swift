import Foundation

/// Thread-safe storage for active call state on iOS.
///
/// Uses a serial `DispatchQueue` for all operations to ensure
/// thread safety when accessed from multiple queues (main thread,
/// CallKit delegate queue, PushKit queue).
///
/// Also provides `PendingCallStore` functionality for cold-start
/// event persistence, matching the Android implementation.
class CallStore {

    // MARK: - Properties

    private var activeCalls: [String: CallInfo] = [:]
    private let queue = DispatchQueue(label: "com.callbundle.callstore", qos: .userInitiated)
    private let defaults = UserDefaults.standard

    private static let pendingAcceptKey = "com.callbundle.pending_accept"
    private static let pendingAcceptTimestampKey = "com.callbundle.pending_accept_ts"
    private static let pendingTTL: TimeInterval = 60 // 60 seconds

    // MARK: - Active Call Management

    /// Adds a call to the active call store.
    func addCall(callId: String, callerName: String, handle: String) {
        queue.sync {
            activeCalls[callId] = CallInfo(
                callId: callId,
                callerName: callerName,
                handle: handle,
                state: "incoming",
                startedAt: Date()
            )
        }
    }

    /// Removes a call from the active call store.
    func removeCall(callId: String) {
        queue.sync {
            activeCalls.removeValue(forKey: callId)
        }
    }

    /// Removes all active calls.
    func removeAllCalls() {
        queue.sync {
            activeCalls.removeAll()
        }
    }

    /// Updates the state of an active call.
    func updateCallState(callId: String, state: String) {
        queue.sync {
            activeCalls[callId]?.state = state
        }
    }

    /// Returns all active calls as a list of dictionaries.
    func getAllCalls() -> [[String: Any]] {
        return queue.sync {
            activeCalls.values.map { call in
                [
                    "callId": call.callId,
                    "callerName": call.callerName,
                    "handle": call.handle,
                    "state": call.state,
                    "startedAt": Int64(call.startedAt.timeIntervalSince1970 * 1000),
                ]
            }
        }
    }

    // MARK: - Pending Accept (Cold-Start)

    /// Saves a pending accept event for cold-start delivery.
    ///
    /// When a user accepts a call from a killed state, the Flutter
    /// engine may not be ready yet. This persists the accept event
    /// to UserDefaults (synchronous) so it can be delivered when
    /// the engine is ready.
    func savePendingAccept(callId: String) {
        defaults.set(callId, forKey: CallStore.pendingAcceptKey)
        defaults.set(Date().timeIntervalSince1970, forKey: CallStore.pendingAcceptTimestampKey)
        defaults.synchronize()
        NSLog("[CallBundle] Saved pending accept: \(callId)")
    }

    /// Consumes the pending accept event (single consumption).
    ///
    /// Returns the callId if there is a valid pending accept within
    /// the TTL window, then clears it from storage.
    func consumePendingAccept() -> String? {
        guard let callId = defaults.string(forKey: CallStore.pendingAcceptKey) else {
            return nil
        }

        let timestamp = defaults.double(forKey: CallStore.pendingAcceptTimestampKey)
        let elapsed = Date().timeIntervalSince1970 - timestamp

        // Clear from storage (single consumption)
        defaults.removeObject(forKey: CallStore.pendingAcceptKey)
        defaults.removeObject(forKey: CallStore.pendingAcceptTimestampKey)
        defaults.synchronize()

        // Check TTL
        guard elapsed < CallStore.pendingTTL else {
            NSLog("[CallBundle] Pending accept expired: \(callId) (elapsed: \(elapsed)s)")
            return nil
        }

        NSLog("[CallBundle] Consumed pending accept: \(callId)")
        return callId
    }
}

// MARK: - CallInfo

/// Represents an active call's metadata.
struct CallInfo {
    let callId: String
    let callerName: String
    let handle: String
    var state: String
    let startedAt: Date
}
