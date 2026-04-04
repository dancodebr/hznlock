package com.example.hznlock

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
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
            val admin = ComponentName(context, AdminReceiver::class.java)

            dpm.addUserRestriction(admin, "no_config_settings")

            android.os.Handler(Looper.getMainLooper()).postDelayed({

                dpm.clearUserRestriction(admin, "no_config_settings")

            }, 60_000)

        }

    }
}