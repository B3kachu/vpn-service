package com.example.simplevpn

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object AppSettings {
    private const val PREFS_NAME = "bobby_connect_settings"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_THEME = "theme"
    private const val KEY_DNS = "dns"
    private const val KEY_NETWORK_STACK = "network_stack"
    private const val KEY_SPLIT_TUNNEL_PACKAGES = "split_tunnel_packages"
    private const val KEY_SPLIT_TUNNEL_DOMAINS = "split_tunnel_domains"
    private const val KEY_SPLIT_TUNNEL_MODE = "split_tunnel_mode"

    enum class LanguageOption(val tag: String) {
        ENGLISH("en"),
        RUSSIAN("ru")
    }

    enum class ThemeOption(val value: String) {
        LIGHT("light"),
        DARK("dark")
    }

    enum class DnsOption(val value: String, val servers: List<String>) {
        GOOGLE("google", listOf("8.8.8.8", "8.8.4.4")),
        CLOUDFLARE("cloudflare", listOf("1.1.1.1", "1.0.0.1")),
        QUAD9("quad9", listOf("9.9.9.9", "149.112.112.112")),
        ADGUARD("adguard", listOf("94.140.14.14", "94.140.15.15"))
    }

    enum class NetworkStackOption(val value: String) {
        MIXED("mixed"),
        IPV4("ipv4")
    }

    enum class SplitTunnelMode(val value: String) {
        BYPASS_SELECTED("bypass_selected"),
        PROXY_SELECTED("proxy_selected")
    }

    fun applySaved(context: Context) {
        applyTheme(getTheme(context))
        applyLanguage(getLanguage(context))
    }

    fun getLanguage(context: Context): LanguageOption {
        val stored = prefs(context).getString(KEY_LANGUAGE, null)
        return when (stored) {
            LanguageOption.RUSSIAN.tag -> LanguageOption.RUSSIAN
            LanguageOption.ENGLISH.tag -> LanguageOption.ENGLISH
            else -> {
                if (Locale.getDefault().language.equals(LanguageOption.RUSSIAN.tag, ignoreCase = true)) {
                    LanguageOption.RUSSIAN
                } else {
                    LanguageOption.ENGLISH
                }
            }
        }
    }

    fun setLanguage(context: Context, language: LanguageOption) {
        prefs(context).edit().putString(KEY_LANGUAGE, language.tag).apply()
        applyLanguage(language)
    }

    fun getTheme(context: Context): ThemeOption {
        val stored = prefs(context).getString(KEY_THEME, null)
        return when (stored) {
            ThemeOption.DARK.value -> ThemeOption.DARK
            ThemeOption.LIGHT.value -> ThemeOption.LIGHT
            else -> {
                val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
                    ThemeOption.DARK
                } else {
                    ThemeOption.LIGHT
                }
            }
        }
    }

    fun setTheme(context: Context, theme: ThemeOption) {
        prefs(context).edit().putString(KEY_THEME, theme.value).apply()
        applyTheme(theme)
    }

    fun getDnsOption(context: Context): DnsOption {
        val stored = prefs(context).getString(KEY_DNS, null)
        return DnsOption.entries.firstOrNull { it.value == stored } ?: DnsOption.GOOGLE
    }

    fun setDnsOption(context: Context, option: DnsOption) {
        prefs(context).edit().putString(KEY_DNS, option.value).apply()
    }

    fun getNetworkStack(context: Context): NetworkStackOption {
        val stored = prefs(context).getString(KEY_NETWORK_STACK, null)
        return NetworkStackOption.entries.firstOrNull { it.value == stored } ?: NetworkStackOption.MIXED
    }

    fun setNetworkStack(context: Context, option: NetworkStackOption) {
        prefs(context).edit().putString(KEY_NETWORK_STACK, option.value).apply()
    }

    fun getSplitTunnelPackages(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_SPLIT_TUNNEL_PACKAGES, emptySet())?.toSet().orEmpty()
    }

    fun setSplitTunnelPackages(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(KEY_SPLIT_TUNNEL_PACKAGES, packages.toSet()).apply()
    }

    fun getSplitTunnelDomains(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_SPLIT_TUNNEL_DOMAINS, emptySet())
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()
    }

    fun setSplitTunnelDomains(context: Context, domains: Set<String>) {
        prefs(context).edit()
            .putStringSet(
                KEY_SPLIT_TUNNEL_DOMAINS,
                domains.map { it.trim() }.filter { it.isNotBlank() }.toSet()
            )
            .apply()
    }

    fun getSplitTunnelMode(context: Context): SplitTunnelMode {
        val stored = prefs(context).getString(KEY_SPLIT_TUNNEL_MODE, null)
        return SplitTunnelMode.entries.firstOrNull { it.value == stored } ?: SplitTunnelMode.BYPASS_SELECTED
    }

    fun setSplitTunnelMode(context: Context, mode: SplitTunnelMode) {
        prefs(context).edit().putString(KEY_SPLIT_TUNNEL_MODE, mode.value).apply()
    }

    private fun applyLanguage(language: LanguageOption) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }

    private fun applyTheme(theme: ThemeOption) {
        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                ThemeOption.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeOption.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            }
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
