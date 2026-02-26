import Flutter
import UIKit
import CallKit
import PushKit
import AVFoundation

/// Main entry point for the CallBundle iOS plugin.
///
/// Handles all MethodChannel communication between Dart and native iOS.
/// Coordinates CallKit, PushKit, audio session, and notification managers.
///
/// ## Key Architecture Decisions
///
/// - **PushKit handled IN the plugin** — eliminates AppDelegate code.
/// - **MethodChannel for ALL native→Dart events** — no EventChannel/WeakReference.
/// - **isUserInitiated** on events — eliminates _isEndingCallKitProgrammatically flag.
/// - **PendingCallStore** for cold-start — eliminates 3-second hardcoded delay.
/// - **AudioSessionManager** with `.mixWithOthers` — prevents HMS audio kill.
public class CallBundlePlugin: NSObject, FlutterPlugin {

    // MARK: - Properties

    private var channel: FlutterMethodChannel?
    private var callKitController: CallKitController?
    private var pushKitHandler: PushKitHandler?
    private var audioSessionManager: AudioSessionManager?
    private var callStore: CallStore?
    private var missedCallManager: MissedCallNotificationManager?
    private var isReady = false

    /// Singleton for access from PushKit callbacks (which fire before Flutter engine is ready).
    static var shared: CallBundlePlugin?

    /// Exposes the CallKit controller for PushKit handler access.
    var callKitControllerForPush: CallKitController? {
        return callKitController
    }

    // MARK: - Plugin Registration

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(
            name: "com.callbundle/main",
            binaryMessenger: registrar.messenger()
        )
        let instance = CallBundlePlugin()
        instance.channel = channel
        instance.callStore = CallStore()
        instance.audioSessionManager = AudioSessionManager()
        instance.missedCallManager = MissedCallNotificationManager()

        registrar.addMethodCallDelegate(instance, channel: channel)
        registrar.addApplicationDelegate(instance)

        // Initialize CallKit
        instance.callKitController = CallKitController(plugin: instance)

        // Initialize PushKit
        instance.pushKitHandler = PushKitHandler(plugin: instance)

        CallBundlePlugin.shared = instance
    }

    // MARK: - MethodChannel Handler

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "configure":
            handleConfigure(call, result: result)
        case "showIncomingCall":
            handleShowIncomingCall(call, result: result)
        case "showOutgoingCall":
            handleShowOutgoingCall(call, result: result)
        case "endCall":
            handleEndCall(call, result: result)
        case "endAllCalls":
            handleEndAllCalls(result: result)
        case "setCallConnected":
            handleSetCallConnected(call, result: result)
        case "getActiveCalls":
            handleGetActiveCalls(result: result)
        case "requestPermissions":
            handleRequestPermissions(result: result)
        case "getVoipToken":
            handleGetVoipToken(result: result)
        case "dispose":
            handleDispose(result: result)
        default:
            result(FlutterMethodNotImplemented)
        }
    }

    // MARK: - Method Implementations

    private func handleConfigure(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let args = call.arguments as? [String: Any] else {
            result(FlutterError(code: "INVALID_ARGS", message: "Expected map arguments", details: nil))
            return
        }

        let iosConfig = args["ios"] as? [String: Any]
        let appName = iosConfig?["appName"] as? String ?? "CallBundle"
        let iconTemplateImageName = iosConfig?["iconTemplateImageName"] as? String
        let ringtoneSound = iosConfig?["ringtoneSound"] as? String
        let supportsVideo = iosConfig?["supportsVideo"] as? Bool ?? false
        let maximumCallGroups = iosConfig?["maximumCallGroups"] as? Int ?? 1
        let maximumCallsPerCallGroup = iosConfig?["maximumCallsPerCallGroup"] as? Int ?? 1
        let includesCallsInRecents = iosConfig?["includesCallsInRecents"] as? Bool ?? true

        callKitController?.configure(
            appName: appName,
            iconTemplateImageName: iconTemplateImageName,
            ringtoneSound: ringtoneSound,
            supportsVideo: supportsVideo,
            maximumCallGroups: maximumCallGroups,
            maximumCallsPerCallGroup: maximumCallsPerCallGroup,
            includesCallsInRecents: includesCallsInRecents
        )

        // Register PushKit
        pushKitHandler?.registerForVoipPush()

        // Send ready signal
        isReady = true
        sendReadySignal()

        // Deliver pending events from cold-start
        deliverPendingEvents()

        result(nil)
    }

    private func handleShowIncomingCall(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let args = call.arguments as? [String: Any] else {
            result(FlutterError(code: "INVALID_ARGS", message: "Expected map arguments", details: nil))
            return
        }

        let callId = args["callId"] as? String ?? UUID().uuidString
        let callerName = args["callerName"] as? String ?? "Unknown"
        let handle = args["handle"] as? String ?? ""
        let handleType = args["handleType"] as? String ?? "generic"
        let hasVideo = args["hasVideo"] as? Bool ?? false

        let cxHandleType: CXHandle.HandleType
        switch handleType {
        case "phone":
            cxHandleType = .phoneNumber
        case "email":
            cxHandleType = .emailAddress
        default:
            cxHandleType = .generic
        }

        callKitController?.reportIncomingCall(
            uuid: uuidFromString(callId),
            handle: handle,
            handleType: cxHandleType,
            callerName: callerName,
            hasVideo: hasVideo
        ) { [weak self] error in
            if let error = error {
                result(FlutterError(
                    code: "INCOMING_CALL_ERROR",
                    message: error.localizedDescription,
                    details: nil
                ))
            } else {
                self?.callStore?.addCall(callId: callId, callerName: callerName, handle: handle)
                result(nil)
            }
        }
    }

    private func handleShowOutgoingCall(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let args = call.arguments as? [String: Any] else {
            result(FlutterError(code: "INVALID_ARGS", message: "Expected map arguments", details: nil))
            return
        }

        let callId = args["callId"] as? String ?? UUID().uuidString
        let callerName = args["callerName"] as? String ?? "Unknown"
        let handle = args["handle"] as? String ?? ""
        let handleType = args["handleType"] as? String ?? "generic"
        let hasVideo = args["hasVideo"] as? Bool ?? false

        let cxHandleType: CXHandle.HandleType
        switch handleType {
        case "phone":
            cxHandleType = .phoneNumber
        case "email":
            cxHandleType = .emailAddress
        default:
            cxHandleType = .generic
        }

        callKitController?.startOutgoingCall(
            uuid: uuidFromString(callId),
            handle: handle,
            handleType: cxHandleType,
            callerName: callerName,
            hasVideo: hasVideo
        )

        callStore?.addCall(callId: callId, callerName: callerName, handle: handle)
        result(nil)
    }

    private func handleEndCall(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let args = call.arguments as? [String: Any],
              let callId = args["callId"] as? String else {
            result(FlutterError(code: "INVALID_ARGS", message: "Missing callId", details: nil))
            return
        }

        callKitController?.endCall(uuid: uuidFromString(callId))
        callStore?.removeCall(callId: callId)
        result(nil)
    }

    private func handleEndAllCalls(result: @escaping FlutterResult) {
        callKitController?.endAllCalls()
        callStore?.removeAllCalls()
        result(nil)
    }

    private func handleSetCallConnected(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let args = call.arguments as? [String: Any],
              let callId = args["callId"] as? String else {
            result(FlutterError(code: "INVALID_ARGS", message: "Missing callId", details: nil))
            return
        }

        callKitController?.reportCallConnected(uuid: uuidFromString(callId))
        callStore?.updateCallState(callId: callId, state: "active")
        result(nil)
    }

    private func handleGetActiveCalls(result: @escaping FlutterResult) {
        let calls = callStore?.getAllCalls() ?? []
        result(calls)
    }

    private func handleRequestPermissions(result: @escaping FlutterResult) {
        missedCallManager?.requestNotificationPermission { granted in
            result([
                "notification": granted,
                // Phone/microphone are always available on iOS via CallKit
                "phone": true,
                "microphone": true,
            ])
        }
    }

    private func handleGetVoipToken(result: @escaping FlutterResult) {
        result(pushKitHandler?.currentVoipToken)
    }

    private func handleDispose(result: @escaping FlutterResult) {
        isReady = false
        result(nil)
    }

    // MARK: - Native → Dart Communication

    /// Sends a call event to Dart via MethodChannel.
    ///
    /// Uses `isUserInitiated` to distinguish user actions from programmatic
    /// actions, eliminating the `_isEndingCallKitProgrammatically` flag.
    func sendCallEvent(type: String, callId: String, isUserInitiated: Bool, extra: [String: Any]? = nil) {
        guard isReady else {
            // Store as pending if not ready (cold-start scenario)
            if type == "accepted" {
                callStore?.savePendingAccept(callId: callId)
            }
            return
        }

        var event: [String: Any] = [
            "type": type,
            "callId": callId,
            "isUserInitiated": isUserInitiated,
            "timestamp": Int64(Date().timeIntervalSince1970 * 1000),
        ]

        if let extra = extra {
            event["extra"] = extra
        }

        DispatchQueue.main.async { [weak self] in
            self?.channel?.invokeMethod("onCallEvent", arguments: event)
        }
    }

    /// Sends the VoIP token update to Dart.
    func sendVoipTokenUpdate(token: String) {
        DispatchQueue.main.async { [weak self] in
            self?.channel?.invokeMethod("onVoipTokenUpdated", arguments: token)
        }
    }

    /// Sends the ready signal to Dart.
    private func sendReadySignal() {
        DispatchQueue.main.async { [weak self] in
            self?.channel?.invokeMethod("onReady", arguments: nil)
        }
    }

    /// Delivers pending events stored during cold-start.
    private func deliverPendingEvents() {
        guard let pending = callStore?.consumePendingAccept() else { return }
        sendCallEvent(type: "accepted", callId: pending, isUserInitiated: true)
    }

    // MARK: - Audio Session

    /// Configures the audio session for a call.
    func configureAudioSession(active: Bool) {
        if active {
            audioSessionManager?.activateForCall()
        } else {
            audioSessionManager?.deactivate()
        }
    }

    // MARK: - Utilities

    /// Converts a string callId to a deterministic UUID.
    ///
    /// Uses UUID v5-like hashing so the same callId always maps to the same UUID.
    private func uuidFromString(_ string: String) -> UUID {
        if let uuid = UUID(uuidString: string) {
            return uuid
        }
        // Create deterministic UUID from arbitrary string
        let data = string.data(using: .utf8)!
        var hash = [UInt8](repeating: 0, count: 16)
        data.withUnsafeBytes { bytes in
            for (i, byte) in bytes.enumerated() {
                hash[i % 16] ^= byte
            }
        }
        // Set version (4) and variant (RFC 4122)
        hash[6] = (hash[6] & 0x0F) | 0x40
        hash[8] = (hash[8] & 0x3F) | 0x80

        let uuid = NSUUID(uuidBytes: hash) as UUID
        return uuid
    }
}
