package com.alexqem.chr.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.alexqem.chr.core.ble.BleGattManager
import com.alexqem.chr.core.common.ElmConstants
import com.alexqem.chr.core.common.SessionConstants
import com.alexqem.chr.core.common.VehicleConstants
import com.alexqem.chr.core.database.ChrDatabase
import com.alexqem.chr.core.database.SampleEntity
import com.alexqem.chr.core.database.SessionEntity
import com.alexqem.chr.core.elm.ElmConnection
import com.alexqem.chr.core.elm.ElmProtocol
import com.alexqem.chr.core.model.SensorSample
import com.alexqem.chr.core.model.SessionMeta
import com.alexqem.chr.core.sensors.Sensors
import com.alexqem.chr.core.sim.SimElmConnection
import com.alexqem.chr.core.storage.SessionFileManager
import com.alexqem.chr.ui.MainActivity
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class LiveSessionState(
    val isRunning: Boolean = false,
    val isSim: Boolean = false,
    val mode: String = "cold",
    val isWarmedUp: Boolean = false,
    val warmAtSeconds: Double? = null,
    val lastSample: SensorSample? = null,
    val warmUpRateCPerMin: Double? = null,
    val rowCount: Int = 0,
    val statusMessage: String = "Idle",
    val error: String? = null
)

class ObdLoggingService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loggingJob: Job? = null

    private lateinit var fileManager: SessionFileManager
    private lateinit var database: ChrDatabase
    private var wakeLock: PowerManager.WakeLock? = null

    private val _sessionState = MutableStateFlow(LiveSessionState())
    val sessionState: StateFlow<LiveSessionState> = _sessionState.asStateFlow()

    private val sampleHistoryForRate = ArrayDeque<Pair<Double, Double>>()

    inner class LocalBinder : Binder() {
        fun getService(): ObdLoggingService = this@ObdLoggingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        fileManager = SessionFileManager(this)
        database = ChrDatabase.getDatabase(this)
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChrLogger:ObdLoggingWakeLock")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "OBD-II Capture Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows live vehicle telemetry and logging status"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(statusText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("C-HR Cold Start Logger")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    fun startLogging(deviceAddress: String?, useSimulator: Boolean) {
        if (loggingJob?.isActive == true) return

        wakeLock?.acquire(60 * 60 * 1000L)
        startForeground(NOTIFICATION_ID, buildNotification("Starting OBD-II session..."))

        loggingJob = serviceScope.launch {
            runCaptureSession(deviceAddress, useSimulator)
        }
    }

    fun stopLogging() {
        loggingJob?.cancel()
    }

    private suspend fun runCaptureSession(deviceAddress: String?, useSimulator: Boolean) {
        var conn: ElmConnection? = null
        var csvWriter: BufferedWriter? = null
        var metaFile: File? = null
        var csvFile: File? = null
        var currentMeta: SessionMeta? = null
        var sessionId: Long = 0
        var totalRows = 0
        var warmAtS: Double? = null
        var abortReason: String? = null

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        try {
            _sessionState.value = LiveSessionState(
                isRunning = true,
                isSim = useSimulator,
                statusMessage = if (useSimulator) "Connecting simulator..." else "Connecting to BLE adapter..."
            )

            // Step 1: Connection
            conn = if (useSimulator) {
                SimElmConnection(timeScale = 1.0)
            } else {
                val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter = bluetoothManager.adapter ?: throw IllegalStateException("Bluetooth not available")
                val device = if (!deviceAddress.isNullOrBlank()) {
                    adapter.getRemoteDevice(deviceAddress)
                } else {
                    throw IllegalArgumentException("No BLE device address provided")
                }
                val gattManager = BleGattManager(this@ObdLoggingService, device)
                if (!gattManager.connect()) {
                    throw IllegalStateException("Failed to connect to BLE adapter $deviceAddress")
                }
                gattManager
            }

            // Step 2: Gentle AT init
            _sessionState.value = _sessionState.value.copy(statusMessage = "Initializing ELM protocol...")
            ElmProtocol.initAdapter(conn)

            // Step 3: Verify car answers
            _sessionState.value = _sessionState.value.copy(statusMessage = "Verifying car connection...")
            ElmProtocol.verifyCar(conn)

            // Step 4: Initial coolant probe to determine mode (cold <= 40°C or warm)
            var initialCoolant: Double? = null
            for (attempt in 1..SessionConstants.MAX_INIT_PROBE_ATTEMPTS) {
                val bytes = ElmProtocol.queryMode01(conn, "05", 1)
                if (bytes != null) {
                    initialCoolant = Sensors.decode("coolant", bytes)
                    break
                }
                delay(300)
            }
            val mode = if (initialCoolant != null && initialCoolant <= VehicleConstants.COLD_START_MAX_COOLANT_C) "cold" else "warm"

            // Step 5: Read vehicle identity (Mode 09)
            val carId = ElmProtocol.readCarId(conn)

            // Step 6: Create storage files and DB record
            val files = fileManager.createSessionFiles(mode)
            csvFile = files.first
            metaFile = files.second
            csvWriter = fileManager.openCsvWriter(csvFile)

            currentMeta = SessionMeta(
                carId = carId,
                mode = mode,
                sim = useSimulator,
                startedAt = isoFormat.format(Date()),
                startCoolantC = initialCoolant
            )

            // Write initial meta immediately so session is tracked even if hard-killed
            fileManager.writeMeta(metaFile, currentMeta)

            sessionId = database.sessionDao().insertSession(
                SessionEntity(
                    csvFile = csvFile.name,
                    startedAt = currentMeta.startedAt,
                    finishedAt = null,
                    mode = mode,
                    startCoolantC = initialCoolant,
                    warmAtS = null,
                    rows = 0,
                    aborted = null,
                    metaJson = Gson().toJson(currentMeta)
                )
            )

            _sessionState.value = _sessionState.value.copy(
                mode = mode,
                statusMessage = "Logging ($mode start from ${initialCoolant ?: "—"}°C)"
            )

            // Step 7: Sampling loop
            var sessionTs = 0.0
            var cvtAttempts = 0
            var cvtSupported = true
            var cooldownAfterWarmStartTs: Double? = null

            while (serviceScope.isActive) {
                val loopStart = System.currentTimeMillis()

                // Poll sensors
                val multiResult = ElmProtocol.queryMulti(conn, Sensors.POLL_LIST)
                val sampleMap = HashMap<String, Double?>(Sensors.POLL_LIST.size)

                if (multiResult != null) {
                    for (spec in Sensors.POLL_LIST) {
                        val bytes = multiResult[spec.name]
                        sampleMap[spec.name] = if (bytes != null) Sensors.decode(spec.name, bytes) else null
                    }
                } else {
                    for (spec in Sensors.POLL_LIST) {
                        val bytes = ElmProtocol.queryMode01(conn, spec.pidHex, spec.expectedBytes)
                        sampleMap[spec.name] = if (bytes != null) Sensors.decode(spec.name, bytes) else null
                    }
                }

                // CVT temperature probe
                var cvtTemp: Double? = null
                var cvtRaw: String? = null
                if (cvtSupported && cvtAttempts < SessionConstants.MAX_CVT_PROBE_ATTEMPTS) {
                    cvtAttempts++
                    val cvtPair = ElmProtocol.probeCvt(conn)
                    cvtTemp = cvtPair.first
                    cvtRaw = cvtPair.second
                    if (cvtPair.second == null && cvtAttempts >= SessionConstants.MAX_CVT_PROBE_ATTEMPTS) {
                        cvtSupported = false
                    }
                }

                val currentCoolant = sampleMap["coolant"]

                // Check warm-up detection (>= 80°C)
                if (currentCoolant != null && currentCoolant >= VehicleConstants.WARMED_UP_MIN_COOLANT_C && warmAtS == null) {
                    warmAtS = sessionTs
                    cooldownAfterWarmStartTs = sessionTs
                }

                // Calculate warming rate °C/min over the last 60 seconds
                if (currentCoolant != null) {
                    sampleHistoryForRate.addLast(Pair(sessionTs, currentCoolant))
                    while (sampleHistoryForRate.isNotEmpty() && (sessionTs - sampleHistoryForRate.first().first) > SessionConstants.RATE_CALC_WINDOW_S) {
                        sampleHistoryForRate.removeFirst()
                    }
                }
                val rate = if (sampleHistoryForRate.size >= 2) {
                    val old = sampleHistoryForRate.first()
                    val dtMinutes = (sessionTs - old.first) / 60.0
                    if (dtMinutes >= SessionConstants.RATE_CALC_MIN_INTERVAL_MIN) (currentCoolant!! - old.second) / dtMinutes else null
                } else null

                // Build unified sample
                val sample = SensorSample.fromPollMap(sessionTs, sampleMap, cvtTemp, cvtRaw)

                fileManager.writeCsvRow(csvWriter, sample)
                totalRows++

                // Immediate DB write on every 1 Hz sample (zero buffering, immune to kill/power-loss)
                database.sampleDao().insertSample(sample.toEntity(sessionId))

                _sessionState.value = _sessionState.value.copy(
                    isWarmedUp = warmAtS != null,
                    warmAtSeconds = warmAtS,
                    lastSample = sample,
                    warmUpRateCPerMin = rate,
                    rowCount = totalRows,
                    statusMessage = "Logging: ${sample.coolant?.let { "${it.toInt()}°C" } ?: "—"} | ${sample.rpm?.toInt() ?: "—"} RPM"
                )

                updateNotificationText(
                    "Coolant: ${sample.coolant?.toInt() ?: "—"}°C | RPM: ${sample.rpm?.toInt() ?: "—"} | " +
                    "LTFT: ${sample.ltft?.let { String.format(Locale.US, "%+.1f%%", it) } ?: "—"}"
                )

                // Check completion after 60s cooldown tail
                if (cooldownAfterWarmStartTs != null && (sessionTs - cooldownAfterWarmStartTs) >= SessionConstants.WARM_COOLDOWN_TAIL_S) {
                    break
                }

                if (sessionTs >= SessionConstants.MAX_SESSION_DURATION_S) {
                    break
                }

                sessionTs += 1.0
                val elapsedLoop = System.currentTimeMillis() - loopStart
                val sleepWait = SessionConstants.SAMPLE_INTERVAL_MS - elapsedLoop
                if (sleepWait > 0) delay(sleepWait)
            }

        } catch (e: Exception) {
            abortReason = e.message ?: "Unknown error"
            _sessionState.value = _sessionState.value.copy(error = abortReason)
        } finally {
            try {
                csvWriter?.close()

                val dtcMap = if (conn?.isConnected() == true) {
                    ElmProtocol.readDtc(conn)
                } else emptyMap()

                val completed = (abortReason == null && warmAtS != null)
                val finishedMeta = currentMeta?.copy(
                    warmAtS = warmAtS,
                    completed = completed,
                    dtc = dtcMap,
                    rows = totalRows,
                    finishedAt = isoFormat.format(Date()),
                    aborted = abortReason
                )

                if (metaFile != null && finishedMeta != null) {
                    fileManager.writeMeta(metaFile, finishedMeta)
                }

                if (sessionId != 0L && finishedMeta != null) {
                    database.sessionDao().updateSession(
                        SessionEntity(
                            id = sessionId,
                            csvFile = csvFile?.name ?: "",
                            startedAt = finishedMeta.startedAt,
                            finishedAt = finishedMeta.finishedAt,
                            mode = finishedMeta.mode,
                            startCoolantC = finishedMeta.startCoolantC,
                            warmAtS = finishedMeta.warmAtS,
                            rows = finishedMeta.rows,
                            aborted = finishedMeta.aborted,
                            metaJson = Gson().toJson(finishedMeta)
                        )
                    )
                }

                try {
                    conn?.send(ElmConstants.CMD_RESET, settleWaitMs = ElmConstants.DELAY_RESET_SETTLE_MS, timeoutMs = 1000L)
                } catch (_: Exception) {}

                conn?.close()
            } catch (_: Exception) {}

            _sessionState.value = _sessionState.value.copy(
                isRunning = false,
                statusMessage = if (abortReason != null) "Aborted: $abortReason" else "Session finished & saved"
            )

            if (wakeLock?.isHeld == true) wakeLock?.release()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun updateNotificationText(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        loggingJob?.cancel()
        serviceScope.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "obd_logging_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
