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

object EcuTweaks {
    val ALL: List<TweakSpec> = listOf(
        TweakSpec("auto_fold_mirrors", "Автоскладывание зеркал", "Зеркала сами складываются при закрытии машины", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BA701", "3BA700"),
        TweakSpec("comfort_open_windows", "Открыть окна с ключа", "Удерживайте кнопку открытия на брелоке, чтобы опустить все стёкла", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BB201", "3BB200"),
        TweakSpec("comfort_close_windows", "Закрыть окна с ключа", "Удерживайте кнопку закрытия на брелоке, чтобы поднять все стёкла", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BB301", "3BB300"),
        TweakSpec("blink_hazards_lock", "Мигание при закрытии", "Аварийка мигает, когда вы закрываете или открываете машину", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BA101", "3BA100"),
        TweakSpec("beep_lock_unlock", "Звук при закрытии", "Короткий писк при постановке на охрану и снятии с неё", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BA201", "3BA200"),
        TweakSpec("eco_indicator", "Подсказка ECO на приборке", "Показывает зелёный значок ECO при экономной езде", "0x7C0", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BC101", "3BC100"),
        TweakSpec("ac_eco_mode", "Эко-режим кондиционера", "Кондиционер работает мягче, чтобы экономить топливо", "0x7C4", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BD101", "3BD100"),
        TweakSpec("rear_seatbelt_chime", "Пищалка задних ремней", "Звуковое напоминание, если сзади не пристегнулись", "0x7C0", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BC401", "3BC400"),
        TweakSpec("turn_signal_3", "Поворотник: 3 мигания", "Заводской режим при лёгком касании рычага", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 3B", false, "3BA503"),
        TweakSpec("turn_signal_4", "Поворотник: 4 мигания", "4 мигания при лёгком касании рычага", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 3B", false, "3BA504"),
        TweakSpec("turn_signal_5", "Поворотник: 5 миганий", "Удобно для перестроения в городе — мигает 5 раз", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 3B", false, "3BA505"),
        TweakSpec("turn_signal_6", "Поворотник: 6 миганий", "6 миганий при лёгком касании рычага", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 3B", false, "3BA506"),
        TweakSpec("turn_signal_7", "Поворотник: 7 миганий", "Увеличенное время сигнала при перестроении", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 3B", false, "3BA507"),
        TweakSpec("turn_signal_9", "Поворотник: 9 миганий", "9 миганий при лёгком касании рычага", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 3B", false, "3BA509"),
        TweakSpec("turn_signal_11", "Поворотник: 11 миганий", "Максимум (11 миганий) при лёгком касании рычага", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 3B", false, "3BA50B"),
        TweakSpec("abs_inspection", "Сервисный режим ABS", "Только для СТО: проверка тормозов и ABS на стенде", "0x7B0", "ISO 15765-4 CAN (ATSP6) · UDS 10 03", false, "31010200", requiresVLinker = false),
        TweakSpec("atf_reset", "Сброс счётчика масла CVT", "Нажимать только после замены масла в вариаторе", "0x7E1", "ISO 15765-4 CAN (ATSP6) · UDS 31 01", false, "31010F00", requiresVLinker = false)
    )

    fun isVLinkerAdapter(deviceName: String?): Boolean {
        if (deviceName.isNullOrBlank()) return false
        val lower = deviceName.lowercase(Locale.US)
        return lower.contains("vlinker") || lower.contains("vgate")
    }

    suspend fun execute(
        spec: TweakSpec,
        enabled: Boolean,
        isVLinker: Boolean,
        isSim: Boolean,
        sendFn: (suspend (String) -> String)? = null
    ): TweakResult {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val cmd = if (enabled || spec.writeOffCmd == null) spec.writeOnCmd else spec.writeOffCmd
        val header = "ATSH ${spec.ecu.removePrefix("0x")}"

        if (!isSim && spec.requiresVLinker && !isVLinker) {
            val msg = "Недоступно: требуется Vgate vLinker FD+ BT4.0"
            return TweakResult(false, msg, "[$ts] BLOCKED | ${spec.ecu} | $header | TX: $cmd | $msg")
        }

        if (isSim || sendFn == null) {
            val posResp = "7B${cmd.drop(2).take(2)}"
            val modeTag = if (isSim) "SIM" else "OFFLINE"
            val logLine = "[$ts] $modeTag | ${spec.protocol} | $header | TX: $cmd | RX: $posResp"
            return TweakResult(true, "${spec.title}: выполнено", logLine)
        }

        return try {
            sendFn(header)
            val raw = sendFn(cmd).trim()
            val ok = !raw.contains("7F", ignoreCase = true) &&
                !raw.contains("ERROR", ignoreCase = true) &&
                !raw.contains("NO DATA", ignoreCase = true) &&
                !raw.contains("STOPPED", ignoreCase = true) &&
                !raw.contains("?")
            val logLine = "[$ts] ${if (ok) "OK" else "FAIL"} | ${spec.protocol} | $header | TX: $cmd | RX: $raw"
            TweakResult(ok, if (ok) "${spec.title}: выполнено" else "${spec.title}: ошибка ($raw)", logLine)
        } catch (e: Exception) {
            val logLine = "[$ts] EXC | ${spec.protocol} | $header | TX: $cmd | ERR: ${e.message}"
            TweakResult(false, "${spec.title}: ошибка (${e.message})", logLine)
        }
    }
}
