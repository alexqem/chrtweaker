package com.alexqem.chr.core.model

import com.alexqem.chr.core.database.SampleEntity
import java.util.Locale

data class SensorSample(
    val ts: Double,
    val rpm: Double? = null,
    val speed: Double? = null,
    val coolant: Double? = null,
    val oilTemp: Double? = null,
    val iat: Double? = null,
    val baro: Double? = null,
    val maf: Double? = null,
    val load: Double? = null,
    val stft: Double? = null,
    val ltft: Double? = null,
    val fuelStatus: Double? = null,
    val catalyst: Double? = null,
    val timing: Double? = null,
    val throttle: Double? = null,
    val voltage: Double? = null,
    val runtime: Double? = null,
    val cvtTemp: Double? = null,
    val cvtRaw: String? = null
) {
    fun toCsvRow(): String {
        fun f(v: Double?, digits: Int = 2): String =
            if (v != null && !v.isNaN()) String.format(Locale.US, "%.${digits}f", v) else ""

        return listOf(
            f(ts, 1),
            f(rpm, 1),
            f(speed, 1),
            f(coolant, 1),
            f(oilTemp, 1),
            f(iat, 1),
            f(baro, 1),
            f(maf, 2),
            f(load, 2),
            f(stft, 2),
            f(ltft, 2),
            f(fuelStatus, 0),
            f(catalyst, 1),
            f(timing, 1),
            f(throttle, 2),
            f(voltage, 3),
            f(runtime, 0),
            f(cvtTemp, 1),
            cvtRaw ?: ""
        ).joinToString(",")
    }

    fun toEntity(sessionId: Long): SampleEntity = SampleEntity(
        sessionId = sessionId,
        ts = ts,
        rpm = rpm,
        speed = speed,
        coolant = coolant,
        oilTemp = oilTemp,
        iat = iat,
        baro = baro,
        maf = maf,
        load = load,
        stft = stft,
        ltft = ltft,
        fuelStatus = fuelStatus,
        catalyst = catalyst,
        timing = timing,
        throttle = throttle,
        voltage = voltage,
        runtime = runtime,
        cvtTemp = cvtTemp,
        cvtRaw = cvtRaw
    )

    companion object {
        const val CSV_HEADER = "ts,rpm,speed,coolant,oil_temp,iat,baro,maf,load,stft,ltft,fuel_status,catalyst,timing,throttle,voltage,runtime,cvt_temp,cvt_raw"

        fun fromPollMap(
            ts: Double,
            map: Map<String, Double?>,
            cvtTemp: Double? = null,
            cvtRaw: String? = null
        ): SensorSample = SensorSample(
            ts = ts,
            rpm = map["rpm"],
            speed = map["speed"],
            coolant = map["coolant"],
            oilTemp = map["oil_temp"],
            iat = map["iat"],
            baro = map["baro"],
            maf = map["maf"],
            load = map["load"],
            stft = map["stft"],
            ltft = map["ltft"],
            fuelStatus = map["fuel_status"],
            catalyst = map["catalyst"],
            timing = map["timing"],
            throttle = map["throttle"],
            voltage = map["voltage"],
            runtime = map["runtime"],
            cvtTemp = cvtTemp,
            cvtRaw = cvtRaw
        )
    }
}
