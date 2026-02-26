## 1.0.1

* Documentation updates and metadata cleanup.

## 1.0.0

* Initial release of CallBundle — native incoming & outgoing call UI for Flutter.
* Static `CallBundle` API class with `configure`, `showIncomingCall`, `showOutgoingCall`, `endCall`, `endAllCalls`, `setCallConnected`, `getActiveCalls`, `requestPermissions`, `getVoipToken`.
* Event stream via `CallBundle.onEvent` with `isUserInitiated` flag.
* Ready signal via `CallBundle.onReady` future.
* Endorses `callbundle_android` and `callbundle_ios` as default platforms.
