package com.alexqem.chr.core.model

import com.alexqem.chr.core.common.VehicleConstants

data class CarProfile(
    val make: String = VehicleConstants.MAKE,
    val model: String = VehicleConstants.MODEL,
    val body: String = VehicleConstants.BODY,
    val year: Int = VehicleConstants.YEAR,
    val market: String = "JDM (RHD)",
    val engine: String = VehicleConstants.ENGINE,
    val displacementCc: Int = VehicleConstants.DISPLACEMENT_CC,
    val transmission: String = VehicleConstants.TRANSMISSION,
    val protocolHint: String = VehicleConstants.PROTOCOL_HINT
)

data class CarIdentity(
    val vin: String? = null,
    val calid: String? = null,
    val cvn: String? = null,
    val cvnTcm: String? = null,
    val ecuName: String? = null,
    val ecuNameTcm: String? = null
)
