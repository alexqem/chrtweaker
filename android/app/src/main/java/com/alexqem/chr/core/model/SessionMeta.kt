package com.alexqem.chr.core.model

import com.alexqem.chr.core.common.SessionConstants
import com.alexqem.chr.core.common.VehicleConstants
import com.google.gson.annotations.SerializedName

data class SessionMeta(
    @SerializedName("car") val car: CarProfile = CarProfile(),
    @SerializedName("car_id") val carId: CarIdentity = CarIdentity(),
    @SerializedName("mode") val mode: String = "cold",
    @SerializedName("sim") val sim: Boolean = false,
    @SerializedName("started_at") val startedAt: String = "",
    @SerializedName("start_coolant_c") val startCoolantC: Double? = null,
    @SerializedName("thresholds") val thresholds: Map<String, Double> = mapOf(
        "cold_max_c" to VehicleConstants.COLD_START_MAX_COOLANT_C,
        "warm_min_c" to VehicleConstants.WARMED_UP_MIN_COOLANT_C,
        "sample_interval_s" to (SessionConstants.SAMPLE_INTERVAL_MS / 1000.0)
    ),
    @SerializedName("custom_pids") val customPids: Map<String, String> = mapOf(
        "cvt_temp" to "01B4/21D9/2182 via functional 7DF"
    ),
    @SerializedName("ecu_versions") val ecuVersions: Map<String, String> = emptyMap(),
    @SerializedName("warm_at_s") val warmAtS: Double? = null,
    @SerializedName("completed") val completed: Boolean = false,
    @SerializedName("dtc") val dtc: Map<String, List<String>> = emptyMap(),
    @SerializedName("rows") val rows: Int = 0,
    @SerializedName("finished_at") val finishedAt: String? = null,
    @SerializedName("aborted") val aborted: String? = null
)
