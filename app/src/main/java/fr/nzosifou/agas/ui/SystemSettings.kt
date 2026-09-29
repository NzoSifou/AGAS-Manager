package fr.nzosifou.agas.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import fr.nzosifou.agas.R

/** Raccourcis vers les écrans de réglages du système utilisés par l'appli. */
object SystemSettings {

    val isXiaomi: Boolean = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

    fun openAccessibility(context: Context) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openAppInfo(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
        )
    }

    /** Réglage batterie de l'appli : écran Xiaomi dédié si possible, sinon la demande Android standard. */
    fun openBattery(context: Context) {
        if (isXiaomi) {
            val miui = Intent()
                .setClassName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                .putExtra("package_name", context.packageName)
                .putExtra("package_label", context.getString(R.string.app_name))
            if (runCatching { context.startActivity(miui) }.isSuccess) return
        }
        val standard = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.fromParts("package", context.packageName, null))
        if (runCatching { context.startActivity(standard) }.isSuccess) return
        openAppInfo(context)
    }

    fun openXiaomiAutostart(context: Context) {
        val autostart = Intent()
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        if (runCatching { context.startActivity(autostart) }.isSuccess) return
        openAppInfo(context)
    }
}
