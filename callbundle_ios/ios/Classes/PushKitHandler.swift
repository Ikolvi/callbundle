import Foundation
import PushKit

/// Handles PushKit VoIP push registration and token management.
///
/// ## Key Design Decision
///
/// PushKit is handled INSIDE the plugin, not in AppDelegate.
/// This eliminates the requirement for app developers to write
/// PushKit boilerplate in their AppDelegate.
///
/// ## Important iOS Requirements
///
/// - PushKit tokens MUST be registered on every app launch.
/// - When a VoIP push arrives, `reportNewIncomingCall` MUST be called
///   synchronously before the completion handler returns, or iOS will
///   terminate the app.
class PushKitHandler: NSObject {

    // MARK: - Properties

    private weak var plugin: CallBundlePlugin?
    private var voipRegistry: PKPushRegistry?

    /// The current VoIP push token as a hex string.
    private(set) var currentVoipToken: String?

    // MARK: - Init

    init(plugin: CallBundlePlugin) {
        self.plugin = plugin
        super.init()
    }

    // MARK: - Registration

    /// Registers for VoIP push notifications.
    ///
    /// Must be called after `configure()` to ensure CallKit is ready.
    func registerForVoipPush() {
        let registry = PKPushRegistry(queue: DispatchQueue.main)
        registry.delegate = self
        registry.desiredPushTypes = [.voIP]
        self.voipRegistry = registry
    }
}

// MARK: - PKPushRegistryDelegate

extension PushKitHandler: PKPushRegistryDelegate {

    func pushRegistry(
        _ registry: PKPushRegistry,
        didUpdate pushCredentials: PKPushCredentials,
        for type: PKPushType
    ) {
        guard type == .voIP else { return }

        let token = pushCredentials.token
            .map { String(format: "%02x", $0) }
            .joined()

        currentVoipToken = token
        NSLog("[CallBundle] VoIP token updated: \(token.prefix(8))...")

        plugin?.sendVoipTokenUpdate(token: token)
    }

    func pushRegistry(
        _ registry: PKPushRegistry,
        didReceiveIncomingPushWith payload: PKPushPayload,
        for type: PKPushType,
        completion: @escaping () -> Void
    ) {
        guard type == .voIP else {
            completion()
            return
        }

        let data = payload.dictionaryPayload
        NSLog("[CallBundle] VoIP push received")

        // Extract call data from push payload
        let callId = data["callId"] as? String ?? UUID().uuidString
        let callerName = data["callerName"] as? String
            ?? data["caller_name"] as? String
            ?? "Unknown"
        let handle = data["handle"] as? String
            ?? data["phone"] as? String
            ?? ""
        let hasVideo = data["hasVideo"] as? Bool
            ?? data["has_video"] as? Bool
            ?? false

        // CRITICAL: Must report incoming call SYNCHRONOUSLY here.
        // iOS will terminate the app if reportNewIncomingCall is not
        // called before this completion handler returns.
        guard let callKitController = (CallBundlePlugin.shared as? CallBundlePlugin)?.callKitControllerForPush else {
            // Fallback: report via the plugin's controller
            plugin?.sendCallEvent(
                type: "incoming",
                callId: callId,
                isUserInitiated: false,
                extra: [
                    "callerName": callerName,
                    "handle": handle,
                    "hasVideo": hasVideo,
                ]
            )
            completion()
            return
        }

        let uuid = uuidFromString(callId)
        callKitController.reportIncomingCall(
            uuid: uuid,
            handle: handle,
            handleType: .generic,
            callerName: callerName,
            hasVideo: hasVideo
        ) { [weak self] error in
            if let error = error {
                NSLog("[CallBundle] Failed to report incoming VoIP call: \(error.localizedDescription)")
            } else {
                self?.plugin?.sendCallEvent(
                    type: "incoming",
                    callId: callId,
                    isUserInitiated: false,
                    extra: [
                        "callerName": callerName,
                        "handle": handle,
                        "hasVideo": hasVideo,
                    ]
                )
            }
            completion()
        }
    }

    func pushRegistry(
        _ registry: PKPushRegistry,
        didInvalidatePushTokenFor type: PKPushType
    ) {
        guard type == .voIP else { return }
        NSLog("[CallBundle] VoIP token invalidated")
        currentVoipToken = nil
    }

    // MARK: - Utilities

    private func uuidFromString(_ string: String) -> UUID {
        if let uuid = UUID(uuidString: string) {
            return uuid
        }
        let data = string.data(using: .utf8)!
        var hash = [UInt8](repeating: 0, count: 16)
        data.withUnsafeBytes { bytes in
            for (i, byte) in bytes.enumerated() {
                hash[i % 16] ^= byte
            }
        }
        hash[6] = (hash[6] & 0x0F) | 0x40
        hash[8] = (hash[8] & 0x3F) | 0x80
        return NSUUID(uuidBytes: hash) as UUID
    }
}
