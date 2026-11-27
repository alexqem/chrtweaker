package com.alexqem.chr.core.sensors

import com.alexqem.chr.core.common.SensorConstants

data class PidSpec(
    val name: String,
    val pidHex: String,
    val expectedBytes: Int
)

object Sensors {

    private fun word16(b0: Int, b1: Int): Int = (b0 shl 8) or b1

    val POLL_LIST = listOf(
        PidSpec("rpm", "0C", 2),
        PidSpec("speed", "0D", 1),
        PidSpec("coolant", "05", 1),
        PidSpec("oil_temp", "5C", 1),
        PidSpec("iat", "0F", 1),
        PidSpec("baro", "33", 1),
        PidSpec("maf", "10", 2),
        PidSpec("load", "04", 1),
        PidSpec("stft", "06", 1),
        PidSpec("ltft", "07", 1),
        PidSpec("fuel_status", "03", 2),
        PidSpec("catalyst", "3C", 2),
        PidSpec("timing", "0E", 1),
        PidSpec("throttle", "45", 1),
        PidSpec("voltage", "42", 2),
        PidSpec("runtime", "1F", 2)
    )

    fun decode(name: String, data: List<Int>): Double? {
        if (data.isEmpty()) return null
        return when (name) {
            "rpm" -> if (data.size >= 2) word16(data[0], data[1]) / SensorConstants.RPM_DIVIDER else null
            "speed" -> data[0].toDouble()
            "coolant", "oil_temp", "iat" -> data[0] + SensorConstants.TEMP_OFFSET_C
            "baro" -> data[0].toDouble()
            "maf" -> if (data.size >= 2) word16(data[0], data[1]) / SensorConstants.MAF_DIVIDER else null
            "load", "throttle" -> data[0] * SensorConstants.PERCENT_255_SCALE
            "stft", "ltft" -> (data[0] - SensorConstants.TRIM_OFFSET) * SensorConstants.TRIM_SCALE
            "fuel_status" -> data[0].toDouble()
            "catalyst" -> if (data.size >= 2) word16(data[0], data[1]) / SensorConstants.CATALYST_DIVIDER else null
            "timing" -> (data[0] - SensorConstants.TIMING_OFFSET) / SensorConstants.TIMING_DIVIDER
            "voltage" -> if (data.size >= 2) word16(data[0], data[1]) / SensorConstants.VOLTAGE_DIVIDER else null
            "runtime" -> if (data.size >= 2) word16(data[0], data[1]).toDouble() else null
            "cvt_temp" -> if (data.size >= 2) (word16(data[0], data[1]) / 256.0) + SensorConstants.TEMP_OFFSET_C else null
            else -> null
        }
    }
}
