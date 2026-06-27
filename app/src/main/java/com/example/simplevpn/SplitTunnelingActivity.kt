package com.example.simplevpn

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SplitTunnelingActivity : AppCompatActivity() {
    private lateinit var backTextView: TextView
    private lateinit var summaryTextView: TextView
    private lateinit var modeCard: android.view.View
    private lateinit var modeValueTextView: TextView
    private lateinit var modeHintTextView: TextView
    private lateinit var appsTabButton: Button
    private lateinit var domainsTabButton: Button
    private lateinit var appsTabContentLayout: View
    private lateinit var domainsTabContentLayout: View
    private lateinit var searchEditText: EditText
    private lateinit var domainsEditText: EditText
    private lateinit var appsListView: ListView
    private lateinit var emptyStateTextView: TextView

    private val selectedPackages = linkedSetOf<String>()
    private var currentMode = AppSettings.SplitTunnelMode.BYPASS_SELECTED
    private var currentTab = TAB_APPS
    private var allApps: List<SplitTunnelApp> = emptyList()
    private var filteredApps: List<SplitTunnelApp> = emptyList()
    private lateinit var adapter: SplitTunnelAppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_split_tunneling)

        backTextView = findViewById(R.id.backTextView)
        summaryTextView = findViewById(R.id.splitTunnelSummaryTextView)
        modeCard = findViewById(R.id.modeCard)
        modeValueTextView = findViewById(R.id.modeValueTextView)
        modeHintTextView = findViewById(R.id.modeHintTextView)
        appsTabButton = findViewById(R.id.appsTabButton)
        domainsTabButton = findViewById(R.id.domainsTabButton)
        appsTabContentLayout = findViewById(R.id.appsTabContentLayout)
        domainsTabContentLayout = findViewById(R.id.domainsTabContentLayout)
        searchEditText = findViewById(R.id.searchEditText)
        domainsEditText = findViewById(R.id.domainsEditText)
        appsListView = findViewById(R.id.appsListView)
        emptyStateTextView = findViewById(R.id.emptyStateTextView)

        currentMode = AppSettings.getSplitTunnelMode(this)
        selectedPackages += AppSettings.getSplitTunnelPackages(this)
        domainsEditText.setText(AppSettings.getSplitTunnelDomains(this).joinToString("\n"))
        allApps = loadLaunchableApps()
        filteredApps = allApps
        adapter = SplitTunnelAppsAdapter(this, filteredApps, selectedPackages)
        appsListView.adapter = adapter

        backTextView.setOnClickListener { finish() }
        modeCard.setOnClickListener { showModeDialog() }
        appsTabButton.setOnClickListener { switchTab(TAB_APPS) }
        domainsTabButton.setOnClickListener { switchTab(TAB_DOMAINS) }
        appsListView.setOnItemClickListener { _, _, position, _ ->
            val app = filteredApps[position]
            togglePackage(app.packageName)
        }
        searchEditText.addTextChangedListener(SimpleAfterTextChangedWatcher { query ->
            filterApps(query)
        })
        domainsEditText.addTextChangedListener(SimpleAfterTextChangedWatcher {
            renderSelectionSummary()
        })

        renderMode()
        renderSelectionSummary()
        switchTab(TAB_APPS)
        updateEmptyState()
    }

    override fun finish() {
        AppSettings.setSplitTunnelMode(this, currentMode)
        AppSettings.setSplitTunnelPackages(this, selectedPackages)
        AppSettings.setSplitTunnelDomains(this, parseDomains(domainsEditText.text?.toString().orEmpty()))
        setResult(RESULT_OK)
        super.finish()
    }

    private fun togglePackage(packageName: String) {
        if (selectedPackages.contains(packageName)) {
            selectedPackages.remove(packageName)
        } else {
            selectedPackages.add(packageName)
        }
        adapter.notifyDataSetChanged()
        renderSelectionSummary()
    }

    private fun renderSelectionSummary() {
        val appsCount = selectedPackages.size
        val domainsCount = parseDomains(domainsEditText.text?.toString().orEmpty()).size
        summaryTextView.text = getString(
            R.string.settings_split_summary_template,
            getModeLabel(currentMode),
            appsCount,
            domainsCount
        )
    }

    private fun filterApps(query: CharSequence?) {
        val normalized = query?.toString()?.trim().orEmpty().lowercase()
        filteredApps = if (normalized.isBlank()) {
            allApps
        } else {
            allApps.filter {
                it.label.lowercase().contains(normalized) || it.packageName.lowercase().contains(normalized)
            }
        }
        adapter = SplitTunnelAppsAdapter(this, filteredApps, selectedPackages)
        appsListView.adapter = adapter
        updateEmptyState()
    }

    private fun updateEmptyState() {
        emptyStateTextView.visibility = if (filteredApps.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        appsListView.visibility = if (filteredApps.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun switchTab(tab: String) {
        currentTab = tab
        val showApps = tab == TAB_APPS
        appsTabButton.isSelected = showApps
        domainsTabButton.isSelected = !showApps
        appsTabContentLayout.visibility = if (showApps) View.VISIBLE else View.GONE
        domainsTabContentLayout.visibility = if (showApps) View.GONE else View.VISIBLE
    }

    private fun showModeDialog() {
        val options = AppSettings.SplitTunnelMode.entries.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.split_tunnel_mode_title)
            .setItems(options.map(::getModeLabel).toTypedArray()) { _, which ->
                currentMode = options[which]
                renderMode()
                renderSelectionSummary()
            }
            .show()
    }

    private fun renderMode() {
        modeValueTextView.text = getModeLabel(currentMode)
        modeHintTextView.text = getModeSummary(currentMode)
    }

    private fun getModeLabel(mode: AppSettings.SplitTunnelMode): String {
        return when (mode) {
            AppSettings.SplitTunnelMode.BYPASS_SELECTED -> getString(R.string.split_mode_bypass_selected)
            AppSettings.SplitTunnelMode.PROXY_SELECTED -> getString(R.string.split_mode_proxy_selected)
        }
    }

    private fun getModeSummary(mode: AppSettings.SplitTunnelMode): String {
        return when (mode) {
            AppSettings.SplitTunnelMode.BYPASS_SELECTED -> getString(R.string.split_mode_bypass_selected_summary)
            AppSettings.SplitTunnelMode.PROXY_SELECTED -> getString(R.string.split_mode_proxy_selected_summary)
        }
    }

    private fun parseDomains(rawText: String): Set<String> {
        return rawText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun loadLaunchableApps(): List<SplitTunnelApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)

        return resolved
            .mapNotNull { info ->
                val activityInfo = info.activityInfo ?: return@mapNotNull null
                if (activityInfo.packageName == packageName) {
                    return@mapNotNull null
                }
                SplitTunnelApp(
                    packageName = activityInfo.packageName,
                    label = info.loadLabel(packageManager)?.toString().orEmpty().ifBlank { activityInfo.packageName },
                    icon = runCatching { info.loadIcon(packageManager) }.getOrNull()
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    companion object {
        private const val TAB_APPS = "apps"
        private const val TAB_DOMAINS = "domains"
    }
}
