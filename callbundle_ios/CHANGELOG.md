## 1.0.1

* Documentation updates and metadata cleanup.

## 1.0.0

* Initial release of the CallBundle iOS implementation.
* CallKit integration via `CXProvider` and `CXCallController`.
* PushKit VoIP push handling inside the plugin (no AppDelegate code).
* `AudioSessionManager` with `.mixWithOthers` to prevent HMS audio conflicts.
* `CallStore` with thread-safe serial queue and cold-start persistence.
* Missed call local notifications via `UNUserNotificationCenter`.
* Deterministic UUID mapping for string-based callIds.
