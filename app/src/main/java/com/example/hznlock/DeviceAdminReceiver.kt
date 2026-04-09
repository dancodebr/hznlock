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

        // --- MANTENDO SUAS RESTRIÇÕES ORIGINAIS ---

        // 🔒 Bloqueia criação de novos usuários
        dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)

        // 🔒 Opcional: bloqueia safe boot
        dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)

        // --- ADIÇÃO PARA LOCK TASK MODE (KIOSK) ---

        try {
            // 1. Autoriza o próprio app a entrar em modo Lock Task
            // Isso permite que o app chame startLockTask() sem perguntar ao usuário
            dpm.setLockTaskPackages(admin, arrayOf(context.packageName))

            // 2. Define as features do sistema que ficam BLOQUEADAS no modo Kiosk
            // Isso garante que barra de status, notificações e menu de energia sumam
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(admin,
                    DevicePolicyManager.LOCK_TASK_FEATURE_NONE // Bloqueia TUDO (Barra, Home, Recentes)
                )
            }
        } catch (e: Exception) {
            // Silencioso ou log para debug
        }

        Toast.makeText(context, "Admin e restrições de Kiosk aplicadas!", Toast.LENGTH_SHORT).show()
    }
}