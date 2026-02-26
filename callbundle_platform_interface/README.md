# callbundle_platform_interface

[![pub package](https://img.shields.io/pub/v/callbundle_platform_interface.svg)](https://pub.dev/packages/callbundle_platform_interface)

The platform interface for the [`callbundle`](https://pub.dev/packages/callbundle) plugin.

This package provides the **abstract API contract** and **data models** that all platform implementations must conform to.

---

## Overview

This package defines:

| Component | Description |
|-----------|-------------|
| `CallBundlePlatform` | Abstract class all implementations extend |
| `MethodChannelCallBundle` | Default MethodChannel-based implementation |
| `NativeCallConfig` | Plugin configuration (app name, platform options) |
| `NativeCallParams` | Incoming/outgoing call parameters |
| `NativeCallEvent` | Events from native → Dart |
| `NativeCallInfo` | Active call state |
| `NativeCallPermissions` | Permission status with diagnostics |
| `Nativ| `Nativ| `Nativ| `Nativ| `Nativ| `Nativ| `Nativ| `Nativ| `Nativ| `Nativ| `Nativ| `Nativ| `NatallEventType` | Event type enum |
| `PermissionStatus` | Permission states |
| `NativeHandleType` | Phone/email/generic handle types |

---

## MethodChannel Contract

Channel: `com.callbundle/main`

### Dart → Native

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
| `getVoipToken` | — | Get iOS VoIP token |
| `dispose` | — | Release resources |

### Native → Dart

| Method | Arguments | Description |
|--------|-----------|-------------|
| `onCallEvent` | `Map` | Call event delivery |
| `onVoipTokenUpdated` | `String` | VoIP token update |
| `onReady` | — | Native initialization complete |

---

## Creating a Custom Implementation

To implement a new platform (e.g., Web, Windows)To implement a new plakage:callbTo implement a newerfTo implement a new platform (e.g.,darTo implement a new platform (e.g CallBundlePlatform {
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

## Note on Breaking Changes

Strongly prefer non-breaking changes (such as adding a method to the interface) over breaking changes for this package.

---
---
ngly prefer non-breaking chanev](https://pub.dev/packages/callbundle)
- [GitHub Repository](https://github.com/Ikolvi/callbundle)
- [Ikolvi](https://ikolvi.com)
