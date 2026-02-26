# CallBundle

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Flutter](https://img.shields.io/badge/Flutter-%E2%89%A53.10-blue.svg)](https://flutter.dev)

**Native incoming & outgoing call UI for Flutter.**

CallBundle provides CallKit on iOS and TelecomManager + OEM-adaptive notifications on Android — as a single, reliable federated plugin.

Built by [Ikolvi](https://ikolvi.com).

---

## Features

| Feature | iOS | Android |
|---------|-----|---------|
| Native incoming call UI | CallKit | TelecomManager + Notification |
| Native outgoing call UI | CallKit | TelecomManager |
| VoIP push token | PushKit | — |
| Cold-start call acceptance | PendingCallStore | PendingCallStore |
| OEM-adaptive notifications | — | Budget OEM detection |
| Missed call notifications | UNNotification | NotificationCompat |
| Audio session management | AVAudioSession | MediaPlayer |
| Consumer ProGuard rules | — | Built-in |

## Architecture

CallBundle uses Flutter's **federated plugin** architecture:

```
callbundle/                          # App-facing package (what you import)
callbundle_platform_interface/       # Abstract API + data models
callbundle_android/                  # Android implementation (Kotlin)
callbundle_ios/                      # iOS implementation (Swift)
example/                             # Demo app
```

## Installation

Add to your `pubspec.yaml`:

```yaml
dependencies:
  callbundle:
    git:
      url: https://github.com/Ikolvi/callbundle.git
      path: callbundle
```

## Quick Start

```dart
import 'package:callbundle/callbundle.dart';

// 1. Listen for events
CallBundle.onEvent.listen((event) {
  switch (event.type) {
    case NativeCallEventType.accepted:
      print('Call accepted: ${event.callId}');
    case NativeCallEventType.declined:
      print('Call declined: ${event.callId}');
    case NativeCallEventType.ended:
      print('Call ended: ${event.callId} (user: ${event.isUserInitiated})');
    default:
      break;
  }
});

// 2. Configure
await CallBundle.configure(
  const NativeCallConfig(
    appName: 'My App',
    android: AndroidCallConfig(
      phoneAccountLabel: 'My App Calls',
    ),
    ios: IosCallConfig(
      supportsVideo: false,
      includesCallsInRecents: true,
    ),
  ),
);

// 3. Show incoming call
await CallBundle.showIncomingCall(
  NativeCallParams(
    callId: 'unique-call-id',
    callerName: 'John Doe',
    handle: '+1 234 567 8900',
    callType: NativeCallType.voice,
    android: const AndroidCallParams(),
    ios: const IosCallParams(),
  ),
);

// 4. End call
await CallBundle.endCall('unique-call-id');
```

## API Reference

### CallBundle (Static API)

| Method | Description |
|--------|-------------|
| `configure(NativeCallConfig)` | Initialize the plugin with app configuration |
| `showIncomingCall(NativeCallParams)` | Display native incoming call UI |
| `showOutgoingCall(NativeCallParams)` | Display native outgoing call UI |
| `endCall(String callId)` | End a specific call |
| `endAllCalls()` | End all active calls |
| `setCallConnected(String callId)` | Mark a call as connected |
| `getActiveCalls()` | Get list of active calls |
| `requestPermissions()` | Request required permissions |
| `getVoipToken()` | Get iOS VoIP push token |
| `onEvent` | Stream of `NativeCallEvent` |
| `onReady` | Future that completes when native side is ready |
| `dispose()` | Clean up resources |

### Key Event: `isUserInitiated`

Every `NativeCallEvent` includes `isUserInitiated`:
- `true` — User tapped accept/decline on the native UI
- `false` — Programmatic end from Dart (e.g., `CallBundle.endCall()`)

This eliminates the `_isEndingCallKitProgrammatically` flag pattern.

## Pain Points Solved

| # | Problem | Solution |
|---|---------|----------|
| 1 | EventChannel accept events silently dropped | MethodChannel for ALL communication |
| 2 | 3 parallel accept-detection paths | Single MethodChannel path |
| 3 | Budget OEM notifications silently fail | Built-in OEM-adaptive strategy |
| 4 | Cold-start 3-second hardcoded delay | Deterministic PendingCallStore handshake |
| 5 | Gradle injection on every build | Standard federated plugin registration |
| 6 | iOS audio session conflict | AudioSessionManager with `.mixWithOthers` |
| 7 | 16 ProGuard keep rules in app | Consumer ProGuard rules shipped in plugin |
| 8 | Background isolate crashes | BackgroundIsolateBinaryMessenger support |
| 9 | 437-line fallback plugin in app code | Built-in AdaptiveCallNotification |

## Requirements

| Platform | Minimum |
|----------|---------|
| Flutter | 3.10+ |
| Dart | 3.0+ |
| iOS | 13.0+ |
| Android | API 21+ (Android 5.0) |

## Development

```bash
# Install dependencies
melos bootstrap

# Run analysis
melos run analyze

# Run tests
melos run test

# Format code
melos run format
```

## License

MIT License — see [LICENSE](LICENSE) for details.
