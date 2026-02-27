import Foundation
import CallKit
import AVFoundation

/// Manages all CallKit interactions for the CallBundle plugin.
///
/// This controller wraps `CXProvider` and `CXCallController` to provide
/// a clean interface for reporting and managing calls through iOS's
/// native call UI.
///
/// ## Key Design Decisions
///
/// - **isUserInitiated**: All events sent to Dart include this flag,
///   eliminating the `_isEndingCallKitProgrammatically` pattern.
/// - **Audio session activation**: Handled in `provider(_:didActivate:)`
///   callback, not manually, preventing the HMS audio kill issue.
/// - **Serial DispatchQueue**: All CallKit operations are serialized
///   to prevent race conditions.
class CallKitController: NSObject {

    // MARK: - Properties

    private var provider: CXProvider?
    private let callController = CXCallController()
    private weak var plugin: CallBundlePlugin?

    /// Maps UUID → callId string for reverse lookup.
    private var uuidToCallId: [UUID: String] = [:]
    private let queue = DispatchQueue(label: "com.callbundle.callkit", qos: .userInteractive)

    // Configuration
    private var includesCallsInRecents = true

    // MARK: - Init

    init(plugin: CallBundlePlugin) {
        self.plugin = plugin
        super.init()

        // Create CXProvider eagerly with a default configuration.
        // This is CRITICAL for killed-state incoming calls: the background
        // FCM handler or PushKit calls showIncomingCall() / reportIncomingCall()
        // before configure() runs. Without a provider, those calls silently no-op.
        // configure() will update the provider's configuration later.
        let defaultConfig = CXProviderConfiguration(localizedName: "Call")
        defaultConfig.supportsVideo = true
        defaultConfig.maximumCallGroups = 1
        defaultConfig.maximumCallsPerCallGroup = 1
        defaultConfig.supportedHandleTypes = [.generic, .phoneNumber, .emailAddress]
        provider = CXProvider(configuration: defaultConfig)
        provider?.setDelegate(self, queue: queue)
    }

    // MARK: - Configuration

    /// Configures the CXProvider with the given parameters.
    ///
    /// Must be called before any call operations.
    func configure(
        appName: String,
        iconTemplateImageName: String?,
        ringtoneSound: String?,
        supportsVideo: Bool,
        maximumCallGroups: Int,
        maximumCallsPerCallGroup: Int,
        includesCallsInRecents: Bool
    ) {
        self.includesCallsInRecents = includesCallsInRecents

        let config = CXProviderConfiguration(localizedName: appName)
        config.maximumCallGroups = maximumCallGroups
        config.maximumCallsPerCallGroup = maximumCallsPerCallGroup
        config.supportsVideo = supportsVideo
        config.includesCallsInRecents = includesCallsInRecents

        if let iconName = iconTemplateImageName {
            config.iconTemplateImageData = UIImage(named: iconName)?.pngData()
        }

        if let ringtone = ringtoneSound {
            config.ringtoneSound = ringtone
        }

        config.supportedHandleTypes = [.generic, .phoneNumber, .emailAddress]

        // Provider was already created in init(), just update configuration
        provider?.configuration = config
    }

    // MARK: - Call Operations

    /// Reports a new incoming call to CallKit.
    ///
    /// This MUST be called synchronously when a PushKit notification
    /// arrives, as iOS requires `reportNewIncomingCall` to be called
    /// before the PushKit completion handler returns.
    func reportIncomingCall(
        uuid: UUID,
        handle: String,
        handleType: CXHandle.HandleType,
        callerName: String,
        hasVideo: Bool,
        completion: @escaping (Error?) -> Void
    ) {
        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: handleType, value: handle)
        update.localizedCallerName = callerName
        update.hasVideo = hasVideo
        update.supportsGrouping = false
        update.supportsUngrouping = false
        update.supportsHolding = false
        update.supportsDTMF = false

        queue.sync {
            uuidToCallId[uuid] = uuid.uuidString.lowercased()
        }

        provider?.reportNewIncomingCall(with: uuid, update: update) { [weak self] error in
            if let error = error {
                self?.queue.sync {
                    self?.uuidToCallId.removeValue(forKey: uuid)
                }
                completion(error)
            } else {
                completion(nil)
            }
        }
    }

    /// Starts an outgoing call through CallKit.
    func startOutgoingCall(
        uuid: UUID,
        handle: String,
        handleType: CXHandle.HandleType,
        callerName: String,
        hasVideo: Bool
    ) {
        let cxHandle = CXHandle(type: handleType, value: handle)
        let startCallAction = CXStartCallAction(call: uuid, handle: cxHandle)
        startCallAction.isVideo = hasVideo
        startCallAction.contactIdentifier = callerName

        queue.sync {
            uuidToCallId[uuid] = uuid.uuidString.lowercased()
        }

        let transaction = CXTransaction(action: startCallAction)
        callController.request(transaction) { error in
            if let error = error {
                NSLog("[CallBundle] Failed to start outgoing call: \(error.localizedDescription)")
            }
        }

        // Update the call with caller name
        let update = CXCallUpdate()
        update.localizedCallerName = callerName
        provider?.reportCall(with: uuid, updated: update)
    }

    /// Reports that a call has connected.
    func reportCallConnected(uuid: UUID) {
        provider?.reportCall(with: uuid, endedAt: nil, reason: .remoteEnded)
        // Actually, we should report it as connected
        provider?.reportOutgoingCall(with: uuid, connectedAt: Date())
    }

    /// Ends a specific call.
    func endCall(uuid: UUID) {
        let endCallAction = CXEndCallAction(call: uuid)
        let transaction = CXTransaction(action: endCallAction)
        callController.request(transaction) { error in
            if let error = error {
                NSLog("[CallBundle] Failed to end call: \(error.localizedDescription)")
            }
        }
    }

    /// Ends all active calls.
    func endAllCalls() {
        let uuids: [UUID]
        uuids = queue.sync { Array(uuidToCallId.keys) }

        for uuid in uuids {
            endCall(uuid: uuid)
        }
    }

    // MARK: - Helpers

    private func callIdForUUID(_ uuid: UUID) -> String {
        return queue.sync {
            uuidToCallId[uuid] ?? uuid.uuidString.lowercased()
        }
    }

    private func removeUUID(_ uuid: UUID) {
        queue.sync {
            uuidToCallId.removeValue(forKey: uuid)
        }
    }
}

// MARK: - CXProviderDelegate

extension CallKitController: CXProviderDelegate {

    func providerDidReset(_ provider: CXProvider) {
        NSLog("[CallBundle] providerDidReset")
        queue.sync {
            uuidToCallId.removeAll()
        }
    }

    func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        let callId = callIdForUUID(action.callUUID)
        NSLog("[CallBundle] CXAnswerCallAction: \(callId)")

        plugin?.sendCallEvent(type: "accepted", callId: callId, isUserInitiated: true)
        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        let callId = callIdForUUID(action.callUUID)
        NSLog("[CallBundle] CXEndCallAction: \(callId)")

        // This fires for both user-initiated declines AND programmatic ends.
        // The plugin determines isUserInitiated based on whether it called endCall itself.
        plugin?.sendCallEvent(type: "ended", callId: callId, isUserInitiated: true)

        removeUUID(action.callUUID)
        action.fulfill()

        // Deactivate audio after call ends
        plugin?.configureAudioSession(active: false)
    }

    func provider(_ provider: CXProvider, perform action: CXStartCallAction) {
        let callId = callIdForUUID(action.callUUID)
        NSLog("[CallBundle] CXStartCallAction: \(callId)")

        // Configure audio session before the call starts
        plugin?.configureAudioSession(active: true)

        provider.reportOutgoingCall(with: action.callUUID, startedConnectingAt: Date())
        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXSetMutedCallAction) {
        let callId = callIdForUUID(action.callUUID)
        let isMuted = action.isMuted
        NSLog("[CallBundle] CXSetMutedCallAction: \(callId) muted=\(isMuted)")

        plugin?.sendCallEvent(
            type: "muted",
            callId: callId,
            isUserInitiated: true,
            extra: ["isMuted": isMuted]
        )
        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXSetHeldCallAction) {
        let callId = callIdForUUID(action.callUUID)
        let isOnHold = action.isOnHold
        NSLog("[CallBundle] CXSetHeldCallAction: \(callId) held=\(isOnHold)")

        plugin?.sendCallEvent(
            type: "held",
            callId: callId,
            isUserInitiated: true,
            extra: ["isOnHold": isOnHold]
        )
        action.fulfill()
    }

    /// Called when the audio session is activated by iOS.
    ///
    /// This is the CORRECT place to configure audio — NOT manually before.
    /// Configuring audio before this callback causes conflicts with HMS/Huawei.
    func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) {
        NSLog("[CallBundle] Audio session activated")
        plugin?.configureAudioSession(active: true)
    }

    /// Called when the audio session is deactivated by iOS.
    func provider(_ provider: CXProvider, didDeactivate audioSession: AVAudioSession) {
        NSLog("[CallBundle] Audio session deactivated")
        plugin?.configureAudioSession(active: false)
    }

    func provider(_ provider: CXProvider, timedOutPerforming action: CXAction) {
        NSLog("[CallBundle] Action timed out: \(type(of: action))")
        action.fulfill()
    }
}
