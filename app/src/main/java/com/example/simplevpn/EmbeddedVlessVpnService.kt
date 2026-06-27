package com.example.simplevpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat

class EmbeddedVlessVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var suppressDestroyDisconnect = false

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            VlessConnectionIntents.ACTION_DISCONNECT -> {
                disconnectInternal(
                    status = getString(R.string.status_disconnected),
                    details = getString(R.string.status_disconnected_details),
                    tone = StatusTone.IDLE
                )
                return START_NOT_STICKY
            }

            else -> {
                startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.status_starting)))
                connectInternal()
                return START_STICKY
            }
        }
    }

    override fun onRevoke() {
        disconnectInternal(
            status = getString(R.string.status_permission_revoked),
            details = getString(R.string.status_permission_revoked_details),
            tone = StatusTone.ERROR
        )
    }

    override fun onDestroy() {
        if (!suppressDestroyDisconnect) {
            disconnectInternal(
                status = getString(R.string.status_disconnected),
                details = getString(R.string.status_disconnected_details),
                tone = StatusTone.IDLE,
                stopSelfAfter = false
            )
        }
        super.onDestroy()
    }

    private fun connectInternal() {
        updateRuntimeState(
            status = getString(R.string.status_starting),
            details = getString(R.string.status_starting_details),
            tone = StatusTone.PENDING,
            isRunning = false
        )

        if (!XrayCoreBridge.isAvailable()) {
            failAndStop(
                status = getString(R.string.status_core_missing),
                details = getString(R.string.status_core_missing_details)
            )
            return
        }

        val stored = ProfileStorage.loadVlessConfig(this)
        if (stored == null) {
            failAndStop(
                status = getString(R.string.status_profile_missing),
                details = getString(R.string.status_profile_missing_details)
            )
            return
        }

        val parsed = VlessLinkParser.parse(stored.rawLink)
        if (parsed == null) {
            failAndStop(
                status = getString(R.string.status_parse_failed),
                details = getString(R.string.status_parse_failed_details)
            )
            return
        }

        val preparedIntent = prepare(this)
        if (preparedIntent != null) {
            failAndStop(
                status = getString(R.string.status_permission_denied),
                details = getString(R.string.status_permission_denied_details)
            )
            return
        }

        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }

        val dnsServers = AppSettings.getDnsOption(this).servers
        val splitTunnelMode = AppSettings.getSplitTunnelMode(this)
        val splitTunnelPackages = AppSettings.getSplitTunnelPackages(this)
        val splitTunnelDomains = AppSettings.getSplitTunnelDomains(this)
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(1500)
            .addAddress("10.10.0.2", 32)
            .addRoute("0.0.0.0", 0)

        dnsServers.forEach { builder.addDnsServer(it) }

        when (splitTunnelMode) {
            AppSettings.SplitTunnelMode.BYPASS_SELECTED -> {
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (_: PackageManager.NameNotFoundException) {
                }

                splitTunnelPackages.forEach { excludedPackage ->
                    try {
                        builder.addDisallowedApplication(excludedPackage)
                    } catch (_: PackageManager.NameNotFoundException) {
                    } catch (_: IllegalArgumentException) {
                    }
                }
            }

            AppSettings.SplitTunnelMode.PROXY_SELECTED -> {
                splitTunnelPackages
                    .filterNot { it == packageName }
                    .forEach { allowedPackage ->
                        try {
                            builder.addAllowedApplication(allowedPackage)
                        } catch (_: PackageManager.NameNotFoundException) {
                        } catch (_: IllegalArgumentException) {
                        }
                    }
            }
        }

        vpnInterface = builder.establish()

        val tunFd = vpnInterface?.fd
        if (tunFd == null || tunFd <= 0) {
            failAndStop(
                status = getString(R.string.status_tun_failed),
                details = getString(R.string.status_tun_failed_details)
            )
            return
        }

        val configJson = VlessXrayConfigBuilder.build(
            profile = parsed,
            dnsServers = dnsServers,
            splitTunnelMode = splitTunnelMode,
            splitTunnelDomains = splitTunnelDomains
        )
        val result = XrayCoreBridge.start(this, configJson, tunFd)
        if (result.isFailure) {
            failAndStop(
                status = getString(R.string.status_core_failed),
                details = result.exceptionOrNull()?.message ?: getString(R.string.status_core_failed_details)
            )
            return
        }

        ProfileStorage.incrementVlessConnectCount(this)
        ProfileStorage.saveVlessLastStatusAt(this, System.currentTimeMillis())
        updateRuntimeState(
            status = getString(R.string.status_connected),
            details = getString(
                R.string.status_connected_details,
                parsed.displayName,
                XrayCoreBridge.getVersionOrNull() ?: getString(R.string.version_unknown)
            ),
            tone = StatusTone.SUCCESS,
            isRunning = true
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(getString(R.string.status_connected)))
    }

    private fun disconnectInternal(
        status: String,
        details: String,
        tone: StatusTone,
        stopSelfAfter: Boolean = true
    ) {
        if (stopSelfAfter) {
            suppressDestroyDisconnect = true
        }
        XrayCoreBridge.stop()
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
        ProfileStorage.saveVlessLastStatusAt(this, System.currentTimeMillis())
        updateRuntimeState(status, details, tone, false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (stopSelfAfter) {
            stopSelf()
        }
    }

    private fun failAndStop(status: String, details: String) {
        disconnectInternal(status, details, StatusTone.ERROR)
    }

    private fun updateRuntimeState(
        status: String,
        details: String,
        tone: StatusTone,
        isRunning: Boolean
    ) {
        VlessRuntimeStateStore.currentState = VlessRuntimeState(status, details, tone, isRunning)
        sendBroadcast(Intent(VlessConnectionIntents.ACTION_STATE_CHANGED).setPackage(packageName))
    }

    private fun buildNotification(contentText: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_vpn)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) {
            return
        }

        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "embedded_vless_channel"
        private const val NOTIFICATION_ID = 101
    }
}
