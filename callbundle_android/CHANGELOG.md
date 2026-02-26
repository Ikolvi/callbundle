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
