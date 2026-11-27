package com.alexqem.chr.core.tweaks

import android.content.Context
import com.alexqem.chr.core.elm.ElmParser
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
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
    val requiresVLinker: Boolean = true,
    val readCmd: String? = null,
    val sessionCmd: String? = null
)

data class TweakResult(
    val ok: Boolean,
    val message: String,
    val logLine: String,
    val beforeValue: String? = null,
    val afterValue: String? = null
)

object EcuTweaks {
    private data class TweakJsonItem(
        @SerializedName("id") val id: String,
        @SerializedName("title") val title: String,
        @SerializedName("description") val description: String,
        @SerializedName("ecu") val ecu: String,
        @SerializedName("protocol") val protocol: String,
        @SerializedName("kind") val kind: String,
        @SerializedName("read_cmd") val readCmd: String? = null,
        @SerializedName("session_cmd") val sessionCmd: String? = null,
        @SerializedName("write_on_cmd") val writeOnCmd: String,
        @SerializedName("write_off_cmd") val writeOffCmd: String? = null,
        @SerializedName("requires_vlinker") val requiresVLinker: Boolean = true
    )

    private val fallbackTweaks: List<TweakSpec> = listOf(
        TweakSpec("auto_fold_mirrors", "Автоскладывание зеркал", "Зеркала сами складываются при закрытии машины", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BA701", "3BA700", true, "21A7"),
        TweakSpec("comfort_open_windows", "Открыть окна с ключа", "Удерживайте кнопку открытия на брелоке, чтобы опустить все стёкла", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BB201", "3BB200", true, "21B2"),
        TweakSpec("comfort_close_windows", "Закрыть окна с ключа", "Удерживайте кнопку закрытия на брелоке, чтобы поднять все стёкла", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BB301", "3BB300", true, "21B3"),
        TweakSpec("blink_hazards_lock", "Мигание при закрытии", "Аварийка мигает, когда вы закрываете или открываете машину", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BA101", "3BA100", true, "21A1"),
        TweakSpec("beep_lock_unlock", "Звук при закрытии", "Короткий писк при постановке на охрану и снятии с неё", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BA201", "3BA200", true, "21A2"),
        TweakSpec("eco_indicator", "Подсказка ECO на приборке", "Показывает зелёный значок ECO при экономной езде", "0x7C0", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BC101", "3BC100", true, "21C1"),
        TweakSpec("ac_eco_mode", "Эко-режим кондиционера", "Кондиционер работает мягче, чтобы экономить топливо", "0x7C4", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BD101", "3BD100", true, "21D1"),
        TweakSpec("rear_seatbelt_chime", "Пищалка задних ремней", "Звуковое напоминание, если сзади не пристегнулись", "0x7C0", "ISO 15765-4 CAN (ATSP6) · KWP 21/3B", true, "3BC401", "3BC400", true, "21C4"),
        TweakSpec("turn_signal_3", "Поворотник: 3 мигания", "Заводской режим при лёгком касании рычага", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 3B", false, "3BA503", null, true, "21A5"),
        TweakSpec("turn_signal_4", "Поворотник: 4 мигания", "4 мигания при лёгком касании рычага", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 3B", false, "3BA504", null, true, "21A5"),
        TweakSpec("turn_signal_5", "Поворотник: 5 миганий", "Удобно для перестроения в городе — мигает 5 раз", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 3B", false, "3BA505", null, true, "21A5"),
        TweakSpec("turn_signal_6", "Поворотник: 6 миганий", "6 миганий при лёгком касании рычага", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 3B", false, "3BA506", null, true, "21A5"),
        TweakSpec("turn_signal_7", "Поворотник: 7 миганий", "Увеличенное время сигнала при перестроении", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 3B", false, "3BA507", null, true, "21A5"),
        TweakSpec("turn_signal_9", "Поворотник: 9 миганий", "9 миганий при лёгком касании рычага", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 3B", false, "3BA509", null, true, "21A5"),
        TweakSpec("turn_signal_11", "Поворотник: 11 миганий", "Максимум (11 миганий) при лёгком касании рычага", "0x750", "ISO 15765-4 CAN (ATSP6) · KWP 3B", false, "3BA50B", null, true, "21A5"),
        TweakSpec("abs_inspection", "Сервисный режим ABS", "Только для СТО: проверка тормозов и ABS на стенде", "0x7B0", "ISO 15765-4 CAN (ATSP6) · UDS 10 03", false, "31010200", null, false, null, "1003"),
        TweakSpec("atf_reset", "Сброс счётчика масла CVT", "Нажимать только после замены масла в вариаторе", "0x7E1", "ISO 15765-4 CAN (ATSP6) · UDS 31 01", false, "31010F00", null, false, null, "1003")
    )

    @Volatile
    var ALL: List<TweakSpec> = fallbackTweaks
        private set

    fun loadFromAssets(context: Context) {
        runCatching {
            context.assets.open("tweaks.json").bufferedReader(Charsets.UTF_8).use { reader ->
                val parsed = parseJson(reader.readText())
                if (parsed.isNotEmpty()) {
                    ALL = parsed
                }
            }
        }
    }

    fun parseJson(json: String): List<TweakSpec> {
        val listType = object : TypeToken<List<TweakJsonItem>>() {}.type
        val items: List<TweakJsonItem> = Gson().fromJson(json, listType) ?: return emptyList()
        return items.map { item ->
            TweakSpec(
                id = item.id,
                title = item.title,
                description = item.description,
                ecu = item.ecu,
                protocol = item.protocol,
                isSwitch = item.kind == "switch",
                writeOnCmd = item.writeOnCmd,
                writeOffCmd = item.writeOffCmd,
                requiresVLinker = item.requiresVLinker,
                readCmd = item.readCmd,
                sessionCmd = item.sessionCmd
            )
        }
    }

    fun isVLinkerAdapter(deviceName: String?): Boolean {
        if (deviceName.isNullOrBlank()) return false
        val lower = deviceName.lowercase(Locale.US)
        return lower.contains("vlinker") || lower.contains("vgate")
    }

    fun parseReadByte(readCmd: String, rawReply: String): String? {
        if (readCmd.length < 4 || ElmParser.isNegative(rawReply)) return null
        val pid = readCmd.drop(2).take(2).uppercase(Locale.US)
        val expectedService = (readCmd.take(2).toIntOrNull(16)?.plus(0x40))
            ?.toString(16)?.uppercase(Locale.US)?.padStart(2, '0') ?: return null

        val tokens = ElmParser.tokenize(rawReply).map { it.uppercase(Locale.US) }
        for (i in tokens.indices) {
            if (tokens[i] == expectedService && i + 2 < tokens.size && tokens[i + 1] == pid) {
                val valueByte = tokens[i + 2].toIntOrNull(16) ?: continue
                return valueByte.toString(16).uppercase(Locale.US).padStart(2, '0')
            }
            if (tokens[i] == "$expectedService$pid" && i + 1 < tokens.size) {
                val valueByte = tokens[i + 1].toIntOrNull(16) ?: continue
                return valueByte.toString(16).uppercase(Locale.US).padStart(2, '0')
            }
        }
        return null
    }

    private fun isWriteNegative(raw: String): Boolean {
        return raw.isBlank() ||
            raw.contains("7F", ignoreCase = true) ||
            ElmParser.isNegative(raw)
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
        val targetByte = cmd.takeLast(2).uppercase(Locale.US)

        if (!isSim && spec.requiresVLinker && !isVLinker) {
            val msg = "Недоступно: требуется Vgate vLinker FD+ BT4.0"
            return TweakResult(false, msg, "[$ts] BLOCKED | ${spec.ecu} | $header | TX: $cmd | $msg")
        }

        if (isSim || sendFn == null) {
            val posResp = "7B${cmd.drop(2).take(2)}"
            val modeTag = if (isSim) "SIM" else "OFFLINE"
            val beforeSim = if (spec.readCmd != null) (if (enabled) "00" else "01") else null
            val afterSim = if (spec.readCmd != null) targetByte else null
            val diff = if (beforeSim != null && afterSim != null) " (было $beforeSim → стало $afterSim)" else ""
            val logLine = "[$ts] $modeTag | ${spec.protocol} | $header | TX: $cmd | RX: $posResp$diff"
            return TweakResult(true, "${spec.title}: выполнено$diff", logLine, beforeSim, afterSim)
        }

        var lastErr: Exception? = null
        repeat(2) { attempt ->
            try {
                sendFn(header)
                if (!spec.sessionCmd.isNullOrBlank()) {
                    sendFn(spec.sessionCmd)
                }
                val beforeVal = spec.readCmd?.let { rCmd ->
                    parseReadByte(rCmd, sendFn(rCmd).trim())
                }

                val raw = sendFn(cmd).trim()
                val writeOk = !isWriteNegative(raw)
                if (!writeOk) {
                    if (attempt == 1) {
                        val logLine = "[$ts] FAIL | ${spec.protocol} | $header | TX: $cmd | RX: $raw"
                        return TweakResult(false, "${spec.title}: ошибка ($raw)", logLine, beforeVal, null)
                    }
                    kotlinx.coroutines.delay(250)
                    return@repeat
                }

                val afterVal = spec.readCmd?.let { rCmd ->
                    parseReadByte(rCmd, sendFn(rCmd).trim())
                }
                val verified = spec.readCmd == null || afterVal == null || afterVal == targetByte
                val diff = if (afterVal != null) " (было ${beforeVal ?: "?"} → стало $afterVal)" else ""

                if (verified) {
                    val logLine = "[$ts] OK | ${spec.protocol} | $header | TX: $cmd | RX: $raw$diff"
                    return TweakResult(true, "${spec.title}: выполнено$diff", logLine, beforeVal, afterVal)
                }

                if (attempt == 1) {
                    val failDiff = " (было ${beforeVal ?: "?"} → осталось $afterVal, ожидалось $targetByte)"
                    val logLine = "[$ts] VERIFY_FAIL | ${spec.protocol} | $header | TX: $cmd | RX: $raw$failDiff"
                    return TweakResult(false, "${spec.title}: не применилось$failDiff", logLine, beforeVal, afterVal)
                }
            } catch (e: Exception) {
                lastErr = e
            }
            kotlinx.coroutines.delay(250)
        }
        val msg = lastErr?.message ?: "Нет ответа от ECU"
        val logLine = "[$ts] EXC | ${spec.protocol} | $header | TX: $cmd | ERR: $msg"
        return TweakResult(false, "${spec.title}: ошибка ($msg)", logLine)
    }
}
