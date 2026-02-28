# CallBundle

[![pub package](https://img.shields.io/pub/v/callbundle.svg)](https://pub.dev/packages/callbundle)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/Ikolvi/callbundle/blob/main/LICENSE)

Native incoming & outgoing call UI for Flutter. Provides CallKit on iOS and TelecomManager + OEM-adaptive notifications on Android.

---

## Table of Contents

1. [Installation](#installation)
2. [Platform Setup](#platform-setup)
3. [Basic Usage](#basic-usage)
4. [API Reference](#api-reference)
5. [Permissions](#permissions)
6. [FCM Integration](#fcm-integration)
7. [iOS VoIP Push (PushKit)](#ios-voip-push-pushkit)
8. [Cold-Start Handling](#cold-start-handling)
9. [Event Handling](#event-handling)
10. [Configuration Options](#configuration-options)
11. [Background Reject (Killed State)](#background-reject-killed-state)
12. [Advanced Usage](#advanced-usage)

---

## Installation

```yaml
dependencies:
  callbundle: ^1.0.0
```

The Android (`callbundle_android`) and iOS (`callbundle_ios`) packages are **endorsed** — they are automatically included. No additional dependency lines needed.

---

## Platform Setup

### Android

No additional setup needed. The plugin ships:

- **AndroidManifest.xml** with all required permissions (auto-merged)
- **Consumer ProGuard rules** (no app-level rules needed)
- **ConnectionService** and **BroadcastReceiver** registration

Permissions shipped by the plugin:

```
FOREGROUND_SERVICE
FOREGROUND_SERVICE_PHONE_CALL
USE_FULL_SCREEN_INTENT
MANAGE_OWN_CALLS
WAKE_LOCK
VIBRATE
POST_NOTIFICATIONS
READ_PHONE_STATE
READ_PHONE_NUMBERS
SYSTEM_ALERT_WINDOW
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
```

### iOS

Add the VoIP background mode to your `Info.plist`:

```xml
<key>UIBackgroundModes</key>
<array>
    <string>voip</string>
</array>
```

The plugin handles PushKit registration internally — **no AppDelegate code needed**.

For complete iOS setup including VoIP certificate configuration, see the [callbundle_ios README](https://pub.dev/packages/callbundle_ios).

---

## Basic Usage

```dart
import 'package:callbundle/callbundle.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // 1. Listen for call events BEFORE configure
  CallBundle.onEvent.listen(_handleCallEvent);

  // 2. Configure the plugin
  await CallBundle.configure(const NativeCallConfig(
    appName: 'MyApp',
    android: AndroidCallConfig(
      phoneAccountLabel: 'MyApp Calls',
      notificationChannelName: 'Incoming Calls',
    ),
    ios: IosCallConfig(
      supportsVideo: false,
      maximumCallGroups: 1,
      maximumCallsPerCallGroup: 1,
      includesCallsInRecents: true,
    ),
  ));

  // 3. Check and request permissions
  await _handlePermissions();

  runApp(const MyApp());
}

void _handleCallEvent(NativeCallEvent event) {
  switch (event.type) {
    case NativeCallEventType.accepted:
      // User tapped Accept — connect VoIP
      connectToRoom(event.callId, event.extra);
      break;
    case NativeCallEventType.declined:
      // User tapped Decline
      notifyServerCallDeclined(event.callId);
      break;
    case NativeCallEventType.ended:
      if (event.isUserInitiated) {
        // User ended from native UI
        disconnectFromRoom(event.callId);
      }
      // else: programmatic end from your code, already handled
      break;
    default:
      break;
  }
}
```

---

## API Reference

### CallBundle (Static API)

| Method | Returns | Description |
|--------|---------|-------------|
| `configure(NativeCallConfig)` | `Future<void>` | Initialize plugin. Call once at startup. |
| `showIncomingCall(NativeCallParams)` | `Future<void>` | Show native incoming call UI. |
| `showOutgoingCall(NativeCallParams)` | `Future<void>` | Show native outgoing call UI. |
| `endCall(String callId)` | `Future<void>` | End a specific call. |
| `endAllCalls()` | `Future<void>` | End all active calls. |
| `setCallConnected(String callId)` | `Future<void>` | Mark call as connected/active. |
| `getActiveCalls()` | `Future<List<NativeCallInfo>>` | Get all active calls. |
| `checkPermissions()` | `Future<NativeCallPermissions>` | Check status without prompting. |
| `requestPermissions()` | `Future<NativeCallPermissions>` | Request permissions (triggers system dialogs). |
| `requestBatteryOptimizationExemption()` | `Future<bool>` | Request Doze mode exemption (Android). |
| `getVoipToken()` | `Future<String?>` | Get iOS VoIP push token. |
| `onEvent` | `Stream<NativeCallEvent>` | All native call events. |
| `onReady` | `Future<void>` | Completes when native side is ready. |
| `dispose()` | `Future<void>` | Release all resources. |

### NativeCallConfig

```dart
NativeCallConfig(
  appName: 'MyApp',                          // Required
  backgroundReject: BackgroundRejectConfig(   // Optional killed-state reject
    urlPattern: 'https://api.example.com/v1/api/calls/{callId}/reject',
    authStorageKey: 'access_token',
  ),
  android: const AndroidCallConfig(
    phoneAccountLabel: 'MyApp Calls',        // TelecomManager label
    notificationChannelName: 'Calls',        // Notification channel name
    oemAdaptiveMode: true,                   // Budget OEM detection
  ),
  ios: IosCallConfig(
    supportsVideo: false,                    // Video call support
    maximumCallGroups: 1,                    // Max concurrent call groups
    maximumCallsPerCallGroup: 1,             // Max calls per group
    includesCallsInRecents: true,            // Show in Phone app Recents
    iconTemplateImageName: null,             // Custom CallKit icon
    ringtoneSound: null,                     // Custom ringtone filename
  ),
)
```

### NativeCallParams

```dart
NativeCallParams(
  callId: 'unique-id',                       // Required — unique identifier
  callerName: 'John Doe',                    // Required — displayed to user
  handle: '+1234567890',                     // Phone number or identifier
  callType: NativeCallType.voice,            // voice or video
  duration: 60000,                           // Auto-dismiss timeout (ms)
  callerAvatar: 'https://...',               // Avatar URL (both platforms)
  extra: {'roomId': 'abc'},                  // Pass-through metadata
  android: const AndroidCallParams(),        // Android-specific options
  ios: const IosCallParams(                  // iOS-specific options
    handleType: NativeHandleType.phone,
  ),
)
```

### NativeCallEvent

| Property | Type | Description |
|----------|------|-------------|
| `type` | `NativeCallEventType` | `accepted`, `declined`, `ended`, `incoming`, `missed`, `timedOut` |
| `callId` | `String` | The call identifier |
| `isUserInitiated` | `bool` | `true` if user tapped the native UI button |
| `extra` | `Map<String, dynamic>` | Pass-through metadata from `NativeCallParams.extra` |
| `eventId` | `int` | Monotonic ID for deduplication |
| `timestamp` | `DateTime` | When the event occurred |

### NativeCallPermissions

| Property | Type | Description |
|----------|------|-------------|
| `notificationPermission` | `PermissionStatus` | Notification permission status |
| `fullScreenIntentPermission` | `PermissionStatus` | Full-screen intent (Android 14+) |
| `phoneAccountEnabled` | `bool` | TelecomManager account registered |
| `batteryOptimizationExempt` | `bool` | Exempt from battery optimization |
| `oemAutoStartEnabled` | `bool` | OEM auto-start enabled |
| `manufacturer` | `String` | Device manufacturer |
| `model` | `String` | Device model |
| `osVersion` | `String` | OS version string |
| `diagnosticInfo` | `Map?` | OEM detection diagnostics |
| `isFullyReady` | `bool` | All critical permissions granted |

---

## Permissions

CallBundle provides a **Dart-driven permission flow** — check silently, show your own UI, then request:

```dart
Future<void> _handlePermissions() async {
  // 1. Check current status (no prompts)
  final status = await CallBundle.checkPermissions();

  if (status.notificationPermission != PermissionStatus.granted) {
    // 2. Show YOUR custom explanation dialog
    final agreed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Enable Notifications'),
        content: const Text(
          'We need notification permission to show incoming call '
          'alerts. Without this, you may miss important calls.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Not Now'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Allow'),
          ),
        ],
      ),
    );

    // 3. Request only if user agreed
    if (agreed == true) {
      final result = await CallBundle.requestPermissions();
      print('After request: ${result.notificationPermission.name}');
    }
  }
}
```

### What `requestPermissions()` does per platform

| Platform | Action |
|----------|--------|
| **Android 13+** | System dialog for `POST_NOTIFICATIONS` |
| **Android 14+** | Opens Settings for `USE_FULL_SCREEN_INTENT` |
| **Android < 13** | No dialog needed (auto-granted) |
| **iOS** | `UNUserNotificationCenter.requestAuthorization()` |

### Battery Optimization Exemption

Battery optimization (Doze mode) on Android can prevent incoming calls from being delivered reliably:

```dart
final perms = await CallBundle.checkPermissions();
if (!perms.batteryOptimizationExempt) {
  final shouldRequest = await showBatteryExplanationDialog();
  if (shouldRequest) {
    final exempt = await CallBundle.requestBatteryOptimizationExemption();
    if (!exempt) {
      // System dialog shown — re-check after user returns
      final newPerms = await CallBundle.checkPermissions();
      print('Exempt: ${newPerms.batteryOptimizationExempt}');
    }
  }
}
```

| Platform | `checkPermissions()` | `requestBatteryOptimizationExemption()` |
|----------|---------------------|----------------------------------------|
| Android 23+ | `PowerManager.isIgnoringBatteryOptimizations()` | Opens `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |
| Android < 23 | Returns `true` | Returns `true` |
| iOS | Returns `true` | Returns `true` (not applicable) |

---

## FCM Integration

CallBundle handles the **native call UI** — your app handles **push delivery**:

```dart
@pragma('vm:entry-point')
Future<void> firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  WidgetsFlutterBinding.ensureInitialized();

  await CallBundle.configure(const NativeCallConfig(
    appName: 'MyApp',
    android: AndroidCallConfig(phoneAccountLabel: 'MyApp Calls'),
    ios: IosCallConfig(),
  ));

  await CallBundle.showIncomingCall(NativeCallParams(
    callId: message.data['callId'] ?? '',
    callerName: message.data['callerName'] ?? 'Unknown',
    handle: message.data['handle'] ?? '',
    callType: NativeCallType.voice,
    extra: message.data,
    android: const AndroidCallParams(),
    ios: const IosCallParams(),
  ));
}
```

---

## iOS VoIP Push (PushKit)

On iOS, use VoIP pushes for the most reliable incoming call experience. The plugin handles PushKit internally and reports the incoming call to CallKit synchronously (required by iOS).

```dart
// Get the VoIP token to register with your server
final token = await CallBundle.getVoipToken();
if (token != null) {
  await registerTokenWithServer(token);
}
```

For setting up VoIP push certificates (PEM file creation, APNs configuration), see the [callbundle_ios README — VoIP Certificate Setup](https://pub.dev/packages/callbundle_ios).

---

## Cold-Start Handling

When the app is **killed** and a user taps Accept on a notification:

### Android Flow

```
1. User taps Accept on notification
2. CallActionReceiver.onReceive() fires
3. If plugin alive → event delivered immediately via onEvent
4. If plugin null → PendingCallStore.savePendingAccept()
5. App restarts → configure() → deliverPendingEvents() → event delivered
```

### iOS Flow

```
1. VoIP push arrives → PushKit wakes app
2. reportNewIncomingCall() called synchronously
3. User taps Accept → CallKit delegate fires
4. If Dart ready → event sent immediately
5. If Dart not ready → CallStore.savePendingAccept()
6. Dart calls configure() → deliverPendingEvents() → event delivered
```

**No hardcoded delays.** Events are delivered as soon as `configure()` completes.

```dart
// Always listen BEFORE configure to catch cold-start events
CallBundle.onEvent.listen((event) {
  if (event.type == NativeCallEventType.accepted) {
    connectToVoipRoom(event.callId, event.extra);
  }
});

await CallBundle.configure(config); // Pending events delivered here
```

---

## Event Handling

### The `isUserInitiated` Pattern

Every event includes `isUserInitiated` to distinguish user actions from programmatic actions:

```dart
CallBundle.onEvent.listen((event) {
  if (event.type == NativeCallEventType.ended) {
    if (event.isUserInitiated) {
      // User tapped "End Call" on native UI
      disconnectRoom(event.callId);
      notifyServer(event.callId, 'ended_by_user');
    } else {
      // Your code called CallBundle.endCall()
      // No action needed — avoid double-disconnect
    }
  }
});
```

This eliminates the `_isEndingCallKitProgrammatically` flag pattern.

### Complete event handler

```dart
CallBundle.onEvent.listen((event) {
  switch (event.type) {
    case NativeCallEventType.incoming:
      prepareVoipConnection(event.callId);
      break;
    case NativeCallEventType.accepted:
      connectToRoom(event.callId, event.extra);
      break;
    case NativeCallEventType.declined:
      notifyServerCallDeclined(event.callId);
      break;
    case NativeCallEventType.ended:
      if (event.isUserInitiated) {
        disconnectRoom(event.callId);
        notifyServer(event.callId, 'ended');
      }
      break;
    case NativeCallEventType.missed:
      showMissedCallNotification(event.callId);
      break;
    default:
      break;
  }
});
```

---

## Configuration Options

### AndroidCallConfig

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `phoneAccountLabel` | `String` | **Required** | TelecomManager registration label |
| `notificationChannelName` | `String?` | `null` | Notification channel display name |
| `notificationChannelId` | `String?` | `null` | Custom notification channel ID |
| `useTelecomManager` | `bool` | `true` | Use ConnectionService + TelecomManager |
| `oemAdaptiveMode` | `bool` | `true` | Auto-detect budget OEMs |

### IosCallConfig

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `supportsVideo` | `bool` | `false` | Enable video call support |
| `maximumCallGroups` | `int` | `1` | Max concurrent call groups |
| `maximumCallsPerCallGroup` | `int` | `1` | Max calls per group |
| `includesCallsInRecents` | `bool` | `true` | Show in Phone app Recents |
| `iconTemplateImageName` | `String?` | `null` | Custom CallKit icon asset |
| `ringtoneSound` | `String?` | `null` | Custom ringtone filename |

---

## Background Reject (Killed State)

When the user declines a call from the notification while the app is **killed**, the Dart isolate is unavailable. `BackgroundRejectConfig` enables a **direct native HTTP request** from Kotlin, bypassing Dart entirely:

```dart
await CallBundle.configure(NativeCallConfig(
  appName: 'MyApp',
  backgroundReject: BackgroundRejectConfig(
    urlPattern: 'https://api.example.com/v1/api/calls/{callId}/reject',
    httpMethod: 'PUT',
    authStorageKey: 'access_token',
    headers: {'X-Call-Id': '{callId}'},
    body: '{"reason": "user_declined"}',
    refreshToken: RefreshTokenConfig(
      url: 'https://api.example.com/v1/auth/refresh-token',
      refreshTokenKey: 'refresh_token',
      bodyTemplate: '{"refreshToken": "{refreshToken}"}',
      accessTokenJsonPath: 'data.accessToken',
      refreshTokenJsonPath: 'data.refreshToken',
    ),
  ),
  android: const AndroidCallConfig(phoneAccountLabel: 'MyApp'),
  ios: const IosCallConfig(),
));
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `urlPattern` | `String` | **Required** | Full URL with `{key}` placeholders |
| `httpMethod` | `String` | `'PUT'` | HTTP method |
| `authStorageKey` | `String?` | `null` | Key in `flutter_secure_storage` for Bearer token |
| `authKeyPrefix` | `String?` | `null` | Custom key prefix for `flutter_secure_storage` |
| `headers` | `Map<String, String>` | `{}` | Additional request headers |
| `body` | `String?` | `null` | Request body (supports `{key}` placeholders) |

### Dynamic Placeholders

| Placeholder | Description |
|---|---|
| `{callId}` | Unique call identifier |
| `{callerName}` | Display name of the caller |
| `{callType}` | Type of call (voice, video) |
| `{handle}` | Phone number or SIP address |
| `{uuid}` | Auto-generated UUID per request |
| *any custom key* | Any extra from the notification |

> **iOS:** Not needed — CallKit/PushKit keep the app alive during calls.

For detailed background reject and token refresh docs, see the [callbundle_android README](https://pub.dev/packages/callbundle_android).

---

## Advanced Usage

### Outgoing calls

```dart
await CallBundle.showOutgoingCall(NativeCallParams(
  callId: 'outgoing-123',
  callerName: 'Jane Smith',
  handle: '+1987654321',
  callType: NativeCallType.voice,
  android: const AndroidCallParams(),
  ios: const IosCallParams(),
));

// When VoIP connects:
await CallBundle.setCallConnected('outgoing-123');

// When done:
await CallBundle.endCall('outgoing-123');
```

### Get active calls

```dart
final calls = await CallBundle.getActiveCalls();
for (final call in calls) {
  print('${call.callerName} — ${call.state.name}');
}
```

### Wait for native readiness

```dart
await CallBundle.onReady;
```

---

## Links

- [GitHub Repository](https://github.com/Ikolvi/callbundle)
- [API Documentation](https://pub.dev/documentation/callbundle/latest/)
- [Platform Interface](https://pub.dev/packages/callbundle_platform_interface)
- [Android Implementation](https://pub.dev/packages/callbundle_android)
- [iOS Implementation](https://pub.dev/packages/callbundle_ios)
- [Ikolvi](https://ikolvi.com)
