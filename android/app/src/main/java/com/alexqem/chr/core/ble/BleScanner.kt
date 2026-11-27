package com.alexqem.chr.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import com.alexqem.chr.core.common.BleConstants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class DiscoveredBleDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int,
    val score: Int
)

class BleScanner(
    private val bluetoothAdapter: BluetoothAdapter?
) {
    @SuppressLint("MissingPermission")
    fun scanDevices(includeAllBle: Boolean = false): Flow<List<DiscoveredBleDevice>> = callbackFlow {
        val found = mutableMapOf<String, DiscoveredBleDevice>()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device ?: return
                val name = dev.name ?: result.scanRecord?.deviceName ?: "Unknown"
                val addr = dev.address ?: return

                val uuids = result.scanRecord?.serviceUuids?.map { it.uuid.toString().lowercase() } ?: emptyList()
                val isTargetUuid = uuids.any { u -> BleConstants.TARGET_UUID_FRAGMENTS.any { u.contains(it) } }
                val isObdNamed = BleConstants.ADAPTER_NAME_KEYWORDS.any { name.contains(it, ignoreCase = true) }

                if (isTargetUuid || isObdNamed || includeAllBle) {
                    val score = when {
                        isTargetUuid && isObdNamed -> 3
                        isObdNamed -> 2
                        isTargetUuid -> 1
                        else -> 0
                    }
                    found[addr] = DiscoveredBleDevice(
                        device = dev,
                        name = name,
                        address = addr,
                        rssi = result.rssi,
                        score = score
                    )
                    trySend(found.values.sortedByDescending { it.score })
                }
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("BLE scan failed: error $errorCode"))
            }
        }

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        scanner?.startScan(callback)

        awaitClose {
            try {
                scanner?.stopScan(callback)
            } catch (_: Exception) {}
        }
    }
}
