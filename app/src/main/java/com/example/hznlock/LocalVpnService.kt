package com.example.hznlock

import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.content.ComponentName
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import java.net.NetworkInterface

class LocalVpnService : VpnService() {

    fun isAccessibilityEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(
            ctx.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabled.contains("${ctx.packageName}/${FocusAccessibilityService::class.java.name}")
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val TAG = "HznLockVPN"

    // --- ADIÇÃO: Variáveis para o loop de bloqueio ---
    private val handler = Handler(Looper.getMainLooper())
    private var lockRunnable: Runnable? = null
    // ------------------------------------------------

    companion object {
        const val ACTION_START = "START_VPN"
        const val ACTION_STOP = "STOP_VPN"

        fun isVpnRunning(): Boolean {
            return try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                interfaces?.asSequence()?.any { it.isUp && it.name == "tun0" } ?: false
            } catch (e: Exception) {
                false
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceNotification()

        when (intent?.action) {
            ACTION_START -> {
                startVpn()
                startLockLoop()
            }
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }


    private fun startVpn() {
        Log.i(TAG, "Iniciando VPN de DNS...")

        try {
            val builder = Builder()
            builder.setSession("HznLock DNS")

            builder.addDnsServer("1.1.1.3")
            builder.addDnsServer("1.0.0.3")
            builder.addDnsServer("2606:4700:4700::1113")
            builder.addDnsServer("2606:4700:4700::1003")

            builder.addAddress("10.0.0.2", 32)

            builder.addDisallowedApplication("com.mercadopago.wallet")
            builder.addDisallowedApplication("com.twitter.android")

            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                Log.i(TAG, "VPN estabelecida apenas para DNS.")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao estabelecer VPN: ${e.message}")
        }
    }

    private fun startLockLoop() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, AdminReceiver::class.java)

        val targetPkgs = arrayOf(
            "com.android.chrome",
            "com.android.vending",
            "cm.aptoide.pt",
            "com.brave.browser",
            "org.telegram.messenger"
        )

        lockRunnable = object : Runnable {
            override fun run() {

                val enabled = isAccessibilityEnabled(this@LocalVpnService)

                try {
                    for (pkg in targetPkgs) {
                        dpm.setApplicationHidden(admin, pkg, !enabled)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro lock: ${e.message}")
                }

                handler.postDelayed(this, 3000) // loop contínuo
            }
        }

        handler.post(lockRunnable!!)
    }

    private fun stopVpn() {
        // --- ADIÇÃO: Cancela o loop se o serviço for parado ---
        lockRunnable?.let { handler.removeCallbacks(it) }

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) { }
        stopForeground(true)
        stopSelf()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startForegroundServiceNotification() {
        val channelId = "vpn_channel"
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(channelId, "VPN Ativa", NotificationManager.IMPORTANCE_MIN)
        )

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("HznLock Ativo")
            .setContentText("Proteção Ativada.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()

        startForeground(1337, notification)
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}