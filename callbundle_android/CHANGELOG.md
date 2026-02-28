## 1.0.14

* **Caller avatar support** — profile photos displayed in incoming call full-screen Activity (via Coil), notification large icon, and CallStyle person icon.
* Added `callerAvatar` field to `CallInfo` data class.
* Added `io.coil-kt:coil:2.7.0` dependency for image loading.
* `NotificationHelper.showOngoingCallNotification` now accepts optional `callerAvatar` parameter.
* `IncomingCallActivity` shows profile photo with circle crop and graceful initials fallback.
* Documentation updates — new Caller Avatar section.

## 1.0.13

* Version alignment — all CallBundle packages now share the same version number.
* Documentation rewrite — clean formatting, OEM-adaptive notifications, background reject, token refresh, ProGuard, and battery optimization docs.

## 1.0.12

* **Fix: Background FCM engine hijacking main plugin instance** — `onAttachedToEngine` now preserves the configured main instance so `CallActionReceiver` sends through the active MethodChannel instead of storing pending.
* **Fix: Caller metadata missing after background accept** — `onCallAccepted` now accepts optional `intentExtra` fallback from notification PendingIntent when `callStateManager` doesn't have the call (registered by background engine).
* **Fix: Accept notification action doesn't open app (killed state)** — Accept button now uses `PendingIntent.getActivity()` instead of `getBroadcast()`, which directly launches the Activity with a strong OS-level BAL exemption that works on Android 12+ and all OEMs. Handles intent in `onNewIntent` (background) and `onAttachedToActivity` (killed).
* **Fix: CallStyle Accept also uses getActivity** — Android 12+ `CallStyle.forIncomingCall` was still using `getBroadcast()` for Accept, now fixed.
* **Fix: Ringtone continues playing after decline** — `mediaPlayer` and `vibrator` are now static/companion fields shared across all `NotificationHelper` instances.
* **Fix: Notification auto-timeout** — incoming call notifications auto-dismiss after configured `duration` (default 60s), with `timedOut` event sent to Dart. Safety net for delayed `call_cancelled` FCM.
* **Safety net: `onNewIntent` delivers pending events** — if pending accept was stored (background engine fallback), `bringAppToForeground` intent triggers pending delivery via `NewIntentListener`.

## 1.0.9

* **Fix: App not brought to foreground after accepting call** — `CallActionReceiver` and `CallBundlePlugin.onCallAccepted()` now launch the main Activity with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_REORDER_TO_FRONT` so the app is visible after accepting from a notification in background/killed state.

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
