import 'package:flutter_test/flutter_test.dart';
import 'package:callbundle_android/callbundle_android.dart';
import 'package:callbundle_platform_interface/callbundle_platform_interface.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('CallBundleAndroid registers itself as platform instance', () {
    // Save original instance
    final original = CallBundlePlatform.instance;

    // Register
    CallBundleAndroid.registerWith();

    // Verify it's set
    expect(CallBundlePlatform.instance, isA<CallBundleAndroid>());

    // Restore
    CallBundlePlatform.instance = original;
  });

  test('CallBundleAndroid extends MethodChannelCallBundle', () {
    final android = CallBundleAndroid();
    expect(android, isA<MethodChannelCallBundle>());
  });

  test('CallBundleAndroid extends CallBundlePlatform', () {
    final android = CallBundleAndroid();
    expect(android, isA<CallBundlePlatform>());
  });
}
