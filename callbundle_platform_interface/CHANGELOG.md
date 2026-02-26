## 1.0.7

* Fix unresolved `[CallBundle.configure]` doc reference for pub.dev documentation score.

## 1.0.6

* Comprehensive README with MethodChannel contract and custom implementation guide.

## 1.0.5

* Version bump to align with Android cold-start fix.

## 1.0.4

* Add `checkPermissions()` method for silent permission status checks.
* Enables Dart-driven permission flow: check → custom dialog → request.

## 1.0.3

* Version bump to align with Android permission fix.

## 1.0.2

* Version bump to align with iOS/Android bug fixes.

## 1.0.1

* Documentation updates and metadata cleanup.

## 1.0.0

* Initial release of the CallBundle platform interface.
* Abstract `CallBundlePlatform` class with full API contract.
* `MethodChannelCallBundle` default implementation.
* Data models: `NativeCallParams`, `NativeCallConfig`, `NativeCallEvent`, `NativeCallInfo`, `NativeCallPermissions`.
* Enums: `NativeCallType`, `NativeCallState`, `NativeHandleType`, `NativeCallEventType`, `PermissionStatus`.
* MethodChannel-based communication for all native↔Dart events.
* `isUserInitiated` field on events to distinguish user vs programmatic actions.
* Monotonic `eventId` for deduplication.
