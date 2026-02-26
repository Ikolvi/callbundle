/// Platform interface for the CallBundle federated Flutter plugin.
///
/// This package defines the abstract API contract, data models, and event
/// types that platform-specific implementations must fulfill.
///
/// App developers should not depend on this package directly. Instead,
/// import `package:callbundle/callbundle.dart`.
library;

// Abstract platform class
export 'src/callbundle_platform.dart';

// Default MethodChannel implementation
export 'src/method_channel_callbundle.dart';

// Data models
export 'src/models/native_call_config.dart';
export 'src/models/native_call_enums.dart';
export 'src/models/native_call_event.dart';
export 'src/models/native_call_info.dart';
export 'src/models/native_call_params.dart';
export 'src/models/native_call_permissions.dart';
