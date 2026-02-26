import 'package:flutter/foundation.dart';

import 'native_call_enums.dart';

/// Permission and diagnostic information for native call functionality.
///
/// Returned by [CallBundlePlatform.requestPermissions] to provide
/// a comprehensive view of the device's permission state and
/// OEM-specific diagnostics.
@immutable
class NativeCallPermissions {
  /// Creates a native call permissions instance.
  const NativeCallPermissions({
    required this.notificationPermission,
    required this.fullScreenIntentPermission,
    required this.phoneAccountEnabled,
    required this.batteryOptimizationExempt,
    this.oemAutoStartEnabled,
    required this.manufacturer,
    required this.model,
    required this.osVersion,
    this.diagnosticInfo = const <String, dynamic>{},
  });

  /// Current notification permission status.
  final PermissionStatus notificationPermission;

  /// Full-screen intent permission status (Android 14+ only).
  ///
  /// On iOS or older Android versions, this is always [PermissionStatus.granted].
  final PermissionStatus fullScreenIntentPermission;

  /// Whether the PhoneAccount is enabled in Android TelecomManager.
  ///
  /// On iOS, this is always `true`.
  final bool phoneAccountEnabled;

  /// Whether the app is exempt from battery optimization (Doze mode).
  ///
  /// On iOS, this is always `true`.
  final bool batteryOptimizationExempt;

  /// Whether OEM auto-start permission is enabled.
  ///
  /// `null` if undetectable on this device.
  /// Relevant for OEMs like Xiaomi, Huawei, OPPO, Vivo.
  final bool? oemAutoStartEnabled;

  /// Device manufacturer (e.g., "samsung", "xiaomi").
  final String manufacturer;

  /// Device model (e.g., "SM-A515F").
  final String model;

  /// OS version string (e.g., "34" for Android SDK, "17.0" for iOS).
  final String osVersion;

  /// Detailed per-OEM diagnostic information.
  ///
  /// Contains manufacturer-specific data useful for debugging
  /// notification delivery issues.
  final Map<String, dynamic> diagnosticInfo;

  /// Whether all critical permissions are granted for full functionality.
  bool get isFullyReady {
    return notificationPermission.isGranted &&
        fullScreenIntentPermission.isGranted &&
        phoneAccountEnabled &&
        batteryOptimizationExempt;
  }

  /// Serializes this instance to a [Map] for MethodChannel transport.
  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      'notificationPermission': notificationPermission.name,
      'fullScreenIntentPermission': fullScreenIntentPermission.name,
      'phoneAccountEnabled': phoneAccountEnabled,
      'batteryOptimizationExempt': batteryOptimizationExempt,
      if (oemAutoStartEnabled != null)
        'oemAutoStartEnabled': oemAutoStartEnabled,
      'manufacturer': manufacturer,
      'model': model,
      'osVersion': osVersion,
      'diagnosticInfo': diagnosticInfo,
    };
  }

  /// Creates an instance from a [Map] received via MethodChannel.
  factory NativeCallPermissions.fromMap(Map<String, dynamic> map) {
    return NativeCallPermissions(
      notificationPermission: PermissionStatus.fromString(
        map['notificationPermission'] as String?,
      ),
      fullScreenIntentPermission: PermissionStatus.fromString(
        map['fullScreenIntentPermission'] as String?,
      ),
      phoneAccountEnabled: map['phoneAccountEnabled'] as bool? ?? false,
      batteryOptimizationExempt:
          map['batteryOptimizationExempt'] as bool? ?? false,
      oemAutoStartEnabled: map['oemAutoStartEnabled'] as bool?,
      manufacturer: map['manufacturer'] as String? ?? 'unknown',
      model: map['model'] as String? ?? 'unknown',
      osVersion: map['osVersion'] as String? ?? 'unknown',
      diagnosticInfo: Map<String, dynamic>.from(
        map['diagnosticInfo'] as Map<dynamic, dynamic>? ??
            <String, dynamic>{},
      ),
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is NativeCallPermissions &&
        other.notificationPermission == notificationPermission &&
        other.fullScreenIntentPermission == fullScreenIntentPermission &&
        other.phoneAccountEnabled == phoneAccountEnabled &&
        other.batteryOptimizationExempt == batteryOptimizationExempt &&
        other.oemAutoStartEnabled == oemAutoStartEnabled &&
        other.manufacturer == manufacturer &&
        other.model == model &&
        other.osVersion == osVersion &&
        mapEquals(other.diagnosticInfo, diagnosticInfo);
  }

  @override
  int get hashCode {
    return Object.hash(
      notificationPermission,
      fullScreenIntentPermission,
      phoneAccountEnabled,
      batteryOptimizationExempt,
      oemAutoStartEnabled,
      manufacturer,
      model,
      osVersion,
      Object.hashAll(diagnosticInfo.entries),
    );
  }

  @override
  String toString() {
    return 'NativeCallPermissions('
        'notificationPermission: $notificationPermission, '
        'fullScreenIntentPermission: $fullScreenIntentPermission, '
        'phoneAccountEnabled: $phoneAccountEnabled, '
        'batteryOptimizationExempt: $batteryOptimizationExempt, '
        'manufacturer: $manufacturer, '
        'model: $model, '
        'osVersion: $osVersion, '
        'isFullyReady: $isFullyReady)';
  }
}
