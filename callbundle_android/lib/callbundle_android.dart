/// Android implementation of the CallBundle plugin.
///
/// This package is endorsed by the `callbundle` app-facing package.
/// Apps should not depend on this package directly.
library;

import 'package:callbundle_platform_interface/callbundle_platform_interface.dart';

/// The Android implementation of [CallBundlePlatform].
///
/// This class registers itself as the default platform implementation
/// when the Flutter engine attaches on Android. It delegates all calls
/// to the native Kotlin code via `MethodChannel("com.callbundle/main")`.
class CallBundleAndroid extends MethodChannelCallBundle {
  /// Registers this class as the default instance of [CallBundlePlatform].
  ///
  /// Called automatically by Flutter's plugin registration mechanism.
  static void registerWith() {
    CallBundlePlatform.instance = CallBundleAndroid();
  }
}
