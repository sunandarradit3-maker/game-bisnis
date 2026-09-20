package com.ditz.gameboost

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PremiumGameBoostApp(this) }
    }
}

private data class DeviceSnapshot(
    val refreshHz: Float,
    val thermal: String,
    val batteryTempC: Float?,
    val freeRamMb: Long,
    val network: String,
    val downstreamMbps: Int
)

private data class GamePreset(val name: String, val pkg: String)

@Composable
fun PremiumGameBoostApp(activity: MainActivity) {
    val controller = remember { PerformanceController(activity) }
    val scope = rememberCoroutineScope()
    val prefs = remember { activity.getSharedPreferences("ui_profile", Context.MODE_PRIVATE) }

    var selectedPackage by remember { mutableStateOf(prefs.getString("package", "com.mobile.legends") ?: "com.mobile.legends") }
    var refreshRate by remember { mutableFloatStateOf(prefs.getInt("refresh", 120).toFloat()) }
    var brightness by remember { mutableFloatStateOf(prefs.getInt("brightness", 85).toFloat()) }
    var lockBrightness by remember { mutableStateOf(prefs.getBoolean("lock_brightness", true)) }
    var highRefresh by remember { mutableStateOf(prefs.getBoolean("high_refresh", true)) }
    var dnd by remember { mutableStateOf(prefs.getBoolean("dnd", true)) }
    var thermalGuard by remember { mutableStateOf(prefs.getBoolean("thermal", true)) }
    var rootGameMode by remember { mutableStateOf(prefs.getBoolean("root_game", true)) }
    var fixedPerformance by remember { mutableStateOf(prefs.getBoolean("fixed_perf", false)) }
    var mobileDataAlwaysOn by remember { mutableStateOf(prefs.getBoolean("mobile_data", true)) }
    var reduceAnimations by remember { mutableStateOf(prefs.getBoolean("reduce_anim", true)) }
    var aggressiveAnimations by remember { mutableStateOf(prefs.getBoolean("aggr_anim", false)) }
    var status by remember { mutableStateOf("Siap. Tidak ada metrik FPS palsu; panel hanya memakai status sistem yang benar-benar terbaca.") }
    var rootState by remember { mutableStateOf("Belum dicek") }
    var snapshot by remember { mutableStateOf(readDeviceSnapshot(activity)) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        while (true) {
            snapshot = readDeviceSnapshot(activity)
            delay(2500)
        }
    }

    val games = remember {
        listOf(
            GamePreset("Mobile Legends", "com.mobile.legends"),
            GamePreset("Free Fire", "com.dts.freefireth"),
            GamePreset("Free Fire MAX", "com.dts.freefiremax"),
            GamePreset("PUBG Mobile", "com.tencent.ig"),
            GamePreset("COD Mobile", "com.garena.game.codm")
        )
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF8B5CF6),
            secondary = Color(0xFF22D3EE),
            background = Color(0xFF080A0F),
            surface = Color(0xFF11141B),
            surfaceVariant = Color(0xFF171B24)
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("ASTRA GAMEBOOST", fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("Premium Android performance panel • real system controls", color = Color(0xFF9CA3AF))

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("LIVE DEVICE STATUS", fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Stat("Display", "${snapshot.refreshHz.roundToInt()} Hz")
                            Stat("Thermal", snapshot.thermal)
                            Stat("Battery", snapshot.batteryTempC?.let { "%.1f°C".format(it) } ?: "N/A")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Stat("Free RAM", "${snapshot.freeRamMb} MB")
                            Stat("Network", snapshot.network)
                            Stat("Link", if (snapshot.downstreamMbps > 0) "${snapshot.downstreamMbps} Mbps" else "N/A")
                        }
                        Text("FPS game lain tidak ditampilkan karena Android biasa tidak menyediakan angka FPS real-time yang terpercaya tanpa tracing/privilege khusus.", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                    }
                }

                SectionTitle("Game Target")
                games.forEach { game ->
                    OutlinedButton(
                        onClick = { selectedPackage = game.pkg },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (selectedPackage == game.pkg) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("${game.name}  •  ${game.pkg}")
                    }
                }
                OutlinedTextField(
                    value = selectedPackage,
                    onValueChange = { selectedPackage = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Custom package name") },
                    singleLine = true,
                    supportingText = { Text("Contoh: com.mobile.legends") }
                )

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("DISPLAY & SESSION", fontWeight = FontWeight.Bold)
                        Text("Refresh target: ${refreshRate.roundToInt()} Hz")
                        Slider(value = refreshRate, onValueChange = { refreshRate = it }, valueRange = 60f..144f, steps = 13)
                        Text("Brightness lock: ${brightness.roundToInt()}%")
                        Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 20f..100f)
                        FeatureSwitch("High Refresh Controller", "Menulis peak/min refresh rate dan memverifikasi hasilnya. OEM bisa menolak.", highRefresh) { highRefresh = it }
                        FeatureSwitch("Brightness Lock", "Mengatur brightness sistem selama sesi lalu mengembalikan nilai semula.", lockBrightness) { lockBrightness = it }
                        FeatureSwitch("DND Gaming", "Aktifkan priority interruption filter saat game berjalan.", dnd) { dnd = it }
                        FeatureSwitch("Thermal Guard", "Watchdog nyata; saat status thermal SEVERE, tuning agresif dimatikan.", thermalGuard) { thermalGuard = it }
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ROOT PERFORMANCE LAYER", fontWeight = FontWeight.Bold)
                        Text("Status root: $rootState", color = Color(0xFF9CA3AF), fontSize = 13.sp)
                        OutlinedButton(onClick = {
                            scope.launch {
                                rootState = "Mengecek…"
                                val ok = withContext(Dispatchers.IO) { controller.isRootAvailable() }
                                rootState = if (ok) "Tersedia" else "Tidak tersedia / akses ditolak"
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("CHECK ROOT") }
                        FeatureSwitch("Android Game Mode: Performance", "Menjalankan cmd game mode performance pada game target.", rootGameMode) { rootGameMode = it }
                        FeatureSwitch("Fixed Performance Mode", "Meminta Android fixed-performance mode. Lebih panas; Thermal Guard sangat disarankan.", fixedPerformance) { fixedPerformance = it }
                        FeatureSwitch("Fast Network Handover", "Mengaktifkan mobile_data_always_on untuk perpindahan koneksi lebih siap; bukan bonding/ping booster palsu.", mobileDataAlwaysOn) { mobileDataAlwaysOn = it }
                        FeatureSwitch("UI Animation Reduction", "Mengurangi animasi sistem agar transisi launcher/menu terasa lebih cepat; bukan peningkat FPS game.", reduceAnimations) { reduceAnimations = it }
                        FeatureSwitch("Zero Animation", "Jika aktif, skala animasi sistem = 0. Gunakan hanya bila memang diinginkan.", aggressiveAnimations) { aggressiveAnimations = it }
                    }
                }

                SectionTitle("Permissions")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { controller.openWriteSettingsPermission() }, modifier = Modifier.weight(1f)) {
                        Text(if (controller.canWriteSystemSettings()) "SYSTEM ✓" else "ALLOW SYSTEM")
                    }
                    OutlinedButton(onClick = { controller.openDndPermission() }, modifier = Modifier.weight(1f)) {
                        Text(if (controller.hasDndAccess()) "DND ✓" else "ALLOW DND")
                    }
                }

                Button(
                    onClick = {
                        val pkg = selectedPackage.trim()
                        if (!pkg.matches(Regex("[A-Za-z0-9._]+")) || !pkg.contains('.')) {
                            status = "Package name tidak valid."
                            return@Button
                        }
                        prefs.edit()
                            .putString("package", pkg)
                            .putInt("refresh", refreshRate.roundToInt())
                            .putInt("brightness", brightness.roundToInt())
                            .putBoolean("lock_brightness", lockBrightness)
                            .putBoolean("high_refresh", highRefresh)
                            .putBoolean("dnd", dnd)
                            .putBoolean("thermal", thermalGuard)
                            .putBoolean("root_game", rootGameMode)
                            .putBoolean("fixed_perf", fixedPerformance)
                            .putBoolean("mobile_data", mobileDataAlwaysOn)
                            .putBoolean("reduce_anim", reduceAnimations)
                            .putBoolean("aggr_anim", aggressiveAnimations)
                            .apply()

                        if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }

                        val config = BoostConfig(
                            packageName = pkg,
                            brightnessPercent = brightness.roundToInt(),
                            refreshRate = refreshRate.roundToInt(),
                            lockBrightness = lockBrightness,
                            highRefreshRate = highRefresh,
                            dnd = dnd,
                            thermalGuard = thermalGuard,
                            rootGameMode = rootGameMode,
                            fixedPerformance = fixedPerformance,
                            mobileDataAlwaysOn = mobileDataAlwaysOn,
                            reduceAnimations = reduceAnimations,
                            aggressiveAnimations = aggressiveAnimations
                        )
                        status = "Menerapkan tuning…"
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { controller.apply(config) }
                            val service = Intent(activity, BoosterService::class.java).putExtra(BoosterService.EXTRA_THERMAL_GUARD, thermalGuard)
                            activity.startForegroundService(service)
                            val launchIntent = activity.packageManager.getLaunchIntentForPackage(pkg)
                            if (launchIntent != null) {
                                status = result.summary() + " • game dibuka"
                                activity.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } else {
                                status = result.summary() + " • game tidak ditemukan; tuning tetap aktif sampai RESTORE"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("BOOST & LAUNCH", fontWeight = FontWeight.Bold) }

                OutlinedButton(
                    onClick = {
                        status = "Mengembalikan pengaturan…"
                        activity.stopService(Intent(activity, BoosterService::class.java))
                        scope.launch {
                            val restored = withContext(Dispatchers.IO) { controller.restore() }
                            status = "RESTORE selesai: ${if (restored.isEmpty()) "tidak ada baseline aktif" else restored.joinToString()}"
                            snapshot = readDeviceSnapshot(activity)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text("EMERGENCY RESTORE") }

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("SESSION LOG", fontWeight = FontWeight.Bold)
                        Text(status, fontSize = 13.sp)
                        if (controller.wasThermalTripped()) {
                            Text("Thermal Guard pernah memicu safety fallback pada sesi ini.", color = Color(0xFFF59E0B), fontSize = 12.sp)
                        }
                    }
                }

                Text(
                    "Yang sengaja tidak diklaim: touch sampling paksa universal, CPU/GPU overclock universal, network bonding palsu, atau lock FPS 100%. Fitur itu bergantung kernel/OEM/game. Aplikasi ini hanya menandai fitur aktif jika perintah atau write-setting benar-benar dijalankan.",
                    color = Color(0xFF9CA3AF), fontSize = 12.sp
                )
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
}

@Composable
private fun FeatureSwitch(title: String, desc: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(desc, color = Color(0xFF9CA3AF), fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 80.dp)) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(label, color = Color(0xFF9CA3AF), fontSize = 11.sp)
    }
}

private fun readDeviceSnapshot(context: Context): DeviceSnapshot {
    val activity = context as? MainActivity
    val refresh = activity?.display?.refreshRate ?: 0f

    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val thermalValue = if (Build.VERSION.SDK_INT >= 29) pm.currentThermalStatus else PowerManager.THERMAL_STATUS_NONE
    val thermal = when (thermalValue) {
        PowerManager.THERMAL_STATUS_NONE -> "Normal"
        PowerManager.THERMAL_STATUS_LIGHT -> "Light"
        PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
        else -> "Unknown"
    }

    val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val rawTemp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
    val batteryTemp = if (rawTemp != Int.MIN_VALUE) rawTemp / 10f else null

    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    val freeRamMb = mem.availMem / (1024L * 1024L)

    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    val network = when {
        caps == null -> "Offline"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        else -> "Other"
    }
    val downMbps = (caps?.linkDownstreamBandwidthKbps ?: 0) / 1000

    return DeviceSnapshot(refresh, thermal, batteryTemp, freeRamMb, network, downMbps)
}
