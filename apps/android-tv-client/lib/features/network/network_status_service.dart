import 'package:flutter/services.dart';

import 'network_snapshot.dart';

class NetworkStatusService {
  const NetworkStatusService();

  static const MethodChannel _channel = MethodChannel(
    'com.openclaw.assistant/network',
  );

  Future<NetworkSnapshot> getNetworkSnapshot() async {
    try {
      final raw = await _channel.invokeMapMethod<Object?, Object?>(
        'getNetworkSnapshot',
      );
      if (raw == null) {
        return NetworkSnapshot.fallback;
      }
      return NetworkSnapshot.fromJson(raw);
    } on PlatformException {
      return NetworkSnapshot.fallback;
    }
  }
}
