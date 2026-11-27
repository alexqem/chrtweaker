package com.alexqem.chr.core.elm

import android.util.Log
import com.alexqem.chr.core.common.ElmConstants
import com.alexqem.chr.core.model.CarIdentity
import com.alexqem.chr.core.model.VehicleProfileRegistry
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
            val hexCompact = hexJoined.replace(" ", "")

            if (hexCompact.contains(marker, ignoreCase = true)) {
                val temp = when (cmd) {
                    "01B4" -> ElmParser.findPayloadBytes(tokens, "41", "B4", 2)?.let {
                        Sensors.decode("cvt_temp", it)
                    }
                    "21D9" -> ElmParser.findPayloadBytes(tokens, "61", "D9", 4)?.let {
                        ((it[2] shl 8) or it[3]) / 256.0 - 40.0
                    }
                    "2182" -> ElmParser.findPayloadBytes(tokens, "61", "82", 2)?.let {
                        Sensors.decode("cvt_temp", it)
                    }
                    "221627" -> {
                        val idx = tokens.indexOfFirst { it.equals("62", ignoreCase = true) }
                        if (idx != -1 && idx + 4 < tokens.size &&
                            tokens[idx + 1].equals("16", ignoreCase = true) &&
                            tokens[idx + 2].equals("27", ignoreCase = true)
                        ) {
                            val a = tokens[idx + 3].toIntOrNull(16)
                            val b = tokens[idx + 4].toIntOrNull(16)
                            if (a != null && b != null) ((a shl 8) or b) / 256.0 - 40.0 else null
                        } else null
                    }
                    else -> null
                }
                return Pair(temp, hexJoined)
            }
        }
        return Pair(null, null)
    }

    data class CapabilityProbeResult(
        val supported: Map<String, String>,
        val ecuVersions: Map<String, String>,
        val logs: List<String>
    )

    fun parseEcuAsciiReply(raw: String?, vararg headerBytes: String): String? {
        if (raw.isNullOrBlank() || ElmParser.isNegative(raw)) return null
        val tokens = ElmParser.tokenize(raw).map { it.uppercase(java.util.Locale.US) }
        val hLen = headerBytes.size
        var matchIdx = -1
        for (i in 0..(tokens.size - hLen)) {
            if ((0 until hLen).all { k -> tokens[i + k] == headerBytes[k].uppercase(java.util.Locale.US) }) {
                matchIdx = i + hLen
                break
            }
        }
        val compact = headerBytes.joinToString("").uppercase(java.util.Locale.US)
        if (matchIdx == -1) {
            val cIdx = tokens.indexOfFirst { it == compact }
            if (cIdx != -1) matchIdx = cIdx + 1
        }
        if (matchIdx == -1 || matchIdx >= tokens.size) return null

        val bytes = mutableListOf<Byte>()
        for (k in matchIdx until tokens.size) {
            val b = tokens[k].toIntOrNull(16) ?: continue
            if (b in 0x20..0x7E) bytes.add(b.toByte())
        }
        return String(bytes.toByteArray(), Charsets.US_ASCII).trim().ifBlank { null }
    }

    suspend fun probeExtendedCapabilities(
        conn: ElmConnection,
        physicalSupported: Boolean,
        hybridProfile: Boolean
    ): CapabilityProbeResult {
        if (!physicalSupported) {
            val msg = "Extended ECU probe (7C0/700/7D2) skipped: adapter lacks physical ATSH"
            Log.i("ChrProbe", msg)
            return CapabilityProbeResult(emptyMap(), emptyMap(), listOf(msg))
        }

        val supported = linkedMapOf<String, String>()
        val ecuVersions = linkedMapOf<String, String>()
        val logs = mutableListOf<String>()

        val ecuAddrs = buildList {
            addAll(listOf("7C0", "700", "750", "7C4", "7B0", "7A0", "780", "730"))
            if (hybridProfile) addAll(listOf("7D2", "7E2"))
        }
        for (hdr in ecuAddrs) {
            try {
                conn.send("ATSH $hdr", timeoutMs = ElmConstants.TIMEOUT_QUICK_MS)
                runCatching { conn.send("3E00", timeoutMs = ElmConstants.TIMEOUT_QUICK_MS) }
                runCatching { conn.send("1001", timeoutMs = ElmConstants.TIMEOUT_QUICK_MS) }
                val r22 = queryRaw(conn, "22F190", timeoutMs = ElmConstants.TIMEOUT_QUICK_MS)
                val ascii22 = parseEcuAsciiReply(r22, "62", "F1", "90")
                if (ascii22 != null) {
                    ecuVersions[hdr] = ascii22
                    val line = "OK $hdr 22F190: $ascii22"
                    Log.i("ChrProbe", line)
                    logs.add(line)
                } else {
                    val r1a = queryRaw(conn, "1A8801", timeoutMs = ElmConstants.TIMEOUT_QUICK_MS)
                    val ascii1a = parseEcuAsciiReply(r1a, "5A", "88", "01")
                    if (ascii1a != null) {
                        ecuVersions[hdr] = ascii1a
                        val line = "OK $hdr 1A8801: $ascii1a"
                        Log.i("ChrProbe", line)
                        logs.add(line)
                    } else {
                        Log.i("ChrProbe", "SILENT $hdr 22F190/1A8801")
                    }
                }
            } catch (e: Exception) {
                Log.w("ChrProbe", "Error reading ECU version $hdr: ${e.message}")
            } finally {
                runCatching { conn.send("ATSH 7DF", timeoutMs = ElmConstants.TIMEOUT_QUICK_MS) }
            }
        }

        val probesByHeader = VehicleProfileRegistry.extendedProbes()
            .filter { !it.hybridOnly || hybridProfile }
            .groupBy { it.header }

        for ((header, cmds) in probesByHeader) {
            try {
                conn.send("ATSH $header", timeoutMs = ElmConstants.TIMEOUT_QUICK_MS)
                for (probe in cmds) {
                    val note = if (probe.verified) "verified" else "platform-twin candidate, not verified on NGX10"
                    val reply = queryRaw(conn, probe.cmd, timeoutMs = ElmConstants.TIMEOUT_QUICK_MS)
                    if (reply != null) {
                        val compact = ElmParser.tokenize(reply).joinToString(" ")
                        supported["$header:${probe.cmd}"] = compact
                        val line = "OK $header ${probe.cmd} (${probe.name}, $note): $compact"
                        Log.i("ChrProbe", line)
                        logs.add(line)
                    } else {
                        Log.i("ChrProbe", "SILENT $header ${probe.cmd} (${probe.name}, $note)")
                    }
                }
            } catch (e: Exception) {
                Log.w("ChrProbe", "Error probing $header: ${e.message}")
            } finally {
                runCatching { conn.send("ATSH 7DF", timeoutMs = ElmConstants.TIMEOUT_QUICK_MS) }
            }
        }
        if (logs.isEmpty()) {
            logs.add("Extended ECU probe: no extra PIDs answered")
        }
        return CapabilityProbeResult(supported, ecuVersions, logs)
    }

    data class MisfireStats(
        val totalAllCylinders: Int,
        val cyl1: Int? = null,
        val underLoad: Int? = null
    )

    fun parseUdsMisfireValue(raw: String?, pidHi: String, pidLo: String): Int? {
        if (raw.isNullOrBlank()) return null
        val tokens = ElmParser.tokenize(raw)
        val idx = tokens.indexOfFirst { it.equals("62", ignoreCase = true) }
        if (idx == -1 || idx + 3 >= tokens.size) return null
        if (!tokens[idx + 1].equals(pidHi, ignoreCase = true) ||
            !tokens[idx + 2].equals(pidLo, ignoreCase = true)
        ) return null
        val a = tokens[idx + 3].toIntOrNull(16) ?: return null
        val b = if (idx + 4 < tokens.size && tokens[idx + 4].length == 2) {
            tokens[idx + 4].toIntOrNull(16)
        } else null
        return if (b != null) (a shl 8) or b else a
    }

    suspend fun readMisfireStats(conn: ElmConnection): MisfireStats? {
        return try {
            conn.send("ATSH 700", timeoutMs = ElmConstants.TIMEOUT_QUICK_MS)
            val allRaw = queryRaw(conn, "2210A5", timeoutMs = ElmConstants.TIMEOUT_QUICK_MS)
            val allVal = parseUdsMisfireValue(allRaw, "10", "A5") ?: return null
            val c1Val = parseUdsMisfireValue(queryRaw(conn, "2210A7", timeoutMs = ElmConstants.TIMEOUT_QUICK_MS), "10", "A7")
            val loadVal = parseUdsMisfireValue(queryRaw(conn, "2210AA", timeoutMs = ElmConstants.TIMEOUT_QUICK_MS), "10", "AA")
            MisfireStats(totalAllCylinders = allVal, cyl1 = c1Val, underLoad = loadVal)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { conn.send("ATSH 7DF", timeoutMs = ElmConstants.TIMEOUT_QUICK_MS) }
        }
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
