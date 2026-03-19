import 'package:flutter/material.dart';

import '../features/tv_home/tv_home_page.dart';

class OpenClawApp extends StatelessWidget {
  const OpenClawApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'OpenClaw TV',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF1D6E5B),
          brightness: Brightness.dark,
        ),
        scaffoldBackgroundColor: const Color(0xFF0D1B1E),
      ),
      home: const TvHomePage(),
    );
  }
}
