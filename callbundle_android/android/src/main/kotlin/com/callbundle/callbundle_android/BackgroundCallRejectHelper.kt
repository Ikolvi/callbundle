package com.callbundle.callbundle_android

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Makes a direct native HTTP request to reject a call, bypassing Dart entirely.
 *
 * ## Why This Exists
 *
 * When the user declines a call from the notification while the app is
 * killed (process dead), the Dart isolate is not reliably available:
 *
 * 1. The background FCM engine's Dart isolate may have its MethodChannel
 *    handler disconnected or the event stream has no listeners.
 * 2. [PendingCallStore] defers the reject to the next app start, which
 *    could be minutes/hours later — the caller keeps ringing.
 *
 * This helper reads the auth token directly from `flutter_secure_storage`'s
 * [EncryptedSharedPreferences] and makes the reject API call immediately
 * on a background thread. The caller sees the rejection within seconds.
 *
 * ## Generic Configuration
 *
 * The URL pattern, HTTP method, auth key, headers, and body are all
 * configured via [BackgroundRejectConfig] in Dart, passed during
 * [CallBundlePlugin.handleConfigure], and stored in SharedPreferences.
 *
 * ## Dynamic Placeholders
 *
 * All `{key}` placeholders in the URL, body, and header values are
 * resolved dynamically from the call data map passed to [rejectCall].
 * This is fully generic — any key from the call metadata can be used:
 *
 * - **URL pattern** — e.g., `https://api.example.com/v1/api/calls/{callId}/reject`
 * - **Request body** — e.g., `{"callId": "{callId}", "callerName": "{callerName}"}`
 * - **Header values** — e.g., `X-Call-Id: {callId}`
 *
 * Available placeholder keys depend on what the caller passes to [rejectCall].
 * Typical keys include: `callId`, `callerName`, `callType`, `callerAvatar`,
 * `handle`, and any other extras embedded in the notification.
 *
 * ## Thread Safety
 *
 * [rejectCall] runs the HTTP request on a new thread and returns immediately.
 * It is safe to call from [CallActionReceiver.onReceive] (main thread) or
 * any other thread.
 *
 * ## Auth Token Reading
 *
 * Uses `EncryptedSharedPreferences` with the same parameters as
 * `flutter_secure_storage` v9+:
 * - File name: `"FlutterSecureStorage"`
 * - MasterKey scheme: `AES256_GCM`
 * - PrefKey encryption: `AES256_SIV`
 * - PrefValue encryption: `AES256_GCM`
 *
 * Requires Android API 23+ (EncryptedSharedPreferences minimum).
 * On API < 23, the reject call is skipped (fallback to PendingCallStore).
 */
object BackgroundCallRejectHelper {

    private const val TAG = "BgCallReject"
    private const val PREFS_NAME = "callbundle_bg_reject_config"
    private const val KEY_URL_PATTERN = "url_pattern"
    private const val KEY_HTTP_METHOD = "http_method"
    private const val KEY_AUTH_STORAGE_KEY = "auth_storage_key"
    private const val KEY_AUTH_KEY_PREFIX = "auth_key_prefix"
    private const val KEY_HEADERS = "headers_json"
    private const val KEY_BODY = "body"

    // Refresh token config keys
    private const val KEY_REFRESH_URL = "refresh_url"
    private const val KEY_REFRESH_HTTP_METHOD = "refresh_http_method"
    private const val KEY_REFRESH_TOKEN_KEY = "refresh_token_key"
    private const val KEY_REFRESH_BODY_TEMPLATE = "refresh_body_template"
    private const val KEY_REFRESH_ACCESS_TOKEN_PATH = "refresh_access_token_path"
    private const val KEY_REFRESH_REFRESH_TOKEN_PATH = "refresh_refresh_token_path"
    private const val KEY_REFRESH_HEADERS = "refresh_headers_json"

    // flutter_secure_storage EncryptedSharedPreferences file name
    private const val FLUTTER_SECURE_STORAGE_FILE = "FlutterSecureStorage"

    // flutter_secure_storage ALWAYS prefixes keys with this string.
    // It is the base64 encoding of "This is the prefix for a secure storage\n".
    // See: FlutterSecureStoragePlugin.java → getKeyFromCall() → addPrefixToKey()
    private const val FLUTTER_SECURE_STORAGE_KEY_PREFIX =
        "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg"

    // Regex to match {placeholder} tokens in templates
    private val PLACEHOLDER_REGEX = Regex("\\{(\\w+)\\}")

    /**
     * Stores the background reject configuration in SharedPreferences.
     *
     * Called by [CallBundlePlugin.handleConfigure] with values from
     * [BackgroundRejectConfig] in Dart.
     *
     * @param context Application context.
     * @param configMap The `backgroundReject` map from the configure call.
     *   Expected keys: `urlPattern`, `httpMethod`, `authStorageKey`,
     *   `headers` (Map), `body` (String).
     */
    fun storeConfig(context: Context, configMap: Map<*, *>) {
        val urlPattern = configMap["urlPattern"] as? String ?: return
        val httpMethod = configMap["httpMethod"] as? String ?: "PUT"
        val authStorageKey = configMap["authStorageKey"] as? String
        val authKeyPrefix = configMap["authKeyPrefix"] as? String
        val headers = configMap["headers"] as? Map<*, *>
        val body = configMap["body"] as? String

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_URL_PATTERN, urlPattern)
            .putString(KEY_HTTP_METHOD, httpMethod)
            .putString(KEY_AUTH_STORAGE_KEY, authStorageKey)
            .putString(KEY_AUTH_KEY_PREFIX, authKeyPrefix)
            .putString(KEY_HEADERS, if (headers != null) JSONObject(headers).toString() else null)
            .putString(KEY_BODY, body)
            .apply()
        Log.d(TAG, "storeConfig: method=$httpMethod, url=${urlPattern.take(50)}..., authKey=$authStorageKey, customPrefix=${authKeyPrefix != null}")

        // Store refresh token config if provided
        val refreshConfig = configMap["refreshToken"] as? Map<*, *>
        if (refreshConfig != null) {
            prefs.edit()
                .putString(KEY_REFRESH_URL, refreshConfig["url"] as? String)
                .putString(KEY_REFRESH_HTTP_METHOD, refreshConfig["httpMethod"] as? String ?: "POST")
                .putString(KEY_REFRESH_TOKEN_KEY, refreshConfig["refreshTokenKey"] as? String)
                .putString(KEY_REFRESH_BODY_TEMPLATE, refreshConfig["bodyTemplate"] as? String)
                .putString(KEY_REFRESH_ACCESS_TOKEN_PATH, refreshConfig["accessTokenJsonPath"] as? String)
                .putString(KEY_REFRESH_REFRESH_TOKEN_PATH, refreshConfig["refreshTokenJsonPath"] as? String)
                .putString(KEY_REFRESH_HEADERS,
                    (refreshConfig["headers"] as? Map<*, *>)?.let { JSONObject(it).toString() })
                .apply()
            Log.d(TAG, "storeConfig: Refresh config stored (url=${(refreshConfig["url"] as? String)?.take(50)})")
        }
    }

    /**
     * Makes a background HTTP request to reject the call.
     *
     * Reads the full configuration from SharedPreferences (stored during
     * configure), reads the auth token from EncryptedSharedPreferences,
     * and makes the request.
     *
     * All `{key}` placeholders in the URL pattern, body, and header values
     * are replaced with matching values from [callData]. Unmatched
     * placeholders are left as-is.
     *
     * Runs on a new thread; returns immediately.
     *
     * @param context Application context
     * @param callData Map of call metadata for placeholder resolution.
     *   At minimum should contain `"callId"`. May also include
     *   `"callerName"`, `"callType"`, `"handle"`, etc.
     */
    fun rejectCall(context: Context, callData: Map<String, String>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.w(TAG, "rejectCall: API < 23, cannot read EncryptedSharedPreferences. Skipping.")
            return
        }

        val callId = callData["callId"]
        if (callId.isNullOrEmpty()) {
            Log.e(TAG, "rejectCall: callData missing 'callId'. Skipping.")
            return
        }

        Thread {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val urlPattern = prefs.getString(KEY_URL_PATTERN, null)
                val httpMethod = prefs.getString(KEY_HTTP_METHOD, "PUT") ?: "PUT"
                val authKey = prefs.getString(KEY_AUTH_STORAGE_KEY, null)
                val authKeyPrefix = prefs.getString(KEY_AUTH_KEY_PREFIX, null)
                val headersJson = prefs.getString(KEY_HEADERS, null)
                val body = prefs.getString(KEY_BODY, null)

                if (urlPattern.isNullOrEmpty()) {
                    Log.w(TAG, "rejectCall: No urlPattern configured. Skipping native reject.")
                    return@Thread
                }

                // Enrich call data with auto-generated values
                val enrichedData = callData.toMutableMap().apply {
                    put("uuid", UUID.randomUUID().toString())
                }

                // Resolve all {placeholder} tokens in the URL pattern
                val resolvedUrl = resolvePlaceholders(urlPattern, enrichedData)
                Log.d(TAG, "rejectCall: $httpMethod $resolvedUrl (callData keys: ${enrichedData.keys})")

                // Read auth token if configured
                var authToken: String? = null
                if (!authKey.isNullOrEmpty()) {
                    authToken = readAuthToken(context, authKey, authKeyPrefix)
                    if (authToken.isNullOrEmpty()) {
                        Log.w(TAG, "rejectCall: Auth token is null/empty for key=$authKey. Proceeding without auth.")
                    }
                }

                // Make HTTP request
                var responseCode = executeRequest(
                    resolvedUrl, httpMethod, authToken, headersJson, body, enrichedData
                )

                // On 401: attempt token refresh and retry once
                if (responseCode == 401 && !authKey.isNullOrEmpty()) {
                    Log.d(TAG, "rejectCall: Got 401 — attempting token refresh...")
                    val refreshed = attemptTokenRefresh(context, authKeyPrefix)
                    if (refreshed) {
                        // Re-read the new auth token
                        authToken = readAuthToken(context, authKey, authKeyPrefix)
                        if (!authToken.isNullOrEmpty()) {
                            // Generate a fresh UUID for the retry
                            enrichedData["uuid"] = UUID.randomUUID().toString()
                            Log.d(TAG, "rejectCall: Retrying with refreshed token...")
                            responseCode = executeRequest(
                                resolvedUrl, httpMethod, authToken, headersJson, body, enrichedData
                            )
                        }
                    } else {
                        Log.w(TAG, "rejectCall: Token refresh failed — cannot retry.")
                    }
                }

                if (responseCode in 200..299) {
                    Log.d(TAG, "rejectCall: SUCCESS — caller side should see rejection now")
                } else {
                    Log.w(TAG, "rejectCall: Final response was HTTP $responseCode for callId=$callId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "rejectCall: Failed for callId=$callId", e)
            }
        }.start()
    }

    /**
     * Replaces all `{key}` placeholders in [template] with matching values
     * from [data]. Unmatched placeholders are left untouched.
     *
     * This is the core of the generic placeholder system. Any key present
     * in [data] can be referenced via `{key}` in the template string.
     *
     * Examples:
     * ```
     * val data = mapOf("callId" to "abc-123", "callerName" to "John")
     * resolvePlaceholders("https://api.com/calls/{callId}/reject", data)
     *   → "https://api.com/calls/abc-123/reject"
     * resolvePlaceholders("{\"caller\": \"{callerName}\"}", data)
     *   → "{\"caller\": \"John\"}"
     * ```
     *
     * @param template The string containing `{key}` placeholders
     * @param data Map of available values for substitution
     * @return The template with all matched placeholders replaced
     */
    private fun resolvePlaceholders(template: String, data: Map<String, String>): String {
        return PLACEHOLDER_REGEX.replace(template) { matchResult ->
            val key = matchResult.groupValues[1]
            data[key] ?: matchResult.value // Keep original if no match
        }
    }

    /**
     * Reads the auth token from flutter_secure_storage's EncryptedSharedPreferences.
     *
     * Uses the same encryption parameters as `flutter_secure_storage` v9+:
     * - MasterKey: AES256_GCM
     * - PrefKeyEncryption: AES256_SIV
     * - PrefValueEncryption: AES256_GCM
     *
     * **Key prefix**: `flutter_secure_storage` always stores keys with a
     * namespace prefix. The default is
     * `VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg` (base64 of
     * "This is the prefix for a secure storage"). For example, the Dart key
     * `"access_token"` is stored as
     * `"VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg_access_token"`.
     *
     * If the app uses a custom `AndroidOptions(preferencesKeyPrefix: ...)`,
     * pass that custom prefix via [customPrefix].
     *
     * @param context Application context
     * @param key The key name as used in Dart (e.g., "access_token")
     * @param customPrefix Optional custom prefix. If null, the default
     *   `flutter_secure_storage` prefix is used.
     * @return The decrypted token, or null if not found or on error
     */
    private fun readAuthToken(context: Context, key: String, customPrefix: String? = null): String? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val securePrefs = EncryptedSharedPreferences.create(
                context,
                FLUTTER_SECURE_STORAGE_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            // flutter_secure_storage always prefixes keys — see addPrefixToKey().
            // Use custom prefix if provided, otherwise use the library default.
            val prefix = customPrefix ?: FLUTTER_SECURE_STORAGE_KEY_PREFIX
            val prefixedKey = "${prefix}_${key}"
            val token = securePrefs.getString(prefixedKey, null)
            if (token != null) {
                Log.d(TAG, "readAuthToken: Found token for key=$key (prefixed, length=${token.length})")
            } else {
                Log.w(TAG, "readAuthToken: No value for prefixedKey=$prefixedKey in $FLUTTER_SECURE_STORAGE_FILE")
            }
            token
        } catch (e: Exception) {
            Log.e(TAG, "readAuthToken: Failed to read from EncryptedSharedPreferences", e)
            null
        }
    }

    /**
     * Executes an HTTP request and returns the response code.
     *
     * This is the core HTTP execution method used by both [rejectCall]
     * and retries after token refresh.
     *
     * @param resolvedUrl The fully resolved URL (no placeholders)
     * @param httpMethod HTTP method (GET, POST, PUT, DELETE, etc.)
     * @param authToken Bearer token, or null to skip Authorization header
     * @param headersJson JSON string of custom headers, or null
     * @param body Request body template (may contain placeholders), or null
     * @param callData Data map for resolving placeholders in headers/body
     * @return HTTP response code (e.g., 200, 401, 500)
     */
    private fun executeRequest(
        resolvedUrl: String,
        httpMethod: String,
        authToken: String?,
        headersJson: String?,
        body: String?,
        callData: Map<String, String>,
    ): Int {
        val url = URL(resolvedUrl)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = httpMethod
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            // Add auth header
            if (!authToken.isNullOrEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer $authToken")
            }

            // Add custom headers (resolve {placeholder} tokens in values)
            if (!headersJson.isNullOrEmpty()) {
                try {
                    val headersObj = JSONObject(headersJson)
                    val keys = headersObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = resolvePlaceholders(headersObj.getString(key), callData)
                        connection.setRequestProperty(key, value)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "executeRequest: Failed to parse custom headers", e)
                }
            }

            // Write body only if explicitly configured (resolve {placeholder} tokens)
            if (body != null) {
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                val resolvedBody = resolvePlaceholders(body, callData)
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(resolvedBody)
                    writer.flush()
                }
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "executeRequest: HTTP $responseCode for $httpMethod ${resolvedUrl.take(80)}")

            if (responseCode !in 200..299) {
                val errorBody = try {
                    connection.errorStream?.bufferedReader()?.readText() ?: "no body"
                } catch (_: Exception) { "unreadable" }
                Log.w(TAG, "executeRequest: HTTP $responseCode — $errorBody")
            }

            return responseCode
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Attempts to refresh the auth token using the stored refresh config.
     *
     * Flow:
     * 1. Reads refresh config from SharedPreferences
     * 2. Reads the refresh token from EncryptedSharedPreferences
     * 3. Makes an HTTP request to the refresh endpoint
     * 4. Parses the response JSON for new access/refresh tokens
     * 5. Stores the new tokens back in EncryptedSharedPreferences
     *
     * @param context Application context
     * @param authKeyPrefix Custom key prefix for EncryptedSharedPreferences,
     *   or null to use the default flutter_secure_storage prefix
     * @return `true` if the token was refreshed successfully, `false` otherwise
     */
    private fun attemptTokenRefresh(context: Context, authKeyPrefix: String?): Boolean {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val refreshUrl = prefs.getString(KEY_REFRESH_URL, null)
            val refreshMethod = prefs.getString(KEY_REFRESH_HTTP_METHOD, "POST") ?: "POST"
            val refreshTokenKey = prefs.getString(KEY_REFRESH_TOKEN_KEY, null)
            val bodyTemplate = prefs.getString(KEY_REFRESH_BODY_TEMPLATE, null)
            val accessTokenPath = prefs.getString(KEY_REFRESH_ACCESS_TOKEN_PATH, null)
            val refreshTokenPath = prefs.getString(KEY_REFRESH_REFRESH_TOKEN_PATH, null)
            val refreshHeadersJson = prefs.getString(KEY_REFRESH_HEADERS, null)
            val authKey = prefs.getString(KEY_AUTH_STORAGE_KEY, null)

            if (refreshUrl.isNullOrEmpty() || refreshTokenKey.isNullOrEmpty() || accessTokenPath.isNullOrEmpty()) {
                Log.w(TAG, "attemptTokenRefresh: Refresh config incomplete " +
                    "(url=$refreshUrl, tokenKey=$refreshTokenKey, accessPath=$accessTokenPath)")
                return false
            }

            // Read the refresh token from secure storage
            val refreshToken = readAuthToken(context, refreshTokenKey, authKeyPrefix)
            if (refreshToken.isNullOrEmpty()) {
                Log.w(TAG, "attemptTokenRefresh: No refresh token found for key=$refreshTokenKey")
                return false
            }

            // Resolve the body template with refresh token and a fresh UUID
            val refreshData = mapOf(
                "refreshToken" to refreshToken,
                "uuid" to UUID.randomUUID().toString()
            )
            val resolvedBody = if (bodyTemplate != null) {
                resolvePlaceholders(bodyTemplate, refreshData)
            } else null

            Log.d(TAG, "attemptTokenRefresh: $refreshMethod $refreshUrl")

            // Make the refresh request
            val url = URL(refreshUrl)
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = refreshMethod
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty("Content-Type", "application/json")

                // Add refresh-specific headers
                if (!refreshHeadersJson.isNullOrEmpty()) {
                    try {
                        val headersObj = JSONObject(refreshHeadersJson)
                        val keys = headersObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val value = resolvePlaceholders(headersObj.getString(key), refreshData)
                            connection.setRequestProperty(key, value)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "attemptTokenRefresh: Failed to parse refresh headers", e)
                    }
                }

                // Write body
                if (resolvedBody != null) {
                    connection.doOutput = true
                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(resolvedBody)
                        writer.flush()
                    }
                }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    val errorBody = try {
                        connection.errorStream?.bufferedReader()?.readText() ?: "no body"
                    } catch (_: Exception) { "unreadable" }
                    Log.w(TAG, "attemptTokenRefresh: HTTP $responseCode — $errorBody")
                    return false
                }

                // Parse response
                val responseBody = connection.inputStream.bufferedReader().readText()
                val responseJson = JSONObject(responseBody)

                // Extract new access token using dot-notation path
                val newAccessToken = resolveJsonPath(responseJson, accessTokenPath)
                if (newAccessToken.isNullOrEmpty()) {
                    Log.w(TAG, "attemptTokenRefresh: Could not extract access token " +
                        "from path=$accessTokenPath")
                    return false
                }

                // Store new access token
                if (!authKey.isNullOrEmpty()) {
                    writeSecureStorageValue(context, authKey, newAccessToken, authKeyPrefix)
                    Log.d(TAG, "attemptTokenRefresh: Stored new access token " +
                        "(length=${newAccessToken.length})")
                }

                // Store new refresh token if path configured and available
                if (!refreshTokenPath.isNullOrEmpty()) {
                    val newRefreshToken = resolveJsonPath(responseJson, refreshTokenPath)
                    if (!newRefreshToken.isNullOrEmpty()) {
                        writeSecureStorageValue(
                            context, refreshTokenKey, newRefreshToken, authKeyPrefix
                        )
                        Log.d(TAG, "attemptTokenRefresh: Stored new refresh token " +
                            "(length=${newRefreshToken.length})")
                    }
                }

                Log.d(TAG, "attemptTokenRefresh: SUCCESS — tokens refreshed")
                return true
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "attemptTokenRefresh: Failed", e)
            return false
        }
    }

    /**
     * Resolves a dot-separated JSON path to a string value.
     *
     * For example, given the JSON:
     * ```json
     * {"data": {"accessToken": "abc123", "expiresIn": 3600}}
     * ```
     * The path `"data.accessToken"` returns `"abc123"`.
     *
     * @param json The root JSON object
     * @param path Dot-separated path (e.g., `"data.accessToken"`)
     * @return The string value at the path, or null if not found
     */
    private fun resolveJsonPath(json: JSONObject, path: String): String? {
        return try {
            val parts = path.split(".")
            var current: Any = json
            for (i in parts.indices) {
                current = when (current) {
                    is JSONObject -> {
                        if (i == parts.lastIndex) {
                            current.getString(parts[i])
                        } else {
                            current.getJSONObject(parts[i])
                        }
                    }
                    else -> return null
                }
            }
            current as? String
        } catch (e: Exception) {
            Log.w(TAG, "resolveJsonPath: Failed for path=$path", e)
            null
        }
    }

    /**
     * Writes a value to flutter_secure_storage's EncryptedSharedPreferences.
     *
     * Uses the same encryption parameters and key prefix as [readAuthToken].
     *
     * @param context Application context
     * @param key The key name as used in Dart (e.g., "access_token")
     * @param value The value to store
     * @param customPrefix Optional custom key prefix, or null for default
     */
    private fun writeSecureStorageValue(
        context: Context,
        key: String,
        value: String,
        customPrefix: String? = null
    ) {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val securePrefs = EncryptedSharedPreferences.create(
                context,
                FLUTTER_SECURE_STORAGE_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val prefix = customPrefix ?: FLUTTER_SECURE_STORAGE_KEY_PREFIX
            val prefixedKey = "${prefix}_${key}"
            securePrefs.edit().putString(prefixedKey, value).apply()
            Log.d(TAG, "writeSecureStorageValue: Stored value for key=$key (prefixed)")
        } catch (e: Exception) {
            Log.e(TAG, "writeSecureStorageValue: Failed to write to " +
                "EncryptedSharedPreferences", e)
        }
    }
}
