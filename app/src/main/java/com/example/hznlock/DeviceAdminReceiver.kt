package com.example.hznlock

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.UserManager


class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, AdminReceiver::class.java)

        // 🔒 Bloqueia criação de novos usuários
        dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)

        // 🔒 Opcional: bloqueia safe boot
        dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)

        Toast.makeText(context, "Admin e restrições aplicadas!", Toast.LENGTH_SHORT).show()
    }
}