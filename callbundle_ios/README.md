# callbundle_ios

[![pub package](https://img.shields.io/pub/v/callbundle_ios.svg)](https://pub.dev/packages/callbundle_ios)

The iOS implementation of [`callbundle`](https://pub.dev/packages/callbundle).

---

## Table of Contents

1. [Usage](#usage)
2. [Architecture](#architecture)
3. [VoIP Certificate Setup (PEM File)](#voip-certificate-setup-pem-file)
4. [PushKit Integration](#pushkit-integration)
5. [CallKit Integration](#callkit-integration)
6. [Audio Session Management](#audio-session-management)
7. [Cold-Start Persistence](#cold-start-persistence)
8. [Thread Safety](#thread-safety)
9. [Permissions](#permissions)
10. [Requirements](#requirements)

---

## Usage

This package is **endorsed** — simply add `callbundle` to your `pubspec.yaml` and this package is included automatically on iOS.

```yaml
dependencies:
  callbundle: ^1.0.0
```

Add the VoIP background mode to your `Info.plist`:

```xml
<key>UIBackgroundModes</key>
<array>
    <string>voip</string>
</array>
```

---

## Architecture

| Component | File | Responsibility |
|-----------|------|----------------|
| `CallBundlePlugin` | `CallbundleIosPlugin.swift` | MethodChannel handler, singleton, event dispatch |
| `CallKitController` | `CallKitController.swift` | CXProvider + CXCallController for native call UI |
| `PushKitHandler` | `PushKitHandler.swift` | PKPushRegistry delegate, VoIP token management |
| `AudioSessionManager` | `AudioSessionManager.swift` | AVAudioSession with `.mixWithOthers` |
| `CallStore` | `CallStore.swift` | Thread-safe call tracking + cold-start persistence |
| `MissedCallNotificationManager` | `MissedCallNotificationManager.swift` | UNUserNotificationCenter for missed calls |

---

## VoIP Certificate Setup (PEM File)

iOS requires a VoIP push certificate to send VoIP pushes via Apple Push Notification service (APNs). This section covers creating the VoIP certificate and exporting it as a PEM file for your server.

### Step 1: Create VoIP Services Certificate

1. Go to [Apple Developer — Certificates](https://developer.apple.com/account/resources/certificates/list)
2. Click **+** to create a new certificate
3. Under **Services**, select **VoIP Services Certificate**
4. Click **Continue**
5. Select your **App ID** (must match your app's bundle identifier)
6. Click **Continue**
7. Upload a **Certificate Signing Request (CSR)**:
   - Open **Keychain Access** on your Mac
   - Menu: **Keychain Access → Certificate Assistant → Request a Certificate From a Certificate Authority**
   - Enter your email, select **Saved to disk**, click **Continue**
   - Save the `.certSigningRequest` file
8. Upload the CSR and click **Continue**
9. Download the generated `.cer` file

### Step 2: Export as PEM File

1. **Double-click** the downloaded `.cer` file to install it in **Keychain Access**
2. In Keychain Access, find the certificate under **My Certificates**:
   - It will be named **VoIP Services: com.yourcompany.yourapp**
3. **Right-click** the certificate → **Export** → choose `.p12` format
4. Set an export password (you'll need it in the next step)
5. Convert `.p12` to `.pem` using Terminal:

```bash
# Extract the certificate
openssl pkcs12 -in voip_cert.p12 -out voip_cert.pem -nodes -clcerts

# Or split into certificate and key files (some servers require this)
openssl pkcs12 -in voip_cert.p12 -out voip_cert_only.pem -nodes -nokeys
openssl pkcs12 -in voip_cert.p12 -out voip_key.pem -nodes -nocerts
```

### Step 3: Configure Your Server

Your push server needs the PEM file to send VoIP pushes to APNs. Here's an example using `curl`:

```bash
# Send a VoIP push to APNs (Development)
curl -v \
  --cert voip_cert.pem \
  --header "apns-topic: com.yourcompany.yourapp.voip" \
  --header "apns-push-type: voip" \
  --header "apns-priority: 10" \
  --header "apns-expiration: 0" \
  --data '{"callId":"abc-123","callerName":"John Doe","handle":"+1234567890"}' \
  https://api.sandbox.push.apple.com/3/device/<VOIP_DEVICE_TOKEN>

# Production
# Replace api.sandbox.push.apple.com with api.push.apple.com
```

### APNs Payload Format

The plugin expects these fields in the VoIP push payload:

```json
{
  "callId": "unique-call-id",
  "callerName": "John Doe",
  "handle": "+1234567890",
  "hasVideo": false
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `callId` | `String` | Yes | Unique call identifier |
| `callerName` | `String` | Yes | Displayed on the CallKit screen |
| `handle` | `String` | No | Phone number or SIP address |
| `hasVideo` | `Bool` | No | Show video call UI (default: `false`) |

Alternative field names are also supported: `caller_name`, `phone`, `has_video`.

### Step 4: Token-Based Authentication (Alternative)

Instead of PEM certificates, you can use APNs **token-based authentication** (`.p8` key). This is recommended for new projects:

1. Go to [Apple Developer — Keys](https://developer.apple.com/account/resources/authkeys/list)
2. Click **+** → Enable **Apple Push Notifications service (APNs)** → Download the `.p8` file
3. Note the **Key ID** and your **Team ID**
4. Use these with your server's APNs library (no PEM needed)

```bash
# Example with curl + JWT token (p8-based auth)
curl -v \
  --header "authorization: bearer <JWT_TOKEN>" \
  --header "apns-topic: com.yourcompany.yourapp.voip" \
  --header "apns-push-type: voip" \
  --header "apns-priority: 10" \
  --data '{"callId":"abc-123","callerName":"John Doe"}' \
  https://api.push.apple.com/3/device/<VOIP_DEVICE_TOKEN>
```

### Important Notes

- VoIP certificates are **separate** from regular APNs certificates
- The APNs topic for VoIP pushes must end with `.voip` (e.g., `com.yourapp.voip`)
- VoIP certificates expire after **1 year** — set a reminder to renew
- Development (sandbox) and Production use different APNs endpoints
- The VoIP token from `CallBundle.getVoipToken()` is device-specific and changes on reinstall

---

## PushKit Integration

PushKit VoIP push is handled **inside the plugin**. No AppDelegate code needed.

When a VoIP push arrives:

1. `PushKitHandler` receives the payload via `PKPushRegistryDelegate`
2. `reportNewIncomingCall` is called **synchronously** (required by iOS — app is terminated if not)
3. CallKit shows the native incoming call screen
4. User interaction → event delivered to Dart via MethodChannel

### Getting the VoIP Token

```dart
final token = await CallBundle.getVoipToken();
if (token != null) {
  // Send this hex string token to your push server
  await registerTokenWithServer(token);
}
```

The token is a hex-encoded string of the device's PushKit credentials. It's updated automatically — listen for `onVoipTokenUpdated` events for real-time updates.

### How PushKit Differs from FCM

| Feature | PushKit (VoIP) | FCM |
|---------|---------------|-----|
| Wake on delivery | Always (even killed) | Not guaranteed |
| Background processing | Guaranteed | Limited |
| CallKit requirement | Must report call synchronously | Not required |
| Token type | Separate VoIP token | FCM token |
| Best for | iOS incoming calls | Android + cross-platform |

**Recommendation:** Use PushKit for iOS incoming calls and FCM for Android.

---

## CallKit Integration

Full `CXProvider` delegate implementation:

- **Incoming calls:** `reportNewIncomingCall` with caller name, handle, and video support
- **Outgoing calls:** `CXStartCallAction` for the green status bar indicator
- **Call connected/ended:** State management via `CXCallController`
- **`isUserInitiated`:** Every event correctly flags whether the user or the system/app initiated it

### Programmatic vs User-Initiated Ends

The plugin tracks `programmaticEndUUIDs` — when your code calls `CallBundle.endCall()`, the resulting `CXEndCallAction` is correctly flagged as `isUserInitiated: false`. This eliminates the `_isEndingCallKitProgrammatically` flag pattern.

---

## Audio Session Management

`AudioSessionManager` configures `AVAudioSession` with `.mixWithOthers`:

- **Category:** `.playAndRecord` with `.defaultToSpeaker` and `.mixWithOthers`
- **Activation:** On call connect, deactivation on call end
- **No conflicts:** Prevents HMS/100ms audio session from being killed by CallKit

---

## Cold-Start Persistence

`CallStore` handles the case where the user answers a call before the Dart engine is ready:

1. VoIP push arrives → CallKit shows incoming call
2. User taps Accept → `CXAnswerCallAction` fires
3. If Dart not ready → `CallStore.savePendingAccept()` (backed by `UserDefaults.synchronize()`)
4. Dart calls `CallBundle.configure()` → `deliverPendingEvents()` delivers stored event
5. No hardcoded delays — events delivered as soon as Dart is ready

Call metadata (`extra`, `callerName`, `handle`) is preserved through the store.

---

## Thread Safety

All `CallStore` operations use a serial `DispatchQueue`:

```swift
private let queue = DispatchQueue(label: "com.callbundle.callstore", qos: .userInitiated)
```

This ensures thread-safe access from multiple callbacks (PushKit, CallKit, MethodChannel).

---

## Permissions

- **`checkPermissions`**: Reads `UNNotificationSettings` without requesting — no system dialog
- **`requestPermissions`**: Calls `UNUserNotificationCenter.requestAuthorization()` — triggers system dialog
- **Battery optimization**: Returns `true` (not applicable on iOS)

---

## Requirements

| Requirement | Value |
|-------------|-------|
| iOS | 13.0+ |
| Swift | 5.0+ |
| CocoaPods | 1.10+ |

### iOS Frameworks Used

| Framework | Purpose |
|-----------|---------|
| `CallKit` | Native incoming/outgoing call UI |
| `PushKit` | VoIP push notification delivery |
| `AVFoundation` | Audio session management |
| `UserNotifications` | Missed call notifications |

---

## Links

- [CallBundle on pub.dev](https://pub.dev/packages/callbundle)
- [Implementation Guide](https://pub.dev/packages/callbundle)
- [GitHub Repository](https://github.com/Ikolvi/callbundle)
- [Ikolvi](https://ikolvi.com)
