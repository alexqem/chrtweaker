package com.alexqem.chr.core.elm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmParserTest {

    @Test
    fun cleanReply_stripsPromptWhitespaceAndCommandEcho() {
        val cleaned = ElmParser.cleanReply("0105", "0105\r\n41 05 7B \r\n>")
        assertEquals("41 05 7B", cleaned)
    }

    @Test
    fun tokenize_splitsMultiLineResponse() {
        val tokens = ElmParser.tokenize("7E8 03 41 0C 0A\r\nF0 >")
        assertEquals(listOf("7E8", "03", "41", "0C", "0A", "F0", ">"), tokens)
    }

    @Test
    fun findPayloadBytes_extractsTwoBytesForRpm() {
        val tokens = ElmParser.tokenize("41 0C 0B B8")
        val bytes = ElmParser.findPayloadBytes(tokens, "41", "0C", 2)
        assertEquals(listOf(0x0B, 0xB8), bytes)
    }

    @Test
    fun findPayloadBytes_returnsNullWhenTruncated() {
        val tokens = ElmParser.tokenize("41 0C 0B")
        assertNull(ElmParser.findPayloadBytes(tokens, "41", "0C", 2))
    }

    @Test
    fun isNegative_detectsStoppedAndCanError() {
        assertTrue(ElmParser.isNegative(""))
        assertTrue(ElmParser.isNegative("NO DATA"))
        assertTrue(ElmParser.isNegative("STOPPED"))
        assertTrue(ElmParser.isNegative("CAN ERROR"))
        assertTrue(ElmParser.isNegative("?"))
        assertFalse(ElmParser.isNegative("41 05 7B"))
    }
}
