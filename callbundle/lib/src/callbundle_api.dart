import 'dart:async';

import 'package:callbundle_platform_interface/callbundle_platform_interface.dart';

/// CallBundle — Native incoming & outgoing call UI for Flutter.
///
/// This is the app-facing public API. Import `package:callbundle/callbundle.dart`
/// to access all functionality.
///
/// ## Quick Start
///
/// ```dart
/// // 1. Configure once during app startup
/// await CallBundle.configure(NativeCallConfig(
///   appName: 'MyApp',
///   android: AndroidCallConfig(phoneAccountLabel: 'MyApp Calls'),
///   ios: IosCallConfig(includesCallsInRecents: true),
/// ));
///
/// // 2. Listen for call events
/// CallBundle.onEvent.listen((event) {
///   switch (event.type) {
///     case NativeCallEventType.accepted:
///       print('Call ${event.callId} accepted by user');
///       break;
///     case NativeCallEventType.declined:
///       print('Call ${event.callId} declined');
///       break;
///     case NativeCallEventType.ended:
///       if (event.isUserInitiated) {
///         print('User ended call ${event.callId}');
///       } else {
///         print('Call ${event.callId} ended programmatically');
///       }
///       break;
///     default:
///       break;
///   }
/// });
///
/// // 3. Show incoming call (safe from background isolates)
/// await CallBundle.showIncomingCall(NativeCallParams(
///   callId: 'abc-123',
///   callerName: 'Ravi Kumar',
///   callType: NativeCallType.video,
///   extra: {'userId': '456', 'roomId': 'xyz'},
/// ));
///
/// // 4. End a call programmatically
/// await CallBundle.endCall('abc-123');
/// ```
///
/// ## Background Isolate Safety
///
/// [showIncomingCall] and [endCall] can be called from FCM background
/// handlers without additional setup. The plugin automatically
/// initializes [BackgroundIsolateBinaryMessenger] support.
///
/// ## Cold-Start Handling
///
/// When a user taps "Accept" while the app is killed, the plugin
/// stores the event in [PendingCallStore]. After the Dart side calls
/// [configure], pending events are automatically delivered via [onEvent].
/// No hardcoded delays are needed.
class CallBundle {
  CallBundle._();

  /// Returns the platform-specific implementation.
  static CallBundlePlatform get _platform => CallBundlePlatform.instance;

  /// Initializes the CallBundle plugin with the given configuration.
  ///
  /// Must be called once during app startup, typically in `main()` or
  /// during dependency injection initialization. After this call returns,
  /// any pending cold-start events are delivered via [onEvent].
  ///
  /// Example:
  /// ```dart
  /// await CallBundle.configure(NativeCallConfig(
  ///   appName: 'CommunityXo',
  ///   android: AndroidCallConfig(
  ///     phoneAccountLabel: 'CommunityXo Calls',
  ///     oemAdaptiveMode: true,
  ///   ),
  ///   ios: IosCallConfig(
  ///     includesCallsInRecents: true,
  ///     supportsVideo: true,
  ///   ),
  /// ));
  /// ```
  static Future<void> configure(NativeCallConfig config) {
    return _platform.configure(config);
  }

  /// Shows a native incoming call UI.
  ///
  /// **Android:** Triggers `ConnectionService` + `TelecomManager` (preferred)
  /// or OEM-adaptive notification fallback.
  ///
  /// **iOS:** Reports to `CXProvider` for the native CallKit full-screen UI.
  ///
  /// **Background isolate safe:** Can be called from FCM background handlers.
  ///
  /// Throws [PlatformException] if the call cannot be shown.
  static Future<void> showIncomingCall(NativeCallParams params) {
    return _platform.showIncomingCall(params);
  }

  /// Shows a native outgoing call UI.
  ///
  /// **Android:** Shows an ongoing call notification.
  /// **iOS:** Triggers `CXStartCallAction` for the green status bar indicator.
  static Future<void> showOutgoingCall(NativeCallParams params) {
    return _platform.showOutgoingCall(params);
  }

  /// Ends a specific call by its ID.
  ///
  /// The resulting [NativeCallEvent] will have `isUserInitiated: false`,
  /// allowing event handlers to distinguish programmatic ends from user taps.
  ///
  /// **Key improvement:** Eliminates the `_isEndingCallKitProgrammatically`
  /// flag pattern used in the previous plugin.
  static Future<void> endCall(String callId) {
    return _platform.endCall(callId);
  }

  /// Ends all active calls.
  ///
  /// Useful during logout, app termination, or error recovery.
  static Future<void> endAllCalls() {
    return _platform.endAllCalls();
  }

  /// Marks a specific call as connected/active.
  ///
  /// Call this after the VoIP session (e.g., HMS 100ms room) is established
  /// to update the native UI state.
  static Future<void> setCallConnected(String callId) {
    return _platform.setCallConnected(callId);
  }

  /// Returns all currently active/tracked calls.
  ///
  /// Each call includes its current state, acceptance status, and metadata.
  static Future<List<NativeCallInfo>> getActiveCalls() {
    return _platform.getActiveCalls();
  }

  /// Requests permissions and returns current permission status.
  ///
  /// May trigger permission dialogs on Android 13+ (notifications)
  /// and Android 14+ (full-screen intent).
  ///
  /// Returns a comprehensive [NativeCallPermissions] object with
  /// per-permission status and OEM-specific diagnostics.
  static Future<NativeCallPermissions> requestPermissions() {
    return _platform.requestPermissions();
  }

  /// Returns the current VoIP push token (iOS only).
  ///
  /// Returns `null` on Android or if the token hasn't been received yet.
  /// Listen to [onEvent] for `onVoipTokenUpdated` events to get
  /// real-time token updates.
  static Future<String?> getVoipToken() {
    return _platform.getVoipToken();
  }

  /// Stream of native call events.
  ///
  /// All user interactions, lifecycle events, and system events from
  /// the native call UI are delivered through this single stream.
  ///
  /// The stream is a broadcast stream — multiple listeners are supported.
  ///
  /// Each event includes:
  /// - [NativeCallEvent.eventId]: Monotonic ID for deduplication.
  /// - [NativeCallEvent.isUserInitiated]: `true` for user taps,
  ///   `false` for programmatic/system actions.
  /// - [NativeCallEvent.extra]: Pass-through metadata from the original
  ///   [NativeCallParams.extra].
  static Stream<NativeCallEvent> get onEvent => _platform.onEvent;

  /// Future that completes when the native side is fully initialized.
  ///
  /// Use this to gate operations that require native readiness.
  /// Completes immediately if already ready.
  static Future<void> get onReady => _platform.onReady;

  /// Releases all resources held by the plugin.
  ///
  /// Call this during app teardown. After disposal, the plugin
  /// should not be used without re-initialization.
  static Future<void> dispose() {
    return _platform.dispose();
  }
}
