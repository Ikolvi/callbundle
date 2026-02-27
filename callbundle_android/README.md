# callbundle_android

[![pub package](https://img.shields.io/pub/v/callbundle_android.svg)](https://pub.dev/packages/callbundle_android)

The Android implementation of [`callbundle`](https://pub.dev/packages/callbundle).

---

## Table of Contents

1. [Usage](#usage)
2. [Architecture](#architecture)
3. [OEM-Adaptive Notifications](#oem-adaptive-notifications)
4. [Cold-Start Event Persistence](#cold-start-event-persistence)
5. [Background Call Rejection (Killed State)](#background-call-rejection-killed-state)
6. [Automatic Token Refresh](#automatic-token-refresh)
7. [Consumer ProGuard Rules](#consumer-proguard-rules)
8. [Permissions](#permissions)
9. [Battery Optimization Exemption](#battery-optimization-exemption)
10. [Requirements](#requirements)

---

## Usage

This package is **endorsed** — simply add `callbundle` to your `pubspec.yaml` and this package is included automatically on Android.

```yaml
dependencies:
  callbundle: ^1.0.0
```

No additional Android setup needed. The plugin ships `AndroidManifest.xml` with all required permissions, `ConnectionService` registration, and consumer ProGuard rules.

---

## Architecture

| Component | File | Responsibility |
|-----------|------|----------------|
| `CallBundlePlugin` | `CallBundlePlugin.kt` | MethodChannel handler, lifecycle, permission requests |
| `CallConnectionService` | `CallConnectionService.kt` | Android TelecomManager ConnectionService |
| `NotificationHelper` | `NotificationHelper.kt` | OEM-adaptive notification builder |
| `CallStateManager` | `CallStateManager.kt` | Thread-safe in-memory call tracking |
| `PendingCallStore` | `PendingCallStore.kt` | SharedPreferences cold-start event persistence |
| `BackgroundCallRejectHelper` | `BackgroundCallRejectHelper.kt` | Native HTTP reject for killed-state decline |
| `CallActionReceiver` | `CallActionReceiver.kt` | BroadcastReceiver for notification actions |
| `OemDetector` | `OemDetector.kt` | Budget OEM manufacturer detection |

---

## OEM-Adaptive Notifications

The plugin auto-detects the device manufacturer and selects the optimal notification strategy:

- **Modern OEMs (API 31+):** `CallStyle.forIncomingCall()` — native system-style incoming call notification
- **Standard OEMs (API 26-30):** High-priority notification with Accept/Decline action buttons
- **Budget OEMs (Xiaomi, Oppo, Vivo, Realme, etc.):** Simplest layout — avoids `RemoteViews` inflation failures common on budget devices

### Static Media Resources

Ringtone (`mediaPlayer`) and vibration (`vibrator`) instances are **static/companion fields** shared across all `NotificationHelper` instances. This ensures reliable cleanup across background FCM engine instances.

### Notification Auto-Timeout

Incoming call notifications auto-dismiss after the configured `duration` (default 60s). A `timedOut` event is sent to Dart. This acts as a safety net for delayed `call_cancelled` FCM messages.

---

## Cold-Start Event Persistence

When the app is killed and user taps Accept or Decline:

1. `CallActionReceiver.onReceive()` fires (works even when app is killed)
2. If plugin is alive → normal event flow via MethodChannel
3. If plugin is null → `PendingCallStore.savePendingAccept()` via `SharedPreferences.commit()` (synchronous)
4. App restarts → `configure()` → `deliverPendingEvents()` → event delivered to Dart

### Accept Button Implementation

The Accept button uses `PendingIntent.getActivity()` instead of `getBroadcast()`. This provides a strong OS-level Background Activity Launch (BAL) exemption that works on Android 12+ and all OEMs:

- **Background state:** Intent handled in `onNewIntent`
- **Killed state:** Intent handled in `onAttachedToActivity`

---

## Background Call Rejection (Killed State)

When the app is killed and user taps **Decline**:

1. `CallActionReceiver` fires → cancels notification + stops ringtone (immediate)
2. `BackgroundCallRejectHelper.rejectCall()` makes a native HTTP request directly from Kotlin
3. Reads auth token from `EncryptedSharedPreferences` (same store as `flutter_secure_storage`) using the correct key prefix
4. URL, method, headers, and body are configured via `BackgroundRejectConfig` during `configure()`
5. `{callId}` placeholder is supported in URL, body, and header values
6. `{uuid}` is a special placeholder — auto-generated as a fresh `UUID.randomUUID()` per request
7. Custom `authKeyPrefix` is supported for apps using non-default `flutter_secure_storage` key prefixes
8. As fallback, `PendingCallStore.savePendingDecline()` persists the event for delivery on next app start

This bypasses Dart entirely — the MethodChannel event stream is unreliable in killed state.

### Configuration

```dart
BackgroundRejectConfig(
  urlPattern: 'https://api.example.com/v1/api/calls/{callId}/reject',
  httpMethod: 'PUT',
  authStorageKey: 'access_token',
  // authKeyPrefix: 'custom_prefix',  // Only if using custom AndroidOptions(preferencesKeyPrefix:)
  headers: {
    'Content-Type': 'application/json',
    'X-Trail-ID': '{uuid}',            // Auto-generated per request
  },
  body: '{"reason": "user_declined"}', // {callId} supported in body too
)
```

### Key Prefix

`flutter_secure_storage` prefixes all keys in `EncryptedSharedPreferences` with a namespace string. The default prefix is `VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg` (base64 of "This is the prefix for a secure storage"). This package handles the prefix automatically — only set `authKeyPrefix` if your app uses a custom prefix via `AndroidOptions(preferencesKeyPrefix:)`.

### Dynamic Placeholders

| Placeholder | Resolved To | Available In |
|-------------|-------------|--------------|
| `{callId}` | Unique call identifier | URL, headers, body |
| `{callerName}` | Display name of the caller | URL, headers, body |
| `{callType}` | voice or video | URL, headers, body |
| `{handle}` | Phone number or SIP address | URL, headers, body |
| `{callerAvatar}` | Avatar URL | URL, headers, body |
| `{uuid}` | Fresh `UUID.randomUUID()` per request | URL, headers, body |
| *any custom key* | Any extra from notification | URL, headers, body |

> `{uuid}` is synthesized at request time. All other placeholders come from call metadata. Unmatched placeholders are left as-is.

---

## Automatic Token Refresh

When a native reject call receives a **401 Unauthorized**, the plugin automatically:

1. Reads the refresh token from `flutter_secure_storage` (EncryptedSharedPreferences)
2. Makes an HTTP request to the configured refresh endpoint
3. Parses the new access token from the JSON response using dot-notation path
4. Stores the new access token (and optionally new refresh token) back in secure storage
5. Retries the original reject request with the new token

### Configuration

```dart
RefreshTokenConfig(
  url: 'https://api.example.com/v1/auth/refresh-token',
  httpMethod: 'POST',
  refreshTokenKey: 'refresh_token',
  bodyTemplate: '{"refreshToken": "{refreshToken}"}',
  accessTokenJsonPath: 'data.accessToken',
  refreshTokenJsonPath: 'data.refreshToken',  // If server rotates tokens
  headers: {
    'Content-Type': 'application/json',
  },
)
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `url` | `String` | **required** | Full URL of the refresh token endpoint |
| `httpMethod` | `String` | `'POST'` | HTTP method for the refresh request |
| `refreshTokenKey` | `String` | **required** | Key in `flutter_secure_storage` for the refresh token |
| `bodyTemplate` | `String` | `'{"refreshToken": "{refreshToken}"}'` | Request body with `{refreshToken}` placeholder |
| `accessTokenJsonPath` | `String` | **required** | Dot-notation path to access token in response |
| `refreshTokenJsonPath` | `String?` | `null` | Dot-notation path to new refresh token |
| `headers` | `Map<String, String>` | `{}` | Additional headers for the refresh request |

### JSON Path Resolution

```json
// Response: {"data": {"accessToken": "new-jwt", "refreshToken": "new-rt"}}
// accessTokenJsonPath: "data.accessToken" → "new-jwt"
// refreshTokenJsonPath: "data.refreshToken" → "new-rt"
```

---

## Consumer ProGuard Rules

Shipped in `proguard-rules.pro` — automatically applied to consumer apps. No app-level ProGuard configuration needed.

---

## Permissions

The plugin's `AndroidManifest.xml` includes all required permissions (auto-merged):

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_PHONE_CALL" />
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
<uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.READ_PHONE_NUMBERS" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

### Permission Requesting

- **`checkPermissions`**: Returns current status without triggering any system dialogs
- **`requestPermissions`**: Triggers system dialogs for `POST_NOTIFICATIONS` (Android 13+) and opens Settings for `USE_FULL_SCREEN_INTENT` (Android 14+)

---

## Battery Optimization Exemption

Battery optimization (Doze mode) can prevent incoming calls from being delivered reliably.

```dart
final perms = await CallBundle.checkPermissions();
if (!perms.batteryOptimizationExempt) {
  final exempt = await CallBundle.requestBatteryOptimizationExemption();
  // Opens ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS dialog
}
```

| Platform | `checkPermissions()` | `requestBatteryOptimizationExemption()` |
|----------|---------------------|----------------------------------------|
| Android 23+ | `PowerManager.isIgnoringBatteryOptimizations()` | Opens system dialog |
| Android < 23 | Returns `true` (Doze didn't exist) | Returns `true` |

---

## Requirements

| Requirement | Value |
|-------------|-------|
| Min SDK | 21 (Android 5.0) |
| Compile SDK | 35 |
| Kotlin | 1.9+ |

---

## Links

- [CallBundle on pub.dev](https://pub.dev/packages/callbundle)
- [Implementation Guide](https://pub.dev/packages/callbundle)
- [GitHub Repository](https://github.com/Ikolvi/callbundle)
- [Ikolvi](https://ikolvi.com)
