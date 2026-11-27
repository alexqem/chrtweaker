package com.alexqem.chr.core.sim

import com.alexqem.chr.core.common.ElmConstants
import com.alexqem.chr.core.common.SensorConstants
import com.alexqem.chr.core.common.VehicleConstants
import com.alexqem.chr.core.elm.ElmConnection
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * High-fidelity in-app simulator duplicating mycar/sim.py.
 * Emulates a cold Toyota 8NR-FTS warming up from 5°C to 88°C.
 */
class SimElmConnection(
    private val timeScale: Double = 1.0
) : ElmConnection {

    private val startTimeMs = System.currentTimeMillis()
    private var connected = true

    private fun elapsedMinutes(): Double {
        return ((System.currentTimeMillis() - startTimeMs) / 1000.0 * timeScale) / 60.0
    }

    override suspend fun send(cmd: String, settleWaitMs: Long, timeoutMs: Long): String {
        if (!connected) throw IllegalStateException("Simulator disconnected")
        if (settleWaitMs > 0) delay(settleWaitMs)
        delay(15) // simulate BLE packet latency

        val cleanCmd = cmd.trim().uppercase()
        if (cleanCmd.startsWith("AT")) {
            return "OK\r\n>"
        }

        if (cleanCmd == ElmConstants.CMD_VERIFY_CAR) {
            return "41 00 BE 3F B8 13\r\n>"
        }

        if (cleanCmd.startsWith("01")) {
            val pids = cleanCmd.substring(2)
            val chunkLength = 2
            val responseParts = mutableListOf("41")
            for (i in 0 until pids.length step chunkLength) {
                if (i + chunkLength <= pids.length) {
                    val pid = pids.substring(i, i + chunkLength)
                    val bytes = pidPayload(pid)
                    if (bytes != null) {
                        responseParts.add(pid)
                        responseParts.addAll(bytes)
                    }
                }
            }
            return responseParts.joinToString(" ") + "\r\n>"
        }

        if (cleanCmd == "0902") {
            return "7E8 10 14 49 02 01 4E 47 58 31 30 2D 53 49 4D 30 30 30 30 31\r\n>"
        }
        if (cleanCmd == "0904") {
            return "7E8 10 12 49 04 01 38 39 36 36 33 31 30 34 31 30 30 30\r\n>"
        }
        if (cleanCmd == "090A") {
            return "7E8 10 14 49 0A 01 45 4E 47 49 4E 45 2D 45 43 4D 2D 53 49 4D\r\n>"
        }

        if (cleanCmd in ElmConstants.DTC_MODES) {
            return "NO DATA\r\n>"
        }

        return "NO DATA\r\n>"
    }

    private fun pidPayload(pid: String): List<String>? {
        val tMin = elapsedMinutes()
        val coolant = min(5.0 + tMin * 9.5, 88.0)
        val oil = min(5.0 + tMin * 7.0, 84.0)

        return when (pid) {
            "0C" -> {
                val rpm = max(VehicleConstants.IDLE_RPM_MIN, 1650.0 - (coolant - 5.0) * 12.0)
                val raw = (rpm * SensorConstants.RPM_DIVIDER).toInt()
                listOf(String.format("%02X", raw shr 8), String.format("%02X", raw and 0xFF))
            }
            "0D" -> listOf("00")
            "05" -> {
                val raw = (coolant - SensorConstants.TEMP_OFFSET_C).toInt()
                listOf(String.format("%02X", raw))
            }
            "5C" -> {
                val raw = (oil - SensorConstants.TEMP_OFFSET_C).toInt()
                listOf(String.format("%02X", raw))
            }
            "0F" -> listOf(String.format("%02X", (5.0 - SensorConstants.TEMP_OFFSET_C).toInt()))
            "33" -> listOf(String.format("%02X", 101))
            "10" -> {
                val maf = max(1.65, 4.8 - (coolant - 5.0) * 0.04)
                val raw = (maf * SensorConstants.MAF_DIVIDER).toInt()
                listOf(String.format("%02X", raw shr 8), String.format("%02X", raw and 0xFF))
            }
            "04" -> listOf(String.format("%02X", (22.0 / SensorConstants.PERCENT_255_SCALE).toInt()))
            "06" -> {
                val stft = sin(tMin * 10.0) * 1.5
                val raw = ((stft / SensorConstants.TRIM_SCALE) + SensorConstants.TRIM_OFFSET).toInt()
                listOf(String.format("%02X", raw))
            }
            "07" -> {
                val raw = ((5.47 / SensorConstants.TRIM_SCALE) + SensorConstants.TRIM_OFFSET).toInt()
                listOf(String.format("%02X", raw))
            }
            "03" -> {
                val loop = if (coolant < VehicleConstants.COLD_START_MAX_COOLANT_C) 1 else 2
                listOf(String.format("%02X", loop), "00")
            }
            "3C" -> {
                val cat = min(20.0 + tMin * 80.0, 480.0)
                val raw = (cat * SensorConstants.CATALYST_DIVIDER).toInt()
                listOf(String.format("%02X", raw shr 8), String.format("%02X", raw and 0xFF))
            }
            "0E" -> listOf(String.format("%02X", (8.0 * SensorConstants.TIMING_DIVIDER + SensorConstants.TIMING_OFFSET).toInt()))
            "45" -> listOf(String.format("%02X", (14.0 / SensorConstants.PERCENT_255_SCALE).toInt()))
            "42" -> {
                val raw = (14.25 * SensorConstants.VOLTAGE_DIVIDER).toInt()
                listOf(String.format("%02X", raw shr 8), String.format("%02X", raw and 0xFF))
            }
            "1F" -> {
                val sec = (tMin * 60.0).toInt()
                listOf(String.format("%02X", sec shr 8), String.format("%02X", sec and 0xFF))
            }
            "B4" -> {
                val cvt = min(5.0 + tMin * 3.5, 65.0)
                val raw = ((cvt - SensorConstants.TEMP_OFFSET_C) * 256.0).toInt()
                listOf(String.format("%02X", raw shr 8), String.format("%02X", raw and 0xFF))
            }
            else -> null
        }
    }

    override fun isConnected(): Boolean = connected

    override fun close() {
        connected = false
    }
}
