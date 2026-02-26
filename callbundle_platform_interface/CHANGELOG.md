## 1.0.0

* Initial release of the CallBundle platform interface.
* Abstract `CallBundlePlatform` class with full API contract.
* `MethodChannelCallBundle` default implementation.
* Data models: `NativeCallParams`, `NativeCallConfig`, `NativeCallEvent`, `NativeCallInfo`, `NativeCallPermissions`.
* Enums: `NativeCallType`, `NativeCallState`, `NativeHandleType`, `NativeCallEventType`, `PermissionStatus`.
* MethodChannel-based communication for all native↔Dart events.
* `isUserInitiated` field on events to distinguish user vs programmatic actions.
* Monotonic `eventId` for deduplication.
