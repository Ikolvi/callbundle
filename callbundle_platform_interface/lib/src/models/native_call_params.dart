import 'package:flutter/foundation.dart';

import 'native_call_enums.dart';

/// Android-specific parameters for configuring a native call.
///
/// These parameters control Android notification appearance, sound,
/// and behavior for incoming/outgoing calls.
@immutable
class AndroidCallParams {
  /// Creates Android-specific call parameters.
  const AndroidCallParams({
    this.channelId,
    this.channelName,
    this.ringtone,
    this.vibrationPattern,
    this.showFullScreen = true,
    this.notificationColor,
    this.autoEndOnTimeout = true,
    this.foregroundServiceType,
  });

  /// Custom notification channel ID override.
  ///
  /// If not provided, the plugin uses its default channel.
  final String? channelId;

  /// Custom notification channel display name.
  final String? channelName;

  /// Sound resource name or URI for the ringtone.
  ///
  /// Can be a raw resource name (e.g., `"ringtone"`) or a content URI.
  final String? ringtone;

  /// Vibration pattern in milliseconds.
  ///
  /// Example: `[0, 1000, 500, 1000, 500]` for a repeating pattern.
  final List<int>? vibrationPattern;

  /// Whether to show a full-screen intent on the lock screen.
  ///
  /// Defaults to `true`. Requires `USE_FULL_SCREEN_INTENT` permission.
  final bool showFullScreen;

  /// ARGB color for notification accent.
  ///
  /// Example: `0xFF4CAF50` for green.
  final int? notificationColor;

  /// Whether to auto-dismiss the notification after the call duration expires.
  ///
  /// Defaults to `true`.
  final bool autoEndOnTimeout;

  /// Foreground service type override.
  ///
  /// Defaults to `"phoneCall"` on Android 14+.
  final String? foregroundServiceType;

  /// Serializes this instance to a [Map] for MethodChannel transport.
  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      if (channelId != null) 'channelId': channelId,
      if (channelName != null) 'channelName': channelName,
      if (ringtone != null) 'ringtone': ringtone,
      if (vibrationPattern != null) 'vibrationPattern': vibrationPattern,
      'showFullScreen': showFullScreen,
      if (notificationColor != null) 'notificationColor': notificationColor,
      'autoEndOnTimeout': autoEndOnTimeout,
      if (foregroundServiceType != null)
        'foregroundServiceType': foregroundServiceType,
    };
  }

  /// Creates an instance from a [Map] received via MethodChannel.
  factory AndroidCallParams.fromMap(Map<String, dynamic> map) {
    return AndroidCallParams(
      channelId: map['channelId'] as String?,
      channelName: map['channelName'] as String?,
      ringtone: map['ringtone'] as String?,
      vibrationPattern: (map['vibrationPattern'] as List<dynamic>?)
          ?.map((e) => e as int)
          .toList(),
      showFullScreen: map['showFullScreen'] as bool? ?? true,
      notificationColor: map['notificationColor'] as int?,
      autoEndOnTimeout: map['autoEndOnTimeout'] as bool? ?? true,
      foregroundServiceType: map['foregroundServiceType'] as String?,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is AndroidCallParams &&
        other.channelId == channelId &&
        other.channelName == channelName &&
        other.ringtone == ringtone &&
        listEquals(other.vibrationPattern, vibrationPattern) &&
        other.showFullScreen == showFullScreen &&
        other.notificationColor == notificationColor &&
        other.autoEndOnTimeout == autoEndOnTimeout &&
        other.foregroundServiceType == foregroundServiceType;
  }

  @override
  int get hashCode {
    return Object.hash(
      channelId,
      channelName,
      ringtone,
      vibrationPattern != null ? Object.hashAll(vibrationPattern!) : null,
      showFullScreen,
      notificationColor,
      autoEndOnTimeout,
      foregroundServiceType,
    );
  }

  @override
  String toString() {
    return 'AndroidCallParams('
        'channelId: $channelId, '
        'channelName: $channelName, '
        'ringtone: $ringtone, '
        'showFullScreen: $showFullScreen, '
        'autoEndOnTimeout: $autoEndOnTimeout)';
  }
}

/// iOS-specific parameters for configuring a native call.
///
/// These parameters control CallKit UI appearance and audio session
/// configuration for incoming/outgoing calls.
@immutable
class IosCallParams {
  /// Creates iOS-specific call parameters.
  const IosCallParams({
    this.iconName,
    this.handleType = NativeHandleType.generic,
    this.supportsVideo = true,
    this.supportsHolding = false,
    this.supportsGrouping = false,
    this.maximumCallGroups = 1,
    this.maximumCallsPerCallGroup = 1,
    this.audioSessionMode = 'default',
    this.audioSessionPreferredSampleRate = 44100.0,
    this.audioSessionPreferredIOBufferDuration = 0.005,
    this.ringtone,
  });

  /// CallKit template icon asset name.
  ///
  /// Must be a single-color (template) image in the app bundle.
  final String? iconName;

  /// The type of handle used to identify the call.
  ///
  /// Defaults to [NativeHandleType.generic].
  final NativeHandleType handleType;

  /// Whether the CallKit UI shows a video button.
  ///
  /// Defaults to `true`.
  final bool supportsVideo;

  /// Whether the call supports the hold action.
  ///
  /// Defaults to `false`.
  final bool supportsHolding;

  /// Whether the call supports grouping/conference.
  ///
  /// Defaults to `false`.
  final bool supportsGrouping;

  /// Maximum number of concurrent call groups.
  ///
  /// Defaults to `1` (single call at a time).
  final int maximumCallGroups;

  /// Maximum number of calls per call group.
  ///
  /// Defaults to `1` (no conference).
  final int maximumCallsPerCallGroup;

  /// AVAudioSession mode string.
  ///
  /// Defaults to `"default"`.
  final String audioSessionMode;

  /// Preferred audio sample rate in Hz.
  ///
  /// Defaults to `44100.0`.
  final double audioSessionPreferredSampleRate;

  /// Preferred I/O buffer duration in seconds.
  ///
  /// Defaults to `0.005`.
  final double audioSessionPreferredIOBufferDuration;

  /// Sound file name in the app bundle for the ringtone.
  final String? ringtone;

  /// Serializes this instance to a [Map] for MethodChannel transport.
  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      if (iconName != null) 'iconName': iconName,
      'handleType': handleType.name,
      'supportsVideo': supportsVideo,
      'supportsHolding': supportsHolding,
      'supportsGrouping': supportsGrouping,
      'maximumCallGroups': maximumCallGroups,
      'maximumCallsPerCallGroup': maximumCallsPerCallGroup,
      'audioSessionMode': audioSessionMode,
      'audioSessionPreferredSampleRate': audioSessionPreferredSampleRate,
      'audioSessionPreferredIOBufferDuration':
          audioSessionPreferredIOBufferDuration,
      if (ringtone != null) 'ringtone': ringtone,
    };
  }

  /// Creates an instance from a [Map] received via MethodChannel.
  factory IosCallParams.fromMap(Map<String, dynamic> map) {
    return IosCallParams(
      iconName: map['iconName'] as String?,
      handleType:
          NativeHandleType.fromString(map['handleType'] as String?),
      supportsVideo: map['supportsVideo'] as bool? ?? true,
      supportsHolding: map['supportsHolding'] as bool? ?? false,
      supportsGrouping: map['supportsGrouping'] as bool? ?? false,
      maximumCallGroups: map['maximumCallGroups'] as int? ?? 1,
      maximumCallsPerCallGroup:
          map['maximumCallsPerCallGroup'] as int? ?? 1,
      audioSessionMode: map['audioSessionMode'] as String? ?? 'default',
      audioSessionPreferredSampleRate:
          (map['audioSessionPreferredSampleRate'] as num?)?.toDouble() ??
              44100.0,
      audioSessionPreferredIOBufferDuration:
          (map['audioSessionPreferredIOBufferDuration'] as num?)
                  ?.toDouble() ??
              0.005,
      ringtone: map['ringtone'] as String?,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is IosCallParams &&
        other.iconName == iconName &&
        other.handleType == handleType &&
        other.supportsVideo == supportsVideo &&
        other.supportsHolding == supportsHolding &&
        other.supportsGrouping == supportsGrouping &&
        other.maximumCallGroups == maximumCallGroups &&
        other.maximumCallsPerCallGroup == maximumCallsPerCallGroup &&
        other.audioSessionMode == audioSessionMode &&
        other.audioSessionPreferredSampleRate ==
            audioSessionPreferredSampleRate &&
        other.audioSessionPreferredIOBufferDuration ==
            audioSessionPreferredIOBufferDuration &&
        other.ringtone == ringtone;
  }

  @override
  int get hashCode {
    return Object.hash(
      iconName,
      handleType,
      supportsVideo,
      supportsHolding,
      supportsGrouping,
      maximumCallGroups,
      maximumCallsPerCallGroup,
      audioSessionMode,
      audioSessionPreferredSampleRate,
      audioSessionPreferredIOBufferDuration,
      ringtone,
    );
  }

  @override
  String toString() {
    return 'IosCallParams('
        'iconName: $iconName, '
        'handleType: $handleType, '
        'supportsVideo: $supportsVideo, '
        'supportsHolding: $supportsHolding)';
  }
}

/// Parameters for showing or managing a native incoming or outgoing call.
///
/// This is the primary data class passed to [CallBundlePlatform.showIncomingCall]
/// and [CallBundlePlatform.showOutgoingCall].
///
/// Example:
/// ```dart
/// final params = NativeCallParams(
///   callId: 'abc-123',
///   callerName: 'Ravi Kumar',
///   callType: NativeCallType.video,
///   handle: 'Software Engineer',
///   extra: {'userId': '456'},
/// );
/// ```
@immutable
class NativeCallParams {
  /// Creates native call parameters.
  ///
  /// [callId] and [callerName] are required.
  const NativeCallParams({
    required this.callId,
    required this.callerName,
    this.callerAvatar,
    this.callType = NativeCallType.voice,
    this.handle,
    this.duration = 60000,
    this.extra = const <String, dynamic>{},
    this.android,
    this.ios,
  });

  /// Unique identifier for this call.
  ///
  /// Must be unique across all active calls. Used for event correlation
  /// and call management operations.
  final String callId;

  /// Display name of the caller.
  ///
  /// Shown as the primary text in the native call UI.
  final String callerName;

  /// Optional URL for the caller's avatar image.
  ///
  /// Used in notifications on Android. Not used in iOS CallKit UI
  /// (CallKit uses the contact's photo from Contacts.framework).
  final String? callerAvatar;

  /// The type of call (voice or video).
  ///
  /// Defaults to [NativeCallType.voice].
  final NativeCallType callType;

  /// Optional subtitle/handle text.
  ///
  /// Displayed as secondary text in the call UI.
  /// Example: job title, phone number, or "Video Call".
  final String? handle;

  /// Ring timeout duration in milliseconds.
  ///
  /// After this duration, the call is considered missed.
  /// Defaults to `60000` (60 seconds).
  final int duration;

  /// Pass-through metadata for the Dart layer.
  ///
  /// This data is included in [NativeCallEvent.extra] when events
  /// are received, allowing the app to correlate events with
  /// application-specific data.
  final Map<String, dynamic> extra;

  /// Android-specific parameter overrides.
  final AndroidCallParams? android;

  /// iOS-specific parameter overrides.
  final IosCallParams? ios;

  /// Serializes this instance to a [Map] for MethodChannel transport.
  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      'callId': callId,
      'callerName': callerName,
      if (callerAvatar != null) 'callerAvatar': callerAvatar,
      'callType': callType.intValue,
      if (handle != null) 'handle': handle,
      'duration': duration,
      'extra': extra,
      if (android != null) 'android': android!.toMap(),
      if (ios != null) 'ios': ios!.toMap(),
    };
  }

  /// Creates an instance from a [Map] received via MethodChannel.
  factory NativeCallParams.fromMap(Map<String, dynamic> map) {
    return NativeCallParams(
      callId: map['callId'] as String,
      callerName: map['callerName'] as String,
      callerAvatar: map['callerAvatar'] as String?,
      callType: NativeCallType.fromString(
        map['callType']?.toString(),
      ),
      handle: map['handle'] as String?,
      duration: map['duration'] as int? ?? 60000,
      extra: Map<String, dynamic>.from(
        map['extra'] as Map<dynamic, dynamic>? ?? <String, dynamic>{},
      ),
      android: map['android'] != null
          ? AndroidCallParams.fromMap(
              Map<String, dynamic>.from(
                map['android'] as Map<dynamic, dynamic>,
              ),
            )
          : null,
      ios: map['ios'] != null
          ? IosCallParams.fromMap(
              Map<String, dynamic>.from(
                map['ios'] as Map<dynamic, dynamic>,
              ),
            )
          : null,
    );
  }

  /// Creates a copy with the specified fields replaced.
  NativeCallParams copyWith({
    String? callId,
    String? callerName,
    String? callerAvatar,
    NativeCallType? callType,
    String? handle,
    int? duration,
    Map<String, dynamic>? extra,
    AndroidCallParams? android,
    IosCallParams? ios,
  }) {
    return NativeCallParams(
      callId: callId ?? this.callId,
      callerName: callerName ?? this.callerName,
      callerAvatar: callerAvatar ?? this.callerAvatar,
      callType: callType ?? this.callType,
      handle: handle ?? this.handle,
      duration: duration ?? this.duration,
      extra: extra ?? this.extra,
      android: android ?? this.android,
      ios: ios ?? this.ios,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is NativeCallParams &&
        other.callId == callId &&
        other.callerName == callerName &&
        other.callerAvatar == callerAvatar &&
        other.callType == callType &&
        other.handle == handle &&
        other.duration == duration &&
        mapEquals(other.extra, extra) &&
        other.android == android &&
        other.ios == ios;
  }

  @override
  int get hashCode {
    return Object.hash(
      callId,
      callerName,
      callerAvatar,
      callType,
      handle,
      duration,
      Object.hashAll(extra.entries),
      android,
      ios,
    );
  }

  @override
  String toString() {
    return 'NativeCallParams('
        'callId: $callId, '
        'callerName: $callerName, '
        'callType: $callType, '
        'handle: $handle, '
        'duration: $duration)';
  }
}
