import 'package:flutter/foundation.dart';

/// Android-specific configuration for the CallBundle plugin.
@immutable
class AndroidCallConfig {
  /// Creates Android-specific call configuration.
  const AndroidCallConfig({
    required this.phoneAccountLabel,
    this.phoneAccountIcon,
    this.useTelecomManager = true,
    this.oemAdaptiveMode = true,
    this.notificationChannelId,
    this.notificationChannelName,
  });

  /// Label for the PhoneAccount in Android Settings → Phone → Calling accounts.
  ///
  /// This is the name users see when managing call accounts.
  final String phoneAccountLabel;

  /// Icon resource name for the PhoneAccount.
  ///
  /// Must reference a drawable resource in the plugin or app.
  final String? phoneAccountIcon;

  /// Whether to use Android's TelecomManager (ConnectionService).
  ///
  /// If `true` (default), uses `ConnectionService` + `TelecomManager` for
  /// proper system integration. Falls back to notification-only if the
  /// device doesn't support it.
  ///
  /// If `false`, uses notification-only mode (always).
  final bool useTelecomManager;

  /// Whether to automatically detect budget OEMs and adapt notification strategy.
  ///
  /// When enabled, the plugin detects the device manufacturer and applies
  /// the optimal notification strategy for maximum reliability.
  ///
  /// Defaults to `true`.
  final bool oemAdaptiveMode;

  /// Custom notification channel ID override.
  final String? notificationChannelId;

  /// Custom notification channel display name override.
  final String? notificationChannelName;

  /// Serializes this instance to a [Map] for MethodChannel transport.
  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      'phoneAccountLabel': phoneAccountLabel,
      if (phoneAccountIcon != null) 'phoneAccountIcon': phoneAccountIcon,
      'useTelecomManager': useTelecomManager,
      'oemAdaptiveMode': oemAdaptiveMode,
      if (notificationChannelId != null)
        'notificationChannelId': notificationChannelId,
      if (notificationChannelName != null)
        'notificationChannelName': notificationChannelName,
    };
  }

  /// Creates an instance from a [Map] received via MethodChannel.
  factory AndroidCallConfig.fromMap(Map<String, dynamic> map) {
    return AndroidCallConfig(
      phoneAccountLabel: map['phoneAccountLabel'] as String,
      phoneAccountIcon: map['phoneAccountIcon'] as String?,
      useTelecomManager: map['useTelecomManager'] as bool? ?? true,
      oemAdaptiveMode: map['oemAdaptiveMode'] as bool? ?? true,
      notificationChannelId: map['notificationChannelId'] as String?,
      notificationChannelName: map['notificationChannelName'] as String?,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is AndroidCallConfig &&
        other.phoneAccountLabel == phoneAccountLabel &&
        other.phoneAccountIcon == phoneAccountIcon &&
        other.useTelecomManager == useTelecomManager &&
        other.oemAdaptiveMode == oemAdaptiveMode &&
        other.notificationChannelId == notificationChannelId &&
        other.notificationChannelName == notificationChannelName;
  }

  @override
  int get hashCode {
    return Object.hash(
      phoneAccountLabel,
      phoneAccountIcon,
      useTelecomManager,
      oemAdaptiveMode,
      notificationChannelId,
      notificationChannelName,
    );
  }

  @override
  String toString() {
    return 'AndroidCallConfig('
        'phoneAccountLabel: $phoneAccountLabel, '
        'useTelecomManager: $useTelecomManager, '
        'oemAdaptiveMode: $oemAdaptiveMode)';
  }
}

/// iOS-specific configuration for the CallBundle plugin.
@immutable
class IosCallConfig {
  /// Creates iOS-specific call configuration.
  const IosCallConfig({
    this.maximumCallGroups = 1,
    this.maximumCallsPerCallGroup = 1,
    this.includesCallsInRecents = true,
    this.supportsVideo = true,
    this.iconTemplateImageName,
    this.ringtoneSound,
  });

  /// Maximum number of concurrent call groups.
  ///
  /// Defaults to `1` (single call at a time).
  final int maximumCallGroups;

  /// Maximum number of calls per call group.
  ///
  /// Defaults to `1` (no conference).
  final int maximumCallsPerCallGroup;

  /// Whether calls appear in the iOS Phone app's Recent Calls list.
  ///
  /// Defaults to `true`.
  final bool includesCallsInRecents;

  /// Whether the provider supports video calls.
  ///
  /// Defaults to `true`.
  final bool supportsVideo;

  /// The name of the template image asset for the CallKit UI.
  ///
  /// Must be a single-color (template) image in the app bundle.
  /// Example: `"CallKitLogo"`.
  final String? iconTemplateImageName;

  /// The ringtone sound file name in the app bundle.
  ///
  /// Example: `"ringtone.caf"`. If not set, the system default is used.
  final String? ringtoneSound;

  /// Serializes this instance to a [Map] for MethodChannel transport.
  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      'maximumCallGroups': maximumCallGroups,
      'maximumCallsPerCallGroup': maximumCallsPerCallGroup,
      'includesCallsInRecents': includesCallsInRecents,
      'supportsVideo': supportsVideo,
      if (iconTemplateImageName != null)
        'iconTemplateImageName': iconTemplateImageName,
      if (ringtoneSound != null) 'ringtoneSound': ringtoneSound,
    };
  }

  /// Creates an instance from a [Map] received via MethodChannel.
  factory IosCallConfig.fromMap(Map<String, dynamic> map) {
    return IosCallConfig(
      maximumCallGroups: map['maximumCallGroups'] as int? ?? 1,
      maximumCallsPerCallGroup:
          map['maximumCallsPerCallGroup'] as int? ?? 1,
      includesCallsInRecents:
          map['includesCallsInRecents'] as bool? ?? true,
      supportsVideo: map['supportsVideo'] as bool? ?? true,
      iconTemplateImageName: map['iconTemplateImageName'] as String?,
      ringtoneSound: map['ringtoneSound'] as String?,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is IosCallConfig &&
        other.maximumCallGroups == maximumCallGroups &&
        other.maximumCallsPerCallGroup == maximumCallsPerCallGroup &&
        other.includesCallsInRecents == includesCallsInRecents &&
        other.supportsVideo == supportsVideo &&
        other.iconTemplateImageName == iconTemplateImageName &&
        other.ringtoneSound == ringtoneSound;
  }

  @override
  int get hashCode {
    return Object.hash(
      maximumCallGroups,
      maximumCallsPerCallGroup,
      includesCallsInRecents,
      supportsVideo,
      iconTemplateImageName,
      ringtoneSound,
    );
  }

  @override
  String toString() {
    return 'IosCallConfig('
        'maximumCallGroups: $maximumCallGroups, '
        'maximumCallsPerCallGroup: $maximumCallsPerCallGroup, '
        'includesCallsInRecents: $includesCallsInRecents, '
        'supportsVideo: $supportsVideo)';
  }
}

/// Configuration for initializing the CallBundle plugin.
///
/// Pass this to `CallBundle.configure` during app startup.
///
/// Example:
/// ```dart
/// await CallBundle.configure(NativeCallConfig(
///   appName: 'CommunityXo',
///   missedCallNotification: true,
///   android: AndroidCallConfig(
///     phoneAccountLabel: 'CommunityXo Calls',
///     oemAdaptiveMode: true,
///   ),
///   ios: IosCallConfig(
///     includesCallsInRecents: true,
///     supportsVideo: true,
///   ),
/// ));
/// ```
@immutable
class NativeCallConfig {
  /// Creates a plugin configuration.
  const NativeCallConfig({
    required this.appName,
    this.defaultRingtone,
    this.defaultVibrationPattern,
    this.missedCallNotification = true,
    this.android,
    this.ios,
    this.backgroundReject,
  });

  /// The app name displayed in notifications and system UI.
  final String appName;

  /// Default ringtone for calls that don't specify one.
  final String? defaultRingtone;

  /// Default vibration pattern for calls that don't specify one.
  final List<int>? defaultVibrationPattern;

  /// Whether to show a missed call notification when a call isn't answered.
  ///
  /// Defaults to `true`.
  final bool missedCallNotification;

  /// Android-specific configuration.
  final AndroidCallConfig? android;

  /// iOS-specific configuration.
  final IosCallConfig? ios;

  /// Configuration for native background call rejection.
  ///
  /// When the user declines a call from the notification while the app
  /// is killed, the native side makes a direct HTTP request using this
  /// configuration. This is necessary because in killed state, the Dart
  /// isolate may not have an active listener on the call events stream.
  ///
  /// If null, background rejection falls back to [PendingCallStore] and
  /// is delivered when the app next starts.
  ///
  /// See [BackgroundRejectConfig] for details.
  final BackgroundRejectConfig? backgroundReject;

  /// Serializes this instance to a [Map] for MethodChannel transport.
  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      'appName': appName,
      if (defaultRingtone != null) 'defaultRingtone': defaultRingtone,
      if (defaultVibrationPattern != null)
        'defaultVibrationPattern': defaultVibrationPattern,
      'missedCallNotification': missedCallNotification,
      if (android != null) 'android': android!.toMap(),
      if (ios != null) 'ios': ios!.toMap(),
      if (backgroundReject != null)
        'backgroundReject': backgroundReject!.toMap(),
    };
  }

  /// Creates an instance from a [Map] received via MethodChannel.
  factory NativeCallConfig.fromMap(Map<String, dynamic> map) {
    return NativeCallConfig(
      appName: map['appName'] as String,
      defaultRingtone: map['defaultRingtone'] as String?,
      defaultVibrationPattern:
          (map['defaultVibrationPattern'] as List<dynamic>?)
              ?.map((e) => e as int)
              .toList(),
      missedCallNotification:
          map['missedCallNotification'] as bool? ?? true,
      android: map['android'] != null
          ? AndroidCallConfig.fromMap(
              Map<String, dynamic>.from(
                map['android'] as Map<dynamic, dynamic>,
              ),
            )
          : null,
      ios: map['ios'] != null
          ? IosCallConfig.fromMap(
              Map<String, dynamic>.from(
                map['ios'] as Map<dynamic, dynamic>,
              ),
            )
          : null,
      backgroundReject: map['backgroundReject'] != null
          ? BackgroundRejectConfig.fromMap(
              Map<String, dynamic>.from(
                map['backgroundReject'] as Map<dynamic, dynamic>,
              ),
            )
          : null,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is NativeCallConfig &&
        other.appName == appName &&
        other.defaultRingtone == defaultRingtone &&
        listEquals(
          other.defaultVibrationPattern,
          defaultVibrationPattern,
        ) &&
        other.missedCallNotification == missedCallNotification &&
        other.android == android &&
        other.ios == ios &&
        other.backgroundReject == backgroundReject;
  }

  @override
  int get hashCode {
    return Object.hash(
      appName,
      defaultRingtone,
      defaultVibrationPattern != null
          ? Object.hashAll(defaultVibrationPattern!)
          : null,
      missedCallNotification,
      android,
      ios,
      backgroundReject,
    );
  }

  @override
  String toString() {
    return 'NativeCallConfig('
        'appName: $appName, '
        'missedCallNotification: $missedCallNotification, '
        'backgroundReject: $backgroundReject)';
  }
}

/// Configuration for native background call rejection.
///
/// When the user declines a call while the app is killed, the native
/// side makes an HTTP request directly (bypassing Dart) to notify the
/// server. This ensures the caller sees the rejection immediately.
///
/// ## URL Pattern
///
/// Use `{key}` placeholders in [urlPattern]. They will be replaced with
/// matching values from the call data at runtime.
///
/// ## Dynamic Placeholders
///
/// All `{key}` tokens in [urlPattern], [body], and [headers] values are
/// resolved from the call metadata available at decline time. This is
/// fully generic — any key from the notification extras can be used:
///
/// | Placeholder | Description |
/// |---|---|
/// | `{callId}` | Unique call identifier (always available) |
/// | `{callerName}` | Display name of the caller |
/// | `{callType}` | Type of call (voice, video) |
/// | `{callerAvatar}` | Avatar URL of the caller |
/// | `{handle}` | Phone number or SIP address |
/// | *any custom key* | Any extra embedded in the notification |
///
/// Examples:
/// - [urlPattern] — `'https://api.example.com/calls/{callId}/reject'`
/// - [body] — `'{"callId": "{callId}", "callerName": "{callerName}"}'`
/// - [headers] values — `{'X-Call-Id': '{callId}'}`
///
/// Unmatched placeholders are left as-is.
///
/// ## Authentication
///
/// If [authStorageKey] is provided, the native side reads the auth token
/// from `flutter_secure_storage` (`EncryptedSharedPreferences` on Android,
/// Keychain on iOS) and adds an `Authorization: Bearer <token>` header.
///
/// ### Key Prefix (Android)
///
/// `flutter_secure_storage` prefixes all keys before storing them in
/// `EncryptedSharedPreferences`. The default prefix is
/// `VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg` (base64 of
/// "This is the prefix for a secure storage"). So a Dart key
/// `"access_token"` is actually stored as
/// `"VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg_access_token"`.
///
/// The [authKeyPrefix] parameter lets you override this prefix if your
/// app uses a custom `AndroidOptions(preferencesKeyPrefix: '...')`.
/// If omitted, the standard `flutter_secure_storage` default is used.
///
/// ## Example
///
/// ```dart
/// BackgroundRejectConfig(
///   urlPattern: 'https://api.example.com/v1/api/calls/{callId}/reject',
///   httpMethod: 'PUT',
///   authStorageKey: 'access_token',
///   headers: {'X-Call-Id': '{callId}'},
///   body: '{"reason": "user_declined"}',
/// )
/// ```
class BackgroundRejectConfig {
  /// Creates a background reject configuration.
  const BackgroundRejectConfig({
    required this.urlPattern,
    this.httpMethod = 'PUT',
    this.authStorageKey,
    this.authKeyPrefix,
    this.headers = const {},
    this.body,
    this.refreshToken,
  });

  /// URL pattern with `{key}` placeholders.
  ///
  /// Example: `'https://api.example.com/v1/api/calls/{callId}/reject'`
  ///
  /// At runtime, all `{key}` tokens are replaced with matching values
  /// from the call metadata (e.g., `{callId}`, `{callerName}`, etc.).
  final String urlPattern;

  /// HTTP method for the reject request.
  ///
  /// Defaults to `'PUT'`. Common alternatives: `'POST'`, `'DELETE'`.
  final String httpMethod;

  /// Key name in `flutter_secure_storage` where the auth token is stored.
  ///
  /// On Android, this reads from `EncryptedSharedPreferences` (file
  /// `"FlutterSecureStorage"`). On iOS, reads from Keychain.
  ///
  /// If provided, adds `Authorization: Bearer <token>` header.
  /// If null, no auth header is added.
  ///
  /// Example: `'access_token'`
  final String? authStorageKey;

  /// Custom key prefix for `flutter_secure_storage` on Android.
  ///
  /// `flutter_secure_storage` prefixes all keys in
  /// `EncryptedSharedPreferences` with a namespace string. The default
  /// prefix is `VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg`
  /// (base64 of "This is the prefix for a secure storage").
  ///
  /// **You only need to set this** if your app uses a custom prefix via
  /// `AndroidOptions(preferencesKeyPrefix: 'your_custom_prefix')`.
  ///
  /// If null (default), the standard `flutter_secure_storage` prefix is
  /// used — which is correct for the vast majority of apps.
  final String? authKeyPrefix;

  /// Additional headers to include in the request.
  ///
  /// `Content-Type: application/json` (when body is present) and
  /// `Authorization` (when [authStorageKey] is set) are added automatically.
  ///
  /// Header values support `{key}` placeholders from call metadata.
  final Map<String, String> headers;

  /// Optional JSON request body.
  ///
  /// If null, no body is sent. Supports `{key}` placeholders from call
  /// metadata (e.g., `'{"callId": "{callId}", "reason": "declined"}'`).
  final String? body;

  /// Optional token refresh configuration.
  ///
  /// When provided and the reject request receives a **401 Unauthorized**:
  /// 1. Reads the refresh token from `flutter_secure_storage`
  /// 2. Makes a refresh token HTTP request
  /// 3. Parses the new access token from the response
  /// 4. Stores the new access token back in `flutter_secure_storage`
  /// 5. Retries the original reject request with the new token
  ///
  /// If null, 401 errors are not retried.
  final RefreshTokenConfig? refreshToken;

  /// Serializes this instance to a [Map] for MethodChannel transport.
  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      'urlPattern': urlPattern,
      'httpMethod': httpMethod,
      if (authStorageKey != null) 'authStorageKey': authStorageKey,
      if (authKeyPrefix != null) 'authKeyPrefix': authKeyPrefix,
      if (headers.isNotEmpty) 'headers': headers,
      if (body != null) 'body': body,
      if (refreshToken != null) 'refreshToken': refreshToken!.toMap(),
    };
  }

  /// Creates an instance from a [Map] received via MethodChannel.
  factory BackgroundRejectConfig.fromMap(Map<String, dynamic> map) {
    return BackgroundRejectConfig(
      urlPattern: map['urlPattern'] as String,
      httpMethod: map['httpMethod'] as String? ?? 'PUT',
      authStorageKey: map['authStorageKey'] as String?,
      authKeyPrefix: map['authKeyPrefix'] as String?,
      headers: map['headers'] != null
          ? Map<String, String>.from(
              map['headers'] as Map<dynamic, dynamic>,
            )
          : const {},
      body: map['body'] as String?,
      refreshToken: map['refreshToken'] != null
          ? RefreshTokenConfig.fromMap(
              Map<String, dynamic>.from(
                map['refreshToken'] as Map<dynamic, dynamic>,
              ),
            )
          : null,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is BackgroundRejectConfig &&
        other.urlPattern == urlPattern &&
        other.httpMethod == httpMethod &&
        other.authStorageKey == authStorageKey &&
        other.authKeyPrefix == authKeyPrefix &&
        mapEquals(other.headers, headers) &&
        other.body == body &&
        other.refreshToken == refreshToken;
  }

  @override
  int get hashCode {
    return Object.hash(
      urlPattern,
      httpMethod,
      authStorageKey,
      authKeyPrefix,
      Object.hashAll(
        headers.entries.map((e) => Object.hash(e.key, e.value)),
      ),
      body,
      refreshToken,
    );
  }

  @override
  String toString() {
    return 'BackgroundRejectConfig('
        'urlPattern: $urlPattern, '
        'httpMethod: $httpMethod, '
        'authStorageKey: $authStorageKey, '
        'authKeyPrefix: ${authKeyPrefix ?? "default"}, '
        'refreshToken: ${refreshToken != null ? "configured" : "none"})';  
  }
}

/// Configuration for native token refresh when a 401 is received.
///
/// When the background reject call receives a **401 Unauthorized**, the
/// native side will:
/// 1. Read the refresh token from `flutter_secure_storage` using
///    [refreshTokenKey]
/// 2. Make an HTTP request to [url] with the refresh token in the body
/// 3. Parse the new access token from the JSON response using
///    [accessTokenJsonPath]
/// 4. Store the new access token back in `flutter_secure_storage`
/// 5. Optionally store a new refresh token if [refreshTokenJsonPath]
///    is provided
/// 6. Retry the original reject request with the new access token
///
/// ## Example
///
/// ```dart
/// RefreshTokenConfig(
///   url: 'https://api.example.com/v1/auth/refresh-token',
///   httpMethod: 'POST',
///   refreshTokenKey: 'refresh_token',
///   bodyTemplate: '{"refreshToken": "{refreshToken}"}',
///   accessTokenJsonPath: 'data.accessToken',
///   refreshTokenJsonPath: 'data.refreshToken',
///   headers: {
///     'X-App-Version': '2.0.5',
///     'X-Platform': 'android',
///   },
/// )
/// ```
///
/// The `{refreshToken}` placeholder in [bodyTemplate] is replaced with
/// the actual refresh token value read from secure storage.
class RefreshTokenConfig {
  /// Creates a refresh token configuration.
  const RefreshTokenConfig({
    required this.url,
    this.httpMethod = 'POST',
    required this.refreshTokenKey,
    this.bodyTemplate = '{"refreshToken": "{refreshToken}"}',
    required this.accessTokenJsonPath,
    this.refreshTokenJsonPath,
    this.headers = const {},
  });

  /// Full URL for the refresh token endpoint.
  ///
  /// Example: `'https://api.example.com/v1/auth/refresh-token'`
  final String url;

  /// HTTP method for the refresh request. Defaults to `'POST'`.
  final String httpMethod;

  /// Key in `flutter_secure_storage` where the refresh token is stored.
  ///
  /// Example: `'refresh_token'`
  final String refreshTokenKey;

  /// Request body template for the refresh request.
  ///
  /// The `{refreshToken}` placeholder is replaced with the actual
  /// refresh token value. Defaults to `'{"refreshToken": "{refreshToken}"}'`.
  final String bodyTemplate;

  /// Dot-separated JSON path to the access token in the refresh response.
  ///
  /// Example: `'data.accessToken'` for response `{"data": {"accessToken": "..."}}`
  final String accessTokenJsonPath;

  /// Optional dot-separated JSON path to a new refresh token in the response.
  ///
  /// If provided, the new refresh token is stored back in
  /// `flutter_secure_storage` at [refreshTokenKey].
  ///
  /// Example: `'data.refreshToken'`
  final String? refreshTokenJsonPath;

  /// Additional headers for the refresh request.
  ///
  /// `Content-Type: application/json` is added automatically.
  final Map<String, String> headers;

  /// Serializes this instance to a [Map] for MethodChannel transport.
  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      'url': url,
      'httpMethod': httpMethod,
      'refreshTokenKey': refreshTokenKey,
      'bodyTemplate': bodyTemplate,
      'accessTokenJsonPath': accessTokenJsonPath,
      if (refreshTokenJsonPath != null)
        'refreshTokenJsonPath': refreshTokenJsonPath,
      if (headers.isNotEmpty) 'headers': headers,
    };
  }

  /// Creates an instance from a [Map] received via MethodChannel.
  factory RefreshTokenConfig.fromMap(Map<String, dynamic> map) {
    return RefreshTokenConfig(
      url: map['url'] as String,
      httpMethod: map['httpMethod'] as String? ?? 'POST',
      refreshTokenKey: map['refreshTokenKey'] as String,
      bodyTemplate: map['bodyTemplate'] as String? ??
          '{"refreshToken": "{refreshToken}"}',
      accessTokenJsonPath: map['accessTokenJsonPath'] as String,
      refreshTokenJsonPath: map['refreshTokenJsonPath'] as String?,
      headers: map['headers'] != null
          ? Map<String, String>.from(
              map['headers'] as Map<dynamic, dynamic>,
            )
          : const {},
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is RefreshTokenConfig &&
        other.url == url &&
        other.httpMethod == httpMethod &&
        other.refreshTokenKey == refreshTokenKey &&
        other.bodyTemplate == bodyTemplate &&
        other.accessTokenJsonPath == accessTokenJsonPath &&
        other.refreshTokenJsonPath == refreshTokenJsonPath &&
        mapEquals(other.headers, headers);
  }

  @override
  int get hashCode {
    return Object.hash(
      url,
      httpMethod,
      refreshTokenKey,
      bodyTemplate,
      accessTokenJsonPath,
      refreshTokenJsonPath,
      Object.hashAll(
        headers.entries.map((e) => Object.hash(e.key, e.value)),
      ),
    );
  }

  @override
  String toString() {
    return 'RefreshTokenConfig('
        'url: $url, '
        'httpMethod: $httpMethod, '
        'refreshTokenKey: $refreshTokenKey)';
  }
}
