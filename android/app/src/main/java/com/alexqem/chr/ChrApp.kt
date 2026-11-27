package com.alexqem.chr

import android.app.Application
import com.alexqem.chr.core.database.ChrDatabase
import com.alexqem.chr.core.model.VehicleProfileRegistry
import com.alexqem.chr.core.tweaks.EcuTweaks

class ChrApp : Application() {
    val database: ChrDatabase by lazy { ChrDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        VehicleProfileRegistry.loadFromAssets(this)
        EcuTweaks.loadFromAssets(this)
    }
}
