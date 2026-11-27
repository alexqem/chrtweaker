package com.alexqem.chr.core.model

import android.content.Context
import com.alexqem.chr.core.common.VehicleConstants
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.util.Locale

data class VehicleProfile(
    val id: String,
    val label: String,
    val shortLabel: String,
    val verified: Boolean,
    val cvtProbe: Boolean,
    val coldStartAnalysis: Boolean,
    val vinPrefixes: List<String> = emptyList(),
    val calIdPrefixes: List<String> = emptyList(),
    val idleMinRpm: Double = VehicleConstants.IDLE_RPM_MIN,
    val idleMaxRpm: Double = VehicleConstants.IDLE_RPM_MAX,
    val thermostatMinC: Double = VehicleConstants.THERMOSTAT_OPEN_MIN_C,
    val thermostatMaxC: Double = VehicleConstants.THERMOSTAT_OPEN_MAX_C
)

object VehicleProfileRegistry {
    const val AUTO_PROFILE_ID = "auto"
    const val DEFAULT_PROFILE_ID = "8nrfts"

    private data class ProfilesJsonRoot(
        @SerializedName("base_norms") val baseNorms: Map<String, Any>? = null,
        @SerializedName("profiles") val profiles: List<ProfileJsonItem> = emptyList()
    )

    private data class ProfileJsonItem(
        @SerializedName("id") val id: String,
        @SerializedName("label") val label: String,
        @SerializedName("short") val shortLabel: String,
        @SerializedName("verified") val verified: Boolean = false,
        @SerializedName("cvt_probe") val cvtProbe: Boolean = true,
        @SerializedName("cold_start_analysis") val coldStartAnalysis: Boolean = true,
        @SerializedName("vin_prefixes") val vinPrefixes: List<String> = emptyList(),
        @SerializedName("calid_prefixes") val calIdPrefixes: List<String> = emptyList(),
        @SerializedName("norms") val norms: Map<String, List<Double>>? = null
    )

    private val fallbackProfiles = listOf(
        VehicleProfile(
            id = "8nrfts",
            label = "Toyota C-HR NGX10/NGX50 · 1.2T 8NR-FTS · K114 CVT",
            shortLabel = "C-HR NGX10 · 8NR-FTS 1.2T",
            verified = true,
            cvtProbe = true,
            coldStartAnalysis = true,
            vinPrefixes = listOf("NGX10", "NGX50", "JTDKB"),
            calIdPrefixes = listOf("8966310", "89663F4")
        ),
        VehicleProfile(
            id = "2zrfe",
            label = "Toyota 1.8 NA 2ZR-FE (Corolla / Auris, CVT/MT)",
            shortLabel = "Toyota · 1.8 2ZR-FE",
            verified = false,
            cvtProbe = true,
            coldStartAnalysis = true,
            vinPrefixes = listOf("ZRE15", "ZRE17", "ZRE18", "ZRE21"),
            calIdPrefixes = listOf("8966302", "8966312"),
            idleMinRpm = 620.0,
            idleMaxRpm = 720.0,
            thermostatMinC = 80.0,
            thermostatMaxC = 86.0
        ),
        VehicleProfile(
            id = "2zrfxe",
            label = "Toyota 1.8 Hybrid 2ZR-FXE (C-HR ZYX10 / Corolla Hybrid / Prius)",
            shortLabel = "Toyota · 1.8 Hybrid 2ZR-FXE",
            verified = false,
            cvtProbe = true,
            coldStartAnalysis = false,
            vinPrefixes = listOf("ZYX10", "ZYX11", "ZVW30", "ZVW50", "ZWE21"),
            calIdPrefixes = listOf("8966347")
        ),
        VehicleProfile(
            id = "tnga_20",
            label = "Toyota 2.0 Dynamic Force M20A-FKS/FXS (TNGA 2018+)",
            shortLabel = "Toyota · 2.0 M20A TNGA",
            verified = false,
            cvtProbe = true,
            coldStartAnalysis = true,
            vinPrefixes = listOf("MXAA", "MXGA", "AXAA", "AXAH", "MZEA", "MAXA"),
            calIdPrefixes = listOf("8966306", "8966342"),
            idleMinRpm = 600.0,
            idleMaxRpm = 750.0,
            thermostatMinC = 82.0,
            thermostatMaxC = 88.0
        ),
        VehicleProfile(
            id = "generic",
            label = "Any OBD-II gasoline car — standard Mode-01 telemetry only",
            shortLabel = "OBD-II · Generic",
            verified = false,
            cvtProbe = false,
            coldStartAnalysis = true
        )
    )

    @Volatile
    private var cachedProfiles: List<VehicleProfile> = fallbackProfiles

    fun loadFromAssets(context: Context) {
        runCatching {
            context.assets.open("profiles.json").bufferedReader(Charsets.UTF_8).use { reader ->
                val parsed = parseJson(reader.readText())
                if (parsed.isNotEmpty()) {
                    cachedProfiles = parsed
                }
            }
        }
    }

    fun parseJson(json: String): List<VehicleProfile> {
        val root = Gson().fromJson(json, ProfilesJsonRoot::class.java) ?: return emptyList()
        val baseIdle = extractRange(root.baseNorms?.get("idle_rpm_cvt"))
            ?: (VehicleConstants.IDLE_RPM_MIN to VehicleConstants.IDLE_RPM_MAX)
        val baseTherm = extractRange(root.baseNorms?.get("thermostat_open_c"))
            ?: (VehicleConstants.THERMOSTAT_OPEN_MIN_C to VehicleConstants.THERMOSTAT_OPEN_MAX_C)

        return root.profiles.map { item ->
            val idle = item.norms?.get("idle_rpm_cvt")?.takeIf { it.size >= 2 }
                ?.let { it[0] to it[1] } ?: baseIdle
            val therm = item.norms?.get("thermostat_open_c")?.takeIf { it.size >= 2 }
                ?.let { it[0] to it[1] } ?: baseTherm
            VehicleProfile(
                id = item.id,
                label = item.label,
                shortLabel = item.shortLabel,
                verified = item.verified,
                cvtProbe = item.cvtProbe,
                coldStartAnalysis = item.coldStartAnalysis,
                vinPrefixes = item.vinPrefixes,
                calIdPrefixes = item.calIdPrefixes,
                idleMinRpm = idle.first,
                idleMaxRpm = idle.second,
                thermostatMinC = therm.first,
                thermostatMaxC = therm.second
            )
        }
    }

    private fun extractRange(raw: Any?): Pair<Double, Double>? {
        val list = raw as? List<*> ?: return null
        if (list.size < 2) return null
        val a = (list[0] as? Number)?.toDouble() ?: return null
        val b = (list[1] as? Number)?.toDouble() ?: return null
        return a to b
    }

    fun all(): List<VehicleProfile> = cachedProfiles

    fun byId(id: String?): VehicleProfile {
        val target = id?.trim()?.lowercase(Locale.US)
        return cachedProfiles.firstOrNull { it.id == target }
            ?: cachedProfiles.firstOrNull { it.id == DEFAULT_PROFILE_ID }
            ?: fallbackProfiles.first()
    }

    fun detectFromIdentity(vin: String?, calId: String?): VehicleProfile? {
        val normVin = vin?.trim()?.uppercase(Locale.US).orEmpty()
        val normCal = calId?.trim()?.uppercase(Locale.US).orEmpty()
        if (normVin.isEmpty() && normCal.isEmpty()) return null

        for (profile in cachedProfiles) {
            if (profile.id == "generic") continue
            val vinMatch = profile.vinPrefixes.any { prefix ->
                normVin.isNotEmpty() && (normVin.startsWith(prefix.uppercase(Locale.US)) || normVin.contains(prefix.uppercase(Locale.US)))
            }
            val calMatch = profile.calIdPrefixes.any { prefix ->
                normCal.isNotEmpty() && normCal.startsWith(prefix.uppercase(Locale.US))
            }
            if (vinMatch || calMatch) return profile
        }
        return byId("generic")
    }
}
