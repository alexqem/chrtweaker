package com.alexqem.chr.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.alexqem.chr.core.common.BleConstants
import com.alexqem.chr.core.common.ElmConstants
import com.alexqem.chr.core.elm.ElmConnection
import com.alexqem.chr.core.elm.ElmException
import com.alexqem.chr.core.elm.ElmParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream

@SuppressLint("MissingPermission")
class BleGattManager(
    private val context: Context,
    private val device: BluetoothDevice
) : ElmConnection {

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private val connectDeferred = CompletableDeferred<Boolean>()

    private val incomingBuffer = ByteArrayOutputStream()
    private val responseChannel = Channel<String>(Channel.CONFLATED)
    private val writeMutex = Mutex()
    private var isConnected = false

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true
                g.requestMtu(BleConstants.PREFERRED_MTU)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false
                if (!connectDeferred.isCompleted) {
                    try { g.close() } catch (_: Exception) {}
                    connectDeferred.complete(false)
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val (wChar, nChar) = findObdCharacteristics(g)
                if (wChar != null && nChar != null) {
                    writeChar = wChar
                    val descriptorWritten = enableNotification(g, nChar)
                    if (!descriptorWritten && !connectDeferred.isCompleted) {
                        connectDeferred.complete(true)
                    }
                    return
                }
            }
            if (!connectDeferred.isCompleted) {
                connectDeferred.complete(false)
            }
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (!connectDeferred.isCompleted) {
                connectDeferred.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingBytes(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            characteristic.value?.let { handleIncomingBytes(it) }
        }
    }

    internal fun handleIncomingBytes(bytes: ByteArray) {
        synchronized(incomingBuffer) {
            incomingBuffer.write(bytes)
            val all = incomingBuffer.toByteArray()
            if (all.contains(BleConstants.PROMPT_BYTE)) {
                val fullResponse = String(all, Charsets.US_ASCII)
                incomingBuffer.reset()
                responseChannel.trySend(fullResponse)
            }
        }
    }

    private fun findObdCharacteristics(g: BluetoothGatt): Pair<BluetoothGattCharacteristic?, BluetoothGattCharacteristic?> {
        // 1. Exact FFF0 / FFF1 (or FFF2 write + FFF1 notify)
        g.getService(BleConstants.SERVICE_UUID)?.let { svc ->
            val fff1 = svc.getCharacteristic(BleConstants.CHAR_UUID)
            val writable = svc.characteristics.firstOrNull { isWritable(it) && it != fff1 } ?: fff1?.takeIf { isWritable(it) }
            val notifiable = fff1?.takeIf { isNotifiable(it) } ?: svc.characteristics.firstOrNull { isNotifiable(it) }
            if (writable != null && notifiable != null) return writable to notifiable
        }

        // 2. Fallback: any custom service (skip 1800 Generic Access / 1801 Generic Attribute)
        for (svc in g.services) {
            val uuidStr = svc.uuid.toString().lowercase()
            if (uuidStr.startsWith("00001800") || uuidStr.startsWith("00001801")) continue
            val writable = svc.characteristics.firstOrNull { isWritable(it) }
            val notifiable = svc.characteristics.firstOrNull { isNotifiable(it) }
            if (writable != null && notifiable != null) {
                return writable to notifiable
            }
        }
        return null to null
    }

    private fun isWritable(c: BluetoothGattCharacteristic): Boolean =
        (c.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0

    private fun isNotifiable(c: BluetoothGattCharacteristic): Boolean =
        (c.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0

    internal fun enableNotification(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Boolean {
        g.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(BleConstants.CCCD_UUID) ?: return false
        val value = if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(descriptor, value) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            g.writeDescriptor(descriptor)
        }
    }

    suspend fun connect(timeoutMs: Long = BleConstants.CONNECT_TIMEOUT_MS): Boolean {
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        return try {
            withTimeout(timeoutMs) {
                connectDeferred.await()
            }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun send(cmd: String, settleWaitMs: Long, timeoutMs: Long): String = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val char = writeChar ?: throw ElmException("BLE characteristic not ready")
            val currentGatt = gatt ?: throw ElmException("BLE Gatt not connected")

            synchronized(incomingBuffer) { incomingBuffer.reset() }

            val payload = (cmd + ElmConstants.CR).toByteArray(Charsets.US_ASCII)
            val writeType = if ((char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                currentGatt.writeCharacteristic(char, payload, writeType)
            } else {
                @Suppress("DEPRECATION")
                char.value = payload
                @Suppress("DEPRECATION")
                char.writeType = writeType
                @Suppress("DEPRECATION")
                currentGatt.writeCharacteristic(char)
            }

            if (settleWaitMs > 0) delay(settleWaitMs)

            val raw = try {
                withTimeout(timeoutMs) {
                    responseChannel.receive()
                }
            } catch (e: Exception) {
                throw ElmException("Timeout waiting for reply to '$cmd'", e)
            }

            ElmParser.cleanReply(cmd, raw)
        }
    }

    override fun isConnected(): Boolean = isConnected

    override fun close() {
        isConnected = false
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {}
        gatt = null
    }
}
