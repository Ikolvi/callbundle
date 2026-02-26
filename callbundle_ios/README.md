# callbundle_ios

[![pub package](https://img.shields.io/pub/v/callbundle_ios.svg)](https://pub.dev/packages/callbundle_ios)

The iOS implementation of [`callbundle`](https://pub.dev/packages/callbundle).

---

## Usage

This package is **endorsed** — simply add `callbundle` to your `pubspec.yaml` and this package is included automatically on iOS.

```yaml
dependencies:
  callbundle: ^1.0.0
```

---

## Architecture

| Component | File | Responsibility |
|-----------|------|----------------|
| `CallBundlePlugin` | `CallbundleIosPlugin.swift` | MethodChannel handler, singleton access, event dispatch |
| `CallKitController` | `CallKitController.swift` | CXProvider + CXCallController for native call UI |
| `PushKitHandler` | `PushKitHandler.swift` | PKPushRegistry delegate, VoIP token management |
| `AudioSessionManager` | `AudioSessionManager.swift` | AVAudioSession with `.mixWithOthers` |
| `CallStore` | `CallStore.swift` | Thread-safe call tracking + cold-start persistence |
| `MissedCallNotificationManager` | `MissedCallNotificationManager.swift` | UNUserNotificationCenter for missed calls |

---

## Key Features

### PushKit In-Plugin

PushKit VoIP push is handled **inside the plugin**. No AppDelegate code needed.

When a VoIP push arrives:
1. `PushKitHandler` receives the payload via `PKPushRegistryDelegate`
2. `reportNewIncomingCall` is called **synchronously** (required by iOS — app is terminated if not)
3. CallKit3. CallKit native incoming call screen3. CallKit3. Ca → event delivered to Dart via MethodChannel

**Required `Info.plist` entry:**
```xml
<key>UIBackgroundModes</key>
<array>
    <string>voip</string>
</array>
```

### CallKit Integration

Full `CXProvider` delegate with:
- Incoming call reporting (`reportNewIncomingCall`)
- Outgoing call initiation (`CXStartCallAction`)
- Call connected/ended state management
- `isUserInitiated` on every event (no `_isEndingCallKitProgrammatically` flag)

### Audio Session Management

`AudioSessionManager` configures `AVAudioSession` with `.mixWithOthers`:
- Prevents conflict with HMS/100ms audio sessions
- Activates on call connect, deactivates on call end
- Configures `.playAndRecord` category with `.defaultToSpeaker` option

### Cold-Start Persistence

`CallStore` `CallStore` `CallStore` ba`CallStore` `CallStore` `CallStore` ba`CallStore` `CallStore` `CallSsy`CallStore` `CallStore` `CallStore` ba`CallStore` `CallStore` `CallStore` ba`CallStore` `CallStore` `configure()` via `deliverPendingEvents()`

### Thread Safety

All `CallStore` operations use a serial `DispatchQueue`:
```swift
private let queue = DispatchQueue(label: "com.callbundle.callstore", qos: .userInitiated)
```

### Permission Checking

- **`checkPermissions`**: Reads `UNNotificationCenter.getNotificationSettings()` without requesting
- **`requestPermissions`**: Calls `UNUserNotificationCenter.requestAuthorization- **`requestPermissions`**: Calls `UNUserNotificationCenter.requestAu| Purpose |
|-----------|---------|
| `CallKit` | Native incoming/outgoing call UI |
| `PushKit` | VoIP push notification delivery |
| `AVFoundation` | Audio session management |
| `UserNotifications` | Missed call notifications |

---

## Requirements

| Requirement | Value |
|-------------|-------|
| iOS | 13.0+ |
| Swift | 5.0+ |
| CocoaPods | 1.10+ |

---

## Links

- [CallBundle on pub.dev](https://pub.dev/packages/callbundle)
- [Implementation Guide](https://github.com/Ikolvi/callbundle/blob/main/callbundle/README.md)
- [GitHub Repository](https://github.com/Ikolvi/callbundle)
- [Ikolvi](https://ikolvi.com)
