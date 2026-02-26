import 'package:flutter_test/flutter_test.dart';
import 'package:callbundle_ios/callbundle_ios.dart';
import 'package:callbundle_platform_interface/callbundle_platform_interface.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('CallBundleIOS registers itself as platform instance', () {
    final original = CallBundlePlatform.instance;

    CallBundleIOS.registerWith();

    expect(CallBundlePlatform.instance, isA<CallBundleIOS>());

    CallBundlePlatform.instance = original;
  });

  test('CallBundleIOS extends MethodChannelCallBundle', () {
    final ios = CallBundleIOS();
    expect(ios, isA<MethodChannelCallBundle>());
  });

  test('CallBundleIOS extends CallBundlePlatform', () {
    final ios = CallBundleIOS();
    expect(ios, isA<CallBundlePlatform>());
  });
}
