## 1.0.9

* **Fix: `reportCallConnected()` was ending the call immediately** — removed destructive `reportCall(with:endedAt:reason:.remoteEnded)` that killed the CallKit audio session right after the user accepted.
* **Fix: `CXEndCallAction` always reported `isUserInitiated: true`** — added `programmaticEndUUIDs` tracking so programmatic `endCall()` from Dart is correctly flagged as `isUserInitiated: false`, preventing duplicate end-call dispatch loops.
* **Fix: `providerDidReset` cleanup** — now clears `programmaticEndUUIDs` tracking set.

## 1.0.8

* Fix incoming call UI not showing when app is in killed state — `CXProvider` now created eagerly in `init()` instead of waiting for `configure()`.
* Store and thread `extra` metadata through `CallStore` for all call types.
* `sendCallEvent` auto-resolves `extra` from `CallStore` when not explicitly provided.
* `savePendingAccept` / `consumePendingAccept` now preserve `extra` for cold-start event delivery.

## 1.0.7

* Version bump to align with pub.dev score fixes.

## 1.0.6

* Comprehensive README with PushKit flow, CallKit integration, audio session, and cold-start details.

## 1.0.5

* Version bump to align with Android cold-start fix.

## 1.0.4

* Add `checkPermissions` native handler — returns permission status without prompting.
* Fix `requestPermissions` to return `NativeCallPermissions` format instead of simple map.

## 1.0.3

* Version bump to align with Android permission fix.

## 1.0.2

* Fix `handleType` and `hasVideo` reading from nested `ios` params instead of top-level args.
* Fix `endCall` and `setCallConnected` to accept plain string callId (matching Dart MethodChannel contract).
* Determine video calls from `callType` field correctly.

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
