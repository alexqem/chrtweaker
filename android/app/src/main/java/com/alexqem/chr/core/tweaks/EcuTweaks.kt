package com.alexqem.chr.core.tweaks

import android.content.Context
import android.util.Log
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

enum class TweakError {
    GATING_VLINKER_REQUIRED,
    NO_ECU_RESPONSE,
    WRITE_FAILED,
    VERIFY_FAILED,
    EXCEPTION
}

data class TweakResult(
    val ok: Boolean,
    val logLine: String,
    val error: TweakError? = null,
    val rawDetail: String? = null,
    val beforeValue: String? = null,
    val afterValue: String? = null,
    val expectedValue: String? = null
)

object EcuTweaks {
    private data class TweakJsonItem(
        @SerializedName("id") val id: String,
        @SerializedName("title") val title: String,
        @SerializedName("title_en") val titleEn: String? = null,
        @SerializedName("description") val description: String,
        @SerializedName("description_en") val descriptionEn: String? = null,
        @SerializedName("ecu") val ecu: String,
        @SerializedName("protocol") val protocol: String,
        @SerializedName("kind") val kind: String,
        @SerializedName("read_cmd") val readCmd: String? = null,
        @SerializedName("session_cmd") val sessionCmd: String? = null,
        @SerializedName("write_on_cmd") val writeOnCmd: String,
        @SerializedName("write_off_cmd") val writeOffCmd: String? = null,
        @SerializedName("requires_vlinker") val requiresVLinker: Boolean = true
    )

    @Volatile
    var ALL: List<TweakSpec> = emptyList()
        private set

    fun loadFromAssets(context: Context) {
        runCatching {
            context.assets.open("tweaks.json").bufferedReader(Charsets.UTF_8).use { reader ->
                loadFromJson(reader.readText())
            }
        }.onFailure { err ->
            Log.e("EcuTweaks", "tweaks.json missing or unparsable — packaging bug", err)
        }
    }

    fun loadFromJson(json: String, locale: Locale = Locale.getDefault()) {
        val parsed = parseJson(json, locale)
        if (parsed.isNotEmpty()) {
            ALL = parsed
        }
    }

    fun parseJson(json: String, locale: Locale = Locale.getDefault()): List<TweakSpec> {
        val useEn = locale.language.equals("en", ignoreCase = true)
        val listType = object : TypeToken<List<TweakJsonItem>>() {}.type
        val items: List<TweakJsonItem> = Gson().fromJson(json, listType) ?: return emptyList()
        return items.map { item ->
            TweakSpec(
                id = item.id,
                title = if (useEn && !item.titleEn.isNullOrBlank()) item.titleEn else item.title,
                description = if (useEn && !item.descriptionEn.isNullOrBlank()) item.descriptionEn else item.description,
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
            return TweakResult(
                ok = false,
                logLine = "[$ts] BLOCKED | ${spec.ecu} | $header | TX: $cmd | GATING_VLINKER_REQUIRED",
                error = TweakError.GATING_VLINKER_REQUIRED
            )
        }

        if (isSim || sendFn == null) {
            val posResp = "7B${cmd.drop(2).take(2)}"
            val modeTag = if (isSim) "SIM" else "OFFLINE"
            val beforeSim = if (spec.readCmd != null) (if (enabled) "00" else "01") else null
            val afterSim = if (spec.readCmd != null) targetByte else null
            val diff = if (beforeSim != null && afterSim != null) " ($beforeSim -> $afterSim)" else ""
            val logLine = "[$ts] $modeTag | ${spec.protocol} | $header | TX: $cmd | RX: $posResp$diff"
            return TweakResult(ok = true, logLine = logLine, beforeValue = beforeSim, afterValue = afterSim)
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
                        return TweakResult(
                            ok = false,
                            logLine = logLine,
                            error = TweakError.WRITE_FAILED,
                            rawDetail = raw,
                            beforeValue = beforeVal
                        )
                    }
                    kotlinx.coroutines.delay(250)
                    return@repeat
                }

                val afterVal = spec.readCmd?.let { rCmd ->
                    parseReadByte(rCmd, sendFn(rCmd).trim())
                }
                val verified = spec.readCmd == null || afterVal == null || afterVal == targetByte
                val diff = if (afterVal != null) " (${beforeVal ?: "?"} -> $afterVal)" else ""

                if (verified) {
                    val logLine = "[$ts] OK | ${spec.protocol} | $header | TX: $cmd | RX: $raw$diff"
                    return TweakResult(ok = true, logLine = logLine, beforeValue = beforeVal, afterValue = afterVal)
                }

                if (attempt == 1) {
                    val failDiff = " (${beforeVal ?: "?"} -> $afterVal, expected $targetByte)"
                    val logLine = "[$ts] VERIFY_FAIL | ${spec.protocol} | $header | TX: $cmd | RX: $raw$failDiff"
                    return TweakResult(
                        ok = false,
                        logLine = logLine,
                        error = TweakError.VERIFY_FAILED,
                        beforeValue = beforeVal,
                        afterValue = afterVal,
                        expectedValue = targetByte
                    )
                }
            } catch (e: Exception) {
                lastErr = e
            }
            kotlinx.coroutines.delay(250)
        }
        val errDetail = lastErr?.message
        val logLine = "[$ts] EXC | ${spec.protocol} | $header | TX: $cmd | ERR: ${errDetail ?: "NO_ECU_RESPONSE"}"
        return TweakResult(
            ok = false,
            logLine = logLine,
            error = if (errDetail == null) TweakError.NO_ECU_RESPONSE else TweakError.EXCEPTION,
            rawDetail = errDetail
        )
    }
}
