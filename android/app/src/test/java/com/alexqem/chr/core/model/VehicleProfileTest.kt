package com.alexqem.chr.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleProfileTest {

    @Test
    fun detectFromIdentity_identifiesChrNgx10AndNgx50ByVinAndCalId() {
        val byVin = VehicleProfileRegistry.detectFromIdentity("NGX10-0012345", null)
        assertEquals("8nrfts", byVin?.id)
        assertTrue(byVin?.cvtProbe == true)

        val awd = VehicleProfileRegistry.detectFromIdentity("NGX50-0009999", null)
        assertEquals("8nrfts", awd?.id)

        val byCal = VehicleProfileRegistry.detectFromIdentity(null, "8966310A1200")
        assertEquals("8nrfts", byCal?.id)
    }

    @Test
    fun detectFromIdentity_identifiesCorollaHybridAndTnga() {
        val corolla = VehicleProfileRegistry.detectFromIdentity("ZRE182-1234567", null)
        assertEquals("2zrfe", corolla?.id)
        assertEquals(620.0, corolla?.idleMinRpm)

        val hybrid = VehicleProfileRegistry.detectFromIdentity("ZYX10-9999999", null)
        assertEquals("2zrfxe", hybrid?.id)
        assertFalse(hybrid?.coldStartAnalysis ?: true)

        val tnga = VehicleProfileRegistry.detectFromIdentity("MXAA54-1234567", null)
        assertEquals("tnga_20", tnga?.id)
        assertEquals(600.0, tnga?.idleMinRpm)
    }

    @Test
    fun detectFromIdentity_fallsBackToGenericForUnknownCar() {
        val unknown = VehicleProfileRegistry.detectFromIdentity("WVWZZZ1KZ8W000001", "03C906022")
        assertEquals("generic", unknown?.id)
        assertFalse(unknown?.cvtProbe ?: true)
    }

    @Test
    fun detectFromIdentity_returnsNullWhenIdentityEmpty() {
        assertNull(VehicleProfileRegistry.detectFromIdentity(null, ""))
    }
}
