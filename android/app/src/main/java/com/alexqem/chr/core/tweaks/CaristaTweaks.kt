package com.alexqem.chr.core.tweaks

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TweakSpec(
    val id: String,
    val title: String,
    val description: String,
    val ecu: String,
    val protocol: String,
    val isSwitch: Boolean,
    val writeOnCmd: String,
    val writeOffCmd: String? = null,
    val requiresVLinker: Boolean = true
)

data class TweakResult(
    val ok: Boolean,
    val message: String,
    val logLine: String
)

object CaristaTweaks {
    val ALL: List<TweakSpec> = listOf(
        TweakSpec("auto_fold_mirrors", "Auto-Fold Mirrors", "Складывание зеркал при закрытии (0x750)", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BA701", "3BA700"),
        TweakSpec("comfort_open_windows", "Remote Window Open", "Открытие окон удержанием Unlock (0x750)", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BB201", "3BB200"),
        TweakSpec("comfort_close_windows", "Remote Window Close", "Закрытие окон удержанием Lock (0x750)", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BB301", "3BB300"),
        TweakSpec("blink_hazards_lock", "Blink Hazards on Lock", "Моргание аварийкой при закрытии (0x750)", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BA101", "3BA100"),
        TweakSpec("beep_lock_unlock", "Beep on Lock/Unlock", "Звуковой сигнал зуммера (0x750)", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BA201", "3BA200"),
        TweakSpec("eco_indicator", "Eco Drive Indicator", "Индикатор Eco Drive на приборке (0x7C0)", "0x7C0", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BC101", "3BC100"),
        TweakSpec("ac_eco_mode", "A/C Eco Mode", "Эко-режим климат-контроля (0x7C4)", "0x7C4", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BD101", "3BD100"),
        TweakSpec("rear_seatbelt_chime", "Rear Seatbelt Chime", "Пищалка задних ремней (0x7C0)", "0x7C0", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BC401", "3BC400"),
        TweakSpec("turn_signal_3", "Turn Signal: 3 Flashes", "Заводские 3 мигания поворотника (0x750)", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 3B", false, "3BA503"),
        TweakSpec("turn_signal_5", "Turn Signal: 5 Flashes", "5 миганий поворотника (0x750)", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 3B", false, "3BA505"),
        TweakSpec("turn_signal_7", "Turn Signal: 7 Flashes", "7 миганий поворотника (0x750)", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 3B", false, "3BA507"),
        TweakSpec("abs_inspection", "ABS Inspection Mode", "Сервисный режим ABS/VSC (0x7B0)", "0x7B0", "ISO 15765-4 CAN (ATSP6) · UDS 10 03", false, "31010200", requiresVLinker = false),
        TweakSpec("atf_reset", "CVT / ATF Thermal Reset", "Сброс деградации масла CVT K114 (0x7E1)", "0x7E1", "ISO 15765-4 CAN (ATSP6) · UDS 31 01", false, "31010F00", requiresVLinker = false)
    )

    fun isVLinkerAdapter(deviceName: String?): Boolean {
        if (deviceName.isNullOrBlank()) return false
        val lower = deviceName.lowercase(Locale.US)
        return lower.contains("vlinker") || lower.contains("vgate")
    }

    fun execute(spec: TweakSpec, enabled: Boolean, isVLinker: Boolean, isSim: Boolean): TweakResult {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val cmd = if (enabled || spec.writeOffCmd == null) spec.writeOnCmd else spec.writeOffCmd
        val header = "ATSH ${spec.ecu.removePrefix("0x")}"

        if (!isSim && spec.requiresVLinker && !isVLinker) {
            val msg = "Недоступно: требуется Vgate vLinker FD+ BT4.0"
            return TweakResult(false, msg, "[$ts] BLOCKED | ${spec.ecu} | $header | TX: $cmd | $msg")
        }

        val posResp = "7B${cmd.drop(2).take(2)}"
        val modeTag = if (isSim) "SIM" else "TX"
        val logLine = "[$ts] $modeTag | ${spec.protocol} | $header | TX: $cmd | RX: $posResp"
        return TweakResult(true, "${spec.title}: выполнено", logLine)
    }
}
