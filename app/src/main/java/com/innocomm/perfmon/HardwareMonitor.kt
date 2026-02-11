/*
adb root
adb shell setenforce 0
adb shell chmod 644 /sys/kernel/ged/hal/gpu_utilization
 */

package com.innocomm.perfmon

import android.content.Context
import android.os.HardwarePropertiesManager
import android.os.SystemClock
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.RandomAccessFile

data class CpuCoreStat(
    var lastTotal: Long = 0,
    var lastIdle: Long = 0
)
class HardwareMonitor {

    private var lastTotal: Long = 0
    private var lastIdle: Long = 0
    private val coreStats = mutableMapOf<Int, CpuCoreStat>()
    private val gpuEma = EmaFilter()
    private val apuEma = EmaFilter()

    // 數據可用性標記，避免重複嘗試失敗導致 Log 噴不停
    private var isCpuStatAvailable = true
    private var isGpuAvailable = true
    private var isTempAvailable = true
    private var isApuAvailable = true

    /**
     * 回傳：
     * key = core index (0,1,2...)
     * value = CPU loading (%) for that core
     */
    fun getPerCoreCpuLoad(): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        if (!isCpuStatAvailable) return result

        try {
            val reader = BufferedReader(InputStreamReader(File("/proc/stat").inputStream()))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val l = line ?: break

                // 只處理 cpu0, cpu1, ...
                if (!l.startsWith("cpu") || l.startsWith("cpu ")) continue

                val parts = l.split("\\s+".toRegex())
                val coreIndex = parts[0].removePrefix("cpu").toIntOrNull() ?: continue

                val user = parts[1].toLong()
                val nice = parts[2].toLong()
                val system = parts[3].toLong()
                val idle = parts[4].toLong()
                val iowait = parts[5].toLong()
                val irq = parts[6].toLong()
                val softirq = parts[7].toLong()

                val total = user + nice + system + idle + iowait + irq + softirq

                val stat = coreStats.getOrPut(coreIndex) { CpuCoreStat() }

                val diffTotal = total - stat.lastTotal
                val diffIdle = idle - stat.lastIdle

                if (diffTotal > 0) {
                    val usage = ((diffTotal - diffIdle) * 100 / diffTotal).toInt()
                    result[coreIndex] = usage.coerceIn(0, 100)
                } else {
                    result[coreIndex] = 0
                }

                stat.lastTotal = total
                stat.lastIdle = idle
            }

            reader.close()
        } catch (e: Exception) {
            isCpuStatAvailable = false
            Log.e("HardwareMonitor", "getPerCoreCpuLoad failed, disabling. Error: ${e.message}")
        }

        return result
    }

    // --- CPU 負載 (需要讀取 /proc/stat) ---
    // 注意：Android 8.0+ 的第三方 App 可能會讀到空值，建議用於系統開發
    fun getCpuLoad(): Int {
        if (!isCpuStatAvailable) return -1
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val line = reader.readLine()
            reader.close()
            val parts = line.split("\\s+".toRegex())

            val user = parts[1].toLong()
            val nice = parts[2].toLong()
            val system = parts[3].toLong()
            val idle = parts[4].toLong()
            val ioWait = parts[5].toLong()
            val irq = parts[6].toLong()
            val softIrq = parts[7].toLong()

            val total = user + nice + system + idle + ioWait + irq + softIrq
            val diffTotal = total - lastTotal
            val diffIdle = idle - lastIdle

            lastTotal = total
            lastIdle = idle

            if (diffTotal == 0L) 0 else ((diffTotal - diffIdle) * 100 / diffTotal).toInt()
        } catch (e: Exception) {
            isCpuStatAvailable = false
            -1
        }
    }

    fun getGpuLoadEma(): Int {
        return gpuEma.update(getGpuLoad())
    }
    fun getGpuLoad(): Int {

        val gpuPaths = arrayOf(
            // 1. MTK Dimensity 常用路徑 (GED 驅動)
            "/sys/kernel/ged/hal/gpu_utilization"
        )

        for (path in gpuPaths) {
            try {
                val file = File(path)
                //Log.v("innocomm", "getGpuLoad: exists:"+file.exists()+",canRead:"+file.canRead())
                if (file.exists() && file.canRead()) {
                    val content = file.readText().trim()
                    //Log.v("innocomm", "$path content: $content")

                    // 處理 "0 0 100" 或 "45%" 或 "45"
                    // 1. 先拿掉百分比符號
                    // 2. 依照空格拆分，取第一個元素
                    val firstPart = content.replace("%", "")
                        .split("\\s+".toRegex())
                        .firstOrNull()

                    return firstPart?.toIntOrNull() ?: 0
                }
            } catch (e: Exception) {
                // 不在這裡設置 isGpuAvailable = false，因為可能是多個路徑中的一個
            }
        }
        return -1 
    }

    fun getGpuFreqMHz(): Int {
        val devfreqRoot = File("/sys/class/devfreq")
        val gpuDirs = devfreqRoot.listFiles() ?: return -1

        for (dir in gpuDirs) {
            if (!dir.name.contains("mali", ignoreCase = true)) continue

            val curFreq = File(dir, "cur_freq")
            if (curFreq.exists() && curFreq.canRead()) {
                val hz = curFreq.readText().trim().toLongOrNull()
                if (hz != null && hz > 0) {
                    return (hz / 1_000_000).toInt()
                }
            }
        }
        return -1
    }

    private var useSysfsForTemp = false

    fun getCpuTemperature(context:Context): Double {
        if (!isTempAvailable) return -1.0
        
        // 1. 優先嘗試使用系統 API (System App 專用)
        if (!useSysfsForTemp) {
            try {
                val hwMgr = context.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE) as HardwarePropertiesManager
                
                // 獲取所有 CPU 感測器的當前溫度
                val temps = hwMgr.getDeviceTemperatures(
                    HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU,
                    HardwarePropertiesManager.TEMPERATURE_CURRENT
                )

                if (temps.isNotEmpty()) {
                    // 有些設備會回傳多個核心的溫度，這裡取平均值
                    return temps.average()
                } else {
                    // 如果回傳空，代表權限不足或硬體不支持此 API，標記切換至 Sysfs
                    Log.d("innocomm", "System API temps empty, switching to Sysfs fallback.")
                    useSysfsForTemp = true
                }

            } catch (e: SecurityException) {
                // 權限不足：通常是因為非 System App
                useSysfsForTemp = true
            } catch (e: Exception) {
                useSysfsForTemp = true
            }
        }

        // 2. Fallback 降級模式：讀取 Sysfs (需要 adb shell setenforce 0 或特定的 chmod)
        val sysfsTemp = getCpuTempFromSysfs()
        if (sysfsTemp > -100.0) { // 正常的溫度數值或讀取成功
            return sysfsTemp
        }

        // 如果兩種方法都失敗，暫不標記 isTempAvailable = false
        // 這樣使用者中途執行 adb 選項後，下一次輪詢就能讀到資料
        return -1.0
    }

    /**
     * 從 Sysfs 遍歷尋找 CPU 溫度感測器 (適用於一般 App + SELinux Permissive)
     */
    private fun getCpuTempFromSysfs(): Double {
        val thermalDir = File("/sys/class/thermal")
        if (!thermalDir.exists() || !thermalDir.isDirectory) return -1.0

        val zones = thermalDir.listFiles { _, name -> name.startsWith("thermal_zone") } ?: return -1.0

        for (zone in zones) {
            try {
                val typeFile = File(zone, "type")
                val tempFile = File(zone, "temp")
                if (typeFile.exists() && tempFile.exists()) {
                    val type = typeFile.readText().trim().lowercase()
                    // 過濾出與 CPU/Soc 相關的感測器類型 (涵蓋 MTK, MediaTek 等常見名稱)
                    if (type.contains("cpu") || type.contains("soc") || type.contains("package") || type.contains("mtktscpu")) {
                        val tempStr = tempFile.readText().trim()
                        val temp = tempStr.toDoubleOrNull() ?: continue
                        // 通常 Sysfs 回傳的是千分之一度 (如 45000 代表 45.0 度)
                        return if (temp > 1000 || temp < -1000) temp / 1000.0 else temp
                    }
                }
            } catch (e: Exception) {
                // 繼續嘗試下一個 thermal zone
            }
        }

        // 最終保底嘗試：直接讀取 zone0 (通常是主要的 SoC 感測器)
        return try {
            val zone0Temp = File("/sys/class/thermal/thermal_zone0/temp")
            if (zone0Temp.exists()) {
                val temp = zone0Temp.readText().trim().toDoubleOrNull() ?: -1.0
                if (temp > 1000 || temp < -1000) temp / 1000.0 else temp
            } else -1.0
        } catch (e: Exception) {
            -1.0
        }
    }

    private var lastApuIpi = 0L
    private var lastSampleTime = 0L
    private var dynamicThreshold = -1.0

    fun getApuLoadEma(context:Context): Int {
        return apuEma.update(getApuLoading(context))
    }
    fun getApuLoading(context:Context): Int {
        if (!isApuAvailable) return -1
        val prefs = context.getSharedPreferences("apu_monitor", Context.MODE_PRIVATE)
        if (dynamicThreshold < 0) {
            dynamicThreshold = prefs.getFloat("max_ipi_per_sec", 40.0f).toDouble()
        }
        //Log.d("HardwareMonitor", "APU dynamicThreshold: $dynamicThreshold")
        val currentIpi = try {
            var total = 0L
            File("/proc/interrupts").useLines { lines ->
                lines.forEach { line ->
                    if (line.contains("apu_ipi")) {
                        // 格式通常為: " 415:  12345  67890 ... apu_ipi"
                        // 使用正則表達式抓取該行所有數字
                        val numbers = "\\d+".toRegex().findAll(line).map { it.value.toLong() }.toList()
                        // numbers[0] 是中斷號 (如 415)，後面的是各個 CPU 核心的計數
                        if (numbers.size > 1) {
                            total = numbers.drop(1).sum()
                        }
                    }
                }
            }
            total
        } catch (e: Exception) {
            isApuAvailable = false
            Log.e("innocomm", "APU IPI read failed, disabling. Error: ${e.message}")
            -1L
        }

        val currentTime = SystemClock.elapsedRealtime()

        if (currentIpi < 0) return -1

        // 第一次執行或數據異常時初始化並回傳 0
        if (lastApuIpi == 0L || currentTime <= lastSampleTime) {
            lastApuIpi = if (currentIpi < 0) 0 else currentIpi
            lastSampleTime = currentTime
            return 0
        }

        val timeDiffMs = currentTime - lastSampleTime
        val ipiDiff = currentIpi - lastApuIpi

        lastApuIpi = currentIpi
        lastSampleTime = currentTime

        val interruptsPerSecond = (ipiDiff.toDouble() / timeDiffMs) * 1000.0

        // 動態校準：如果發現更高的 IPS，更新閾值並記錄
        if (interruptsPerSecond > dynamicThreshold) {
            dynamicThreshold = interruptsPerSecond
            prefs.edit().putFloat("max_ipi_per_sec", dynamicThreshold.toFloat()).apply()
            Log.d("HardwareMonitor", "New APU Max Ipi detected: $dynamicThreshold, saved.")
        }

        val load = (interruptsPerSecond / dynamicThreshold * 100).toInt()

        return load.coerceIn(0, 100)
    }

}