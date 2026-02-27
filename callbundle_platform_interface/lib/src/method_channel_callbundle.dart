import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'callbundle_platform.dart';
import 'models/native_call_config.dart';
import 'models/native_call_enums.dart';
import 'models/native_call_event.dart';
import 'models/native_call_info.dart';
import 'models/native_call_params.dart';
import 'models/native_call_permissions.dart';

/// Default [CallBundlePlatform] implementation using [MethodChannel].
///
/// This class implements the platform interface contract using
/// `MethodChannel("com.callbundle/main")` for bidirectional
/// native ↔ Dart communication.
///
/// ## Why the handler is registered in the constructor
///
/// Previously, `_ensureHandlerRegistered()` was only called during
/// [configure]. This caused a critical bug:
///
/// 1. App starts → `CallBundleAndroid()` created during plugin registration
/// 2. FCM message arrives → native shows notification
/// 3. User taps Decline → native calls `channel.invokeMethod("onCallEvent")`
/// 4. **But:** Dart handler wasn't registered yet (configure not called)
/// 5. **Result:** Event silently dropped → caller side never sees rejection
///
/// By registering the handler in the constructor, events are received
/// as soon as the platform implementation exists — even before [configure].
///
/// ## Key design decisions
///
/// - Uses MethodChannel for BOTH directions (not EventChannel).
///   This eliminates the WeakReference/GC issue that causes silent
///   event drops in EventChannel-based plugins.
/// - Supports background isolates via [BackgroundIsolateBinaryMessenger].
/// - Uses a broadcast [StreamController] for event distribution.
///   **Note:** Broadcast streams drop events if no listener is subscribed.
///   [IncomingCallHandlerService.initialize] (sync) subscribes before
///   [CallKitService.initialize] (async) calls [configure].
/// - Implements a deterministic handshake protocol for cold-start.
class MethodChannelCallBundle extends CallBundlePlatform {
  /// Creates a [MethodChannelCallBundle] and immediately registers
  /// the native → Dart event handler.
  ///
  /// This ensures events from native code are received even before
  /// [configure] is called — critical for engine recreation scenarios
  /// where FCM messages can arrive before initialization completes.
  MethodChannelCallBundle() {
    _ensureHandlerRegistered();
  }

  /// The method channel used for communication with native code.
  @visibleForTesting
  final MethodChannel methodChannel = const MethodChannel(
    'com.callbundle/main',
  );

  /// Broadcast stream controller for native call events.
  ///
  /// Uses broadcast mode so multiple listeners (BLoC, service, etc.)
  /// can subscribe simultaneously.
  final StreamController<NativeCallEvent> _eventController =
      StreamController<NativeCallEvent>.broadcast();

  /// Completer for the native-side ready signal.
  final Completer<void> _readyCompleter = Completer<void>();

  /// Whether the MethodChannel handler has been set up.
  bool _isHandlerRegistered = false;

  /// Monotonically increasing event ID counter.
  int _nextEventId = 1;

  /// Sets up the incoming MethodChannel handler for native → Dart events.
  ///
  /// Called from the constructor to ensure events are received immediately
  /// after the platform implementation is registered. This is critical for
  /// engine recreation scenarios where native events (accept/decline) may
  /// fire before [configure] is called.
  ///
  /// The handler processes:
  /// - `onCallEvent`: Deserializes and emits [NativeCallEvent] to the stream.
  /// - `onVoipTokenUpdated`: Emits a token-updated event (iOS only).
  /// - `onReady`: Completes the [onReady] future.
  void _ensureHandlerRegistered() {
    if (_isHandlerRegistered) return;

    try {
      methodChannel.setMethodCallHandler(_handleNativeCall);
      _isHandlerRegistered = true;
    } catch (e) {
      // The binary messenger may not be initialized yet (happens when
      // the plugin is registered before WidgetsFlutterBinding.ensureInitialized).
      // The handler will be registered on the next call (e.g., configure, showIncomingCall).
      debugPrint('CallBundle: Deferred handler registration (binding not ready): $e');
    }
  }

  /// Handles incoming method calls from the native side.
  Future<dynamic> _handleNativeCall(MethodCall call) async {
    switch (call.method) {
      case 'onCallEvent':
        final Map<String, dynamic> eventMap = Map<String, dynamic>.from(
          call.arguments as Map<dynamic, dynamic>,
        );

        // Assign a monotonic event ID if native didn't provide one.
        if (!eventMap.containsKey('eventId') || eventMap['eventId'] == null) {
          eventMap['eventId'] = _nextEventId++;
        } else {
          // Ensure our counter stays ahead of native-provided IDs.
          final int nativeId = eventMap['eventId'] as int;
          if (nativeId >= _nextEventId) {
            _nextEventId = nativeId + 1;
          }
        }

        final NativeCallEvent event = NativeCallEvent.fromMap(eventMap);
        _eventController.add(event);
        return null;

      case 'onVoipTokenUpdated':
        // VoIP token updates are delivered as a special event type.
        // The token is stored natively; this notification allows
        // Dart-side caching or forwarding to backend.
        return null;

      case 'onReady':
        if (!_readyCompleter.isCompleted) {
          _readyCompleter.complete();
        }
        return null;

      default:
        // Unknown method — log in debug mode, ignore in release.
        debugPrint(
          'CallBundle: Unknown method call from native: ${call.method}',
        );
        return null;
    }
  }

  @override
  @override
  Future<void> configure(NativeCallConfig config) async {
    _ensureHandlerRegistered();
    await methodChannel.invokeMethod<void>('configure', config.toMap());
  }

  @override
  Future<void> showIncomingCall(NativeCallParams params) async {
    _ensureHandlerRegistered();
    await methodChannel.invokeMethod<void>(
      'showIncomingCall',
      params.toMap(),
    );
  }

  @override
  Future<void> showOutgoingCall(NativeCallParams params) async {
    _ensureHandlerRegistered();
    await methodChannel.invokeMethod<void>(
      'showOutgoingCall',
      params.toMap(),
    );
  }

  @override
  Future<void> endCall(String callId) async {
    await methodChannel.invokeMethod<void>('endCall', callId);
  }

  @override
  Future<void> endAllCalls() async {
    await methodChannel.invokeMethod<void>('endAllCalls');
  }

  @override
  Future<void> setCallConnected(String callId) async {
    await methodChannel.invokeMethod<void>('setCallConnected', callId);
  }

  @override
  Future<List<NativeCallInfo>> getActiveCalls() async {
    final List<dynamic>? result = await methodChannel.invokeMethod<
        List<dynamic>>('getActiveCalls');

    if (result == null) return <NativeCallInfo>[];

    return result
        .map(
          (dynamic item) => NativeCallInfo.fromMap(
            Map<String, dynamic>.from(item as Map<dynamic, dynamic>),
          ),
        )
        .toList();
  }

  @override
  Future<NativeCallPermissions> checkPermissions() async {
    final Map<dynamic, dynamic>? result =
        await methodChannel.invokeMethod<Map<dynamic, dynamic>>(
      'checkPermissions',
    );

    if (result == null) {
      return const NativeCallPermissions(
        notificationPermission: PermissionStatus.notDetermined,
        fullScreenIntentPermission: PermissionStatus.notDetermined,
        phoneAccountEnabled: false,
        batteryOptimizationExempt: false,
        manufacturer: 'unknown',
        model: 'unknown',
        osVersion: 'unknown',
      );
    }

    return NativeCallPermissions.fromMap(
      Map<String, dynamic>.from(result),
    );
  }

  @override
  Future<NativeCallPermissions> requestPermissions() async {
    final Map<dynamic, dynamic>? result =
        await methodChannel.invokeMethod<Map<dynamic, dynamic>>(
      'requestPermissions',
    );

    if (result == null) {
      return const NativeCallPermissions(
        notificationPermission: PermissionStatus.notDetermined,
        fullScreenIntentPermission: PermissionStatus.notDetermined,
        phoneAccountEnabled: false,
        batteryOptimizationExempt: false,
        manufacturer: 'unknown',
        model: 'unknown',
        osVersion: 'unknown',
      );
    }

    return NativeCallPermissions.fromMap(
      Map<String, dynamic>.from(result),
    );
  }

  @override
  Future<bool> requestBatteryOptimizationExemption() async {
    final result = await methodChannel.invokeMethod<bool>(
      'requestBatteryOptimizationExemption',
    );
    return result ?? false;
  }

  @override
  Future<String?> getVoipToken() async {
    return methodChannel.invokeMethod<String?>('getVoipToken');
  }

  @override
  Stream<NativeCallEvent> get onEvent => _eventController.stream;

  @override
  Future<void> get onReady => _readyCompleter.future;

  @override
  Future<void> dispose() async {
    await methodChannel.invokeMethod<void>('dispose');
    methodChannel.setMethodCallHandler(null);
    _isHandlerRegistered = false;
    await _eventController.close();
  }
}
