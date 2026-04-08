import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../features/account/current_user_profile.dart';
import '../features/account/entitlement_recovery_result.dart';
import '../features/avatar/avatar_profile.dart';
import '../features/billing/billing_order_preview.dart';
import '../features/billing/create_billing_order_request.dart';
import '../features/billing/submit_tx_hash_request.dart';
import '../features/billing/update_confirmations_request.dart';
import '../features/control/control_action.dart';
import '../features/control/intent_resolution.dart';
import '../features/conversation/assistant_log_record.dart';
import '../features/conversation/router_status.dart';
import '../features/device_sync/device_registration_request.dart';
import '../features/device_sync/control_authorization_result.dart';
import '../features/device_sync/device_token_usage_event.dart';
import '../features/device_sync/device_token_usage_summary.dart';
import '../features/device_sync/registered_device.dart';
import '../features/model_pool/provider_lease.dart';
import '../features/ota/ota_manifest.dart';
import '../features/ota/ota_report_request.dart';
import '../features/tv_home/models/tv_home_remote_config.dart';
import 'api_config.dart';

class ApiClient {
  const ApiClient({
    http.Client? httpClient,
  }) : _httpClient = httpClient;

  final http.Client? _httpClient;
  static const String _deviceUserId = 'device-user-beta-a';
  static const String _clientPlatform = 'android-tv';

  Map<String, String> _platformHeaders({
    bool includeJson = false,
    bool includeDeviceUser = false,
  }) {
    return {
      if (includeJson) 'Content-Type': 'application/json',
      'x-client-platform': _clientPlatform,
      if (includeDeviceUser) 'x-device-user-id': _deviceUserId,
    };
  }

  Future<Map<String, String>> _authorizedJsonHeaders(http.Client client) async {
    return _platformHeaders(
      includeJson: true,
      includeDeviceUser: true,
    );
  }

  Future<Map<String, String>> _authorizedHeaders(http.Client client) async {
    return _platformHeaders(
      includeDeviceUser: true,
    );
  }

  Future<bool> getHealthStatus() async {
    final client = _httpClient ?? http.Client();

    try {
      final routerResponse = await client.get(
        Uri.parse('${ApiConfig.baseUrl}/router/status'),
        headers: _platformHeaders(includeDeviceUser: true),
      );
      if (routerResponse.statusCode >= 200 && routerResponse.statusCode < 300) {
        return true;
      }

      final legacyResponse = await client.get(
        Uri.parse('${ApiConfig.baseUrl}/health'),
        headers: _platformHeaders(),
      );
      return legacyResponse.statusCode >= 200 && legacyResponse.statusCode < 300;
    } catch (_) {
      return false;
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<CurrentUserProfile> getMe() async {
    final client = _httpClient ?? http.Client();

    try {
      final response = await client.get(
        Uri.parse('${ApiConfig.baseUrl}/me'),
        headers: await _authorizedHeaders(client),
      );

      if (response.statusCode >= 200 && response.statusCode < 300) {
        return CurrentUserProfile.fromJson(
          jsonDecode(response.body) as Map<String, dynamic>,
        );
      }

      return _fallbackProfile();
    } catch (_) {
      return _fallbackProfile();
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<TvHomeRemoteConfig> getTvHomeConfig({
    required String countryCode,
    String? regionCode,
  }) async {
    final client = _httpClient ?? http.Client();

    try {
      final query = Uri(
        path: '/me/tv-home-config',
        queryParameters: {
          'countryCode': countryCode,
          if (regionCode != null && regionCode.isNotEmpty) 'regionCode': regionCode,
        },
      );
      final response = await client.get(
        Uri.parse('${ApiConfig.baseUrl}${query.toString()}'),
        headers: _platformHeaders(),
      );

      if (response.statusCode >= 200 && response.statusCode < 300) {
        return TvHomeRemoteConfig.fromJson(
          jsonDecode(response.body) as Map<String, dynamic>,
        );
      }

      return _fallbackTvHomeConfig(countryCode: countryCode, regionCode: regionCode);
    } catch (_) {
      return _fallbackTvHomeConfig(countryCode: countryCode, regionCode: regionCode);
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<AvatarProfile> getActiveAvatarProfile() async {
    final client = _httpClient ?? http.Client();

    try {
      final response = await client.get(
        Uri.parse('${ApiConfig.baseUrl}/avatars/active'),
        headers: _platformHeaders(),
      );

      if (response.statusCode >= 200 && response.statusCode < 300) {
        return AvatarProfile.fromJson(
          jsonDecode(response.body) as Map<String, dynamic>,
        );
      }

      return _fallbackAvatarProfile();
    } catch (_) {
      return _fallbackAvatarProfile();
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<List<AvatarProfile>> getAvatarProfiles() async {
    final client = _httpClient ?? http.Client();

    try {
      final response = await client.get(
        Uri.parse('${ApiConfig.baseUrl}/avatars'),
        headers: _platformHeaders(),
      );

      if (response.statusCode >= 200 && response.statusCode < 300) {
        final payload = jsonDecode(response.body) as Map<String, dynamic>;
        final items = payload['items'] as List<dynamic>? ?? const [];
        return items
            .whereType<Map<String, dynamic>>()
            .map(AvatarProfile.fromJson)
            .toList();
      }

      return _fallbackAvatarProfiles();
    } catch (_) {
      return _fallbackAvatarProfiles();
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<AvatarProfile> activateAvatarProfile(String id) async {
    final client = _httpClient ?? http.Client();

    try {
      final response = await client.post(
        Uri.parse('${ApiConfig.baseUrl}/avatars/$id/activate'),
        headers: _platformHeaders(),
      );

      if (response.statusCode >= 200 && response.statusCode < 300) {
        return AvatarProfile.fromJson(
          jsonDecode(response.body) as Map<String, dynamic>,
        );
      }

      return _fallbackAvatarProfiles().firstWhere((item) => item.id == id);
    } catch (_) {
      return _fallbackAvatarProfiles().firstWhere((item) => item.id == id);
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<IntentResolution> resolveIntent({
    required String text,
    required String locale,
    String? deviceId,
  }) async {
    final client = _httpClient ?? http.Client();

    try {
      final response = await client.post(
        Uri.parse('${ApiConfig.baseUrl}/router/intent'),
        headers: await _authorizedJsonHeaders(client),
        body: jsonEncode({
          'text': text,
          'locale': locale,
          'deviceId': deviceId,
        }),
      );

      if (response.statusCode >= 200 && response.statusCode < 300) {
        return IntentResolution.fromJson(
          jsonDecode(response.body) as Map<String, dynamic>,
        );
      }

      return const IntentResolution(
        requestId: 'http_error',
        appId: 'unknown',
        action: ControlAction.openApp,
        queryText: null,
        replyText: 'Intent API returned an error',
        assistantText: 'Intent API returned an error',
        shouldExecuteLocally: false,
        mode: 'chat',
        route: 'http_error',
        modelProvider: 'http_error',
        confidence: 0,
        fallbackReason: 'http_error',
        tokenUsage: 0,
        bootstrapTokenRemaining: 0,
      );
    } catch (_) {
      return const IntentResolution(
        requestId: 'network_fallback',
        appId: 'youtube',
        action: ControlAction.openApp,
        queryText: null,
        replyText: 'Opening YouTube from local fallback',
        assistantText: 'Opening YouTube from local fallback',
        shouldExecuteLocally: true,
        mode: 'control',
        route: 'client_fallback',
        modelProvider: 'client_fallback',
        confidence: 0.2,
        fallbackReason: 'network_fallback',
        tokenUsage: 0,
        bootstrapTokenRemaining: 0,
      );
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<IntentResolution> resolveIntentWithDirectLease({
    required String text,
    required String locale,
    required String deviceId,
    String provider = 'MiniMax',
  }) async {
    final lease = await requestProviderLease(
      provider: provider,
      deviceUuid: deviceId,
    );

    if (!lease.isUsable) {
      return resolveIntent(
        text: text,
        locale: locale,
        deviceId: deviceId,
      );
    }

    final client = _httpClient ?? http.Client();

    try {
      final response = await client.post(
        Uri.parse('${lease.baseUrl!}/chat/completions'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer ${lease.apiKey!}',
        },
        body: jsonEncode({
          'model': lease.model,
          'temperature': 0.2,
          'messages': [
            {
              'role': 'system',
              'content': [
                'You are an Android TV voice assistant intent router.',
                'Decide whether the user is asking for local device control or normal chat.',
                'Supported appId values: youtube, netflix, prime_video, disney_plus, plex, vlc, spotify, settings, cast, local_files, media, system, assistant.',
                'Supported action values: open_app, search, play, pause, resume, next, previous, fast_forward, rewind, back, up, down, left, right, select, home, menu, volume_up, volume_down, mute, unsupported.',
                'Return strict JSON only with keys: appId, action, queryText, replyText, shouldExecuteLocally, mode, route.',
                'mode must be control or chat.',
                'If request is conversation, set appId=assistant, action=unsupported, shouldExecuteLocally=false, mode=chat.',
                'Keep replyText in the same language when possible.',
              ].join(' '),
            },
            {
              'role': 'user',
              'content': text,
            },
          ],
        }),
      );

      if (response.statusCode < 200 || response.statusCode >= 300) {
        return resolveIntent(
          text: text,
          locale: locale,
          deviceId: deviceId,
        );
      }

      final payload = jsonDecode(response.body) as Map<String, dynamic>;
      final choices = payload['choices'] as List<dynamic>? ?? const [];
      final firstChoice = choices.isNotEmpty ? choices.first : null;
      final message =
          firstChoice is Map<String, dynamic> ? firstChoice['message'] : null;
      final content =
          message is Map<String, dynamic> ? message['content'] as String? : null;
      if (content == null || content.trim().isEmpty) {
        return resolveIntent(
          text: text,
          locale: locale,
          deviceId: deviceId,
        );
      }

      final normalized = content
          .replaceFirst(RegExp(r'^```json\s*', caseSensitive: false), '')
          .replaceFirst(RegExp(r'^```\s*', caseSensitive: false), '')
          .replaceFirst(RegExp(r'\s*```$', caseSensitive: false), '');
      final parsed = jsonDecode(normalized) as Map<String, dynamic>;

      unawaited(logDirectTransport(
        locale: locale,
        userText: text,
        assistantText:
            parsed['replyText'] as String? ?? 'I understood your request.',
        appId: parsed['appId'] as String? ?? 'assistant',
        action: parsed['action'] as String? ?? 'unsupported',
        route: parsed['route'] as String? ?? 'client_direct_provider_lease',
        deviceId: deviceId,
        modelProvider:
            '${lease.provider.toLowerCase()}_client_direct_provider_lease',
        transportMode: 'client_direct_provider_lease',
      ));

      return IntentResolution(
        requestId: 'direct_lease_${DateTime.now().millisecondsSinceEpoch}',
        appId: parsed['appId'] as String? ?? 'assistant',
        action: _parseControlAction(parsed['action'] as String?),
        queryText: parsed['queryText'] as String?,
        replyText: parsed['replyText'] as String? ?? 'I understood your request.',
        assistantText:
            parsed['replyText'] as String? ?? 'I understood your request.',
        shouldExecuteLocally:
            parsed['shouldExecuteLocally'] as bool? ?? false,
        mode: parsed['mode'] as String? ?? 'chat',
        route: parsed['route'] as String? ?? 'client_direct_provider_lease',
        modelProvider:
            '${lease.provider.toLowerCase()}_client_direct_provider_lease',
        confidence: 0.9,
        fallbackReason: null,
        tokenUsage: 0,
        bootstrapTokenRemaining: 0,
      );
    } catch (_) {
      return resolveIntent(
        text: text,
        locale: locale,
        deviceId: deviceId,
      );
    } finally {
      if (lease.leaseId != null) {
        await releaseProviderLease(lease.leaseId!);
      }
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<ProviderLease> requestProviderLease({
    required String provider,
    required String deviceUuid,
  }) async {
    final client = _httpClient ?? http.Client();

    try {
      final response = await client.post(
        Uri.parse('${ApiConfig.baseUrl}/router/provider-lease'),
        headers: await _authorizedJsonHeaders(client),
        body: jsonEncode({
          'provider': provider,
          'deviceUuid': deviceUuid,
        }),
      );

      if (response.statusCode >= 200 && response.statusCode < 300) {
        return ProviderLease.fromJson(
          jsonDecode(response.body) as Map<String, dynamic>,
        );
      }

      return ProviderLease(provider: provider, denied: true);
    } catch (_) {
      return ProviderLease(provider: provider, denied: true);
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<void> releaseProviderLease(String leaseId) async {
    final client = _httpClient ?? http.Client();

    try {
      await client.post(
        Uri.parse('${ApiConfig.baseUrl}/router/provider-lease/release'),
        headers: await _authorizedJsonHeaders(client),
        body: jsonEncode({
          'leaseId': leaseId,
        }),
      );
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<RouterStatus> getRouterStatus() async {
    final client = _httpClient ?? http.Client();

    try {
      final response = await client.get(
        Uri.parse('${ApiConfig.baseUrl}/router/status'),
        headers: _platformHeaders(),
      );

      if (response.statusCode >= 200 && response.statusCode < 300) {
        return RouterStatus.fromJson(
          jsonDecode(response.body) as Map<String, dynamic>,
        );
      }

      return _fallbackRouterStatus();
    } catch (_) {
      return _fallbackRouterStatus();
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<List<AssistantLogRecord>> getRouterLogs() async {
    final client = _httpClient ?? http.Client();

    try {
      final response = await client.get(
        Uri.parse('${ApiConfig.baseUrl}/router/logs'),
        headers: await _authorizedHeaders(client),
      );

      if (response.statusCode >= 200 && response.statusCode < 300) {
        final payload = jsonDecode(response.body) as Map<String, dynamic>;
        final items = payload['items'] as List<dynamic>? ?? const [];
        return items
            .whereType<Map<String, dynamic>>()
            .map(AssistantLogRecord.fromJson)
            .toList();
      }

      return const [];
    } catch (_) {
      return const [];
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<RegisteredDevice> registerDevice(
    DeviceRegistrationRequest request,
  ) async {
    final client = _httpClient ?? http.Client();

    try {
      final response = await client.post(
        Uri.parse('${ApiConfig.baseUrl}/devices/register'),
        headers: await _authorizedJsonHeaders(client),
        body: jsonEncode(request.toJson()),
      );

      if (response.statusCode >= 200 && response.statusCode < 300) {
        return RegisteredDevice.fromJson(
          jsonDecode(response.body) as Map<String, dynamic>,
        );
      }

      return _fallbackDevices().first;
    } catch (_) {
      return _fallbackDevices().first;
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<List<RegisteredDevice>> getDevices() async {
    final client = _httpClient ?? http.Client();

    try {
      final response = await client.get(
        Uri.parse('${ApiConfig.baseUrl}/devices'),
        headers: await _authorizedHeaders(client),
      );

      if (response.statusCode >= 200 && response.statusCode < 300) {
        final payload = jsonDecode(response.body) as Map<String, dynamic>;
        final items = payload['items'] as List<dynamic>? ?? const [];
        return items
            .whereType<Map<String, dynamic>>()
            .map(RegisteredDevice.fromJson)
            .toList();
      }

      return _fallbackDevices();
    } catch (_) {
      return _fallbackDevices();
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<ControlAuthorizationResult> authorizeControl({
    required String bindingScope,
    required String appId,
    required String action,
  }) async {
    final client = _httpClient ?? http.Client();

    try {
      final response = await client.post(
        Uri.parse('${ApiConfig.baseUrl}/devices/authorize-control'),
        headers: await _authorizedJsonHeaders(client),
        body: jsonEncode({
          'bindingScope': bindingScope,
          'appId': appId,
          'action': action,
        }),
      );

      if (response.statusCode >= 200 && response.statusCode < 300) {
        return ControlAuthorizationResult.fromJson(
          jsonDecode(response.body) as Map<String, dynamic>,
        );
      }

      return const ControlAuthorizationResult(
        allowed: true,
        bindingScope: 'household',
        appId: 'system',
        action: 'open_app',
        reason: 'authorization_fallback',
      );
    } catch (_) {
      return const ControlAuthorizationResult(
        allowed: true,
        bindingScope: 'household',
        appId: 'system',
        action: 'open_app',
        reason: 'authorization_network_fallback',
      );
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<List<BillingOrderPreview>> getBillingOrders() async {
    final client = _httpClient ?? http.Client();

    try {
      final response = await client.get(
        Uri.parse('${ApiConfig.baseUrl}/billing/orders'),
        headers: await _authorizedHeaders(client),
      );

      if (response.statusCode >= 200 && response.statusCode < 300) {
        final payload = jsonDecode(response.body) as Map<String, dynamic>;
        final items = payload['items'] as List<dynamic>? ?? const [];
        return items
            .whereType<Map<String, dynamic>>()
            .map(BillingOrderPreview.fromJson)
            .toList();
      }

      return _fallbackOrders();
    } catch (_) {
      return _fallbackOrders();
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<List<BillingOrderPreview>> pollBillingOrders() async {
    final client = _httpClient ?? http.Client();

    try {
      final response = await client.post(
        Uri.parse('${ApiConfig.baseUrl}/billing/orders/poll'),
        headers: await _authorizedHeaders(client),
      );

      if (response.statusCode >= 200 && response.statusCode < 300) {
        final payload = jsonDecode(response.body) as Map<String, dynamic>;
        final items = payload['items'] as List<dynamic>? ?? const [];
        return items
            .whereType<Map<String, dynamic>>()
            .map(BillingOrderPreview.fromJson)
            .toList();
      }

      return getBillingOrders();
    } catch (_) {
      return getBillingOrders();
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<BillingOrderPreview> createBillingOrder(
    CreateBillingOrderRequest request,
  ) async {
    final client = _httpClient ?? http.Client();

    try {
      final response = await client.post(
        Uri.parse('${ApiConfig.baseUrl}/billing/orders'),
        headers: await _authorizedJsonHeaders(client),
        body: jsonEncode(request.toJson()),
      );

      if (response.statusCode >= 200 && response.statusCode < 300) {
        return BillingOrderPreview.fromJson(
          jsonDecode(response.body) as Map<String, dynamic>,
        );
      }

      return _fallbackOrders().first;
    } catch (_) {
      return _fallbackOrders().first;
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<BillingOrderPreview> submitBillingOrderTxHash(
    SubmitTxHashRequest request,
  ) async {
    final client = _httpClient ?? http.Client();

    try {
      final response = await client.post(
        Uri.parse('${ApiConfig.baseUrl}/billing/orders/${request.orderId}/tx'),
        headers: await _authorizedJsonHeaders(client),
        body: jsonEncode({
          'txHash': request.txHash,
        }),
      );

      if (response.statusCode >= 200 && response.statusCode < 300) {
        return BillingOrderPreview.fromJson(
          jsonDecode(response.body) as Map<String, dynamic>,
        );
      }

      return _fallbackOrders().first;
    } catch (_) {
      return _fallbackOrders().first;
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<BillingOrderPreview> updateBillingOrderConfirmations(
    UpdateConfirmationsRequest request,
  ) async {
    final client = _httpClient ?? http.Client();

    try {
      final response = await client.post(
        Uri.parse(
          '${ApiConfig.baseUrl}/billing/orders/${request.orderId}/confirmations',
        ),
        headers: await _authorizedJsonHeaders(client),
        body: jsonEncode({
          'confirmations': request.confirmations,
        }),
      );

      if (response.statusCode >= 200 && response.statusCode < 300) {
        return BillingOrderPreview.fromJson(
          jsonDecode(response.body) as Map<String, dynamic>,
        );
      }

      return _fallbackOrders().first;
    } catch (_) {
      return _fallbackOrders().first;
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<EntitlementRecoveryResult> recoverByPaymentProof({
    required String txHash,
    String? chain,
    double? amountUsd,
  }) async {
    final client = _httpClient ?? http.Client();

    try {
      final response = await client.post(
        Uri.parse('${ApiConfig.baseUrl}/auth/recover-by-payment'),
        headers: await _authorizedJsonHeaders(client),
        body: jsonEncode({
          'txHash': txHash,
          if (chain != null && chain.isNotEmpty) 'chain': chain,
          if (amountUsd != null) 'amountUsd': amountUsd,
        }),
      );

      if (response.statusCode >= 200 && response.statusCode < 300) {
        return EntitlementRecoveryResult.fromJson(
          jsonDecode(response.body) as Map<String, dynamic>,
        );
      }

      throw Exception('recover_failed_${response.statusCode}');
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<EntitlementTransferResult> transferEntitlements({
    required String fromDeviceUserId,
    required String toDeviceUserId,
    required String paymentProofTxHash,
  }) async {
    final client = _httpClient ?? http.Client();

    try {
      final response = await client.post(
        Uri.parse('${ApiConfig.baseUrl}/auth/transfer-entitlements'),
        headers: await _authorizedJsonHeaders(client),
        body: jsonEncode({
          'fromDeviceUserId': fromDeviceUserId,
          'toDeviceUserId': toDeviceUserId,
          'paymentProofTxHash': paymentProofTxHash,
        }),
      );

      if (response.statusCode >= 200 && response.statusCode < 300) {
        return EntitlementTransferResult.fromJson(
          jsonDecode(response.body) as Map<String, dynamic>,
        );
      }

      throw Exception('transfer_failed_${response.statusCode}');
    } finally {
      if (_httpClient == null) {
        client.close();
      }
      }
    }

  Future<OtaManifest> getOtaManifest({
    required String deviceUuid,
    required int currentVersionCode,
  }) async {
    final client = _httpClient ?? http.Client();
    final headers = await _authorizedHeaders(client);
    final query =
        'deviceUuid=$deviceUuid&currentVersionCode=$currentVersionCode';

    try {
      final manifestResponse = await client.get(
        Uri.parse('${ApiConfig.baseUrl}/ota/manifest?$query'),
        headers: headers,
      );

      if (manifestResponse.statusCode >= 200 &&
          manifestResponse.statusCode < 300) {
        return OtaManifest.fromJson(
          jsonDecode(manifestResponse.body) as Map<String, dynamic>,
        );
      }

      final bootstrapResponse = await client.get(
        Uri.parse('${ApiConfig.baseUrl}/ota/bootstrap?$query'),
        headers: headers,
      );

      if (bootstrapResponse.statusCode >= 200 &&
          bootstrapResponse.statusCode < 300) {
        final payload = jsonDecode(bootstrapResponse.body) as Map<String, dynamic>;
        final ota = payload['ota'];
        if (ota is Map<String, dynamic>) {
          return OtaManifest.fromJson(ota);
        }
      }

      return OtaManifest.unavailable;
    } catch (_) {
      return OtaManifest.unavailable;
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<void> reportOtaState(OtaReportRequest request) async {
    final client = _httpClient ?? http.Client();

    try {
      await client.post(
        Uri.parse('${ApiConfig.baseUrl}/ota/report'),
        headers: await _authorizedJsonHeaders(client),
        body: jsonEncode(request.toJson()),
      );
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<void> logControlBlock({
    required String locale,
    required String userText,
    required String assistantText,
    required String appId,
    required String action,
    required String route,
    String? deviceId,
  }) async {
    final client = _httpClient ?? http.Client();

    try {
      await client.post(
        Uri.parse('${ApiConfig.baseUrl}/router/control-block'),
        headers: await _authorizedJsonHeaders(client),
        body: jsonEncode({
          'locale': locale,
          'userText': userText,
          'assistantText': assistantText,
          'appId': appId,
          'action': action,
          'route': route,
          'deviceId': deviceId,
        }),
      );
    } catch (_) {
      // Ignore logging failures on the client side.
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  Future<void> logDirectTransport({
    required String locale,
    required String userText,
    required String assistantText,
    required String appId,
    required String action,
    required String route,
    required String modelProvider,
    required String transportMode,
    String? deviceId,
  }) async {
    final client = _httpClient ?? http.Client();

    try {
      await client.post(
        Uri.parse('${ApiConfig.baseUrl}/router/direct-log'),
        headers: await _authorizedJsonHeaders(client),
        body: jsonEncode({
          'locale': locale,
          'userText': userText,
          'assistantText': assistantText,
          'appId': appId,
          'action': action,
          'route': route,
          'deviceId': deviceId,
          'modelProvider': modelProvider,
          'transportMode': transportMode,
        }),
      );
    } catch (_) {
      // Ignore logging failures on the client side.
    } finally {
      if (_httpClient == null) {
        client.close();
      }
    }
  }

  CurrentUserProfile _fallbackProfile() {
    return const CurrentUserProfile(
      id: 'user_demo',
      email: 'demo@openclaw.local',
      displayName: 'OpenClaw Demo User',
      plan: 'family',
      tokenBalance: 128000,
      bootstrapTokenPool: 9872,
      deviceLimit: 3,
      activeDevices: 2,
      paymentPriority: 'stablecoin',
      sharedDeviceBindings: 1,
      entitlementExpiresAt: '2026-03-24T00:00:00.000Z',
      latestStablecoinOrderStatus: 'confirming',
      recentBootstrapTokenUsage: <DeviceTokenUsageEvent>[],
      bootstrapTokenUsageSummary: DeviceTokenUsageSummary(
        todayConsumed: 0,
        totalConsumed: 0,
      ),
    );
  }

  List<RegisteredDevice> _fallbackDevices() {
    return const [
      RegisteredDevice(
        id: 'device_1',
        deviceUuid: 'device_demo_android_tv',
        deviceName: 'Living Room TV',
        androidVersion: '9',
        isAndroidTv: true,
        status: 'active',
        bindingStatus: 'bound',
        bootstrapTokenGrant: 5000,
        bootstrapTokenRemaining: 4872,
      ),
      RegisteredDevice(
        id: 'device_2',
        deviceUuid: 'device_demo_speaker',
        deviceName: 'Bedroom Speaker',
        androidVersion: '11',
        isAndroidTv: false,
        status: 'idle',
        bindingStatus: 'shared',
        bootstrapTokenGrant: 5000,
        bootstrapTokenRemaining: 5000,
      ),
    ];
  }

  List<BillingOrderPreview> _fallbackOrders() {
    return const [
      BillingOrderPreview(
        id: 'order_demo_usdc_001',
        stablecoinSymbol: 'USDC',
        chain: 'Polygon',
        amountUsd: 25,
        status: 'confirming',
        confirmations: 8,
        walletAddress: '0xDEMO1234STABLECOINWALLET',
        txHash: '0xstablecoindemotxhash001',
      ),
    ];
  }

  RouterStatus _fallbackRouterStatus() {
    return const RouterStatus(
      provider: 'mock_llm_router',
      configured: false,
      baseUrl: 'local_fallback',
      model: 'local_mock',
    );
  }

  AvatarProfile _fallbackAvatarProfile() {
    return _fallbackAvatarProfiles().first;
  }

  List<AvatarProfile> _fallbackAvatarProfiles() {
    return const [
      AvatarProfile(
        id: 'avatar_warm_female',
        name: 'Warm Female',
        avatarLabel: 'Warm Female',
        gender: 'female',
        ageGroup: 'adult',
        primaryColorHex: '#6AE6D8',
        secondaryColorHex: '#12656A',
        accentColorHex: '#B0FFF4',
        active: true,
      ),
      AvatarProfile(
        id: 'avatar_calm_male',
        name: 'Calm Male',
        avatarLabel: 'Calm Male',
        gender: 'male',
        ageGroup: 'adult',
        primaryColorHex: '#7CC6FE',
        secondaryColorHex: '#1C4F8A',
        accentColorHex: '#D8EEFF',
        active: false,
      ),
      AvatarProfile(
        id: 'avatar_bright_child',
        name: 'Bright Child',
        avatarLabel: 'Bright Child',
        gender: 'child',
        ageGroup: 'youth',
        primaryColorHex: '#FFD166',
        secondaryColorHex: '#725317',
        accentColorHex: '#FFF0B5',
        active: false,
      ),
      AvatarProfile(
        id: 'avatar_gentle_senior',
        name: 'Gentle Senior',
        avatarLabel: 'Gentle Senior',
        gender: 'senior',
        ageGroup: 'senior',
        primaryColorHex: '#D8B4FE',
        secondaryColorHex: '#5B3A74',
        accentColorHex: '#F2E2FF',
        active: false,
      ),
    ];
  }

  TvHomeRemoteConfig _fallbackTvHomeConfig({
    required String countryCode,
    String? regionCode,
  }) {
    return TvHomeRemoteConfig(
      id: 'tv_home_fallback',
      countryCode: countryCode,
      regionCode: regionCode ?? 'GLOBAL',
      backgroundImageUrl: null,
      featuredAppIds: const [
        'youtube',
        'netflix',
        'prime_video',
        'disney_plus',
        'plex',
      ],
      version: 1,
      updatedAt: '',
    );
  }

  ControlAction _parseControlAction(String? rawAction) {
    switch (rawAction) {
      case 'open_app':
        return ControlAction.openApp;
      case 'search':
        return ControlAction.search;
      case 'play':
        return ControlAction.play;
      case 'pause':
        return ControlAction.pause;
      case 'resume':
        return ControlAction.resume;
      case 'next':
        return ControlAction.next;
      case 'previous':
        return ControlAction.previous;
      case 'fast_forward':
        return ControlAction.fastForward;
      case 'rewind':
        return ControlAction.rewind;
      case 'back':
        return ControlAction.back;
      case 'up':
        return ControlAction.up;
      case 'down':
        return ControlAction.down;
      case 'left':
        return ControlAction.left;
      case 'right':
        return ControlAction.right;
      case 'select':
        return ControlAction.select;
      case 'home':
        return ControlAction.home;
      case 'menu':
        return ControlAction.menu;
      case 'volume_up':
        return ControlAction.volumeUp;
      case 'volume_down':
        return ControlAction.volumeDown;
      case 'mute':
        return ControlAction.mute;
      default:
        return ControlAction.openApp;
    }
  }
}
