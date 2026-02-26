import 'package:flutter/foundation.dart';

import 'native_call_enums.dart';

/// Information about an active or recent native call.
///
/// Returned by [CallBundlePlatform.getActiveCalls] to query
/// the current state of tracked calls.
@immutable
class NativeCallInfo {
  /// Creates a native call info instance.
  const NativeCallInfo({
    required this.callId,
    required this.callerName,
    required this.callType,
    required this.state,
    required this.isAccepted,
    this.startTime,
    this.extra = const <String, dynamic>{},
  });

  /// The unique identifier for this call.
  final String callId;

  /// The display name of the caller.
  final String callerName;

  /// The type of call (voice or video).
  final NativeCallType callType;

  /// The current state of the call.
  final NativeCallState state;

  /// Whether the call has been accepted by the user.
  final bool isAccepted;

  /// When the call started (ring time for incoming, dial time for outgoing).
  ///
  /// `null` if the call hasn't started yet.
  final DateTime? startTime;

  /// Pass-through metadata from [NativeCallParams.extra].
  final Map<String, dynamic> extra;

  /// Serializes this instance to a [Map] for MethodChannel transport.
  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      'callId': callId,
      'callerName': callerName,
      'callType': callType.intValue,
      'state': state.name,
      'isAccepted': isAccepted,
      if (startTime != null)
        'startTime': startTime!.millisecondsSinceEpoch,
      'extra': extra,
    };
  }

  /// Creates an instance from a [Map] received via MethodChannel.
  factory NativeCallInfo.fromMap(Map<String, dynamic> map) {
    return NativeCallInfo(
      callId: map['callId'] as String,
      callerName: map['callerName'] as String,
      callType: NativeCallType.fromString(
        map['callType']?.toString(),
      ),
      state: NativeCallState.fromString(map['state'] as String?),
      isAccepted: map['isAccepted'] as bool? ?? false,
      startTime: map['startTime'] != null
          ? DateTime.fromMillisecondsSinceEpoch(map['startTime'] as int)
          : null,
      extra: Map<String, dynamic>.from(
        map['extra'] as Map<dynamic, dynamic>? ?? <String, dynamic>{},
      ),
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is NativeCallInfo &&
        other.callId == callId &&
        other.callerName == callerName &&
        other.callType == callType &&
        other.state == state &&
        other.isAccepted == isAccepted &&
        other.startTime == startTime &&
        mapEquals(other.extra, extra);
  }

  @override
  int get hashCode {
    return Object.hash(
      callId,
      callerName,
      callType,
      state,
      isAccepted,
      startTime,
      Object.hashAll(extra.entries),
    );
  }

  @override
  String toString() {
    return 'NativeCallInfo('
        'callId: $callId, '
        'callerName: $callerName, '
        'callType: $callType, '
        'state: $state, '
        'isAccepted: $isAccepted)';
  }
}
