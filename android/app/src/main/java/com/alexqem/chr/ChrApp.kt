package com.alexqem.chr

import android.app.Application
import com.alexqem.chr.core.database.ChrDatabase

class ChrApp : Application() {
    val database: ChrDatabase by lazy { ChrDatabase.getDatabase(this) }
}
