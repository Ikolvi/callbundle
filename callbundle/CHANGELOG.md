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
