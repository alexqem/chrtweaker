package com.alexqem.chr.core.common

import java.util.UUID

object BleConstants {
    val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val CHAR_UUID: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    val TARGET_UUID_FRAGMENTS = listOf("fff0", "18f0")
    val ADAPTER_NAME_KEYWORDS = listOf(
        "OBD", "ELM", "LINK", "VIECAR", "VGATE", "KONNWEI",
        "SCAN", "IOS-", "BT", "CAR", "V-LINK"
    )

    const val PREFERRED_MTU = 512
    const val CONNECT_TIMEOUT_MS = 10000L
    const val PROMPT_BYTE: Byte = '>'.code.toByte()
    const val PROMPT_CHAR: Char = '>'
}

object ElmConstants {
    const val CR = "\r"
    const val PROMPT = ">"

    val INIT_SEQUENCE = listOf(
        "ATZ" to 3000L,
        "ATE0" to 1000L,
        "ATL0" to 1000L,
        "ATS1" to 1000L,
        "ATH0" to 1000L,
        "ATSP6" to 1000L
    )

    val NEGATIVE_MARKERS = listOf("UNABLE", "NO DATA", "?", "CAN ERROR", "STOPPED")

    const val CMD_VERIFY_CAR = "0100"
    const val CMD_HEADERS_ON = "ATH1"
    const val CMD_HEADERS_OFF = "ATH0"
    const val CMD_RESET = "ATZ"

    val MODE_09_PIDS = listOf("0902", "0904", "090A")
    val DTC_MODES = listOf("03", "07", "0A")
    val CVT_CANDIDATES = listOf("01B4" to "41B4", "21D9" to "61D9", "2182" to "6182")

    const val TIMEOUT_DEFAULT_MS = 3000L
    const val TIMEOUT_INIT_PROBE_MS = 4000L
    const val TIMEOUT_QUICK_MS = 1500L
    const val DELAY_HEADER_SETTLE_MS = 200L
    const val DELAY_RESET_SETTLE_MS = 500L
}

object VehicleConstants {
    const val MAKE = "Toyota"
    const val MODEL = "C-HR"
    const val BODY = "NGX10"
    const val YEAR = 2020
    const val ENGINE = "8NR-FTS"
    const val DISPLACEMENT_CC = 1197
    const val TRANSMISSION = "CVT (K114)"
    const val PROTOCOL_HINT = "ISO 15765-4 CAN 11/500 (id '6')"

    // Operational Thresholds
    const val COLD_START_MAX_COOLANT_C = 40.0
    const val WARMED_UP_MIN_COOLANT_C = 80.0
    const val THERMOSTAT_OPEN_MIN_C = 80.0
    const val THERMOSTAT_OPEN_MAX_C = 84.0

    const val IDLE_RPM_MIN = 680.0
    const val IDLE_RPM_MAX = 780.0

    const val TRIM_NORMAL_LIMIT_PCT = 10.0
}

object SessionConstants {
    const val SAMPLE_INTERVAL_MS = 1000L
    const val WARM_COOLDOWN_TAIL_S = 60.0
    const val MAX_SESSION_DURATION_S = 45.0 * 60.0
    const val MAX_INIT_PROBE_ATTEMPTS = 5
    const val MAX_CVT_PROBE_ATTEMPTS = 5
    const val RATE_CALC_WINDOW_S = 65.0
    const val RATE_CALC_MIN_INTERVAL_MIN = 0.5
    const val DB_BATCH_INSERT_SIZE = 5
}

object SensorConstants {
    const val TEMP_OFFSET_C = -40.0
    const val RPM_DIVIDER = 4.0
    const val PERCENT_255_SCALE = 100.0 / 255.0
    const val TRIM_OFFSET = 128.0
    const val TRIM_SCALE = 100.0 / 128.0
    const val MAF_DIVIDER = 100.0
    const val CATALYST_DIVIDER = 10.0
    const val TIMING_OFFSET = 128.0
    const val TIMING_DIVIDER = 2.0
    const val VOLTAGE_DIVIDER = 1000.0
}
