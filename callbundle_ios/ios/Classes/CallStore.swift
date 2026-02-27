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
    private static let pendingAcceptExtraKey = "com.callbundle.pending_accept_extra"
    private static let pendingTTL: TimeInterval = 60 // 60 seconds

    // MARK: - Active Call Management

    /// Adds a call to the active call store.
    func addCall(callId: String, callerName: String, handle: String, extra: [String: Any]? = nil) {
        queue.sync {
            activeCalls[callId] = CallInfo(
                callId: callId,
                callerName: callerName,
                handle: handle,
                state: "incoming",
                startedAt: Date(),
                extra: extra ?? [:]
            )
            NSLog("[CallBundle] CallStore.addCall: key='\(callId)', extraCount=\(extra?.count ?? 0), totalCalls=\(activeCalls.count)")
        }
    }

    /// Adds a call if it doesn't exist, or updates (merges) extra data if it does.
    ///
    /// This handles the PushKit → FCM race condition:
    /// - PushKit stores a basic entry (callerName, handle only).
    /// - FCM/Dart later calls handleShowIncomingCall with full extra (8 keys).
    /// - This method merges the richer data into the existing entry.
    func addOrUpdateCall(callId: String, callerName: String, handle: String, extra: [String: Any]? = nil) {
        queue.sync {
            if var existing = activeCalls[callId] {
                // Merge new extra into existing extra (new values override)
                if let newExtra = extra, !newExtra.isEmpty {
                    for (key, value) in newExtra {
                        existing.extra[key] = value
                    }
                    activeCalls[callId] = existing
                }
                NSLog("[CallBundle] CallStore.addOrUpdateCall: UPDATED key='\(callId)', mergedExtraCount=\(existing.extra.count)")
            } else {
                activeCalls[callId] = CallInfo(
                    callId: callId,
                    callerName: callerName,
                    handle: handle,
                    state: "incoming",
                    startedAt: Date(),
                    extra: extra ?? [:]
                )
                NSLog("[CallBundle] CallStore.addOrUpdateCall: NEW key='\(callId)', extraCount=\(extra?.count ?? 0)")
            }
        }
    }

    /// Returns a specific call's metadata, or nil if not found.
    func getCall(callId: String) -> CallInfo? {
        return queue.sync {
            let call = activeCalls[callId]
            NSLog("[CallBundle] CallStore.getCall: key='\(callId)', found=\(call != nil), extraCount=\(call?.extra.count ?? -1), storedKeys=\(Array(activeCalls.keys))")
            return call
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
                var dict: [String: Any] = [
                    "callId": call.callId,
                    "callerName": call.callerName,
                    "handle": call.handle,
                    "state": call.state,
                    "startedAt": Int64(call.startedAt.timeIntervalSince1970 * 1000),
                ]
                if !call.extra.isEmpty {
                    dict["extra"] = call.extra
                }
                return dict
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
    func savePendingAccept(callId: String, extra: [String: Any]? = nil) {
        defaults.set(callId, forKey: CallStore.pendingAcceptKey)
        defaults.set(Date().timeIntervalSince1970, forKey: CallStore.pendingAcceptTimestampKey)
        // Persist extra as a simple String→String dictionary (UserDefaults safe)
        if let extra = extra, !extra.isEmpty {
            let stringExtra = extra.reduce(into: [String: String]()) { result, pair in
                result[pair.key] = "\(pair.value)"
            }
            defaults.set(stringExtra, forKey: CallStore.pendingAcceptExtraKey)
        } else {
            defaults.removeObject(forKey: CallStore.pendingAcceptExtraKey)
        }
        defaults.synchronize()
        NSLog("[CallBundle] Saved pending accept: \(callId)")
    }

    /// Consumes the pending accept event (single consumption).
    ///
    /// Returns a tuple of (callId, extra) if there is a valid pending
    /// accept within the TTL window, then clears it from storage.
    func consumePendingAccept() -> (callId: String, extra: [String: Any])? {
        guard let callId = defaults.string(forKey: CallStore.pendingAcceptKey) else {
            return nil
        }

        let timestamp = defaults.double(forKey: CallStore.pendingAcceptTimestampKey)
        let elapsed = Date().timeIntervalSince1970 - timestamp
        let extra = defaults.dictionary(forKey: CallStore.pendingAcceptExtraKey) ?? [:]

        // Clear from storage (single consumption)
        defaults.removeObject(forKey: CallStore.pendingAcceptKey)
        defaults.removeObject(forKey: CallStore.pendingAcceptTimestampKey)
        defaults.removeObject(forKey: CallStore.pendingAcceptExtraKey)
        defaults.synchronize()

        // Check TTL
        guard elapsed < CallStore.pendingTTL else {
            NSLog("[CallBundle] Pending accept expired: \(callId) (elapsed: \(elapsed)s)")
            return nil
        }

        NSLog("[CallBundle] Consumed pending accept: \(callId)")
        return (callId: callId, extra: extra)
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
    var extra: [String: Any]
}
