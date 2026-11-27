package com.alexqem.chr.ui

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexqem.chr.ChrApp
import com.alexqem.chr.core.ble.BleScanner
import com.alexqem.chr.core.ble.DiscoveredBleDevice
import com.alexqem.chr.core.database.LtftTrendCalculator
import com.alexqem.chr.core.storage.SessionFileManager
import com.alexqem.chr.service.LiveSessionState
import com.alexqem.chr.service.ObdLoggingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.lang.ref.WeakReference

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as ChrApp).database
    private val fileManager = SessionFileManager(application)
    private val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bleScanner = BleScanner(bluetoothManager?.adapter)
    private val prefs = application.getSharedPreferences("chr_prefs", Context.MODE_PRIVATE)

    private var loggingServiceRef: WeakReference<ObdLoggingService>? = null
    private val _isServiceBound = MutableStateFlow(false)

    private val _sessionState = MutableStateFlow(LiveSessionState())
    val sessionState: StateFlow<LiveSessionState> = _sessionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBleDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _includeAllBle = MutableStateFlow(false)
    val includeAllBle: StateFlow<Boolean> = _includeAllBle.asStateFlow()

    private val _selectedDevice = MutableStateFlow<DiscoveredBleDevice?>(null)
    val selectedDevice: StateFlow<DiscoveredBleDevice?> = _selectedDevice.asStateFlow()

    private val _useSimulator = MutableStateFlow(false)
    val useSimulator: StateFlow<Boolean> = _useSimulator.asStateFlow()

    private val _trendReport = MutableStateFlow<String?>(null)
    val trendReport: StateFlow<String?> = _trendReport.asStateFlow()

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    private val _sampleHistory = MutableStateFlow<List<com.alexqem.chr.core.model.SensorSample>>(emptyList())
    val sampleHistory: StateFlow<List<com.alexqem.chr.core.model.SensorSample>> = _sampleHistory.asStateFlow()

    private val _tweakStatus = MutableStateFlow<Pair<Boolean, String>?>(null)
    val tweakStatus: StateFlow<Pair<Boolean, String>?> = _tweakStatus.asStateFlow()

    private val _tweakLogs = MutableStateFlow<List<String>>(emptyList())
    val tweakLogs: StateFlow<List<String>> = _tweakLogs.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ObdLoggingService.LocalBinder
            val s = binder.getService()
            loggingServiceRef = WeakReference(s)
            _isServiceBound.value = true

            viewModelScope.launch {
                s.sessionState.collect {
                    _sessionState.value = it
                    val sample = it.lastSample
                    if (sample != null) {
                        val current = _sampleHistory.value
                        if (current.lastOrNull()?.ts != sample.ts) {
                            _sampleHistory.value = (current + sample).takeLast(300)
                        }
                    } else if (!it.isRunning && _sampleHistory.value.isNotEmpty()) {
                        // Keep history after stop until next session starts
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            loggingServiceRef = null
            _isServiceBound.value = false
        }
    }

    init {
        val intent = Intent(application, ObdLoggingService::class.java)
        application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        val savedAddr = prefs.getString("last_device_addr", null)
        val savedName = prefs.getString("last_device_name", null)
        if (!savedAddr.isNullOrBlank() && !savedName.isNullOrBlank()) {
            val dev = bluetoothManager?.adapter?.getRemoteDevice(savedAddr)
            if (dev != null) {
                _selectedDevice.value = DiscoveredBleDevice(dev, savedName, savedAddr, 0, 2)
            }
        }
    }

    fun setUseSimulator(value: Boolean) {
        _useSimulator.value = value
    }

    fun selectDevice(device: DiscoveredBleDevice) {
        _selectedDevice.value = device
        prefs.edit()
            .putString("last_device_addr", device.address)
            .putString("last_device_name", device.name)
            .apply()
    }

    fun toggleIncludeAllBle(value: Boolean) {
        _includeAllBle.value = value
        startBleScan(force = true)
    }

    fun startBleScan(force: Boolean = false) {
        if (_isScanning.value && !force) return
        _isScanning.value = true
        _discoveredDevices.value = emptyList()

        viewModelScope.launch {
            try {
                bleScanner.scanDevices(includeAllBle = _includeAllBle.value).collect { list ->
                    _discoveredDevices.value = list
                    if (_selectedDevice.value == null && list.isNotEmpty()) {
                        _selectedDevice.value = list.first()
                    }
                }
            } catch (_: Exception) {
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun startSession() {
        val app = getApplication<Application>()
        val intent = Intent(app, ObdLoggingService::class.java)
        app.startForegroundService(intent)

        loggingServiceRef?.get()?.startLogging(
            deviceAddress = _selectedDevice.value?.address,
            useSimulator = _useSimulator.value
        )
    }

    fun stopSession() {
        loggingServiceRef?.get()?.stopLogging()
    }

    fun loadLtftTrends() {
        viewModelScope.launch {
            val stats = db.sessionDao().getLtftTrends()
            _trendReport.value = LtftTrendCalculator.formatTrendReport(stats)
        }
    }

    fun clearTrendReport() {
        _trendReport.value = null
    }

    fun getLatestCsvFile(): File? {
        return fileManager.listSessions().firstOrNull()
    }

    fun exportLogsToDownloads() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = fileManager.exportLogsTarGzToDownloads()
            _exportStatus.value = result.fold(
                onSuccess = { name -> "Saved to Downloads/$name" },
                onFailure = { err -> "Export failed: ${err.message}" }
            )
        }
    }

    fun clearExportStatus() {
        _exportStatus.value = null
    }

    fun isVLinkerConnected(): Boolean =
        com.alexqem.chr.core.tweaks.EcuTweaks.isVLinkerAdapter(_selectedDevice.value?.name)

    fun runTweak(spec: com.alexqem.chr.core.tweaks.TweakSpec, enabled: Boolean) {
        _tweakStatus.value = true to "Применение: ${spec.title}..."
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = com.alexqem.chr.core.tweaks.EcuTweaks.execute(
                spec = spec,
                enabled = enabled,
                isVLinker = isVLinkerConnected(),
                isSim = _useSimulator.value
            )
            _tweakStatus.value = result.ok to result.message
            _tweakLogs.value = (_tweakLogs.value + result.logLine).takeLast(100)
        }
    }

    fun clearTweakStatus() {
        _tweakStatus.value = null
    }

    override fun onCleared() {
        if (_isServiceBound.value) {
            getApplication<Application>().unbindService(serviceConnection)
        }
        super.onCleared()
    }
}
