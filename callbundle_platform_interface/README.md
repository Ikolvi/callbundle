# callbundle_platform_interface

[![pub package](https://img.shields.io/pub/v/callbundle_platform_interface.svg)](https://pub.dev/packages/callbundle_platform_interface)

The platform interface for the [`callbundle`](https://pub.dev/packages/callbundle) plugin.

This package provides the **abstract API contract** and **data models** that all platform implementations must conform to.

---

## Overview

| Component | Description |
|-----------|-------------|
| `CallBundlePlatform` | Abstract class all implementations extend |
| `MethodChannelCallBundle` | Default MethodChannel-based implementation |
| `NativeCallConfig` | Plugin configuration (app name, platform options) |
| `BackgroundRejectConfig` | Native HTTP reject config for killed-state decline |
| `RefreshTokenConfig` | Token refresh config for automatic 401 retry |
| `NativeCallParams` | Incoming/outgoing call parameters |
| `NativeCallEvent` | Events from native to Dart |
| `NativeCallInfo` | Active call state |
| `NativeCallPermissions` | Permission status with diagnostics |
| `NativeCallType` | Voice/video enum |
| `NativeCallState` | Call state enum (ringing, active, held, ended) |
| `NativeCallEventType` | Event type enum |
| `PermissionStatus` | Permission states |
| `NativeHandleType` | Phone/email/generic handle types |

---

## MethodChannel Contract

Channel: `com.callbundle/main`

### Dart to Native

| Method | Arguments | Description |
|--------|-----------|-------------|
| `configure` | `Map` | Initialize with config |
| `showIncomingCall` | `Map` | Show incoming call UI |
| `showOutgoingCall` | `Map` | Show outgoing call UI |
| `endCall` | `String` | End specific call |
| `endAllCalls` | — | End all calls |
| `setCallConnected` | `String` | Mark call connected |
| `getActiveCalls` | — | Get active call list |
| `checkPermissions` | — | Check status (no prompts) |
| `requestPermissions` | — | Request (triggers dialogs) |
| `requestBatteryOptimizationExemption` | — | Request Doze exemption (Android) |
| `getVoipToken` | — | Get iOS VoIP token |
| `dispose` | — | Release resources |

### Native to Dart

| Method | Arguments | Description |
|--------|-----------|-------------|
| `onCallEvent` | `Map` | Call event delivery |
| `onVoipTokenUpdated` | `String` | VoIP token update |
| `onReady` | — | Native initialization complete |

---

## Creating a Custom Implementation

To implement a new platform (e.g., Web, Windows):

```dart
import 'package:callbundle_platform_interface/callbundle_platform_interface.dart';

class CallBundleWeb extends CallBundlePlatform {
  static void registerWith() {
    CallBundlePlatform.instance = CallBundleWeb();
  }

  @override
  Future<void> configure(NativeCallConfig config) async {
    // Your implementation
  }

  // ... implement all abstract methods
}
```

Register in `pubspec.yaml`:

```yaml
flutter:
  plugin:
    implements: callbundle
    platforms:
      web:
        dartPluginClass: CallBundleWeb
```

---

## Data Models

### NativeCallConfig

Top-level plugin configuration with platform-specific sub-configs:

- `appName` — Display name for the app
- `android` — `AndroidCallConfig` with TelecomManager, notification, and OEM settings
- `ios` — `IosCallConfig` with CallKit, video, and recents settings
- `backgroundReject` — `BackgroundRejectConfig` for killed-state native HTTP reject

### NativeCallParams

Parameters for showing incoming/outgoing calls:

- `callId` — Unique identifier
- `callerName` — Display name
- `handle` — Phone number or SIP address
- `callType` — Voice or video
- `duration` — Auto-dismiss timeout in milliseconds
- `extra` — Pass-through metadata (survives cold-start)
- `android` / `ios` — Platform-specific options

### NativeCallEvent

Events delivered from native to Dart:

- `type` — Event type (accepted, declined, ended, incoming, missed, timedOut)
- `callId` — Which call this event belongs to
- `isUserInitiated` — `true` for user taps, `false` for programmatic/system
- `extra` — Pass-through metadata from the original call
- `eventId` — Monotonic ID for deduplication
- `timestamp` — When the event occurred

### NativeCallPermissions

Comprehensive permission status with OEM diagnostics:

- `notificationPermission` — Notification permission status
- `fullScreenIntentPermission` — Full-screen intent (Android 14+)
- `phoneAccountEnabled` — TelecomManager account registered
- `batteryOptimizationExempt` — Doze mode exemption status
- `manufacturer`, `model`, `osVersion` — Device info
- `isFullyReady` — All critical permissions granted

### BackgroundRejectConfig

Native HTTP reject configuration for killed-state decline:

- `urlPattern` — URL with `{key}` placeholders
- `httpMethod` — HTTP method (default: PUT)
- `authStorageKey` — Key in `flutter_secure_storage` for Bearer token
- `headers`, `body` — Request headers and body with placeholder support
- `refreshToken` — `RefreshTokenConfig` for automatic 401 retry

### RefreshTokenConfig

Automatic token refresh when native reject receives 401:

- `url` — Refresh token endpoint
- `refreshTokenKey` — Key in `flutter_secure_storage` for refresh token
- `bodyTemplate` — Request body with `{refreshToken}` placeholder
- `accessTokenJsonPath` — Dot-notation path to new access token in response
- `refreshTokenJsonPath` — Dot-notation path to new refresh token (if rotated)

---

## Note on Breaking Changes

Strongly prefer non-breaking changes (such as adding a method to the interface) over breaking changes for this package.

---

## Links

- [CallBundle on pub.dev](https://pub.dev/packages/callbundle)
- [GitHub Repository](https://github.com/Ikolvi/callbundle)
- [Ikolvi](https://ikolvi.com)
