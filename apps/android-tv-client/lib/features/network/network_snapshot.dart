class NetworkSnapshot {
  const NetworkSnapshot({
    required this.isConnected,
    required this.transport,
    required this.currentSsid,
    required this.visibleNetworks,
    required this.canReadWifiList,
    required this.statusText,
  });

  final bool isConnected;
  final String transport;
  final String? currentSsid;
  final List<String> visibleNetworks;
  final bool canReadWifiList;
  final String statusText;

  factory NetworkSnapshot.fromJson(Map<dynamic, dynamic> json) {
    return NetworkSnapshot(
      isConnected: json['isConnected'] as bool? ?? false,
      transport: json['transport'] as String? ?? 'offline',
      currentSsid: json['currentSsid'] as String?,
      visibleNetworks: (json['visibleNetworks'] as List<dynamic>? ?? const [])
          .map((item) => item.toString())
          .where((item) => item.isNotEmpty)
          .toList(),
      canReadWifiList: json['canReadWifiList'] as bool? ?? false,
      statusText: json['statusText'] as String? ?? 'Offline',
    );
  }

  static const fallback = NetworkSnapshot(
    isConnected: false,
    transport: 'offline',
    currentSsid: null,
    visibleNetworks: <String>[],
    canReadWifiList: false,
    statusText: 'Offline',
  );
}
