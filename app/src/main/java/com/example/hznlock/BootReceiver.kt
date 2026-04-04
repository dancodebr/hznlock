package com.example.hznlock

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import android.os.Looper

class BootReceiver : BroadcastReceiver() {
    @SuppressLint("ServiceCast")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Tenta iniciar sua VPN e o serviço de bloqueio
            val vpnIntent = Intent(context, LocalVpnService::class.java).apply {
                action = LocalVpnService.ACTION_START
            }

            context.startForegroundService(vpnIntent)

            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

            val handler = android.os.Handler(Looper.getMainLooper())
            val end = System.currentTimeMillis() + 60_000

            val task = object : Runnable {
                override fun run() {
                    if (System.currentTimeMillis() < end) {
                        dpm.lockNow() // fecha a tela
                        handler.postDelayed(this, 1000) // 1.5s (ajusta 1–2s)
                    }
                }
            }

            handler.post(task)

        }

    }
}