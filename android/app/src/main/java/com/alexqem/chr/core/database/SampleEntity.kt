package com.alexqem.chr.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "samples",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class SampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val ts: Double,
    val rpm: Double?,
    val speed: Double?,
    val coolant: Double?,
    val oilTemp: Double?,
    val iat: Double?,
    val baro: Double?,
    val maf: Double?,
    val load: Double?,
    val stft: Double?,
    val ltft: Double?,
    val fuelStatus: Double?,
    val catalyst: Double?,
    val timing: Double?,
    val throttle: Double?,
    val voltage: Double?,
    val runtime: Double?,
    val cvtTemp: Double?,
    val cvtRaw: String?
) {
    fun toSensorSample(): com.alexqem.chr.core.model.SensorSample = com.alexqem.chr.core.model.SensorSample(
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
}
