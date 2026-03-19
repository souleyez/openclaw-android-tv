import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:openclaw_android_tv_client/app_shell/app.dart';

void main() {
  testWidgets('TV shell renders headline and voice action', (WidgetTester tester) async {
    await tester.binding.setSurfaceSize(const Size(1920, 1080));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(const OpenClawApp());

    expect(find.text('OpenClaw TV'), findsOneWidget);
    expect(find.text('Press To Talk'), findsOneWidget);
  });
}
