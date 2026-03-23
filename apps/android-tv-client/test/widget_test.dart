import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:openclaw_android_tv_client/app_shell/app.dart';
import 'package:openclaw_android_tv_client/features/avatar/virtual_host_avatar.dart';

void main() {
  testWidgets('TV shell renders the current dashboard shell', (WidgetTester tester) async {
    await tester.binding.setSurfaceSize(const Size(1920, 1080));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(const OpenClawApp());
    await tester.pump();

    expect(find.byType(Scaffold), findsOneWidget);
    expect(find.byType(VirtualHostAvatar), findsOneWidget);
    expect(find.byIcon(Icons.cast_connected_rounded), findsAtLeastNWidgets(1));
    expect(find.byIcon(Icons.usb_rounded), findsAtLeastNWidgets(1));
    expect(find.byIcon(Icons.settings_rounded), findsAtLeastNWidgets(1));
  });
}
