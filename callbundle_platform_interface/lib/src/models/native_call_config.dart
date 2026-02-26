import 'package:flutter/foundation.dart';

/// Android-specific configuration for the CallBundle plugin.
@immutable
class AndroidCallConfig {
  /// Creates Android-specific call configuration.
  const AndroidCallConfig({
    required this.phoneAccountLabel,
    this.phoneAccountIcon,
    this.useTelecomManager = true,
    this.oemAdaptiveMode = true,
    this.notificationChannelId,
    this.notificationChannelName,
  });

  /// Label for the PhoneAccount in Android Settings → Phone → Calling accounts.
  ///
  /// This is the name users see when managing call accounts.
  final String phoneAccountLabel;

  /// Icon resource name for the PhoneAccount.
  ///
  /// Must reference a drawable resource in the plugin or app.
  final String? phoneAccountIcon;

  /// Whether to use Android's TelecomManager (ConnectionService).
  ///
  /// If `true` (default), uses `ConnectionService` + `TelecomManager` for
  /// proper system integration. Falls back to notification-only if the
  /// device doesn't support it.
  ///
  /// If `false`, uses notification-only mode (always).
  final bool useTelecomManager;

  /// Whether to automatically detect budget OEMs and adapt notification strategy.
  ///
  /// When enabled, the plugin detects the device manufacturer and applies
  /// the optimal notification strategy for maximum reliability.
  ///
  /// Defaults to `true`.
  final bool oemAdaptiveMode;

  /// Custom notification channel ID override.
  final String? notificationChannelId;

  /// Custom notification channel display name override.
  final String? notificationChannelName;

  /// Serializes this instance to a [Map] for MethodChannel transport.
  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      'phoneAccountLabel': phoneAccountLabel,
      if (phoneAccountIcon != null) 'phoneAccountIcon': phoneAccountIcon,
      'useTelecomManager': useTelecomManager,
      'oemAdaptiveMode': oemAdaptiveMode,
      if (notificationChannelId != null)
        'notificationChannelId': notificationChannelId,
      if (notificationChannelName != null)
        'notificationChannelName': notificationChannelName,
    };
  }

  /// Creates an instance from a [Map] received via MethodChannel.
  factory AndroidCallConfig.fromMap(Map<String, dynamic> map) {
    return AndroidCallConfig(
      phoneAccountLabel: map['phoneAccountLabel'] as String,
      phoneAccountIcon: map['phoneAccountIcon'] as String?,
      useTelecomManager: map['useTelecomManager'] as bool? ?? true,
      oemAdaptiveMode: map['oemAdaptiveMode'] as bool? ?? true,
      notificationChannelId: map['notificationChannelId'] as String?,
      notificationChannelName: map['notificationChannelName'] as String?,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is AndroidCallConfig &&
        other.phoneAccountLabel == phoneAccountLabel &&
        other.phoneAccountIcon == phoneAccountIcon &&
        other.useTelecomManager == useTelecomManager &&
        other.oemAdaptiveMode == oemAdaptiveMode &&
        other.notificationChannelId == notificationChannelId &&
        other.notificationChannelName == notificationChannelName;
  }

  @override
  int get hashCode {
    return Object.hash(
      phoneAccountLabel,
      phoneAccountIcon,
      useTelecomManager,
      oemAdaptiveMode,
      notificationChannelId,
      notificationChannelName,
    );
  }

  @override
  String toString() {
    return 'AndroidCallConfig('
        'phoneAccountLabel: $phoneAccountLabel, '
        'useTelecomManager: $useTelecomManager, '
        'oemAdaptiveMode: $oemAdaptiveMode)';
  }
}

/// iOS-specific configuration for the CallBundle plugin.
@immutable
class IosCallConfig {
  /// Creates iOS-specific call configuration.
  const IosCallConfig({
    this.maximumCallGroups = 1,
    this.maximumCallsPerCallGroup = 1,
    this.includesCallsInRecents = true,
    this.supportsVideo = true,
    this.iconTemplateImageName,
    this.ringtoneSound,
  });

  /// Maximum number of concurrent call groups.
  ///
  /// Defaults to `1` (single call at a time).
  final int maximumCallGroups;

  /// Maximum number of calls per call group.
  ///
  /// Defaults to `1` (no conference).
  final int maximumCallsPerCallGroup;

  /// Whether calls appear in the iOS Phone app's Recent Calls list.
  ///
  /// Defaults to `true`.
  final bool includesCallsInRecents;

  /// Whether the provider supports video calls.
  ///
  /// Defaults to `true`.
  final bool supportsVideo;

  /// The name of the template image asset for the CallKit UI.
  ///
  /// Must be a single-color (template) image in the app bundle.
  /// Example: `"CallKitLogo"`.
  final String? iconTemplateImageName;

  /// The ringtone sound file name in the app bundle.
  ///
  /// Example: `"ringtone.caf"`. If not set, the system default is used.
  final String? ringtoneSound;

  /// Serializes this instance to a [Map] for MethodChannel transport.
  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      'maximumCallGroups': maximumCallGroups,
      'maximumCallsPerCallGroup': maximumCallsPerCallGroup,
      'includesCallsInRecents': includesCallsInRecents,
      'supportsVideo': supportsVideo,
      if (iconTemplateImageName != null)
        'iconTemplateImageName': iconTemplateImageName,
      if (ringtoneSound != null) 'ringtoneSound': ringtoneSound,
    };
  }

  /// Creates an instance from a [Map] received via MethodChannel.
  factory IosCallConfig.fromMap(Map<String, dynamic> map) {
    return IosCallConfig(
      maximumCallGroups: map['maximumCallGroups'] as int? ?? 1,
      maximumCallsPerCallGroup:
          map['maximumCallsPerCallGroup'] as int? ?? 1,
      includesCallsInRecents:
          map['includesCallsInRecents'] as bool? ?? true,
      supportsVideo: map['supportsVideo'] as bool? ?? true,
      iconTemplateImageName: map['iconTemplateImageName'] as String?,
      ringtoneSound: map['ringtoneSound'] as String?,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is IosCallConfig &&
        other.maximumCallGroups == maximumCallGroups &&
        other.maximumCallsPerCallGroup == maximumCallsPerCallGroup &&
        other.includesCallsInRecents == includesCallsInRecents &&
        other.supportsVideo == supportsVideo &&
        other.iconTemplateImageName == iconTemplateImageName &&
        other.ringtoneSound == ringtoneSound;
  }

  @override
  int get hashCode {
    return Object.hash(
      maximumCallGroups,
      maximumCallsPerCallGroup,
      includesCallsInRecents,
      supportsVideo,
      iconTemplateImageName,
      ringtoneSound,
    );
  }

  @override
  String toString() {
    return 'IosCallConfig('
        'maximumCallGroups: $maximumCallGroups, '
        'maximumCallsPerCallGroup: $maximumCallsPerCallGroup, '
        'includesCallsInRecents: $includesCallsInRecents, '
        'supportsVideo: $supportsVideo)';
  }
}

/// Configuration for initializing the CallBundle plugin.
///
/// Pass this to [CallBundle.configure] during app startup.
///
/// Example:
/// ```dart
/// await CallBundle.configure(NativeCallConfig(
///   appName: 'CommunityXo',
///   missedCallNotification: true,
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
@immutable
class NativeCallConfig {
  /// Creates a plugin configuration.
  const NativeCallConfig({
    required this.appName,
    this.defaultRingtone,
    this.defaultVibrationPattern,
    this.missedCallNotification = true,
    this.android,
    this.ios,
  });

  /// The app name displayed in notifications and system UI.
  final String appName;

  /// Default ringtone for calls that don't specify one.
  final String? defaultRingtone;

  /// Default vibration pattern for calls that don't specify one.
  final List<int>? defaultVibrationPattern;

  /// Whether to show a missed call notification when a call isn't answered.
  ///
  /// Defaults to `true`.
  final bool missedCallNotification;

  /// Android-specific configuration.
  final AndroidCallConfig? android;

  /// iOS-specific configuration.
  final IosCallConfig? ios;

  /// Serializes this instance to a [Map] for MethodChannel transport.
  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      'appName': appName,
      if (defaultRingtone != null) 'defaultRingtone': defaultRingtone,
      if (defaultVibrationPattern != null)
        'defaultVibrationPattern': defaultVibrationPattern,
      'missedCallNotification': missedCallNotification,
      if (android != null) 'android': android!.toMap(),
      if (ios != null) 'ios': ios!.toMap(),
    };
  }

  /// Creates an instance from a [Map] received via MethodChannel.
  factory NativeCallConfig.fromMap(Map<String, dynamic> map) {
    return NativeCallConfig(
      appName: map['appName'] as String,
      defaultRingtone: map['defaultRingtone'] as String?,
      defaultVibrationPattern:
          (map['defaultVibrationPattern'] as List<dynamic>?)
              ?.map((e) => e as int)
              .toList(),
      missedCallNotification:
          map['missedCallNotification'] as bool? ?? true,
      android: map['android'] != null
          ? AndroidCallConfig.fromMap(
              Map<String, dynamic>.from(
                map['android'] as Map<dynamic, dynamic>,
              ),
            )
          : null,
      ios: map['ios'] != null
          ? IosCallConfig.fromMap(
              Map<String, dynamic>.from(
                map['ios'] as Map<dynamic, dynamic>,
              ),
            )
          : null,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is NativeCallConfig &&
        other.appName == appName &&
        other.defaultRingtone == defaultRingtone &&
        listEquals(
          other.defaultVibrationPattern,
          defaultVibrationPattern,
        ) &&
        other.missedCallNotification == missedCallNotification &&
        other.android == android &&
        other.ios == ios;
  }

  @override
  int get hashCode {
    return Object.hash(
      appName,
      defaultRingtone,
      defaultVibrationPattern != null
          ? Object.hashAll(defaultVibrationPattern!)
          : null,
      missedCallNotification,
      android,
      ios,
    );
  }

  @override
  String toString() {
    return 'NativeCallConfig('
        'appName: $appName, '
        'missedCallNotification: $missedCallNotification)';
  }
}
