/// CallBundle — Native incoming & outgoing call UI for Flutter.
///
/// A federated Flutter plugin providing native call UI on iOS (CallKit)
/// and Android (TelecomManager + OEM-adaptive notifications).
///
/// ## Usage
///
/// ```dart
/// import 'package:callbundle/callbundle.dart';
///
/// await CallBundle.configure(NativeCallConfig(appName: 'MyApp'));
/// CallBundle.onEvent.listen((event) => print(event));
/// ```
library;

// App-facing API
export 'src/callbundle_api.dart';

// Re-export platform interface models for convenience
export 'package:callbundle_platform_interface/callbundle_platform_interface.dart'
    show
        NativeCallConfig,
        BackgroundRejectConfig,
        RefreshTokenConfig,
        AndroidCallConfig,
        IosCallConfig,
        NativeCallParams,
        AndroidCallParams,
        IosCallParams,
        NativeCallEvent,
        NativeCallEventType,
        NativeCallInfo,
        NativeCallPermissions,
        NativeCallType,
        NativeCallState,
        NativeHandleType,
        PermissionStatus;
