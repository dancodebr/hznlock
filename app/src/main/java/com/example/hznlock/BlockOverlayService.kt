package com.example.hznlock

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager


class BlockOverlayService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Certifique-se de que o NotificationHelper esteja configurado corretamente
        startForeground(1, NotificationHelper.buildOverlayNotification(this))
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlayOnly()
        isBlocking = false
        instance = null
    }

    companion object {
        @Volatile var isBlocking = false
        @Volatile private var lastBackSentTime = 0L
        private var currentShieldView: View? = null
        internal var instance: BlockOverlayService? = null
        private var overlayView: View? = null
        private val mainHandler = Handler(Looper.getMainLooper())
        private var wm: WindowManager? = null

        // Configurações de tempo
        private const val BACK_REPETITION_DELAY = 500L // Intervalo mínimo entre comandos Back (anti-spam)
        private const val BACK_2_DELAY = 800L
        private const val BACK_3_DELAY = 1500L
        private const val RELEASE_DELAY = 2200L

        private fun getOverlayParams(): WindowManager.LayoutParams {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or // Força renderização rápida
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,

                PixelFormat.TRANSLUCENT
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            return params
        }

        fun showOverlay(context: Context, pkg: String) {
            val now = System.currentTimeMillis()
            val a11y = FocusAccessibilityService.instance

            // 1. SE JÁ ESTÁ BLOQUEANDO: Apenas renovamos a agressividade
            if (isBlocking && overlayView != null) {
                // Se o sensor detectar a aba proibida de novo, mandamos outro Back (respeitando o limite de 300ms)
                if (now - lastBackSentTime > BACK_REPETITION_DELAY) {
                    lastBackSentTime = now
                    a11y?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                }
                return
            }

            // 2. INÍCIO DO BLOQUEIO
            isBlocking = true
            lastBackSentTime = now
            val ctx = instance ?: context
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            mainHandler.post {
                try {
                    // Limpa qualquer overlay residual antes de inflar o novo
                    if (overlayView == null) {
                        val view = LayoutInflater.from(ctx).inflate(R.layout.overlay_block, null)
                        overlayView = view
                        wm.addView(view, getOverlayParams())
                    }

                    // Reinicia os timers: se o usuário "voltar" pra aba proibida,
                    // o ciclo de 2.2s recomeça do zero.
                    mainHandler.removeCallbacksAndMessages(null)

                    // Sequência de expulsão (3 Backs)
                    a11y?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

                    mainHandler.postDelayed({
                        a11y?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    }, BACK_2_DELAY)

                    mainHandler.postDelayed({
                        a11y?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    }, BACK_3_DELAY)

                    // Liberação final após o tempo de segurança
                    mainHandler.postDelayed({
                        removeOverlayOnly()
                        isBlocking = false
                    }, RELEASE_DELAY)

                } catch (e: Exception) {
                    Log.e("HZN", "Falha ao aplicar overlay: ${e.message}")
                    isBlocking = false
                }
            }
        }

        private fun removeOverlayOnly() {
            mainHandler.post {
                try {
                    val ctx = instance ?: overlayView?.context ?: return@post
                    val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    overlayView?.let {
                        if (it.isAttachedToWindow) {
                            wm.removeViewImmediate(it)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("HZN", "Erro ao remover view: ${e.message}")
                } finally {
                    overlayView = null
                }
            }
        }
    }
}