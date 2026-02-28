## 1.0.15

* Updated dependency constraints to require callbundle_platform_interface ^1.0.14, callbundle_android ^1.0.14, callbundle_ios ^1.0.14.
* Documentation updates for caller avatar feature — `callerAvatar` now supported on both platforms.

## 1.0.14

* Updated dependency constraints to require callbundle_platform_interface ^1.0.13, callbundle_android ^1.0.13, callbundle_ios ^1.0.13.

## 1.0.13

* Version alignment — all CallBundle packages now share the same version number.
* Documentation rewrite — clean formatting, full API reference, PushKit/PEM guide link, FCM integration, permissions, background reject, cold-start handling.

## 1.0.12

* **Fix: Call accept across all app states** — comprehensive fix for background, killed, and lock screen scenarios.
* **Android**: Preserved main plugin instance from background FCM engine hijack, Accept uses `PendingIntent.getActivity()` for reliable Activity launch, static ringtone/vibration for cross-engine cleanup, notification auto-timeout, CallStyle fix.
* **iOS**: Fixed `reportCallConnected()`, `CXEndCallAction` isUserInitiated tracking.

## 1.0.9

* **Fix: Call answering broken across all app states** — comprehensive fix for incoming call accept flow.
* **Android**: App now brought to foreground after accepting call from notification (background/killed state).
* **iOS**: Fixed `reportCallConnected()` that was immediately ending the CallKit call after accept, killing the audio session. Fixed `CXEndCallAction` handler to correctly distinguish user-initiated vs programmatic ends.

## 1.0.8

* **Fix: Incoming call UI not showing in killed state** — both Android and iOS now initialize native call infrastructure eagerly at plugin registration.
* **Fix: Call notification not dismissed on accept** — Android now cancels the notification when the user accepts.
* **Fix: Caller metadata (`extra`) lost in cold-start flows** — `extra` is now threaded through PendingIntents (Android) and `CallStore` (iOS) so it survives app-killed accept/decline.

## 1.0.7

* Fix corrupted Links section in README.
* Fix unresolved doc references (`PendingCallStore`, `PlatformException`).
* Raise dependency lower bounds to fix `pub-downgrade` compatibility check.

## 1.0.6

* Complete implementation guide README: installation, permissions, FCM integration, cold-start handling, event patterns, configuration reference.

## 1.0.5

* Version bump to align with Android cold-start fix.

## 1.0.4

* Add `CallBundle.checkPermissions()` for silent permission status checks.
* Enables custom Dart dialogs before system permission prompts.
* Example app updated with permission explanation dialog flow.

## 1.0.3

* Android: request notification and full-screen intent permissions explicitly.

## 1.0.2

* Updated platform dependencies with incoming/outgoing call UI bug fixes.

## 1.0.1

* Documentation updates and metadata cleanup.

## 1.0.0

* Initial release of CallBundle — native incoming & outgoing call UI for Flutter.
* Static `CallBundle` API class with `configure`, `showIncomingCall`, `showOutgoingCall`, `endCall`, `endAllCalls`, `setCallConnected`, `getActiveCalls`, `requestPermissions`, `getVoipToken`.
* Event stream via `CallBundle.onEvent` with `isUserInitiated` flag.
* Ready signal via `CallBundle.onReady` future.
* Endorses `callbundle_android` and `callbundle_ios` as default platforms.
