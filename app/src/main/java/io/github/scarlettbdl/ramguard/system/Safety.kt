package io.github.scarlettbdl.ramguard.system

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import android.app.admin.DevicePolicyManager

/**
 * Paquetes que nunca se deben forzar. Matar cualquiera de estos puede dejar el
 * telefono sin UI, sin teclado, sin telefono o reiniciando servicios en loop.
 *
 * Si queres experimentar, edita esta lista; el resto de la app la respeta.
 */
class Safety(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager
    private val ownPackage: String = appContext.packageName

    private val criticalPackages: Set<String> = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.telephony",
        "com.android.bluetooth",
        "com.android.nfc",
        "com.android.se",
        "com.android.shell",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.credentialmanager",
        "com.google.android.credentialmanager",
        "com.android.keychain",
        "com.android.certinstaller",
        "com.android.wallpaper",
        "com.android.wallpaperbackup",
        "com.android.dynsystem",
        "com.android.vending",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.ext.services",
        "com.google.android.ext.shared",
        "com.google.android.webview",
        "com.android.webview"
    )

    private val launcherPackage: String? by lazy {
        runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager
                .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
        }.getOrNull()
    }

    private val inputMethodPackages: Set<String> by lazy {
        runCatching {
            val imm = appContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.enabledInputMethodList.map { it.packageName }.toSet()
        }.getOrDefault(emptySet())
    }

    private val accessibilityPackages: Set<String> by lazy {
        runCatching {
            val manager = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            manager
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .mapNotNull { it.resolveInfo?.serviceInfo?.packageName }
                .toSet()
        }.getOrDefault(emptySet())
    }

    private val deviceAdminPackages: Set<String> by lazy {
        runCatching {
            val dpm = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.activeAdmins?.map { it.packageName }?.toSet() ?: emptySet()
        }.getOrDefault(emptySet())
    }

    fun isProtected(pkg: String): Boolean {
        if (pkg == ownPackage) return true
        if (pkg in criticalPackages) return true
        if (pkg.startsWith("com.android.providers.")) return true
        if (pkg.startsWith("com.android.internal.")) return true
        if (pkg == launcherPackage) return true
        if (pkg in inputMethodPackages) return true
        if (pkg in accessibilityPackages) return true
        if (pkg in deviceAdminPackages) return true
        return false
    }

    fun reason(pkg: String): String? = when {
        pkg == ownPackage -> "es RamGuard"
        pkg in criticalPackages -> "componente del sistema"
        pkg.startsWith("com.android.providers.") -> "proveedor del sistema"
        pkg == launcherPackage -> "es el launcher activo"
        pkg in inputMethodPackages -> "es tu teclado"
        pkg in accessibilityPackages -> "servicio de accesibilidad activo"
        pkg in deviceAdminPackages -> "administrador del dispositivo"
        else -> null
    }
}
