class ApiConfig {
  const ApiConfig._();

  static const String _defaultBaseOrigin = 'http://127.0.0.1:3000';
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
