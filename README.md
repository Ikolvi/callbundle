# CallBundle

[![pub package](https://img.shields.io/pub/v/callbundle.svg)](https://pub.dev/packages/callbundle)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Flutter](https://img.shields.io/badge/Flutter-%E2%89%A53.10-blue.svg)](https://flutter.dev)

**Native incoming & outgoing call UI for Flutter.**

CallBundle provides CallKit on iOS and TelecomManager + OEM-adaptive notifications on Android — as a single, reliable federated plugin.

Built by [Ikolvi](https://ikolvi.com).

---

## Why CallBundle?

Existing call plugins suffer from silent event drops, cold-start failures, and OEM incompatibilities. CallBundle was built from scratch to solve these problems:

| Problem | CallBundle Solution |
|---------|---------------------|
| EventChannel accept events silently dropped | **MethodChannel for ALL communication** |
| 3 parallel accept-detection paths | **Single reliable MethodChannel path** |
| Budget OEM no| Budget OEM no| Budget OEM no| Budget OEM no| Budget OEM no| Bu strategy** |
| Cold-start 3-second hardcoded delay | **Deterministic PendingCallStore handshake** |
| iOS audio session conflict with HMS | **AudioSessionManager with `.mixWithOthers`** |
| 16 ProGuard keep rules in app | **Consumer ProGuard rules shipped in plugin** |
| 437-line fallback plugin in app code | **Built-in AdaptiveCallNotification** |
| `_isEndingCallKitProgrammatically` flag | **`isUserInitiated` field on every event** |

---

## Platform Support

| Feature | iOS | Android |
|---------|:---:|:-------:|
| Native incoming call UI | CallKit | TelecomManager + Notification |
| Native outgoing call UI | CallKit | Notification |
| VoIP push token management | PushKit | — |
| Cold-start call acceptance | UserDefaults | SharedPreferences |
| OEM-adaptive notifications | — | 18+ manufacturers detected |
| Missed call notifications | UNNotification | NotificationCompat |
| Audio session management | AVAudioSession | — |
| Consumer ProGuard rules | — | Built-in |
| Background isolate support | — | BinaryMess| Background isolate support | — | BinaryMess| Background isolate supportndle: ^1.0.0| Background isolate support | — | BinaryMess| Background isolate support | — | BiBu| Background isolate support | — | BinaryMess| Background isolroid: AndroidCallConfig(phoneAccountLabel: 'MyApp Calls'),
  ios: IosCallConfig(supportsVideo: false, includesCallsInRecents: true),
));

// 2. Listen for events
CallBundle.onEvent.listen((event) {
  switch (event.type) {
    case NativeCallEventType.accepted:
      prin      prin      prin      prin      prin      prin      pventType.declined:
      print('Call declined: ${event.callId}');
    case NativeCallEventType.ended:
      print      print      print      print      print      print      pri;
    default:
      break;
  }
});

// 3. Show incoming call
await CallBundle.showIncomingCall(NativeCallParams(
  callId: 'unique-call-id',
  callerName: 'John Doe',
  handle: '+1 234 567 8900',
  callType: NativeCallType.voice,
  android: const AndroidCallParams(),
  ios: const IosCallParams(),
));

// 4. End call
await CallBundle.endCall('unique-call-id');
```

> See the [full implementation guide](callbundle/README.md) for permissions, FCM integration, cold-start handling, and advanced usage.

---

## Documentation

| Document | Description |
|----------|-------------|
| [**Implementation Guide**](callbundle/README.md) | Full setup, API reference, permissions, FCM, cold-start |
| [**Platform Interface**](callbundle_platform_interface/README.md) | Abstract API contract and data models |
| [**Android Implementation**](callbundle_android/README.md) | TelecomManager, notifications, OEM detection |
| [**iOS Implementation**](callbundle_ios/README.md) | CallKit, PushKit, audio session management |
| [**Example App**](example/) | Working demo with all features |

---

## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ##��## ## ## ## ## ## #�──�## ## ## ## ## ## ## ##��─────┐
│         Your Flutter App        │
│   import 'callbundle.dart'      │
└──────────────┬──────────────────┘
               │
┌──────────────▼──────────────────┐
│      callbundle (app-facing)    │
│   Static CallBundle API class   │
└──────────────┬──────────────────┘
               │
┌──────────────▼─────────────────�┌──────────────▼─────────────────�┌─� Enums         │
└──────┬──────────────────┬───────┘
       │                  │
┌──────▼──────┐   ┌───────▼──────┐
│  callbundle │   │  callbundle  │
│  _android   │   │     _ios     │
│  (Kotlin)   │   │   (Swift)    │
└─────────────┘   └──────────────┘
```

All communication uses **MethodChannel** (`com.callbundle/main`) in both directions. No EventChannel, no WeakReference.

---

## Requirements

| Platform | Minimum |
|----------|---------|
| Flutter | 3.10+ |
| Dart SDK | 3.0+ |
| iOS | 13.0+ |
| Android | API 21 (Android 5.0) |
| Kotlin | 1.9+ |
| Swift | 5.0+ | Swift | 5.0+ |opm| Swift | 5.0+ | Swift | 5.0+ |opm| Swift | 5.0+ | Swift | 5.0+ |opm| Swift | 5.0+ | Swift | 5.0+ |opm| Swift | 5.0+ | Swift | 5.0+ |opm| Swift | 5.0+ | Swift | 5.0+ |opm| Swift | 5.0+ | Swift | 5.0+ |opm| Swift | 5.0+ |undle | Swift | 5.0+ | Swift | 5.0+ |opm| Swift | 5.0+ | Swift | 5.0+ |opm| Swift | 5.0+ | Swift | 5.0+ |opm| tter test
cd callbundle && fvm flucd callbundle && fvm fl_android && fvm flutter test
cd callbundle_ios && fvm flutter test

# Run # Run # Run # Run # Run # Run # Run # Run
```

---

## License

MIT License — see [LICENSE](LICENSE) for details.

**Built with reliability in mind by [Ikolvi](https://ikolvi.com).**
