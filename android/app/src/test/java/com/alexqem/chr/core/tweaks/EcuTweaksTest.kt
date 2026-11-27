package com.alexqem.chr.core.tweaks

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EcuTweaksTest {

    @Test
    fun parseReadByte_handlesSpacedAndCompactReplies() {
        assertEquals("01", EcuTweaks.parseReadByte("21A7", "61 A7 01"))
        assertEquals("00", EcuTweaks.parseReadByte("21A7", "758 03 61A7 00"))
        assertEquals(null, EcuTweaks.parseReadByte("21A7", "NO DATA"))
    }

    @Test
    fun execute_blocksNonVLinkerOnBodyEcu() = runBlocking {
        val spec = EcuTweaks.ALL.first { it.id == "auto_fold_mirrors" }
        val res = EcuTweaks.execute(spec, enabled = true, isVLinker = false, isSim = false)
        assertFalse(res.ok)
        assertTrue(res.message.contains("vLinker"))
    }

    @Test
    fun execute_verifiesReadBackBeforeAndAfterWrite() = runBlocking {
        val spec = EcuTweaks.ALL.first { it.id == "auto_fold_mirrors" }
        var stateByte = "00"
        val sent = mutableListOf<String>()

        val res = EcuTweaks.execute(spec, enabled = true, isVLinker = true, isSim = false) { cmd ->
            sent.add(cmd)
            when {
                cmd.startsWith("ATSH") -> "OK"
                cmd == "21A7" -> "61 A7 $stateByte"
                cmd == "3BA701" -> {
                    stateByte = "01"
                    "7B A7"
                }
                else -> "?"
            }
        }

        assertTrue(res.ok)
        assertEquals("00", res.beforeValue)
        assertEquals("01", res.afterValue)
        assertTrue(res.message.contains("было 00 → стало 01"))
        assertEquals(listOf("ATSH 750", "21A7", "3BA701", "21A7"), sent)
    }

    @Test
    fun execute_failsWhenReadBackShowsValueDidNotChange() = runBlocking {
        val spec = EcuTweaks.ALL.first { it.id == "auto_fold_mirrors" }
        val res = EcuTweaks.execute(spec, enabled = true, isVLinker = true, isSim = false) { cmd ->
            when {
                cmd.startsWith("ATSH") -> "OK"
                cmd == "21A7" -> "61 A7 00"
                cmd == "3BA701" -> "7B A7"
                else -> "?"
            }
        }

        assertFalse(res.ok)
        assertTrue(res.message.contains("не применилось"))
    }

    @Test
    fun execute_retriesOnStoppedAndSendsUdsSessionCmd() = runBlocking {
        val spec = EcuTweaks.ALL.first { it.id == "abs_inspection" }
        var writeAttempts = 0
        val sent = mutableListOf<String>()
        val res = EcuTweaks.execute(spec, enabled = true, isVLinker = false, isSim = false) { cmd ->
            sent.add(cmd)
            if (cmd.startsWith("ATSH") || cmd == "1003") "OK"
            else {
                writeAttempts++
                "STOPPED"
            }
        }

        assertFalse(res.ok)
        assertEquals(2, writeAttempts)
        assertTrue(sent.contains("1003"))
        assertTrue(res.message.contains("STOPPED"))
    }
}
