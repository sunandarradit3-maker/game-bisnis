package com.ditz.gameboost

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class PerformanceController(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val resolver = context.contentResolver

    data class ApplyResult(
        val applied: List<String>,
        val skipped: List<String>,
        val failed: List<String>
    ) {
        fun summary(): String = buildString {
            append("Aktif: ${applied.size}")
            if (skipped.isNotEmpty()) append(" • perlu izin/tidak didukung: ${skipped.size}")
            if (failed.isNotEmpty()) append(" • gagal: ${failed.size}")
        }
    }

    fun canWriteSystemSettings(): Boolean = Settings.System.canWrite(context)

    fun openWriteSettingsPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun hasDndAccess(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    fun openDndPermission() {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun isRootAvailable(): Boolean {
        val r = runRoot("id")
        return r.ok && r.output.contains("uid=0")
    }

    fun apply(config: BoostConfig): ApplyResult {
        saveBaselineOnce(config.packageName)
        val applied = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val failed = mutableListOf<String>()

        if (config.lockBrightness) {
            if (canWriteSystemSettings()) {
                val okMode = Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                val okValue = Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, (config.brightnessPercent.coerceIn(5, 100) * 255 / 100))
                if (okMode && okValue) applied += "Brightness ${config.brightnessPercent}%" else failed += "Brightness"
            } else skipped += "Brightness: izinkan Modify system settings"
        }

        if (config.highRefreshRate) {
            if (canWriteSystemSettings()) {
                val target = config.refreshRate.toFloat()
                val a = Settings.System.putFloat(resolver, "peak_refresh_rate", target)
                val b = Settings.System.putFloat(resolver, "min_refresh_rate", target)
                val readBack = Settings.System.getFloat(resolver, "peak_refresh_rate", -1f)
                if (a && b && readBack >= target - 1f) applied += "Refresh ${config.refreshRate}Hz" else failed += "Refresh rate (OEM menolak/read-back tidak cocok)"
            } else skipped += "Refresh rate: izinkan Modify system settings"
        }

        if (config.dnd) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.isNotificationPolicyAccessGranted) {
                try {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    applied += "DND Gaming"
                } catch (_: Throwable) { failed += "DND" }
            } else skipped += "DND: berikan Notification Policy access"
        }

        val root = isRootAvailable()
        if (config.rootGameMode) {
            if (root && validPackage(config.packageName)) {
                val r = runRoot("cmd game mode performance ${config.packageName}")
                if (r.ok) applied += "Android Game Mode: performance" else failed += "Game Mode: ${r.output.take(80)}"
            } else skipped += "Game Mode performance: membutuhkan root"
        }

        if (config.fixedPerformance) {
            if (root) {
                val r = runRoot("cmd power set-fixed-performance-mode-enabled true")
                if (r.ok) applied += "Fixed Performance Mode" else failed += "Fixed Performance Mode"
            } else skipped += "Fixed Performance: membutuhkan root"
        }

        if (config.mobileDataAlwaysOn) {
            if (root) {
                val r = runRoot("settings put global mobile_data_always_on 1")
                if (r.ok) applied += "Mobile data always-on" else failed += "Mobile data always-on"
            } else skipped += "Fast network handover: membutuhkan root"
        }

        if (config.reduceAnimations) {
            if (root) {
                val values = if (config.aggressiveAnimations) listOf("0", "0", "0") else listOf("0.5", "0.5", "0.5")
                val cmds = listOf(
                    "settings put global window_animation_scale ${values[0]}",
                    "settings put global transition_animation_scale ${values[1]}",
                    "settings put global animator_duration_scale ${values[2]}"
                )
                if (cmds.all { runRoot(it).ok }) applied += "UI animation reduction" else failed += "UI animation reduction"
            } else skipped += "Animation tuning: membutuhkan root"
        }

        prefs.edit().putBoolean(KEY_SESSION_ACTIVE, true).putString(KEY_PACKAGE, config.packageName).apply()
        return ApplyResult(applied, skipped, failed)
    }

    fun thermalSafetyFallback() {
        if (!prefs.getBoolean(KEY_SESSION_ACTIVE, false)) return
        runRoot("cmd power set-fixed-performance-mode-enabled false")
        val pkg = prefs.getString(KEY_PACKAGE, "") ?: ""
        if (validPackage(pkg)) runRoot("cmd game mode standard $pkg")
        if (canWriteSystemSettings()) {
            Settings.System.putFloat(resolver, "min_refresh_rate", 60f)
            Settings.System.putFloat(resolver, "peak_refresh_rate", 60f)
        }
        prefs.edit().putBoolean(KEY_THERMAL_TRIPPED, true).apply()
    }

    fun restore(): List<String> {
        val restored = mutableListOf<String>()
        val pkg = prefs.getString(KEY_PACKAGE, "") ?: ""

        if (canWriteSystemSettings()) {
            if (prefs.contains(KEY_BRIGHTNESS)) {
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, prefs.getInt(KEY_BRIGHTNESS, 128))
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, prefs.getInt(KEY_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC))
                restored += "Brightness"
            }
            if (prefs.contains(KEY_PEAK_REFRESH)) {
                Settings.System.putFloat(resolver, "peak_refresh_rate", prefs.getFloat(KEY_PEAK_REFRESH, 60f))
                Settings.System.putFloat(resolver, "min_refresh_rate", prefs.getFloat(KEY_MIN_REFRESH, 60f))
                restored += "Refresh rate"
            }
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted && prefs.contains(KEY_DND_FILTER)) {
            try {
                nm.setInterruptionFilter(prefs.getInt(KEY_DND_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL))
                restored += "DND"
            } catch (_: Throwable) { }
        }

        if (isRootAvailable()) {
            runRoot("cmd power set-fixed-performance-mode-enabled false")
            if (validPackage(pkg)) runRoot("cmd game mode standard $pkg")
            restoreRootSetting("mobile_data_always_on", KEY_MOBILE_DATA_ALWAYS_ON)
            restoreRootSetting("window_animation_scale", KEY_WINDOW_ANIM)
            restoreRootSetting("transition_animation_scale", KEY_TRANSITION_ANIM)
            restoreRootSetting("animator_duration_scale", KEY_ANIMATOR_SCALE)
            restored += "Root tuning"
        }

        prefs.edit().putBoolean(KEY_SESSION_ACTIVE, false).putBoolean(KEY_THERMAL_TRIPPED, false).apply()
        return restored
    }

    fun wasThermalTripped(): Boolean = prefs.getBoolean(KEY_THERMAL_TRIPPED, false)

    private fun saveBaselineOnce(packageName: String) {
        if (prefs.getBoolean(KEY_SESSION_ACTIVE, false)) return
        val edit = prefs.edit()
        edit.putString(KEY_PACKAGE, packageName)
        edit.putInt(KEY_BRIGHTNESS, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128))
        edit.putInt(KEY_BRIGHTNESS_MODE, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC))
        edit.putFloat(KEY_PEAK_REFRESH, Settings.System.getFloat(resolver, "peak_refresh_rate", 60f))
        edit.putFloat(KEY_MIN_REFRESH, Settings.System.getFloat(resolver, "min_refresh_rate", 60f))
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) edit.putInt(KEY_DND_FILTER, nm.currentInterruptionFilter)
        if (isRootAvailable()) {
            edit.putString(KEY_MOBILE_DATA_ALWAYS_ON, runRoot("settings get global mobile_data_always_on").output.trim())
            edit.putString(KEY_WINDOW_ANIM, runRoot("settings get global window_animation_scale").output.trim())
            edit.putString(KEY_TRANSITION_ANIM, runRoot("settings get global transition_animation_scale").output.trim())
            edit.putString(KEY_ANIMATOR_SCALE, runRoot("settings get global animator_duration_scale").output.trim())
        }
        edit.apply()
    }

    private fun restoreRootSetting(key: String, prefKey: String) {
        val v = prefs.getString(prefKey, null)?.trim()
        if (!v.isNullOrBlank() && v != "null") runRoot("settings put global $key $v")
    }

    private fun validPackage(pkg: String): Boolean = pkg.matches(Regex("[A-Za-z0-9._]+")) && pkg.contains('.')

    private data class RootResult(val ok: Boolean, val output: String)

    private fun runRoot(command: String): RootResult {
        return try {
            val p = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            val out = BufferedReader(InputStreamReader(p.inputStream)).use { it.readText() }
            val done = p.waitFor(4, TimeUnit.SECONDS)
            if (!done) {
                p.destroyForcibly()
                RootResult(false, "timeout")
            } else RootResult(p.exitValue() == 0, out)
        } catch (t: Throwable) {
            RootResult(false, t.javaClass.simpleName)
        }
    }

    companion object {
        private const val PREFS = "boost_state"
        private const val KEY_SESSION_ACTIVE = "session_active"
        private const val KEY_PACKAGE = "package"
        private const val KEY_THERMAL_TRIPPED = "thermal_tripped"
        private const val KEY_BRIGHTNESS = "brightness"
        private const val KEY_BRIGHTNESS_MODE = "brightness_mode"
        private const val KEY_PEAK_REFRESH = "peak_refresh"
        private const val KEY_MIN_REFRESH = "min_refresh"
        private const val KEY_DND_FILTER = "dnd_filter"
        private const val KEY_MOBILE_DATA_ALWAYS_ON = "mobile_data_always_on"
        private const val KEY_WINDOW_ANIM = "window_animation_scale"
        private const val KEY_TRANSITION_ANIM = "transition_animation_scale"
        private const val KEY_ANIMATOR_SCALE = "animator_duration_scale"
    }
}

data class BoostConfig(
    val packageName: String,
    val brightnessPercent: Int,
    val refreshRate: Int,
    val lockBrightness: Boolean,
    val highRefreshRate: Boolean,
    val dnd: Boolean,
    val thermalGuard: Boolean,
    val rootGameMode: Boolean,
    val fixedPerformance: Boolean,
    val mobileDataAlwaysOn: Boolean,
    val reduceAnimations: Boolean,
    val aggressiveAnimations: Boolean
)
