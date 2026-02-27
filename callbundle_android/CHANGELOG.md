## 1.0.8

* Fix incoming call UI not showing when app is in killed state — `NotificationHelper` now initializes eagerly at plugin registration instead of waiting for `configure()`.
* Fix notification not dismissed on call accept — `cancelNotification` now called in `onCallAccepted()`.
* Thread `extra` metadata through notification PendingIntents so caller info is preserved in cold-start accept/decline flows.
* `CallActionReceiver` now extracts `callExtra` Bundle from Intent when plugin is null, preventing metadata loss.

## 1.0.7

* Add `dartPluginClass: CallBundleAndroid` for proper federated plugin registration.

## 1.0.6

* Comprehensive README with architecture, OEM detection, cold-start flow, and permission details.
* Add lock screen support: `showWhenLocked`, `turnScreenOn`, and keyguard dismissal for incoming call full-screen intent.
* Full-screen intent now includes `FLAG_ACTIVITY_REORDER_TO_FRONT` for reliable activity display.

## 1.0.5

* Fix critical cold-start bug: Accept/Decline from notification when app is killed now persists to `PendingCallStore` instead of being silently dropped.
* Cancel notification on Decline/End even when plugin instance is null.

## 1.0.4

* Add `checkPermissions` native handler — returns permission status without prompting.
* Fix `requestPermissions` response to use consistent `NativeCallPermissions` format.

## 1.0.3

* Actually request `POST_NOTIFICATIONS` permission (API 33+) via system dialog instead of just checking.
* Open system settings for `USE_FULL_SCREEN_INTENT` permission (API 34+) when not granted.
* Implement `PluginRegistry.RequestPermissionsResultListener` for proper permission callback handling.

## 1.0.2

* Fix full-screen intent to target the app's launch Activity instead of empty Intent.
* Ensures incoming call notification properly brings the app to foreground.

## 1.0.1

* Documentation updates and metadata cleanup.

## 1.0.0

* Initial release of the CallBundle Android implementation.
* `ConnectionService` + `TelecomManager` integration.
* OEM-adaptive notification strategy for budget Android devices.
* `PendingCallStore` for deterministic cold-start call delivery.
* Consumer ProGuard rules shipped with the plugin.
* `NotificationCompat.CallStyle` for API 31+ with standard fallback.
* Ringtone and vibration management with ringer mode awareness.
* Full `MethodChannel` handler for all call operations.
