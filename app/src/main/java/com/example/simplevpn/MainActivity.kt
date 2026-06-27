package com.example.simplevpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var homeTabButton: TextView
    private lateinit var settingsTabButton: TextView
    private lateinit var homeContentLayout: View
    private lateinit var settingsContentLayout: View
    private lateinit var dnsCard: View
    private lateinit var networkStackCard: View
    private lateinit var splitTunnelingCard: View
    private lateinit var languageCard: View
    private lateinit var themeCard: View
    private lateinit var dnsValueTextView: TextView
    private lateinit var networkStackValueTextView: TextView
    private lateinit var splitTunnelingValueTextView: TextView
    private lateinit var languageValueTextView: TextView
    private lateinit var themeValueTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var statusBadgeTextView: TextView
    private lateinit var detailTextView: TextView
    private lateinit var vlessLinkEditText: EditText
    private lateinit var saveVlessButton: TextView
    private lateinit var chooseVlessProfileButton: TextView
    private lateinit var vlessConnectButton: TextView
    private lateinit var vlessDisconnectButton: TextView
    private lateinit var vlessTitleTextView: TextView
    private lateinit var vlessSummaryTextView: TextView
    private lateinit var vlessDetailTextView: TextView
    private lateinit var vlessStatusTextView: TextView
    private lateinit var serverValueTextView: TextView
    private lateinit var protocolValueTextView: TextView
    private lateinit var cipherValueTextView: TextView
    private lateinit var routingValueTextView: TextView
    private lateinit var sessionValueTextView: TextView
    private lateinit var connectCountValueTextView: TextView
    private lateinit var lastConnectedValueTextView: TextView
    private lateinit var lastUpdateValueTextView: TextView
    private lateinit var importedValueTextView: TextView

    private var currentScreen: String = SCREEN_HOME

    private val splitTunnelLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateSettingsValues()
        }

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                startEmbeddedConnection()
            } else {
                renderState(
                    VlessRuntimeState(
                        status = getString(R.string.status_permission_denied),
                        details = getString(R.string.status_permission_denied_details),
                        tone = StatusTone.ERROR,
                        isRunning = false
                    )
                )
            }
        }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != VlessConnectionIntents.ACTION_STATE_CHANGED) {
                return
            }
            renderState(VlessRuntimeStateStore.currentState)
            updateStatsViews(ProfileStorage.loadVlessConfig(this@MainActivity))
            updateProfileChooserButton()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        currentScreen = savedInstanceState?.getString(STATE_CURRENT_SCREEN) ?: SCREEN_HOME

        homeTabButton = findViewById(R.id.homeTabButton)
        settingsTabButton = findViewById(R.id.settingsTabButton)
        homeContentLayout = findViewById(R.id.homeContentLayout)
        settingsContentLayout = findViewById(R.id.settingsContentLayout)
        dnsCard = findViewById(R.id.dnsCard)
        networkStackCard = findViewById(R.id.networkStackCard)
        splitTunnelingCard = findViewById(R.id.splitTunnelingCard)
        languageCard = findViewById(R.id.languageCard)
        themeCard = findViewById(R.id.themeCard)
        dnsValueTextView = findViewById(R.id.dnsValueTextView)
        networkStackValueTextView = findViewById(R.id.networkStackValueTextView)
        splitTunnelingValueTextView = findViewById(R.id.splitTunnelingValueTextView)
        languageValueTextView = findViewById(R.id.languageValueTextView)
        themeValueTextView = findViewById(R.id.themeValueTextView)
        statusTextView = findViewById(R.id.statusTextView)
        statusBadgeTextView = findViewById(R.id.statusBadgeTextView)
        detailTextView = findViewById(R.id.detailTextView)
        vlessLinkEditText = findViewById(R.id.vlessLinkEditText)
        saveVlessButton = findViewById(R.id.saveVlessButton)
        chooseVlessProfileButton = findViewById(R.id.chooseVlessProfileButton)
        vlessConnectButton = findViewById(R.id.vlessConnectButton)
        vlessDisconnectButton = findViewById(R.id.vlessDisconnectButton)
        vlessTitleTextView = findViewById(R.id.vlessTitleTextView)
        vlessSummaryTextView = findViewById(R.id.vlessSummaryTextView)
        vlessDetailTextView = findViewById(R.id.vlessDetailTextView)
        vlessStatusTextView = findViewById(R.id.vlessStatusTextView)
        serverValueTextView = findViewById(R.id.serverValueTextView)
        protocolValueTextView = findViewById(R.id.protocolValueTextView)
        cipherValueTextView = findViewById(R.id.cipherValueTextView)
        routingValueTextView = findViewById(R.id.routingValueTextView)
        sessionValueTextView = findViewById(R.id.sessionValueTextView)
        connectCountValueTextView = findViewById(R.id.connectCountValueTextView)
        lastConnectedValueTextView = findViewById(R.id.lastConnectedValueTextView)
        lastUpdateValueTextView = findViewById(R.id.lastUpdateValueTextView)
        importedValueTextView = findViewById(R.id.importedValueTextView)

        homeTabButton.setOnClickListener { switchScreen(SCREEN_HOME) }
        settingsTabButton.setOnClickListener { switchScreen(SCREEN_SETTINGS) }
        dnsCard.setOnClickListener { showDnsDialog() }
        networkStackCard.setOnClickListener { showNetworkStackDialog() }
        splitTunnelingCard.setOnClickListener { openSplitTunneling() }
        languageCard.setOnClickListener { showLanguageDialog() }
        themeCard.setOnClickListener { showThemeDialog() }
        saveVlessButton.setOnClickListener { saveVlessConfig() }
        chooseVlessProfileButton.setOnClickListener { showProfileChooser() }
        vlessConnectButton.setOnClickListener { requestVpnPermissionAndConnect() }
        vlessDisconnectButton.setOnClickListener { stopEmbeddedConnection() }

        restoreSavedVlessConfig()
        refreshBackendHint()
        renderState(VlessRuntimeStateStore.currentState)
        updateStatsViews(ProfileStorage.loadVlessConfig(this))
        updateProfileChooserButton()
        updateSettingsValues()
        switchScreen(currentScreen)
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            stateReceiver,
            IntentFilter(VlessConnectionIntents.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        refreshBackendHint()
        renderState(VlessRuntimeStateStore.currentState)
        updateStatsViews(ProfileStorage.loadVlessConfig(this))
        updateProfileChooserButton()
        updateSettingsValues()
    }

    override fun onStop() {
        try {
            unregisterReceiver(stateReceiver)
        } catch (_: IllegalArgumentException) {
        }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_CURRENT_SCREEN, currentScreen)
        super.onSaveInstanceState(outState)
    }

    private fun saveVlessConfig() {
        val rawLink = vlessLinkEditText.text.toString().trim()
        if (rawLink.isBlank()) {
            Toast.makeText(this, R.string.error_vless_required, Toast.LENGTH_LONG).show()
            return
        }

        val parsed = VlessLinkParser.parse(rawLink)
        if (parsed == null) {
            renderVlessSummary(null)
            renderState(
                VlessRuntimeState(
                    status = getString(R.string.status_parse_failed),
                    details = getString(R.string.status_parse_failed_details),
                    tone = StatusTone.ERROR,
                    isRunning = false
                )
            )
            Toast.makeText(this, R.string.error_vless_invalid, Toast.LENGTH_LONG).show()
            return
        }

        val existing = ProfileStorage.findVlessConfig(this, parsed.rawLink)
        val stored = StoredVlessConfig(
            rawLink = parsed.rawLink,
            displayName = parsed.displayName,
            serverAddress = parsed.serverAddress,
            serverPort = parsed.serverPort,
            transport = parsed.transport.uppercase(),
            security = parsed.security.uppercase(),
            hostHeader = parsed.hostHeader.ifBlank { parsed.sni },
            path = parsed.path,
            importedAtMillis = existing?.importedAtMillis?.takeIf { it > 0L } ?: System.currentTimeMillis(),
            connectCount = existing?.connectCount ?: 0,
            lastConnectedAtMillis = existing?.lastConnectedAtMillis ?: 0L,
            lastStatusAtMillis = existing?.lastStatusAtMillis ?: 0L
        )
        ProfileStorage.saveVlessConfig(this, stored)
        applySelectedProfile(stored)
        renderState(
            VlessRuntimeState(
                status = getString(R.string.status_saved),
                details = getString(R.string.status_saved_details),
                tone = StatusTone.SUCCESS,
                isRunning = VlessRuntimeStateStore.currentState.isRunning
            )
        )
    }

    private fun requestVpnPermissionAndConnect() {
        val rawLink = vlessLinkEditText.text.toString().trim()
        if (rawLink.isBlank()) {
            Toast.makeText(this, R.string.error_vless_required, Toast.LENGTH_LONG).show()
            return
        }

        val parsed = VlessLinkParser.parse(rawLink)
        if (parsed == null) {
            Toast.makeText(this, R.string.error_vless_invalid, Toast.LENGTH_LONG).show()
            return
        }

        saveVlessConfig()

        if (!XrayCoreBridge.isAvailable()) {
            refreshBackendHint()
            renderState(
                VlessRuntimeState(
                    status = getString(R.string.status_core_missing),
                    details = getString(R.string.status_core_missing_details),
                    tone = StatusTone.ERROR,
                    isRunning = false
                )
            )
            Toast.makeText(this, R.string.error_core_missing, Toast.LENGTH_LONG).show()
            return
        }

        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent == null) {
            startEmbeddedConnection()
        } else {
            vpnPermissionLauncher.launch(permissionIntent)
        }
    }

    private fun startEmbeddedConnection() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, EmbeddedVlessVpnService::class.java).apply {
                action = VlessConnectionIntents.ACTION_CONNECT
            }
        )
        renderState(
            VlessRuntimeState(
                status = getString(R.string.status_starting),
                details = getString(R.string.status_starting_details),
                tone = StatusTone.PENDING,
                isRunning = false
            )
        )
    }

    private fun stopEmbeddedConnection() {
        startService(
            Intent(this, EmbeddedVlessVpnService::class.java).apply {
                action = VlessConnectionIntents.ACTION_DISCONNECT
            }
        )
        renderState(
            VlessRuntimeState(
                status = getString(R.string.status_stopping),
                details = getString(R.string.status_stopping_details),
                tone = StatusTone.PENDING,
                isRunning = false
            )
        )
    }

    private fun restoreSavedVlessConfig() {
        val saved = ProfileStorage.loadVlessConfig(this)
        if (saved == null) {
            renderVlessSummary(null)
            updateProfileChooserButton()
            return
        }

        if (vlessLinkEditText.text.isNullOrBlank()) {
            vlessLinkEditText.setText(saved.rawLink)
        }

        renderVlessSummary(VlessLinkParser.parse(saved.rawLink))
        refreshBackendHint()
        updateProfileChooserButton()
    }

    private fun applySelectedProfile(profile: StoredVlessConfig) {
        vlessLinkEditText.setText(profile.rawLink)
        renderVlessSummary(VlessLinkParser.parse(profile.rawLink))
        updateStatsViews(profile)
        refreshBackendHint()
        updateProfileChooserButton()
    }

    private fun clearSelectedProfile() {
        vlessLinkEditText.setText("")
        renderVlessSummary(null)
        updateStatsViews(null)
        refreshBackendHint()
        updateProfileChooserButton()
    }

    private fun showProfileChooser() {
        val profiles = ProfileStorage.loadVlessProfiles(this)
        if (profiles.isEmpty()) {
            Toast.makeText(this, R.string.vless_profiles_empty, Toast.LENGTH_LONG).show()
            return
        }

        val activeRaw = ProfileStorage.loadVlessConfig(this)?.rawLink
        val labels = profiles.map { profile ->
            buildProfileChooserLabel(profile, isActive = profile.rawLink == activeRaw)
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.vless_profiles_title)
            .setItems(labels) { _, which ->
                showProfileActions(profiles[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showProfileActions(profile: StoredVlessConfig) {
        val actions = arrayOf(
            getString(R.string.vless_profile_action_use),
            getString(R.string.vless_profile_action_delete)
        )
        AlertDialog.Builder(this)
            .setTitle(profile.displayName.ifBlank { "${profile.serverAddress}:${profile.serverPort}" })
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> activateProfile(profile)
                    1 -> confirmDeleteProfile(profile)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun activateProfile(profile: StoredVlessConfig) {
        ProfileStorage.setActiveVlessProfile(this, profile.rawLink)
        applySelectedProfile(profile)
        val messageId = if (VlessRuntimeStateStore.currentState.isRunning) {
            R.string.vless_profile_selected_reconnect
        } else {
            R.string.vless_profile_selected
        }
        Toast.makeText(
            this,
            getString(messageId, profile.displayName),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun confirmDeleteProfile(profile: StoredVlessConfig) {
        val activeRaw = ProfileStorage.loadVlessConfig(this)?.rawLink
        if (VlessRuntimeStateStore.currentState.isRunning && activeRaw == profile.rawLink) {
            Toast.makeText(this, R.string.vless_profile_delete_disconnect_first, Toast.LENGTH_LONG).show()
            return
        }

        val profileName = profile.displayName.ifBlank { "${profile.serverAddress}:${profile.serverPort}" }
        AlertDialog.Builder(this)
            .setTitle(R.string.vless_profile_delete_title)
            .setMessage(getString(R.string.vless_profile_delete_message, profileName))
            .setPositiveButton(R.string.vless_profile_action_delete) { _, _ ->
                deleteProfile(profile)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteProfile(profile: StoredVlessConfig) {
        val wasActive = ProfileStorage.loadVlessConfig(this)?.rawLink == profile.rawLink
        val deleted = ProfileStorage.deleteVlessProfile(this, profile.rawLink)
        if (!deleted) {
            Toast.makeText(this, R.string.vless_profile_delete_failed, Toast.LENGTH_LONG).show()
            return
        }

        val nextActive = ProfileStorage.loadVlessConfig(this)
        when {
            nextActive != null && wasActive -> applySelectedProfile(nextActive)
            nextActive == null -> clearSelectedProfile()
            else -> updateProfileChooserButton()
        }

        val deletedName = profile.displayName.ifBlank { "${profile.serverAddress}:${profile.serverPort}" }
        Toast.makeText(
            this,
            getString(R.string.vless_profile_deleted, deletedName),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun buildProfileChooserLabel(profile: StoredVlessConfig, isActive: Boolean): String {
        val title = profile.displayName.ifBlank { "${profile.serverAddress}:${profile.serverPort}" }
        val decoratedTitle = if (isActive) {
            getString(R.string.vless_profile_active_template, title)
        } else {
            title
        }
        val details = "${profile.serverAddress}:${profile.serverPort} | ${profile.transport} | ${profile.security}"
        return "$decoratedTitle\n$details"
    }

    private fun updateProfileChooserButton() {
        val count = ProfileStorage.loadVlessProfiles(this).size
        chooseVlessProfileButton.text = if (count > 0) {
            getString(R.string.choose_vless_profile_count, count)
        } else {
            getString(R.string.choose_vless_profile)
        }
    }

    private fun renderVlessSummary(parsed: ParsedVlessConfig?) {
        if (parsed == null) {
            vlessTitleTextView.text = getString(R.string.vless_empty_title)
            vlessSummaryTextView.text = getString(R.string.vless_empty_summary)
            vlessDetailTextView.text = getString(R.string.vless_empty_detail)
            vlessStatusTextView.text = getString(R.string.vless_backend_pending)
            return
        }

        vlessTitleTextView.text = parsed.displayName
        vlessSummaryTextView.text = getString(
            R.string.vless_summary_template,
            parsed.serverAddress,
            parsed.serverPort,
            parsed.transport.uppercase(),
            parsed.security.uppercase()
        )
        vlessDetailTextView.text = getString(
            R.string.vless_detail_template,
            parsed.hostHeader.ifBlank { parsed.sni.ifBlank { "--" } },
            parsed.path.ifBlank { "/" }
        )
    }

    private fun refreshBackendHint() {
        val saved = ProfileStorage.loadVlessConfig(this)
        vlessStatusTextView.text = when {
            saved == null -> getString(R.string.vless_backend_pending)
            XrayCoreBridge.isAvailable() -> getString(R.string.vless_backend_ready)
            else -> getString(R.string.vless_saved_status)
        }
    }

    private fun renderState(state: VlessRuntimeState) {
        statusTextView.text = getString(R.string.status_template, state.status)
        statusBadgeTextView.text = state.status.uppercase()
        detailTextView.text = state.details
        val badgeColor = when (state.tone) {
            StatusTone.SUCCESS -> ContextCompat.getColor(this, R.color.status_connected_bg)
            StatusTone.PENDING -> ContextCompat.getColor(this, R.color.status_pending_bg)
            StatusTone.ERROR -> ContextCompat.getColor(this, R.color.status_error_bg)
            StatusTone.IDLE -> ContextCompat.getColor(this, R.color.status_idle_bg)
        }
        statusBadgeTextView.backgroundTintList = android.content.res.ColorStateList.valueOf(badgeColor)
        vlessConnectButton.isEnabled = !state.isRunning
        vlessDisconnectButton.isEnabled = state.isRunning || state.tone == StatusTone.PENDING
    }

    private fun updateStatsViews(saved: StoredVlessConfig?) {
        serverValueTextView.text = saved?.let { "${it.serverAddress}:${it.serverPort}" } ?: "--"
        protocolValueTextView.text = saved?.transport?.ifBlank { "--" } ?: "--"
        cipherValueTextView.text = saved?.security?.ifBlank { "--" } ?: "--"
        routingValueTextView.text = saved?.hostHeader?.ifBlank { "--" } ?: "--"
        sessionValueTextView.text = saved?.path?.ifBlank { "/" } ?: "--"
        connectCountValueTextView.text = saved?.connectCount?.toString() ?: "0"
        lastConnectedValueTextView.text = formatTimestamp(saved?.lastConnectedAtMillis ?: 0L)
        lastUpdateValueTextView.text = formatTimestamp(saved?.lastStatusAtMillis ?: 0L)
        importedValueTextView.text = formatTimestamp(saved?.importedAtMillis ?: 0L)
    }

    private fun switchScreen(screen: String) {
        currentScreen = screen
        val showSettings = screen == SCREEN_SETTINGS
        homeContentLayout.visibility = if (showSettings) View.GONE else View.VISIBLE
        settingsContentLayout.visibility = if (showSettings) View.VISIBLE else View.GONE
        homeTabButton.isSelected = !showSettings
        settingsTabButton.isSelected = showSettings
    }

    private fun updateSettingsValues() {
        dnsValueTextView.text = getDnsLabel(AppSettings.getDnsOption(this))
        networkStackValueTextView.text = getNetworkStackLabel(AppSettings.getNetworkStack(this))
        val splitTunnelAppsCount = AppSettings.getSplitTunnelPackages(this).size
        val splitTunnelDomainsCount = AppSettings.getSplitTunnelDomains(this).size
        val splitTunnelMode = AppSettings.getSplitTunnelMode(this)
        splitTunnelingValueTextView.text = getString(
            R.string.settings_split_summary_template,
            getSplitTunnelModeLabel(splitTunnelMode),
            splitTunnelAppsCount,
            splitTunnelDomainsCount
        )
        languageValueTextView.text = getLanguageLabel(AppSettings.getLanguage(this))
        themeValueTextView.text = getThemeLabel(AppSettings.getTheme(this))
    }

    private fun updateLanguage(language: AppSettings.LanguageOption) {
        if (AppSettings.getLanguage(this) == language) return
        AppSettings.setLanguage(this, language)
    }

    private fun updateTheme(theme: AppSettings.ThemeOption) {
        if (AppSettings.getTheme(this) == theme) return
        AppSettings.setTheme(this, theme)
    }

    private fun updateDns(option: AppSettings.DnsOption) {
        AppSettings.setDnsOption(this, option)
        updateSettingsValues()
    }

    private fun updateNetworkStack(option: AppSettings.NetworkStackOption) {
        AppSettings.setNetworkStack(this, option)
        updateSettingsValues()
    }

    private fun showDnsDialog() {
        val options = AppSettings.DnsOption.entries.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_dns)
            .setItems(options.map(::getDnsLabel).toTypedArray()) { _, which ->
                updateDns(options[which])
            }
            .show()
    }

    private fun showNetworkStackDialog() {
        val options = AppSettings.NetworkStackOption.entries.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_network_stack)
            .setItems(options.map(::getNetworkStackLabel).toTypedArray()) { _, which ->
                updateNetworkStack(options[which])
            }
            .show()
    }

    private fun showLanguageDialog() {
        val options = AppSettings.LanguageOption.entries.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_language)
            .setItems(options.map(::getLanguageLabel).toTypedArray()) { _, which ->
                updateLanguage(options[which])
            }
            .show()
    }

    private fun showThemeDialog() {
        val options = AppSettings.ThemeOption.entries.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_theme)
            .setItems(options.map(::getThemeLabel).toTypedArray()) { _, which ->
                updateTheme(options[which])
            }
            .show()
    }

    private fun openSplitTunneling() {
        splitTunnelLauncher.launch(Intent(this, SplitTunnelingActivity::class.java))
    }

    private fun getDnsLabel(option: AppSettings.DnsOption): String {
        return when (option) {
            AppSettings.DnsOption.GOOGLE -> getString(R.string.settings_dns_google)
            AppSettings.DnsOption.CLOUDFLARE -> getString(R.string.settings_dns_cloudflare)
            AppSettings.DnsOption.QUAD9 -> getString(R.string.settings_dns_quad9)
            AppSettings.DnsOption.ADGUARD -> getString(R.string.settings_dns_adguard)
        }
    }

    private fun getNetworkStackLabel(option: AppSettings.NetworkStackOption): String {
        return when (option) {
            AppSettings.NetworkStackOption.MIXED -> getString(R.string.settings_stack_mixed)
            AppSettings.NetworkStackOption.IPV4 -> getString(R.string.settings_stack_ipv4)
        }
    }

    private fun getLanguageLabel(option: AppSettings.LanguageOption): String {
        return when (option) {
            AppSettings.LanguageOption.ENGLISH -> getString(R.string.language_english)
            AppSettings.LanguageOption.RUSSIAN -> getString(R.string.language_russian)
        }
    }

    private fun getThemeLabel(option: AppSettings.ThemeOption): String {
        return when (option) {
            AppSettings.ThemeOption.LIGHT -> getString(R.string.theme_light)
            AppSettings.ThemeOption.DARK -> getString(R.string.theme_dark)
        }
    }

    private fun getSplitTunnelModeLabel(mode: AppSettings.SplitTunnelMode): String {
        return when (mode) {
            AppSettings.SplitTunnelMode.BYPASS_SELECTED -> getString(R.string.split_mode_bypass_selected)
            AppSettings.SplitTunnelMode.PROXY_SELECTED -> getString(R.string.split_mode_proxy_selected)
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0L) {
            return getString(R.string.stat_never)
        }
        return android.text.format.DateFormat.format("dd MMM, HH:mm", timestamp).toString()
    }

    companion object {
        private const val SCREEN_HOME = "home"
        private const val SCREEN_SETTINGS = "settings"
        private const val STATE_CURRENT_SCREEN = "current_screen"
    }
}
