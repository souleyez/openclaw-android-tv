class ApiConfig {
  const ApiConfig._();

  // Android TV now defaults to the unified public gateway.
  // Override with OPENCLAW_API_BASE_URL only when you intentionally test
  // against a local compatibility backend or local reverse proxy.
  static const String _defaultBaseOrigin = 'https://oc.goods-editor.com';
  static const String _configuredBaseOrigin = String.fromEnvironment(
    'OPENCLAW_API_BASE_URL',
    defaultValue: _defaultBaseOrigin,
  );

  static String get baseUrl {
    final trimmed = _configuredBaseOrigin.trim();
    final normalized = trimmed.endsWith('/')
        ? trimmed.substring(0, trimmed.length - 1)
        : trimmed;
    return normalized.endsWith('/api') ? normalized : '$normalized/api';
  }
}
