import 'package:flutter_test/flutter_test.dart';

import 'package:example/main.dart';

void main() {
  testWidgets('CallBundle example app renders', (WidgetTester tester) async {
    await tester.pumpWidget(const CallBundleExampleApp());
    expect(find.text('CallBundle Example'), findsOneWidget);
  });
}
