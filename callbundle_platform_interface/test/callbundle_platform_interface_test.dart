import 'package:flutter_test/flutter_test.dart';

import 'package:callbundle_platform_interface/callbundle_platform_interface.dart';

void main() {
  group('NativeCallParams', () {
    test('serializes to map correctly', () {
      const params = NativeCallParams(
        callId: 'test-123',
        callerName: 'John Doe',
        callType: NativeCallType.video,
        handle: 'Engineer',
        duration: 30000,
        extra: {'userId': '456'},
      );

      final map = params.toMap();

      expect(map['callId'], 'test-123');
      expect(map['callerName'], 'John Doe');
      expect(map['callType'], 1);
      expect(map['handle'], 'Engineer');
      expect(map['duration'], 30000);
      expect(map['extra'], {'userId': '456'});
    });

    test('deserializes from map correctly', () {
      final map = <String, dynamic>{
        'callId': 'test-123',
        'callerName': 'John Doe',
        'callType': '1',
        'handle': 'Engineer',
        'duration': 30000,
        'extra': {'userId': '456'},
      };

      final params = NativeCallParams.fromMap(map);

      expect(params.callId, 'test-123');
      expect(params.callerName, 'John Doe');
      expect(params.callType, NativeCallType.video);
      expect(params.handle, 'Engineer');
      expect(params.duration, 30000);
    });

    test('equality works correctly', () {
      const params1 = NativeCallParams(
        callId: 'test-123',
        callerName: 'John Doe',
      );
      const params2 = NativeCallParams(
        callId: 'test-123',
        callerName: 'John Doe',
      );
      const params3 = NativeCallParams(
        callId: 'test-456',
        callerName: 'Jane Doe',
      );

      expect(params1, equals(params2));
      expect(params1, isNot(equals(params3)));
    });
  });

  group('NativeCallEvent', () {
    test('serializes to map correctly', () {
      final event = NativeCallEvent(
        type: NativeCallEventType.accepted,
        callId: 'test-123',
        isUserInitiated: true,
        extra: const {'userId': '456'},
        timestamp: DateTime.fromMillisecondsSinceEpoch(1000000),
        eventId: 1,
      );

      final map = event.toMap();

      expect(map['type'], 'accepted');
      expect(map['callId'], 'test-123');
      expect(map['isUserInitiated'], true);
      expect(map['eventId'], 1);
    });

    test('deserializes from map correctly', () {
      final map = <String, dynamic>{
        'type': 'declined',
        'callId': 'test-123',
        'isUserInitiated': true,
        'extra': <String, dynamic>{},
        'timestamp': 1000000,
        'eventId': 2,
      };

      final event = NativeCallEvent.fromMap(map);

      expect(event.type, NativeCallEventType.declined);
      expect(event.callId, 'test-123');
      expect(event.isUserInitiated, true);
      expect(event.eventId, 2);
    });
  });

  group('NativeCallEnums', () {
    test('NativeCallType.fromString works', () {
      expect(NativeCallType.fromString('video'), NativeCallType.video);
      expect(NativeCallType.fromString('voice'), NativeCallType.voice);
      expect(NativeCallType.fromString('1'), NativeCallType.video);
      expect(NativeCallType.fromString('0'), NativeCallType.voice);
      expect(NativeCallType.fromString(null), NativeCallType.voice);
    });

    test('NativeCallEventType.fromString works', () {
      expect(
        NativeCallEventType.fromString('accepted'),
        NativeCallEventType.accepted,
      );
      expect(
        NativeCallEventType.fromString('declined'),
        NativeCallEventType.declined,
      );
      expect(
        NativeCallEventType.fromString('timed_out'),
        NativeCallEventType.timedOut,
      );
      expect(
        NativeCallEventType.fromString('callback'),
        NativeCallEventType.callback,
      );
    });

    test('PermissionStatus.isGranted works', () {
      expect(PermissionStatus.granted.isGranted, true);
      expect(PermissionStatus.denied.isGranted, false);
      expect(PermissionStatus.restricted.isGranted, false);
      expect(PermissionStatus.notDetermined.isGranted, false);
    });
  });

  group('NativeCallConfig', () {
    test('serializes to map correctly', () {
      const config = NativeCallConfig(
        appName: 'TestApp',
        missedCallNotification: true,
      );

      final map = config.toMap();

      expect(map['appName'], 'TestApp');
      expect(map['missedCallNotification'], true);
    });
  });

  group('NativeCallPermissions', () {
    test('isFullyReady returns true when all permissions granted', () {
      const permissions = NativeCallPermissions(
        notificationPermission: PermissionStatus.granted,
        fullScreenIntentPermission: PermissionStatus.granted,
        phoneAccountEnabled: true,
        batteryOptimizationExempt: true,
        manufacturer: 'google',
        model: 'Pixel 7',
        osVersion: '34',
      );

      expect(permissions.isFullyReady, true);
    });

    test('isFullyReady returns false when missing permissions', () {
      const permissions = NativeCallPermissions(
        notificationPermission: PermissionStatus.denied,
        fullScreenIntentPermission: PermissionStatus.granted,
        phoneAccountEnabled: true,
        batteryOptimizationExempt: true,
        manufacturer: 'google',
        model: 'Pixel 7',
        osVersion: '34',
      );

      expect(permissions.isFullyReady, false);
    });
  });

  group('CallBundlePlatform', () {
    test('default instance is MethodChannelCallBundle', () {
      expect(
        CallBundlePlatform.instance,
        isA<MethodChannelCallBundle>(),
      );
    });
  });
}
