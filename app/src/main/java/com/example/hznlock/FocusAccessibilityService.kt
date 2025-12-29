package com.example.hznlock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Context
import android.content.res.Configuration
import android.os.Looper
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import android.os.Handler
import android.view.Display
import android.view.Gravity
import android.view.View
import androidx.annotation.RequiresApi

@SuppressLint("AccessibilityPolicy")
class FocusAccessibilityService : AccessibilityService() {

    companion object {
        var instance: FocusAccessibilityService? = null

        // Mover as listas para constantes evita que sejam recriadas a cada evento (Otimização de Memória)
        private val BLOCKED_PACKAGES = hashSetOf(
            "com.android.chrome", "org.mozilla.firefox", "com.opera.browser", "com.opera.mini.native",
            "com.opera.gx", "com.opera.touch", "com.microsoft.emmx", "com.sec.android.app.sbrowser",
            "com.mi.globalbrowser", "com.duckduckgo.mobile.android", "com.vivaldi.browser",
            "org.chromium.chrome", "com.google.android.apps.chrome.beta", "com.google.android.apps.chrome.dev",
            "com.ecosia.android", "com.yandex.browser", "com.kiwibrowser.browser", "com.bromite.bromite",
            "mark.via.gp", "com.aloha.browser", "com.whale.browser", "com.stoutner.privacybrowser.standard",
            "org.torproject.torbrowser", "org.torproject.onionbrowser", "com.puffinapp.puffin",
            "com.cloudmosa.puffin", "com.cloudmosa.puffinFree", "com.uc.browser.en", "com.uc.browser.hd",
            "com.transsion.phoenix", "com.ksmobile.cb", "com.fevdev.ansel.browser",
            "com.cloudflare.onedotonedotonedotone", "com.wireguard.android", "com.tunnelbear.android",
            "com.protonvpn.android", "com.nordvpn.android", "com.surfshark.vpnclient.android",
            "com.expressvpn.vpn", "com.windscribe.vpn", "com.vyprvpn.android", "com.mullvad.vpn",
            "com.cyberghost.vpn", "com.privateinternetaccess.android", "com.anchorfree.hydravpn",
            "com.bitdefender.vpn", "com.ivacy.vpn", "com.hola.android", "com.psiphon3",
            "free.vpn.unblock.proxy.turbovpn", "com.fast.free.unblock.secure.vpn", "com.vpnhub.app",
            "com.strongvpn", "com.adguard.android", "com.google.android.apps.subscriptions.red",
            "com.topjohnwu.magisk", "org.lsposed.manager", "org.meowcat.edxposed.manager",
            "com.saurik.substrate", "bin.mt.plus", "bin.mt.plus.canary", "com.chelpus.lackypatch",
            "np.manager.v3", "com.huluwa.hjmanager", "com.sollyu.xposed.hook.model",
            "com.formyhalos.force_stop", "com.catchingnow.icebox", "com.lbe.parallel.intl",
            "com.lbe.parallel.intl.arm64", "com.applisto.appcloner", "com.excelliance.multiaccount",
            "com.dualspace.multispace.android", "com.multiple.parallel", "com.cloneapp.parallelspace.dualspace",
            "com.tridat.cloneapp", "com.multi.clone.parallel.space", "com.doubleopen",
            "com.miniapp.multi.parallel", "com.ludashi.dualspace", "com.qihoo.magic", "com.black.box",
            "com.vmos.glb", "com.vmos.pro", "com.vmos.google", "com.f1mobile.f1vm", "com.x8zs.sandbox",
            "com.vphonegaga.launcher", "com.virtual.android", "com.redfinger.global", "com.ugphone.app",
            "com.vxp.launcher", "com.twosixtech.android.v71", "com.yehuo.vbox", "app.greyshirts.sslcapture",
            "com.minhui.networkcapture", "com.evbadrit.networkmonitortrial", "com.egorovandreyev.sniffwiz",
            "org.proxydroid", "com.guoshi.httpcanary", "com.termux", "com.termux.api", "com.termux.widget",
            "com.termux.window", "com.termux.x11", "com.offsec.nethunter", "ru.meefik.linuxdeploy",
            "com.zpwebsites.linuxonandroid", "org.debian.alioth.debian_installer", "org.wahtod.debian",
            "tech.ula", "jackpal.androidterm", "com.twaun95.terminal", "com.draco.ladb",
            "com.cgutman.androidmanadblog", "com.teslacoilsw.launcher", "com.teslacoilsw.launcher.prime",
            "com.microsoft.launcher", "com.smartlauncher.arm64", "com.niagara.launcher",
            "com.gau.go.launcherex", "com.android.launcher3", "com.chrislacy.actionlauncher",
            "com.nein.launcher", "app.lawnchair.launcher.v2", "com.hyperion.launcher", "com.aplus.launcher",
            "com.hld.launcher", "com.hld.launcher.hideapp", "com.blankicon.launcher",
            "com.security.privacy.launcher", "com.realvnc.viewer.android", "com.eltechs.ed",
            "com.eltechs.es", "com.limbo.emu.main", "com.limbo.emu.x86", "org.bochs.android",
            "com.copy.pcedos", "org.telegram.messenger", "org.telegram.plus", "org.vidogram.messenger",
            "tw.nekomimi.nekogram", "tw.nekomimi.nekogram.beta", "org.telegram.igram", "uz.usoft.blackgram",
            "org.telegram.multi", "org.telegram.tpro1", "com.cherisher.teleplus", "com.yengshine.tele",
            "com.truedevelopersstudio.automatictap.autoclicker", "com.autoclicker.clicker",
            "com.eth Marc.android.autoclicker", "Marc.android.autoclicker", "com.guoshi.autoclicker",
            "com.phonev.autoclicker", "com.opautoclicker.autoclicker", "com.automouse.autoclicker",
            "simplehat.clicker", "com.haogocn.autoclicker", "com.autoclicker.automatic.tap.clicker",
            "com.click.autoclicker", "com.speed.gc.autoclicker", "com.autoclicker.ms", "com.tujia.autoclicker",
            "com.frapas.autoclicker", "com.muna.autoclicker", "com.nest.autoclicker", "com.loitp.autoclicker",
            "com.infinite.autoclicker", "com.clicker.automatic", "com.autoclicker.tap.automatic",
            "com.automatic.clicker.tap", "com.sh.autoclicker", "com.tt.autoclicker", "com.mobi.autoclicker",
            "com.easy.autoclicker", "com.superclick.autoclicker", "com.smart.autoclicker",
            "com.fast.clicker", "com.clicker.tapper", "com.clic.auto", "com.taps.auto", "com.rhmsoft.edit",
            "com.alphainventor.filemanager", "com.lonelycatgames.Xplore", "com.amaze.filemanager",
            "com.morgan.design.android.file.manager", "com.google.android.apps.nbu.files",
            "com.sec.android.app.myfiles", "com.mi.android.globalFileexplorer", "com.android.documentsui",
            "com.cleanmaster.mguard", "com.kms.free", "com.avast.android.cleaner", "com.piriform.ccleaner",
            "com.glarysoft.anyclean", "com.frozendevs.cache.cleaner", "com.oasisfeng.greenify",
            "com.gsamlabs.bbm", "com.digibites.infonow", "com.uzumapps.wakelockdetector",
            "com.keramidas.TitaniumBackup", "com.riteshsahu.ContextualAppInfo", "com.absyz.uninstaller",
            "com.vs.uninstaller", "com.jumobile.manager.systemapp", "com.hecorat.uninstaller",
            "com.ram.cleaner.antivirus.booster", "com.speed.booster.cleaner", "com.toolbox.cleaner",
            "com.puf.uninstaller", "com.BoshBash.SystemAppRemover", "com.ccp.easyuninstaller",
            "com.mobile_pioneer.cleaner", "com.smartprojects.HideBottomBar", "com.dr.app_manager",
            "com.jv.app.manager", "org.servalproject", "com.shelter.main", "com.island.main",
            "com.hry.app_manager", "com.lb.app_manager", "com.github.heroinand.app_manager",
            "io.github.muntashirakon.AppManager", "com.apk.installer.manager", "com.ext.file.manager",
            "com.android.vending.app.manager"
        )

        private val BYPASS_PLAY_STORE = hashSetOf("vpn","dns","proxy","túnel","virtual","private","privado","anonymous","anonimo","clone","parallel","multiple", "browser","navegador","web","explorer")
        private val BYPASS_KEYWORDS = hashSetOf("clone app", "clonador","android virtual", "multi account","parallel space", "dual space", "apk editor", "mt manager","tunnel","vmos", "vpn bypass", "tirar dns","t.me","nextdns","intra","warp", "cloudflare","como burlar", "proxydroid", "lucky patcher", "hack app", "privacy", "onlyfans","vazadinho","abrir configurações de vpn","private dns bypass", "dns changer", "falha na desinstalação de hznlock.")
        private val BYPASS_BRAVE = hashSetOf("navegador", "browser","explorer", "clone","virtual", "dns", "cloudflare", "vpn", "dual","internet app", "internet apk","anonymous","anonimo")
    }

    private lateinit var wm: WindowManager
    private lateinit var dm: DisplayManager

    // === TOUCH SHIELD DUPLO (SEM DELAY / SEM RECRIAR) ===
    private var portraitShield: View? = null
    private var landscapeShield: View? = null

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("ServiceCast")
    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }

        // cria OS DOIS de uma vez
        createPortraitShield()
        createLandscapeShield()

        // aplica visibilidade correta
        syncShieldWithRotation()

        dm.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            syncShieldWithRotation() // só troca visibilidade
        }
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        syncShieldWithRotation()
    }

    private fun syncShieldWithRotation() {
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val rotation = display?.rotation ?: Surface.ROTATION_0

        val isLandscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270

        portraitShield?.visibility = if (isLandscape) View.INVISIBLE else View.VISIBLE
        landscapeShield?.visibility = if (isLandscape) View.VISIBLE else View.INVISIBLE
    }

    // ---------- CRIAÇÃO FIXA (UMA VEZ) ----------

    private fun createPortraitShield() {
        if (portraitShield != null) return

        portraitShield = View(this).apply {
            setBackgroundColor(0x01000000) // debug
        }

        val params = WindowManager.LayoutParams(
            89,
            89,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 930
            y = 1200
        }

        wm.addView(portraitShield, params)
    }

    private fun createLandscapeShield() {
        if (landscapeShield != null) return

        landscapeShield = View(this).apply {
            setBackgroundColor(0x01000000) // debug diferente
            visibility = View.INVISIBLE // começa escondido
        }

        val params = WindowManager.LayoutParams(
            130,
            130,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 2250
            y = 750
        }

        wm.addView(landscapeShield, params)
    }


    private fun isDangerVisible(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val txt = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        if ((txt.contains("segurança") || txt.contains("privacidade") ||
                    desc.contains("segurança") || desc.contains("privacidade")) && node.isVisibleToUser) {
            return true
        }

        for (i in 0 until node.childCount) {
            if (isDangerVisible(node.getChild(i))) return true
        }
        return false
    }

    fun containsVisibleText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false
        val t = node.text?.toString()?.lowercase() ?: ""
        val d = node.contentDescription?.toString()?.lowercase() ?: ""

        if ((t.contains(text) || d.contains(text)) && node.isVisibleToUser) {
            val r = Rect()
            node.getBoundsInScreen(r)
            if (r.width() > 0 && r.height() > 0) return true
        }

        for (i in 0 until node.childCount) {
            if (containsVisibleText(node.getChild(i), text)) return true
        }
        return false
    }

    // Versão otimizada com limite de profundidade
    private fun containsText(node: AccessibilityNodeInfo?, text: String, depth: Int = 0): Boolean {
        if (node == null || depth > 80) return false // Proteção contra estouro de pilha

        // Checa o nó atual primeiro (fail-fast)
        if (node.text?.toString()?.contains(text, true) == true ||
            node.contentDescription?.toString()?.contains(text, true) == true) return true

        // Só percorre filhos se necessário
        for (i in 0 until node.childCount) {
            if (containsText(node.getChild(i), text, depth + 1)) return true
        }
        return false
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        val pkg = event?.packageName?.toString() ?: return
        val cls = event?.className?.toString() ?: ""
        val root = rootInActiveWindow

        if (pkg == "com.android.systemui") {
            val node = event.source ?: return
            val desc = node.contentDescription?.toString() ?: return
            if (desc.contains("Remover bloco", true)
                ) { BlockOverlayService.showOverlay(this, pkg)
                return } }

        // 1. Bloqueio por pacote e suspicácia (Otimizado)
        val isSuspicious = pkg.contains("virtual") || pkg.contains("cloner") ||
                pkg.contains("sandbox") || pkg.contains("emulator")

        if (BLOCKED_PACKAGES.contains(pkg) || isSuspicious) {
            BlockOverlayService.showOverlay(this, pkg)
            return
        }

        // 3. Permission Controller
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            (pkg == "com.google.android.permissioncontroller" ||
                    pkg == "com.google.android.settings.intelligence" ||
                    cls == "com.android.systemui.statusbar.phone.SystemUIDialog")) {
            BlockOverlayService.showOverlay(this, pkg)
            return
        }

        // 4. Global Keywords (Usando constante estática)
        if (BYPASS_KEYWORDS.any { containsText(root, it) }) {
            BlockOverlayService.showOverlay(this, pkg)
            return
        }

        // 5. Settings Logic
        if (pkg == "com.android.settings") {
            val nodeRoot = root ?: return

            if (cls.contains("SubSettings", true)) {
                val allowed = containsText(nodeRoot, "Tela") ||
                        containsText(nodeRoot, "sobre o dispositivo") ||
                        containsText(nodeRoot, "Ponto de acesso Wi-Fi") ||
                        containsText(nodeRoot, "*campo obrigatório") ||
                        containsText(nodeRoot, "Usar Wi-Fi")

                if (!allowed) {
                    BlockOverlayService.showOverlay(this, pkg)
                    return
                }
            }

            if (isDangerVisible(nodeRoot) ||
                containsText(nodeRoot, "HznLock") ||
                containsText(nodeRoot, "Pesquise nas Configurações") ||
                containsText(nodeRoot, "Todos os Apps")) {

                // Checagem de desinstalação dentro de HznLock
                if (containsText(nodeRoot, "HznLock")) {
                    if (containsText(nodeRoot, "desinstalar") ||
                        containsText(nodeRoot, "app de administrador") ||
                        containsText(nodeRoot, "forçar")) {
                        BlockOverlayService.showOverlay(this, pkg)
                        return
                    }
                }
                BlockOverlayService.showOverlay(this, pkg)
                return
            }
        }

        // 6. Bloqueios específicos por App (Play Store, Brave, etc)
        when (pkg) {
            "com.android.vending" -> {
                if (BYPASS_PLAY_STORE.any { containsVisibleText(root, it) }) {
                    BlockOverlayService.showOverlay(this, pkg)
                }
            }

            "com.brave.browser" -> {
                if (BYPASS_BRAVE.any { containsVisibleText(root, it) }) {
                    BlockOverlayService.showOverlay(this, pkg)
                }
            }

            "org.thunderdog.challegram" -> {
                if (containsVisibleText(root, "buscar")) {
                    BlockOverlayService.showOverlay(this, pkg)
                }
            }

            "com.twitter.android" -> {
                if (containsVisibleText(root, "buscar x")) {
                    BlockOverlayService.showOverlay(this, pkg)
                }
            }

            "com.facebook.katana" -> {
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    if (
                        containsVisibleText(root, "seus posts curtidos") ||
                        containsVisibleText(root, "ver tudo") ||
                        containsVisibleText(root, "shitpost") ||
                        containsVisibleText(root, "vazado")
                    ) {
                        BlockOverlayService.showOverlay(this, pkg)
                    }
                }
            }
        }

        // 7. Long Press Check em Launchers
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            val isLauncher = pkg.contains("launcher") || pkg.contains("trebuchet") ||
                    pkg.contains("home")

            if (isLauncher) {
                val node = event.source ?: return
                val label = node.text?.toString()?.lowercase().orEmpty()
                val desc = node.contentDescription?.toString()?.lowercase().orEmpty()

                if (node.isClickable && (label.contains("hznlock") || desc.contains("hznlock"))) {
                    BlockOverlayService.showOverlay(this, pkg)
                }
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
        dm.unregisterDisplayListener(displayListener)
    }
}