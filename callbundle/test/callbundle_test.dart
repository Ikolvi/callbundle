import 'dart:async';

import 'package:callbundle/callbundle.dart';
import 'package:callbundle_platform_interface/callbundle_platform_interface.dart';
import 'package:flutter_test/flutter_test.dart';

/// Mock platform implementation for testing the app-facing API.
class MockCallBundlePlatform extends CallBundlePlatform {
  final List<String> calls = <String>[];
  final StreamController<NativeCallEvent> eventController =
      StreamController<NativeCallEvent>.broadcast();
  final Completer<void> readyCompleter = Completer<void>();

  @override
  Future<void> configure(NativeCallConfig config) async {
    calls.add('configure:${config.appName}');
  }

  @override
  Future<void> showIncomingCall(NativeCallParams params) async {
    calls.add('showIncomingCall:${params.callId}');
  }

  @override
  Future<void> showOutgoingCall(NativeCallParams params) async {
    calls.add('showOutgoingCall:${params.callId}');
  }

  @override
  Future<void> endCall(String callId) async {
    calls.add('endCall:$callId');
  }

  @override
  Future<void> endAllCalls() async {
    calls.add('endAllCalls');
  }

  @override
  Future<void> setCallConnected(String callId) async {
    calls.add('setCallConnected:$callId');
  }

  @override
  Future<List<NativeCallInfo>> getActiveCalls() async {
    calls.add('getActiveCalls');
    return <NativeCallInfo>[];
  }

  @override
  Future<NativeCallPermissions> requestPermissions() async {
    calls.add('requestPermissions');
    return const NativeCallPermissions(
      notificationPermission: PermissionStatus.granted,
      fullScreenIntentPermission: PermissionStatus.granted,
      phoneAccountEnabled: true,
      batteryOptimizationExempt: true,
      manufacturer: 'test',
      model: 'test',
      osVersion: 'test',
    );
  }

  @override
  Future<String?> getVoipToken() async {
    calls.add('getVoipToken');
    return 'mock-token';
  }

  @override
  Stream<NativeCallEvent> get onEvent => eventController.stream;

  @override
  Future<void> get onReady => readyCompleter.future;

  @override
  Future<void> dispose() async {
    calls.add('dispose');
    await eventController.close();
  }
}

void main() {
  late MockCallBundlePlatform mockPlatform;

  setUp(() {
    mockPlatform = MockCallBundlePlatform();
    CallBundlePlatform.instance = mockPlatform;
  });

  group('CallBundle API delegation', () {
    test('configure delegates to platform', () async {
      const config = NativeCallConfig(appName: 'TestApp');
      await CallBundle.configure(config);
      expect(mockPlatform.calls, contains('configure:TestApp'));
    });

    test('showIncomingCall delegates to platform', () async {
      const params = NativeCallParams(
        callId: 'call-1',
        callerName: 'John',
      );
      await CallBundle.showIncomingCall(params);
      expect(mockPlatform.calls, contains('showIncomingCall:call-1'));
    });

    test('showOutgoingCall delegates to platform', () async {
      const params = NativeCallParams(
        callId: 'call-2',
        callerName: 'Jane',
      );
      await CallBundle.showOutgoingCall(params);
      expect(mockPlatform.calls, contains('showOutgoingCall:call-2'));
    });

    test('endCall delegates to platform', () async {
      await CallBundle.endCall('call-1');
      expect(mockPlatform.calls, contains('endCall:call-1'));
    });

    test('endAllCalls delegates to platform', () async {
      await CallBundle.endAllCalls();
      expect(mockPlatform.calls, contains('endAllCalls'));
    });

    test('setCallConnected delegates to platform', () async {
      await CallBundle.setCallConnected('call-1');
      expect(mockPlatform.calls, contains('setCallConnected:call-1'));
    });

    test('getActiveCalls delegates to platform', () async {
      final result = await CallBundle.getActiveCalls();
      expect(mockPlatform.calls, contains('getActiveCalls'));
      expect(result, isEmpty);
    });

    test('requestPermissions delegates to platform', () async {
      final result = await CallBundle.requestPermissions();
      expect(mockPlatform.calls, contains('requestPermissions'));
      expect(result.isFullyReady, true);
    });

    test('getVoipToken delegates to platform', () async {
      final result = await CallBundle.getVoipToken();
      expect(mockPlatform.calls, contains('getVoipToken'));
      expect(result, 'mock-token');
    });

    test('onEvent streams events from platform', () async {
      final events = <NativeCallEvent>[];
      final sub = CallBundle.onEvent.listen(events.add);

      mockPlatform.eventController.add(
        NativeCallEvent(
          type: NativeCallEventType.accepted,
          callId: 'call-1',
          isUserInitiated: true,
          timestamp: DateTime.now(),
          eventId: 1,
        ),
      );

      await Future<void>.delayed(Duration.zero);

      expect(events, hasLength(1));
      expect(events.first.type, NativeCallEventType.accepted);
      expect(events.first.callId, 'call-1');
      expect(events.first.isUserInitiated, true);

      await sub.cancel();
    });

    test('dispose delegates to platform', () async {
      await CallBundle.dispose();
      expect(mockPlatform.calls, contains('dispose'));
    });
  });
}
