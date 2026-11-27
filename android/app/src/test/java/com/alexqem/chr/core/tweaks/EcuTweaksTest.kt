package com.alexqem.chr.core.tweaks

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale

class EcuTweaksTest {

    @Before
    fun loadTweaksFromSharedAssets() {
        val jsonFile = File("src/main/assets/tweaks.json").takeIf { it.exists() }
            ?: File("app/src/main/assets/tweaks.json")
        EcuTweaks.loadFromJson(jsonFile.readText(Charsets.UTF_8), Locale("ru"))
    }

    @Test
    fun parseJson_supportsEnglishTitleAndDescription() {
        val jsonFile = File("src/main/assets/tweaks.json").takeIf { it.exists() }
            ?: File("app/src/main/assets/tweaks.json")
        val enTweaks = EcuTweaks.parseJson(jsonFile.readText(Charsets.UTF_8), Locale.ENGLISH)
        assertEquals(18, enTweaks.size)
        val mirrors = enTweaks.first { it.id == "auto_fold_mirrors" }
        assertEquals("Auto-fold mirrors", mirrors.title)
    }

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
        assertEquals(TweakError.GATING_VLINKER_REQUIRED, res.error)
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
        assertTrue(res.logLine.contains("00 -> 01"))
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
        assertEquals(TweakError.VERIFY_FAILED, res.error)
        assertEquals("00", res.afterValue)
        assertEquals("01", res.expectedValue)
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
        assertEquals(TweakError.WRITE_FAILED, res.error)
        assertEquals("STOPPED", res.rawDetail)
    }

    @Test
    fun readAllSwitches_readsOnOffAndMarksUnsupported() = runBlocking {
        val probe = EcuTweaks.readAllSwitches(isVLinker = true, isSim = false) { cmd ->
            when {
                cmd.startsWith("ATSH") -> "OK"
                cmd == "21A7" -> "61 A7 01"
                cmd == "21AC" -> "7F 21 12"
                else -> "61 ${cmd.drop(2)} 00"
            }
        }
        assertEquals(true, probe.states["auto_fold_mirrors"])
        assertEquals(true, probe.supported["auto_fold_mirrors"])
        assertEquals(false, probe.supported["reverse_beep_cluster"])
        assertEquals(false, probe.states["blink_hazards_lock"])
    }
}
