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
import com.alexqem.chr.R
import com.alexqem.chr.core.ble.BleScanner
import com.alexqem.chr.core.ble.DiscoveredBleDevice
import com.alexqem.chr.core.database.LtftTrendCalculator
import com.alexqem.chr.core.database.SessionEntity
import com.alexqem.chr.core.model.SensorSample
import com.alexqem.chr.core.model.VehicleProfile
import com.alexqem.chr.core.model.VehicleProfileRegistry
import com.alexqem.chr.core.storage.SessionFileManager
import com.alexqem.chr.core.tweaks.TweakError
import com.alexqem.chr.core.tweaks.TweakResult
import com.alexqem.chr.core.tweaks.TweakSpec
import com.alexqem.chr.service.LiveSessionState
import com.alexqem.chr.service.ObdLoggingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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

    private val _tweakStatus = MutableStateFlow<TweakUiState?>(null)
    val tweakStatus: StateFlow<TweakUiState?> = _tweakStatus.asStateFlow()

    private val _tweakStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val tweakStates: StateFlow<Map<String, Boolean>> = _tweakStates.asStateFlow()

    private val _tweakLogs = MutableStateFlow<List<String>>(emptyList())
    val tweakLogs: StateFlow<List<String>> = _tweakLogs.asStateFlow()

    private val _selectedProfileId = MutableStateFlow(
        prefs.getString("vehicle_profile_id", VehicleProfileRegistry.AUTO_PROFILE_ID)
            ?: VehicleProfileRegistry.AUTO_PROFILE_ID
    )
    val selectedProfileId: StateFlow<String> = _selectedProfileId.asStateFlow()

    private val _activeProfile = MutableStateFlow(
        resolveActiveProfile(_selectedProfileId.value, null)
    )
    val activeProfile: StateFlow<VehicleProfile> = _activeProfile.asStateFlow()

    val allSessions: StateFlow<List<SessionEntity>> = db.sessionDao().getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedHistorySession = MutableStateFlow<SessionEntity?>(null)
    val selectedHistorySession: StateFlow<SessionEntity?> = _selectedHistorySession.asStateFlow()

    private val _selectedHistorySamples = MutableStateFlow<List<SensorSample>>(emptyList())
    val selectedHistorySamples: StateFlow<List<SensorSample>> = _selectedHistorySamples.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ObdLoggingService.LocalBinder
            val s = binder.getService()
            loggingServiceRef = WeakReference(s)
            _isServiceBound.value = true

            viewModelScope.launch {
                s.sessionState.collect {
                    _sessionState.value = it
                    if (it.isRunning) {
                        _activeProfile.value = resolveActiveProfile(_selectedProfileId.value, it.detectedProfileId)
                    }
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

    fun selectProfile(profileId: String) {
        _selectedProfileId.value = profileId
        prefs.edit().putString("vehicle_profile_id", profileId).apply()
        _activeProfile.value = resolveActiveProfile(profileId, _sessionState.value.detectedProfileId)
    }

    internal fun resolveActiveProfile(selectedId: String, detectedId: String?): VehicleProfile {
        return if (selectedId == VehicleProfileRegistry.AUTO_PROFILE_ID) {
            VehicleProfileRegistry.byId(detectedId ?: VehicleProfileRegistry.DEFAULT_PROFILE_ID)
        } else {
            VehicleProfileRegistry.byId(selectedId)
        }
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
            useSimulator = _useSimulator.value,
            profileId = _selectedProfileId.value
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

    fun selectHistorySession(session: SessionEntity?) {
        _selectedHistorySession.value = session
        if (session == null) {
            _selectedHistorySamples.value = emptyList()
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val rows = db.sampleDao().getSamplesForSession(session.id)
            _selectedHistorySamples.value = rows.map { it.toSensorSample() }
        }
    }

    fun shareSessionBundle(session: SessionEntity, onReady: (File) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val rows = db.sampleDao().getSamplesForSession(session.id)
            val result = fileManager.exportSessionBundle(session, rows)
            result.fold(
                onSuccess = { file ->
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onReady(file)
                    }
                },
                onFailure = { err ->
                    _exportStatus.value = "Export failed: ${err.message}"
                }
            )
        }
    }

    fun isVLinkerConnected(): Boolean =
        _sessionState.value.isVLinkerCapable ||
            com.alexqem.chr.core.tweaks.EcuTweaks.isVLinkerAdapter(_selectedDevice.value?.name)

    fun runTweak(spec: TweakSpec, enabled: Boolean) {
        if (_tweakStatus.value is TweakUiState.Pending) return
        val app = getApplication<Application>()
        _tweakStatus.value = TweakUiState.Pending(app.getString(R.string.tweak_applying, spec.title))
        val prev = _tweakStates.value[spec.id] ?: false
        if (spec.isSwitch) {
            _tweakStates.value = _tweakStates.value + (spec.id to enabled)
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val result = com.alexqem.chr.core.tweaks.EcuTweaks.execute(
                    spec = spec,
                    enabled = enabled,
                    isVLinker = isVLinkerConnected(),
                    isSim = _useSimulator.value,
                    sendFn = { cmd ->
                        val svc = loggingServiceRef?.get()
                            ?: throw ObdLoggingService.ObdNotConnectedException()
                        svc.sendRawCommand(cmd)
                    }
                )
                if (result.ok) {
                    _tweakStatus.value = TweakUiState.Success(formatTweakSuccess(app, spec, result))
                } else {
                    if (spec.isSwitch) {
                        _tweakStates.value = _tweakStates.value + (spec.id to prev)
                    }
                    _tweakStatus.value = TweakUiState.Error(formatTweakError(app, spec, result))
                }
                _tweakLogs.value = (_tweakLogs.value + result.logLine).takeLast(100)
            } catch (e: ObdLoggingService.ObdNotConnectedException) {
                if (spec.isSwitch) {
                    _tweakStates.value = _tweakStates.value + (spec.id to prev)
                }
                _tweakStatus.value = TweakUiState.Error(app.getString(R.string.tweak_service_not_bound))
            }
        }
    }

    private fun formatTweakSuccess(app: Application, spec: TweakSpec, result: TweakResult): String {
        val diff = if (result.afterValue != null) {
            app.getString(R.string.tweak_diff_fmt, result.beforeValue ?: "?", result.afterValue)
        } else {
            ""
        }
        return app.getString(R.string.tweak_done, spec.title, diff)
    }

    private fun formatTweakError(app: Application, spec: TweakSpec, result: TweakResult): String {
        return when (result.error) {
            TweakError.GATING_VLINKER_REQUIRED -> app.getString(R.string.tweak_err_vlinker_required)
            TweakError.NO_ECU_RESPONSE -> app.getString(R.string.tweak_err_failed_fmt, spec.title, app.getString(R.string.tweak_err_no_ecu))
            TweakError.VERIFY_FAILED -> app.getString(
                R.string.tweak_err_verify_fmt,
                spec.title,
                result.beforeValue ?: "?",
                result.afterValue ?: "?",
                result.expectedValue ?: "?"
            )
            TweakError.WRITE_FAILED, TweakError.EXCEPTION, null ->
                app.getString(R.string.tweak_err_failed_fmt, spec.title, result.rawDetail ?: app.getString(R.string.tweak_err_no_ecu))
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

sealed interface TweakUiState {
    data class Pending(val message: String) : TweakUiState
    data class Success(val message: String) : TweakUiState
    data class Error(val message: String) : TweakUiState
}
