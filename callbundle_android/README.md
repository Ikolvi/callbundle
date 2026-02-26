# callbundle_android

[![pub package](https://img.shields.io/pub/v/callbundle_android.svg)](https://pub.dev/packages/callbundle_android)

The Android implementation of [`callbundle`](https://pub.dev/packages/callbundle).

---

## Usage

This package is **endorsed** — simply add `callbundle` to your `pubspec.yaml` and this package is included automatically on Android.

```yaml
dependencies:
  callbundle: ^1.0.0
```

---

## Architecture

| Component | File | Responsibility |
|-----------|------|----------------|
| `CallBundlePlugin` | `CallBundlePlugin.kt` | MethodChannel handler, lifecycle, permission requests |
| `CallConnectionService` | `CallConnectionService.kt` | Android TelecomManager ConnectionService |
| `NotificationHelper` | `NotificationHelper.kt` | OEM-adaptive notification builder |
| `CallStateManager` | `CallStateManager.kt` | Thread-safe in-memory call tracking |
| `PendingCallStore` | `PendingCallStore.kt` | `PendingCallStore` | `PendingCallStore.kt` | `PendingCallStore` | `PendingCallStoudget OEM manufacturer d| `PendingCallStore` | `PendingCallStore.kt` | `PendingCallStore` | `PendingCallStore.kt` | `PendingCallStore` |
## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## #rs (Xiaomi, Oppo, Vivo,## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## #rs (Xiaomi, Oppo, Vivo,## ## ## ## ## ## ## ## ##forIncomingCall()` — native ## ## ## ## ## ## #tion
- **Standard OEMs (API 26-30):** High-priority notification with Accept/Decline action buttons
- **Budget OEMs:** Simplest layout — avoids `RemoteViews` inflation failures common on budget devices

### Cold-Start Event Persistence

When the app is killed and user taps Accept:

1. `CallActionReceiver` fires (works even when app is killed)
2. If plugin is alive → normal event flow
3. If plugin is null → `PendingCallStore.savePendingAccept()` via `SharedPreferences.commit()` (synchronous)
4. App restarts → `configure()` → `deliverPendingEvents()` → event delivered to Dart

### Consumer ProGuard Rules

Shipped in `proguard-rules.pro` — automatically applied to consumer apps. No app-level ProGuard configuration needed.

##########################################################################################################################################################################################################################################################################################ERT_WINDOW
```

### Permission Requesting

- **`checkPermissions`**: Returns current status withou- **`checkPermissions`**: Returns current status withou- **`checkPermissions`**: Returns current statu
                            for `USE                            for `USE       
| | | | | | | | | | | | | | | | | | | | | | | | | | | | | | |  (Android 5.0) |
| Compile SDK | 35 |
| Kotlin| Kotlin| Kotlin| Kotlin| Kotlin| Kotlin| Kotlin| Kotlin| Kotlin| Kotlin| Kotlin| Kotlin| Kotlin| Kotlin|bundle)
- [Implementation Guide](https://github.com/Ikolvi/callbundle/blob/main/callbundle/README.md)
- [GitHub Repository](https://github.com/Ikolvi/callbundle)
- [Ikolvi](https://ikolvi.com)
