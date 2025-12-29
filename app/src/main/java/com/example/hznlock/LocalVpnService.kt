package com.example.hznlock

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.RequiresApi
import java.net.NetworkInterface

class LocalVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val TAG = "HznLockVPN"

    // Função utilitária estática para checar se a interface está UP
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
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        Log.i(TAG, "Iniciando VPN de DNS...")

        try {
            val builder = Builder()
            builder.setSession("HznLock DNS")

            // 1. IPs da Cloudflare Family (Filtro de conteúdo adulto/malware)
            builder.addDnsServer("1.1.1.3")
            builder.addDnsServer("1.0.0.3")
            builder.addDnsServer("2606:4700:4700::1113")
            builder.addDnsServer("2606:4700:4700::1003")

            // 2. IP privado para a interface
            builder.addAddress("10.0.0.2", 32)

            // --- O SEGREDO ESTÁ AQUI ---
            // Remova o addRoute("0.0.0.0", 0) que bloqueia a internet.
            // Em vez disso, vamos permitir que todos os apps ignorem a VPN para DADOS,
            // mas o Android ainda forçará o DNS que definimos acima.

            // Se você quer apenas filtrar DNS, não adicione rotas de IP.
            // O Android usará os DNS Servers definidos acima automaticamente.

            // Opcional: Impedir que a VPN capture o tráfego de apps de banco
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
    private fun stopVpn() {
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