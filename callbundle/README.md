# CallBundle — Implementation Guide

[![pub package](https://img.shields.io/pub/v/callbundle.svg)](https://pub.dev/packages/callbundle)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/Ikolvi/callbundle/blob/main/LICENSE)

The app-facing package for CallBundle — native incoming & outgoing call UI for Flutter.

---

## Table of Contents

1. [Installation](#installation)
2. [Platform Setup](#platform-setup)
3. [Basic Usage](#basic-usage)
4. [API Reference](#api-reference)
5. [Permissions](#permissions)
6. [FCM Integration](#fcm-integration)
7. [Cold-Start Handling](#cold-start-handling)
8. [Event Handling](#event-handling)
9. [Configuration Options](#configuration-options)
10. [Advanced Usage](#advanced-usage)

---

## Installation

```yaml
dependencies:
  callbundle: ^1.0.0
```

The Android (`callbundle_android`) and iOS (`callbundle_ios`) packages are **endorsed** — they are automatically The Android (`callbundle_android`) and iOS (`callbundle_ios`) packages are d

No additional setup needed. The plugin ships:

- **AndroidManifest.xml** with all required permissions (auto-merged)
- **Consumer ProGuard rules** (no app-level rules needed)
- **ConnectionService** and **BroadcastReceiver** registration

Permissions shipped by tPermissions shipped by tPermissions shipped by tPermissions shipped by tPermisCREEN_INTENT
MANAGE_OWN_CALLS
WAKE_LOCK
VIBRATE
POST_NOTIFICATIONS
READ_PHONE_STATE
READ_PHONE_NUMBERS
SYSTEM_ALERT_WINDOW
```

### iOS

Add these to your `Info.plist`:

```xml
<key>UIBackgroundModes</key>
<array>
    <string>voip</string>
</array>
```

The plugin handles PushKit registration internally — no AppDelegate code needed.

---

## Basic Usage

```dart
import 'packagimport 'packagimport 'packagimporoid main() import 'packagetsFlutterBinding.ensureInitialized();

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
| `setCallConnected(String callId)` | | `setCallConnected(String callId)` | | `setCa. |
| `getActiveCalls()` | `Future<List<NativeCallInfo| `getActiveCalls()` | `Futs. |
| `checkPermissions()` | `Future<NativeCallPermissions>` | Check status without prompting. |
| `requestPermissions()` | `Future<NativeCallPermissions>` | Request permissions (triggers system dialogs). |
| `getVoipToken()` | `Future<String?>` | Get iOS VoIP push token. |
| `onEvent` | `Stream<NativeCallEvent>` | All native call events. |
| `onReady` | `Future<void>` | Completes when native side is ready. |
| `dispose()` | `Future<void>` | Release all resources. |

### NativeCallConfig

```dart
const NativeCallConfig(
  appName: 'MyApp',                    // Required
  android: AndroidCallConfig(
    phoneAccountLabel: 'MyApp Calls',  // TelecomManager label
    notificationChannelName: 'Calls',  // Notification cha    notificationChannelName: 'Calls',  // Notifi // Budget OEM detection
  ),
  ios: IosCallConfig(
    supportsVideo: false,         supportsVideo: false,         supportsVideo: false,         supportsVMax concurrent call groups
    maximumCallsPerCallGroup: 1,       // Max calls per group
    includesCallsInRecents: true,      // Show in phone Recents
    iconTemplateImageName: null,         iconTemplateImageName: null,         iconTempl              // Custom ringtone filename
  ),
)
```

### NativeCallParams

```dart
NativeCallParams(
  callId: 'unique-id',      callId: 'unique-id',      callId: 'unique-id',      callId: 'unie',             // Required — displayed to user
  handle: '+1234567890',              // Phone number or identifier
  callType: NativeCallType.voice,     // voice or video
  duration: 60000,                    // Auto-dismiss timeout (ms)
  callerAvatar: 'https://...',        // Avatar URL (Android only)
  extra: {'roomId': 'abc'},           // Pass-through metadata
  android: const AndroidCallParams(), // Android-specific options
  ios: const IosCallParams(           // iOS-specific options
    handleType: NativeHandleType.phone,
  ),
)
```

### NativeCallEvent

| Property | Type | Description |
|----------|------|-------------|
| `type` | `NativeCallEventType` | `accepted`, `declined`, `ended`, `incoming`, `missed` |
| `callId` | `String` | The call identifier |
| `isUserInitiated` | `bool` | `true` if user tapped the native UI button |
| `extra` | `Map<String, dynamic>` | Pass-through metadata from `NativeCallParams.extra` |
| `eventId` | `int` | Monotonic ID for deduplication |
| `timestamp` | `int` | Unix timestamp in milliseconds |

### NativeCallPermissions

| Property | Type | Description |
|----------|------|-------------|
| `notificationPermission` | `PermissionStatus` | Notification permission status |
| `fullScreenIntentPermission` | `PermissionStatus` | Full-screen intent (Android 14+) |
| `phoneAccountEnabled` | `bool` | TelecomManager account registered |
| `batteryOptimizationExempt` | `bool` | Exempt from battery optimization |
| `oemAutoStartEnabled` | `bool` | OEM auto-start enabled |
| `manufacturer` | `String` | Device manufacturer |
| `model` | `| `model` | `| `model` | | `osVersion` | `String` | OS version string |
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
                                                                                           onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Allow'),
          ),
        ],
      ),
    );

    // 3. Request only if user agreed
    if (agreed == true) {
      final result = await CallBundle.requestPermissions();
      print('After request: ${result.notificationPe      print('After request: ${res What `requestPermissions()` does per platform

| Platform | Action |
|----------|--------|
| **Android 13+** | System dialog for `POST_NOTIFICATIONS` |
| **Android 14+** | Opens Settings for `USE_FULL_SCREEN_INTENT` |
| **Android < 13** | No dialog needed (auto-granted) |
| **iOS** | `UNUserNotificationCenter.requestAuthorization| **iOS** | `UNUserNotifican

CallBundle handles the **native call UI** — your app handles **push delivery**. Here's the typical flow:

```dart
// In your Firebase messaging setup:
@pragma(@pragmary-point')
FutuFutuFutuFutuFutuFutuFutuFutuFutuFutuFutuFutuFutuoteMessaFutuFutuFutuFutuFutuFutuFutuFutuFutuFutuFutuFutuFutuoteMessaFutuFutuFutuFutuFutuFutuFutuFutuFutuFutuFutuFutuFutuoteMessaFutuFutuFutuCaFutuFutuFutuFutuFutuFutuFutuFutuFutuFuitFutllBundle.cFutuFutuFconst NativFuallConFutuFutuFutuFutuFutuFutuFutuFutuF  andFutuFutndroidCallConfig(phFutuFutuFutuFutuFutuFutuFutuFutuFutuFutuFu: IosCallConfig(),
    ));

    // Show the native incoming call UI
    await CallBundle.showIncomingCall(NativeCallParams(
      callId: message.data['callId'] ?? '',
      callerName: message.data['callerName'] ?? 'Unknown',
      handle: message.data['handle'] ?? '',
      callType: NativeCallType.voice,
      extra:      extra:      extra:      extra:      extra:     ()      extra:      extra:      extra:      extra:      extra:     ()      extriOS, use Vo      extra:      extra:      extra:      extra:      extra:     ()      extrahKit internally:

```dart
// Get the V// Get the V// Get the V// Get tserve// Get the V// Get it Call// Get the V// Get the V// Get the V// Get tserve// Get the V// Get it Call// Get the V// Get the V// Get the V// Get tserve// Get the V// Get it Call// Get the V// Get the V// Get the V// Get tserve// Get the V// Get it Call// Get the V// Get the V// Get the V// Get tserve// Get the V// Get it Call// Get the V// Get the V// Get the V// Get tserve// Get the V// Get it Call// Get the V// Get the  PushKit an// Get the V// Get the V// Get the Vhronously (required by iOS).

---

## Cold-Start Handling

When the app is **killed** and a user taps Accept on a notification:

### Flow (Android)

```
1. User ta1. User ta1. User ta1. User ta1. User ta1.eiver.onReceive() fires
3. If plugin ali3. If plugin ali3. If plugin ali3. If plugin ali3. If plugin ali3. If plugin ali3. If plugin ali3. If plugin ali3. If plugin ali3. If plugin ali3. If plugin ali3. If plugin ali3. If plugin ali3. If plugin ali3. If plugin ali3. If plugin ali3. If plored accept event delivered via onEvent stream
```

### Flow (iOS)

```
1. VoIP push arrives → PushKit wakes app
2. reportNewIncomingCall() called synchronously
3. User taps Accept → CallKit delegate fires
4. If Dart ready → event sent immediately
5. If Dart not ready → CallStore.savePendingAccept() (UserDefaults.synchronize)
6. Dart calls CallBundle.configure()
7. deliverPendingEvents() delivers stored event
```

**No hardcoded delays.** Events are delivered as soon as `configure()` completes, regardless of device speed.

### Handling cold-start in your app

```dart
// Always listen BEFORE configure to catch cold-start events
CallBundle.onEvent.listen((event) {
  if (event.type == NativeCallEventType.accepted) {
    // This fires for both live accepts AND cold-start accepts
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
  if (event.ty  if (event.ty  if (event.ty  if (event.ty  ifnt.isUserInitiated) {
      // User tapped "End Call" on native UI
      // → You need to disconnect your VoIP session
      disconnectRoom(event.callId);
      notifyServer(event.callId, 'ended_by_user');
    } else {
      // Your code called CallBundle.endCall()
      // → VoIP disconnect already handled by your code
      // → No action needed, avoid double-disconnect
    }
  }
});
```

This eliminates the `_isEndingCallKitProgrammatically` flag pattern.

### Typical complete event handler

```dart
CallCallCallCallCallCallCallCallCallCallCallC(event.type) {
    case NativeCallEventType.incoming:
      // Call is bei      // Call is bei      // Call is bei      //epareVoipConnection(event.callId);
      break;

    case NativeCallEventType.accepte :
      // User tapped       // User tapped       // Uscal      // User tapped       // User tapped       // Uscal      // User tapped       // Uase NativeCallEventType.declined:
      // User tapp      // User tapp      // User tapp      // User tapp      // User tapp      //NativeCallEventType.ended:
      if (event.isUs      if (event.isUs      if (event.isUs      if (ev      }
      break;

    case NativeCallEventType.missed:
      // Call timed out / auto-dismissed
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
| `phoneAccountLabel` | `String?` | `null` | TelecomManager registration label |
| `notificationChannelName` | `Stri| `notificationChannelName` | `Stri| io| `notificationChannelName` | `Stri| `notificationChannelName` | `Stri| io| `notificationCcation fallback |
| `ringto| `ringto| `ringto| `ringto| `ringto| Custom ringtone URI |

### IosCallConfig

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `supportsVideo` | `bool` | `false` | Enable vi| `supportsVideo` | `bool` | `false` | Enable vi| `supportsVideo` | `bool` | `false` | Enable vi| `supportsVidoup` | `int` | `1` | Max calls per group |
| `includesCallsInRecents` | `bool` | `true` | Show in Phone app R|cents |
| `iconTemplateImageName` | `String?` | `null` | Custom CallKit icon asset |
| `ringtoneSound` | `String?` | `null` | Custom ringtone filename |

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
  print('${call.callerName} — ${call.state}');
}
```

### Wait for ### Wait for ### Wait for ### Wait for ### Wait for ### Wait for ### Wait for ### Wait for ### Wait for ### Wait for ### Wait for ### Wait for ### Wait for ### Wait for ### Wait for ### ```

---

## Links

- [GitHub Repository](https://github.com/Ikolvi/callbundle)
- [API Doc- [API Doc- [API Doc- [API Doc- [API Doc- [API Docle/latest/)
- [Platform Interface](https://pub.dev/packages/callbundle_platform_interface)
- [Android- [Androitation](ht- [Android- [Andrkages/callb- [Android- [Androitation](ht- [Android- [Andrkagedev/packages/callbundle_ios)
- [Ikolvi](https://ikolvi.com)
