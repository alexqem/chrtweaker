package com.alexqem.chr.core.elm

import com.alexqem.chr.core.common.ElmConstants

object ElmParser {
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun cleanReply(cmd: String, reply: String): String {
        var clean = reply.replace("\r", " ").replace("\n", " ").replace(ElmConstants.PROMPT, "").trim()
        if (clean.startsWith(cmd, ignoreCase = true)) {
            clean = clean.substring(cmd.length).trim()
        }
        return clean
    }

    fun tokenize(raw: String): List<String> {
        return raw.replace("\r", " ").replace("\n", " ").trim().split(WHITESPACE_REGEX).filter { it.isNotBlank() }
    }

    fun findPayloadBytes(tokens: List<String>, serviceEcho: String, pid: String, nBytes: Int): List<Int>? {
        for (i in 0 until tokens.size - 1) {
            if (tokens[i].equals(serviceEcho, ignoreCase = true) && tokens[i + 1].equals(pid, ignoreCase = true)) {
                val start = i + 2
                if (start + nBytes <= tokens.size) {
                    val bytes = ArrayList<Int>(nBytes)
                    for (k in 0 until nBytes) {
                        val byteVal = tokens[start + k].toIntOrNull(16) ?: return null
                        bytes.add(byteVal)
                    }
                    return bytes
                }
            }
        }
        return null
    }

    fun isNegative(reply: String): Boolean {
        if (reply.isBlank()) return true
        return ElmConstants.NEGATIVE_MARKERS.any { reply.contains(it, ignoreCase = true) }
    }
}
