package com.alexqem.chr.core.elm

interface ElmConnection {
    suspend fun send(cmd: String, settleWaitMs: Long = 0L, timeoutMs: Long = 3000L): String
    fun isConnected(): Boolean
    fun close()
}

class ElmException(message: String, cause: Throwable? = null) : Exception(message, cause)
