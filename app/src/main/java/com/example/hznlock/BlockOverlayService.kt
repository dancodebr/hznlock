package com.example.hznlock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.graphics.Path
import android.os.Build
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.annotation.RequiresApi


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
        internal var instance: BlockOverlayService? = null
        private var overlayView: View? = null
        private val mainHandler = Handler(Looper.getMainLooper())
        private var wm: WindowManager? = null

        // overlay padrao
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

        @RequiresApi(Build.VERSION_CODES.P)
        private fun getOverlayParamsClear(): WindowManager.LayoutParams {
            return WindowManager.LayoutParams(
                MATCH_PARENT,
                MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                // Adicionada a FLAG_NOT_TOUCHABLE de volta, mas com uma lógica de timing.
                // Se o overlay for touchable, o gesto do A11y bate nele e morre.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        @RequiresApi(Build.VERSION_CODES.P)
        fun showOverlayClear(context: Context) {
            if (isBlocking) return
            isBlocking = true

            val a11y = FocusAccessibilityService.instance
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            // 1. AÇÃO IMEDIATA: Back e Overlay sem nenhum delay entre eles
            a11y?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

            try {
                if (overlayView == null) {
                    overlayView = LayoutInflater.from(context).inflate(R.layout.overlay_block, null)
                    wm.addView(overlayView, getOverlayParamsClear())
                }
            } catch (e: Exception) {
                isBlocking = false
                return
            }

            // 2. RECENTES: Disparamos apenas 10ms depois, quase junto com o back
            // Isso faz o Android "pular" a animação de retorno e ir direto para o carrossel
            mainHandler.postDelayed({
                a11y?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)

                // 3. SWIPE: Reduzi para 300ms.
                // É o tempo mínimo para o sistema aceitar o toque após abrir os recentes.
                mainHandler.postDelayed({
                    val swipeGesture = buildSwipeUpGesture()

                    a11y?.dispatchGesture(swipeGesture, object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            removeOverlayOnly()
                            isBlocking = false
                        }
                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            cleanupState()
                        }
                    }, null)
                }, 300)

            }, 10) // 10ms é imperceptível, serve apenas para não travar o Binder
        }

        // Função auxiliar para evitar repetição de código
        private fun cleanupState() {
            removeOverlayOnly()
            isBlocking = false
        }

        private fun buildSwipeUpGesture(): GestureDescription {
            val dm = Resources.getSystem().displayMetrics
            val x = dm.widthPixels / 2f
            val path = Path().apply {
                moveTo(x, dm.heightPixels * 0.6f) // Começa um pouco mais alto
                lineTo(x, dm.heightPixels * 0.2f) // Termina rápido
            }
            // 200ms de duração faz o card "voar" para fora da tela
            return GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 200))
                .build()
        }

        fun showOverlay(context: Context, pkg: String) {
            if (isBlocking) return // trava dura, imediata

            isBlocking = true // 🔒 TRAVA AQUI, NÃO NO HANDLER

            val now = System.currentTimeMillis()
            lastBackSentTime = now
            val a11y = FocusAccessibilityService.instance
            val ctx = instance ?: context
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            mainHandler.postAtFrontOfQueue { // ⚡ PRIORIDADE
                try {
                    if (overlayView == null) {
                        overlayView = LayoutInflater.from(ctx)
                            .inflate(R.layout.overlay_block, null)
                        wm.addView(overlayView, getOverlayParams())

                    }

                    a11y?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)


                    mainHandler.postDelayed({
                        a11y?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

                    }, BACK_2_DELAY)

                    mainHandler.postDelayed({
                        a11y?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

                    }, BACK_3_DELAY)

                    mainHandler.postDelayed({
                        removeOverlayOnly()
                        isBlocking = false // 🔓 só aqui libera

                    }, RELEASE_DELAY)

                } catch (e: Exception) {
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