package com.example.simplevpn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class StoredVlessConfig(
    val rawLink: String,
    val displayName: String,
    val serverAddress: String,
    val serverPort: Int,
    val transport: String,
    val security: String,
    val hostHeader: String,
    val path: String,
    val importedAtMillis: Long,
    val connectCount: Int,
    val lastConnectedAtMillis: Long,
    val lastStatusAtMillis: Long
)

object ProfileStorage {
    private const val PREFS_NAME = "vless_profile_storage"
    private const val KEY_VLESS_PROFILES_JSON = "vless_profiles_json"
    private const val KEY_ACTIVE_VLESS_RAW = "active_vless_raw"

    private const val KEY_VLESS_RAW = "vless_raw"
    private const val KEY_VLESS_NAME = "vless_name"
    private const val KEY_VLESS_SERVER = "vless_server"
    private const val KEY_VLESS_PORT = "vless_port"
    private const val KEY_VLESS_TRANSPORT = "vless_transport"
    private const val KEY_VLESS_SECURITY = "vless_security"
    private const val KEY_VLESS_HOST = "vless_host"
    private const val KEY_VLESS_PATH = "vless_path"
    private const val KEY_VLESS_IMPORTED_AT = "vless_imported_at"
    private const val KEY_VLESS_CONNECT_COUNT = "vless_connect_count"
    private const val KEY_VLESS_LAST_CONNECTED_AT = "vless_last_connected_at"
    private const val KEY_VLESS_LAST_STATUS_AT = "vless_last_status_at"

    fun saveVlessConfig(context: Context, config: StoredVlessConfig) {
        saveOrUpdateVlessProfile(context, config, makeActive = true)
    }

    fun saveOrUpdateVlessProfile(
        context: Context,
        config: StoredVlessConfig,
        makeActive: Boolean = true
    ) {
        val prefs = prefs(context)
        migrateLegacyProfileIfNeeded(prefs)

        val profiles = loadProfilesInternal(prefs).toMutableList()
        val existingIndex = profiles.indexOfFirst { it.rawLink == config.rawLink }
        if (existingIndex >= 0) {
            profiles[existingIndex] = config
        } else {
            profiles.add(0, config)
        }

        val activeRaw = if (makeActive) {
            config.rawLink
        } else {
            prefs.getString(KEY_ACTIVE_VLESS_RAW, null)
        }

        saveProfilesInternal(prefs, profiles, activeRaw)
    }

    fun loadVlessProfiles(context: Context): List<StoredVlessConfig> {
        val prefs = prefs(context)
        migrateLegacyProfileIfNeeded(prefs)

        val profiles = loadProfilesInternal(prefs).toMutableList()
        val activeRaw = prefs.getString(KEY_ACTIVE_VLESS_RAW, null)
        val activeProfileIndex = profiles.indexOfFirst { it.rawLink == activeRaw }
        if (activeProfileIndex > 0) {
            val activeProfile = profiles.removeAt(activeProfileIndex)
            profiles.add(0, activeProfile)
        }
        return profiles
    }

    fun loadVlessConfig(context: Context): StoredVlessConfig? {
        val prefs = prefs(context)
        migrateLegacyProfileIfNeeded(prefs)

        val profiles = loadProfilesInternal(prefs)
        if (profiles.isEmpty()) {
            return null
        }

        val activeRaw = prefs.getString(KEY_ACTIVE_VLESS_RAW, null)
        val activeProfile = profiles.firstOrNull { it.rawLink == activeRaw } ?: profiles.first()
        if (activeRaw != activeProfile.rawLink) {
            prefs.edit().putString(KEY_ACTIVE_VLESS_RAW, activeProfile.rawLink).apply()
        }
        return activeProfile
    }

    fun findVlessConfig(context: Context, rawLink: String): StoredVlessConfig? {
        return loadVlessProfiles(context).firstOrNull { it.rawLink == rawLink }
    }

    fun setActiveVlessProfile(context: Context, rawLink: String): StoredVlessConfig? {
        val prefs = prefs(context)
        migrateLegacyProfileIfNeeded(prefs)

        val profile = loadProfilesInternal(prefs).firstOrNull { it.rawLink == rawLink } ?: return null
        prefs.edit().putString(KEY_ACTIVE_VLESS_RAW, rawLink).apply()
        return profile
    }

    fun deleteVlessProfile(context: Context, rawLink: String): Boolean {
        val prefs = prefs(context)
        migrateLegacyProfileIfNeeded(prefs)

        val profiles = loadProfilesInternal(prefs)
        if (profiles.none { it.rawLink == rawLink }) {
            return false
        }

        val updatedProfiles = profiles.filterNot { it.rawLink == rawLink }
        val currentActiveRaw = prefs.getString(KEY_ACTIVE_VLESS_RAW, null)
        val nextActiveRaw = if (currentActiveRaw == rawLink) {
            updatedProfiles.firstOrNull()?.rawLink
        } else {
            currentActiveRaw
        }
        saveProfilesInternal(prefs, updatedProfiles, nextActiveRaw)
        return true
    }

    fun incrementVlessConnectCount(context: Context): StoredVlessConfig? {
        val current = loadVlessConfig(context) ?: return null
        val updated = current.copy(
            connectCount = current.connectCount + 1,
            lastConnectedAtMillis = System.currentTimeMillis()
        )
        saveVlessConfig(context, updated)
        return updated
    }

    fun saveVlessLastStatusAt(context: Context, timestamp: Long) {
        val current = loadVlessConfig(context) ?: return
        saveVlessConfig(
            context,
            current.copy(lastStatusAtMillis = timestamp)
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun migrateLegacyProfileIfNeeded(
        prefs: android.content.SharedPreferences
    ) {
        val existingProfilesJson = prefs.getString(KEY_VLESS_PROFILES_JSON, null)
        if (!existingProfilesJson.isNullOrBlank()) {
            return
        }

        val raw = prefs.getString(KEY_VLESS_RAW, null) ?: return
        val legacyProfile = StoredVlessConfig(
            rawLink = raw,
            displayName = prefs.getString(KEY_VLESS_NAME, "") ?: "",
            serverAddress = prefs.getString(KEY_VLESS_SERVER, "") ?: "",
            serverPort = prefs.getInt(KEY_VLESS_PORT, 0),
            transport = prefs.getString(KEY_VLESS_TRANSPORT, "") ?: "",
            security = prefs.getString(KEY_VLESS_SECURITY, "") ?: "",
            hostHeader = prefs.getString(KEY_VLESS_HOST, "") ?: "",
            path = prefs.getString(KEY_VLESS_PATH, "") ?: "",
            importedAtMillis = prefs.getLong(KEY_VLESS_IMPORTED_AT, 0L),
            connectCount = prefs.getInt(KEY_VLESS_CONNECT_COUNT, 0),
            lastConnectedAtMillis = prefs.getLong(KEY_VLESS_LAST_CONNECTED_AT, 0L),
            lastStatusAtMillis = prefs.getLong(KEY_VLESS_LAST_STATUS_AT, 0L)
        )
        saveProfilesInternal(prefs, listOf(legacyProfile), legacyProfile.rawLink)
    }

    private fun loadProfilesInternal(
        prefs: android.content.SharedPreferences
    ): List<StoredVlessConfig> {
        val rawJson = prefs.getString(KEY_VLESS_PROFILES_JSON, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(rawJson)
            val profiles = mutableListOf<StoredVlessConfig>()
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(index) ?: continue
                profiles += StoredVlessConfig(
                    rawLink = item.optString(KEY_VLESS_RAW),
                    displayName = item.optString(KEY_VLESS_NAME),
                    serverAddress = item.optString(KEY_VLESS_SERVER),
                    serverPort = item.optInt(KEY_VLESS_PORT),
                    transport = item.optString(KEY_VLESS_TRANSPORT),
                    security = item.optString(KEY_VLESS_SECURITY),
                    hostHeader = item.optString(KEY_VLESS_HOST),
                    path = item.optString(KEY_VLESS_PATH),
                    importedAtMillis = item.optLong(KEY_VLESS_IMPORTED_AT),
                    connectCount = item.optInt(KEY_VLESS_CONNECT_COUNT),
                    lastConnectedAtMillis = item.optLong(KEY_VLESS_LAST_CONNECTED_AT),
                    lastStatusAtMillis = item.optLong(KEY_VLESS_LAST_STATUS_AT)
                )
            }
            profiles.filter { it.rawLink.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveProfilesInternal(
        prefs: android.content.SharedPreferences,
        profiles: List<StoredVlessConfig>,
        requestedActiveRaw: String?
    ) {
        val normalizedProfiles = profiles
            .filter { it.rawLink.isNotBlank() }
            .distinctBy { it.rawLink }

        val activeRaw = requestedActiveRaw
            ?.takeIf { raw -> normalizedProfiles.any { it.rawLink == raw } }
            ?: normalizedProfiles.firstOrNull()?.rawLink

        val jsonArray = JSONArray()
        normalizedProfiles.forEach { profile ->
            jsonArray.put(
                JSONObject().apply {
                    put(KEY_VLESS_RAW, profile.rawLink)
                    put(KEY_VLESS_NAME, profile.displayName)
                    put(KEY_VLESS_SERVER, profile.serverAddress)
                    put(KEY_VLESS_PORT, profile.serverPort)
                    put(KEY_VLESS_TRANSPORT, profile.transport)
                    put(KEY_VLESS_SECURITY, profile.security)
                    put(KEY_VLESS_HOST, profile.hostHeader)
                    put(KEY_VLESS_PATH, profile.path)
                    put(KEY_VLESS_IMPORTED_AT, profile.importedAtMillis)
                    put(KEY_VLESS_CONNECT_COUNT, profile.connectCount)
                    put(KEY_VLESS_LAST_CONNECTED_AT, profile.lastConnectedAtMillis)
                    put(KEY_VLESS_LAST_STATUS_AT, profile.lastStatusAtMillis)
                }
            )
        }

        prefs.edit().apply {
            putString(KEY_VLESS_PROFILES_JSON, jsonArray.toString())
            if (activeRaw == null) {
                remove(KEY_ACTIVE_VLESS_RAW)
            } else {
                putString(KEY_ACTIVE_VLESS_RAW, activeRaw)
            }
        }.apply()
    }
}
