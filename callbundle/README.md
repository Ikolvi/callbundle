# CallBundle

A federated Flutter plugin for managing native incoming and outgoing call UI on iOS (CallKit) and Android (TelecomManager + Notification).

CallBundle is built with reliability, scalability, and enterprise standards in mind.

## Features

- **Native call UI** on both iOS (CallKit) and Android (TelecomManager + Notification)
- **MethodChannel-only** communication — no EventChannel, no silent event drops
- **Cold-start support** — deterministic handshake via PendingCallStore
- **OEM-adaptive notifications** — built-in budget OEM detection and adaptive strategies
- **Audio session management** — controlled `.mixWithOthers` mode, prevents HMS audio kill
- **PushKit in-plugin** — VoIP push handled inside the plugin, not in AppDelegate
- **Consumer ProGuard rules** — shipped in the plugin, no app-level configuration needed
- **Thread-safe state** — ConcurrentHashMap (Android), serial DispatchQueue (iOS)

## Getting Started

Add `callbundle` to your `pubspec.yaml`:

```yaml
dependencies:
  callbundle: ^1.0.0
```

## Usage

```dart
import 'package:callbundle/callbundle.dart';

// Configure the plugin
await CallBundle.configure(NativeCallConfig(
  appName: 'MyApp',
  android: AndroidCallConfig(phoneAccountLabel: 'MyApp Calls'),
  ios: IosCallConfig(supportsVideo: true),
));

// Listen for call events
CallBundle.onEvent.listen((event) {
  print('Call event: ${event.type} for call ${event.callId}');
});

// Show incoming call
await CallBundle.showIncomingCall(NativeCallParams(
  callId: 'unique-call-id',
  callerName: 'John Doe',
  callType: NativeCallType.audio,
  handle: '+1234567890',
));

// End a call
await CallBundle.endCall('unique-call-id');
```

## Links

- [GitHub Repository](https://github.com/Ikolvi/callbundle)
- [API Documentation](https://pub.dev/documentation/callbundle/latest/)
- [Ikolvi](https://ikolvi.com)
