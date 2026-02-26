import 'package:flutter/foundation.dart';

import 'native_call_enums.dart';

/// Represents an event from the native call UI.
///
/// Events are delivered via [CallBundlePlatform.onEvent] as a
/// `Stream<NativeCallEvent>`. Each event includes a unique [eventId]
/// for deduplication and an [isUserInitiated] flag to distinguish
/// user actions from programmatic/system actions.
///
/// This design eliminates the need for:
/// - `_handledCallIds` deduplication sets (use [eventId] instead)
/// - `_isEndingCallKitProgrammatically` flags (use [isUserInitiated] instead)
@immutable
class NativeCallEvent {
  /// Creates a native call event.
  const NativeCallEvent({
    required this.type,
    required this.callId,
    required this.isUserInitiated,
    this.extra = const <String, dynamic>{},
    required this.timestamp,
    required this.eventId,
  });

  /// The type of event that occurred.
  final NativeCallEventType type;

  /// The call ID this event belongs to.
  ///
  /// Corresponds to [NativeCallParams.callId] from the original call.
  final String callId;

  /// Whether this event was initiated by the user.
  ///
  /// - `true`: User tapped a button on the native UI (Accept, Decline, etc.)
  /// - `false`: Programmatic action (e.g., `CallBundle.endCall()`) or system
  ///   action (e.g., another call interrupted).
  ///
  /// **Key improvement:** Eliminates the
  /// `_isEndingCallKitProgrammatically` boolean flag pattern.
  final bool isUserInitiated;

  /// Pass-through metadata from [NativeCallParams.extra].
  ///
  /// Contains the same data that was provided when showing the call,
  /// allowing event handlers to access application-specific context.
  final Map<String, dynamic> extra;

  /// When the event occurred.
  final DateTime timestamp;

  /// Monotonically increasing event ID for deduplication.
  ///
  /// Each event has a unique, strictly increasing ID. This allows
  /// consumers to detect and skip duplicate events without maintaining
  /// a separate deduplication set.
  ///
  /// **Key improvement:** Eliminates the `_handledCallIds` Set pattern.
  final int eventId;

  /// Serializes this instance to a [Map] for MethodChannel transport.
  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      'type': type.name,
      'callId': callId,
      'isUserInitiated': isUserInitiated,
      'extra': extra,
      'timestamp': timestamp.millisecondsSinceEpoch,
      'eventId': eventId,
    };
  }

  /// Creates an instance from a [Map] received via MethodChannel.
  factory NativeCallEvent.fromMap(Map<String, dynamic> map) {
    return NativeCallEvent(
      type: NativeCallEventType.fromString(map['type'] as String?),
      callId: map['callId'] as String,
      isUserInitiated: map['isUserInitiated'] as bool? ?? true,
      extra: Map<String, dynamic>.from(
        map['extra'] as Map<dynamic, dynamic>? ?? <String, dynamic>{},
      ),
      timestamp: DateTime.fromMillisecondsSinceEpoch(
        map['timestamp'] as int? ?? DateTime.now().millisecondsSinceEpoch,
      ),
      eventId: map['eventId'] as int? ?? 0,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is NativeCallEvent &&
        other.type == type &&
        other.callId == callId &&
        other.isUserInitiated == isUserInitiated &&
        mapEquals(other.extra, extra) &&
        other.timestamp == timestamp &&
        other.eventId == eventId;
  }

  @override
  int get hashCode {
    return Object.hash(
      type,
      callId,
      isUserInitiated,
      Object.hashAll(extra.entries),
      timestamp,
      eventId,
    );
  }

  @override
  String toString() {
    return 'NativeCallEvent('
        'type: $type, '
        'callId: $callId, '
        'isUserInitiated: $isUserInitiated, '
        'eventId: $eventId)';
  }
}
