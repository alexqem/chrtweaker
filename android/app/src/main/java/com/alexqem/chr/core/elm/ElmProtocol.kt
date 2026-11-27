package com.alexqem.chr.core.elm

import com.alexqem.chr.core.common.ElmConstants
import com.alexqem.chr.core.model.CarIdentity
import com.alexqem.chr.core.sensors.PidSpec
import com.alexqem.chr.core.sensors.Sensors

object ElmProtocol {

    suspend fun initAdapter(conn: ElmConnection) {
        for ((cmd, waitMs) in ElmConstants.INIT_SEQUENCE) {
            conn.send(cmd, settleWaitMs = waitMs)
        }
    }

    suspend fun verifyCar(conn: ElmConnection) {
        val reply = conn.send(ElmConstants.CMD_VERIFY_CAR, timeoutMs = ElmConstants.TIMEOUT_INIT_PROBE_MS)
        if (reply.isBlank() || reply.contains("UNABLE", ignoreCase = true)) {
            throw ElmException("Adapter answered, but the car did not respond (0100 -> UNABLE). Turn ignition ON.")
        }
    }

    suspend fun queryRaw(conn: ElmConnection, cmd: String, timeoutMs: Long = ElmConstants.TIMEOUT_DEFAULT_MS): String? {
        val r = conn.send(cmd, timeoutMs = timeoutMs)
        return if (ElmParser.isNegative(r)) null else r
    }

    suspend fun queryMode01(conn: ElmConnection, pid: String, nData: Int): List<Int>? {
        val raw = queryRaw(conn, "01$pid") ?: return null
        val tokens = ElmParser.tokenize(raw)
        return ElmParser.findPayloadBytes(tokens, "41", pid, nData)
    }

    suspend fun queryMulti(conn: ElmConnection, specs: List<PidSpec>): Map<String, List<Int>>? {
        val cmd = "01" + specs.joinToString("") { it.pidHex }
        val raw = queryRaw(conn, cmd) ?: return null
        val tokens = ElmParser.tokenize(raw)
        val out = HashMap<String, List<Int>>(specs.size)

        for (spec in specs) {
            val bytes = ElmParser.findPayloadBytes(tokens, "41", spec.pidHex, spec.expectedBytes)
            if (bytes != null) {
                out[spec.name] = bytes
            }
        }
        return if (out.isNotEmpty()) out else null
    }

    suspend fun probeCvt(conn: ElmConnection): Pair<Double?, String?> {
        for ((cmd, marker) in ElmConstants.CVT_CANDIDATES) {
            val r = queryRaw(conn, cmd, timeoutMs = ElmConstants.TIMEOUT_QUICK_MS) ?: continue
            val tokens = ElmParser.tokenize(r)
            val hexJoined = tokens.joinToString(" ")

            if (hexJoined.contains(marker, ignoreCase = true)) {
                val temp = if (cmd == "01B4") {
                    val bytes = ElmParser.findPayloadBytes(tokens, "41", "B4", 2)
                    if (bytes != null) Sensors.decode("cvt_temp", bytes) else null
                } else null
                return Pair(temp, hexJoined)
            }
        }
        return Pair(null, null)
    }

    suspend fun readCarId(conn: ElmConnection): CarIdentity {
        try {
            conn.send(ElmConstants.CMD_HEADERS_ON, settleWaitMs = ElmConstants.DELAY_HEADER_SETTLE_MS)
            val vin = parseIsoTpText(conn.send("0902", timeoutMs = ElmConstants.TIMEOUT_DEFAULT_MS), "4902")
            val calId = parseIsoTpText(conn.send("0904", timeoutMs = ElmConstants.TIMEOUT_DEFAULT_MS), "4904")
            val ecuName = parseIsoTpText(conn.send("090A", timeoutMs = ElmConstants.TIMEOUT_DEFAULT_MS), "490A")
            return CarIdentity(vin = vin, calid = calId, ecuName = ecuName)
        } catch (_: Exception) {
            return CarIdentity()
        } finally {
            conn.send(ElmConstants.CMD_HEADERS_OFF, settleWaitMs = ElmConstants.DELAY_HEADER_SETTLE_MS)
        }
    }

    suspend fun readDtc(conn: ElmConnection): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()
        try {
            conn.send(ElmConstants.CMD_HEADERS_ON, settleWaitMs = ElmConstants.DELAY_HEADER_SETTLE_MS)
            for (m in ElmConstants.DTC_MODES) {
                val r = queryRaw(conn, m, timeoutMs = ElmConstants.TIMEOUT_DEFAULT_MS)
                if (r == null || r.contains("NO DATA", ignoreCase = true)) {
                    result[m] = emptyList()
                    continue
                }
                result[m] = parseDtcReply(r, "4" + m.last())
            }
        } catch (_: Exception) {
        } finally {
            conn.send(ElmConstants.CMD_HEADERS_OFF, settleWaitMs = ElmConstants.DELAY_HEADER_SETTLE_MS)
        }
        return result
    }

    private fun parseIsoTpText(raw: String, marker: String): String? {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        val asciiBytes = mutableListOf<Byte>()
        for (line in lines) {
            val tokens = ElmParser.tokenize(line)
            if (tokens.size < 4) continue
            val idx = tokens.indexOfFirst { it.equals(marker, ignoreCase = true) }
            val start = if (idx != -1) idx + 2 else 3
            for (k in start until tokens.size) {
                val b = tokens[k].toIntOrNull(16) ?: continue
                if (b in 0x20..0x7E) {
                    asciiBytes.add(b.toByte())
                }
            }
        }
        val str = String(asciiBytes.toByteArray(), Charsets.US_ASCII).trim()
        return str.ifBlank { null }
    }

    private fun parseDtcReply(raw: String, marker: String): List<String> {
        val codes = mutableListOf<String>()
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        for (line in lines) {
            val tokens = ElmParser.tokenize(line)
            val markerIdx = tokens.indexOfFirst { it.startsWith(marker, ignoreCase = true) }
            if (markerIdx == -1) continue

            var i = markerIdx + 2
            while (i + 1 < tokens.size) {
                val b0 = tokens[i].toIntOrNull(16) ?: break
                val b1 = tokens[i + 1].toIntOrNull(16) ?: break
                i += 2

                if ((b0 == 0 && b1 == 0) || (b0 == 0xAA && b1 == 0xAA)) continue

                val prefixChar = when ((b0 shr 6) and 0x03) {
                    0 -> 'P'
                    1 -> 'C'
                    2 -> 'B'
                    else -> 'U'
                }
                val d1 = (b0 shr 4) and 0x03
                val d2 = b0 and 0x0F
                val d3 = (b1 shr 4) and 0x0F
                val d4 = b1 and 0x0F
                codes.add(String.format(java.util.Locale.US, "%c%d%X%X%X", prefixChar, d1, d2, d3, d4))
            }
        }
        return codes.distinct()
    }
}
