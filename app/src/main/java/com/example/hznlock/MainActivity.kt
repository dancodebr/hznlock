package com.example.hznlock

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.hznlock.ui.theme.HznLockTheme
import kotlinx.coroutines.delay

// --- FUNÇÕES UTILITÁRIAS ---

fun isAccessibilityEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.contains("${context.packageName}/${FocusAccessibilityService::class.java.name}")
}

fun isDeviceAdminEnabled(context: Context): Boolean {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val comp = ComponentName(context, AdminReceiver::class.java)
    return dpm.isAdminActive(comp)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificação de permissão de notificação para Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }

        requestVpnAtStartup()

        setContent {
            HznLockTheme {
                CyberMinimalUI(
                    onRequestOverlay = {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    },
                    onOpenA11y = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    onOpenDeviceAdmin = {
                        val componentName = ComponentName(this@MainActivity, AdminReceiver::class.java)
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Ative para evitar que o app seja encerrado ou desinstalado.")
                        }
                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("HznLock", "Erro ao abrir Admin: ${e.message}")
                        }
                    },
                    onDisableBatteryOpt = {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            startActivity(intent)
                        }
                    },
                    onForceStartVpn = { startVpnService() },
                    onOpenVpnSettings = {
                        try {
                            startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
                        } catch (e: Exception) {
                            Log.e("HznLock", "Erro ao abrir Config VPN: ${e.message}")
                        }
                    }
                )
            }
        }
    }

    private fun requestVpnAtStartup() {
        startVpnService()
    }

    private fun startVpnService() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, 0)
        } else {
            executeServiceAction(LocalVpnService.ACTION_START)
        }
    }

    private fun executeServiceAction(actionString: String) {
        val intent = Intent(this, LocalVpnService::class.java).apply { action = actionString }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            executeServiceAction(LocalVpnService.ACTION_START)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!LocalVpnService.isVpnRunning()) return
        super.onBackPressed()
    }
}

@Composable
fun CyberMinimalUI(
    onRequestOverlay: () -> Unit,
    onOpenA11y: () -> Unit,
    onOpenDeviceAdmin: () -> Unit,
    onDisableBatteryOpt: () -> Unit,
    onForceStartVpn: () -> Unit,
    onOpenVpnSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val mono = FontFamily.Monospace

    val overlayEnabled = remember { mutableStateOf(false) }
    val a11yEnabled = remember { mutableStateOf(false) }
    val adminEnabled = remember { mutableStateOf(false) }
    val batteryOptIgnored = remember { mutableStateOf(false) }
    val vpnActive = remember { mutableStateOf(false) }

    // ESTADO PARA SUMIR O BOTÃO
    val showVpnButton = remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            overlayEnabled.value = Settings.canDrawOverlays(ctx)
            a11yEnabled.value = isAccessibilityEnabled(ctx)
            adminEnabled.value = isDeviceAdminEnabled(ctx)

            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            batteryOptIgnored.value = pm.isIgnoringBatteryOptimizations(ctx.packageName)

            val isRunning = LocalVpnService.isVpnRunning()
            vpnActive.value = isRunning

            if (!isRunning) {
                onForceStartVpn()
            }
            delay(5000)
        }
    }

    @Composable
    fun CyberStatus(text: String, active: Boolean) {
        Text(
            text = text,
            color = if (active) Color.Black else Color(0xFFFF4444),
            fontFamily = mono,
            modifier = Modifier
                .fillMaxWidth()
                .background(if (active) Color(0xFF00E5FF) else Color.Transparent)
                .padding(14.dp)
        )
    }

    @Composable
    fun CyberButton(text: String, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111111), contentColor = Color(0xFF00E5FF))
        ) { Text(text, fontFamily = mono) }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).padding(top = 26.dp)) {
        Text(" LOCKHZN ", color = Color(0xFF00E5FF), fontFamily = mono, modifier = Modifier.padding(start = 18.dp, bottom = 10.dp))

        CyberStatus(if (vpnActive.value) "VPN/DNS: PROTEGIDO" else "VPN/DNS: DESPROTEGIDO", vpnActive.value)
        // LÓGICA PARA O BOTÃO SUMIR
        if (showVpnButton.value) {
            CyberButton("Ativar VPN sempre ativa") {
                showVpnButton.value = false // Faz o botão sumir imediatamente
                onOpenVpnSettings()
            }
        }

        CyberStatus(if (overlayEnabled.value) "Overlay: ATIVADO" else "Overlay: DESATIVADO", overlayEnabled.value)
        if (!overlayEnabled.value) CyberButton("Conceder Overlay", onRequestOverlay)

        CyberStatus(if (adminEnabled.value) "Device Admin: ATIVADO" else "Device Admin: DESATIVADO", adminEnabled.value)
        if (!adminEnabled.value) CyberButton("Ativar Device Admin", onOpenDeviceAdmin)

        CyberStatus(if (batteryOptIgnored.value) "Bateria: SEM RESTRIÇÃO" else "Bateria: OTIMIZADA (BLOQUEADO)", batteryOptIgnored.value)
        if (!batteryOptIgnored.value) CyberButton("Remover Restrição Bateria", onDisableBatteryOpt)

        CyberStatus(if (a11yEnabled.value) "Acessibilidade: ATIVADA" else "Acessibilidade: DESATIVADA", a11yEnabled.value)
        if (!a11yEnabled.value) CyberButton("Ativar Acessibilidade", onOpenA11y)

        Spacer(modifier = Modifier.height(10.dp))


    }
}