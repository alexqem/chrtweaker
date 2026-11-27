package com.alexqem.chr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexqem.chr.core.common.VehicleConstants
import com.alexqem.chr.service.LiveSessionState
import com.alexqem.chr.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onShareCsv: (java.io.File) -> Unit
) {
    val state by viewModel.sessionState.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val useSimulator by viewModel.useSimulator.collectAsState()
    val trendReport by viewModel.trendReport.collectAsState()
    val includeAllBle by viewModel.includeAllBle.collectAsState()
    val exportStatus by viewModel.exportStatus.collectAsState()
    val sampleHistory by viewModel.sampleHistory.collectAsState()
    val tweakStatus by viewModel.tweakStatus.collectAsState()
    val tweakLogs by viewModel.tweakLogs.collectAsState()

    var showDevicePicker by remember { mutableStateOf(false) }
    var activeTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(exportStatus) {
        val msg = exportStatus
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearExportStatus()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "C-HR NGX10",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "8NR-FTS 1.2T",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadLtftTrends() }) {
                        Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = "Trends", tint = AccentBlue)
                    }
                    IconButton(onClick = { viewModel.exportLogsToDownloads() }) {
                        Icon(Icons.Default.Download, contentDescription = "Download Logs (tar.gz)", tint = CoolantBlue)
                    }
                    IconButton(onClick = {
                        val file = viewModel.getLatestCsvFile()
                        if (file != null) onShareCsv(file)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share CSV", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        },
        containerColor = BgDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = CardBg,
                contentColor = TextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Телеметрия", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("ECU Tweaks", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (activeTab == 1) {
                CaristaTweaksTab(
                    isVLinker = viewModel.isVLinkerConnected(),
                    isSim = useSimulator,
                    tweakStatus = tweakStatus,
                    tweakLogs = tweakLogs,
                    onRunTweak = { spec, enabled -> viewModel.runTweak(spec, enabled) },
                    onDismissStatus = { viewModel.clearTweakStatus() }
                )
            } else {
            // Status bar pills
            SessionHeaderPills(state)

            Spacer(modifier = Modifier.height(14.dp))

            // 2-Column Telemetry Grid
            TelemetryCardsGrid(state)

            Spacer(modifier = Modifier.height(18.dp))

            // Control Panel
            ControlPanel(
                state = state,
                useSimulator = useSimulator,
                selectedDeviceName = selectedDevice?.name ?: "No device selected",
                onToggleSimulator = { viewModel.setUseSimulator(it) },
                onSelectDeviceClick = {
                    viewModel.startBleScan()
                    showDevicePicker = true
                },
                onStartSession = { viewModel.startSession() },
                onStopSession = { viewModel.stopSession() }
            )

            Spacer(modifier = Modifier.height(24.dp))
            Spacer(modifier = Modifier.height(12.dp))
            TelemetryChartsSection(history = sampleHistory)
            Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // BLE Device Picker Dialog
    if (showDevicePicker) {
        AlertDialog(
            onDismissRequest = { showDevicePicker = false },
            title = { Text("Select OBD BLE Adapter", color = TextPrimary) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Все BLE-устройства", color = TextSecondary, fontSize = 12.sp)
                        Switch(
                            checked = includeAllBle,
                            onCheckedChange = { viewModel.toggleIncludeAllBle(it) }
                        )
                    }

                    if (isScanning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AccentBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scanning for 0xFFF0...", color = TextSecondary, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    if (discoveredDevices.isEmpty() && !isScanning) {
                        Text("No ELM327 BLE devices found yet.", color = TextSecondary)
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(discoveredDevices) { dev ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectDevice(dev)
                                        showDevicePicker = false
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(dev.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                    Text(dev.address, color = TextSecondary, fontSize = 11.sp)
                                }
                                if (dev.score == 2) {
                                    Badge(containerColor = OkGreen) { Text("OBD", color = BgDark) }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevicePicker = false }) { Text("Close") }
            },
            containerColor = CardBg
        )
    }

    // LTFT Trend Dialog
    if (trendReport != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearTrendReport() },
            title = { Text("LTFT Adaptation Trend", color = TextPrimary) },
            text = {
                Text(
                    text = trendReport ?: "",
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearTrendReport() }) { Text("OK") }
            },
            containerColor = CardBg
        )
    }
}

@Composable
fun CaristaTweaksTab(
    isVLinker: Boolean,
    isSim: Boolean,
    tweakStatus: Pair<Boolean, String>?,
    tweakLogs: List<String>,
    onRunTweak: (com.alexqem.chr.core.tweaks.TweakSpec, Boolean) -> Unit,
    onDismissStatus: () -> Unit
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val fullAccess = isVLinker || isSim

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Adapter capability banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (fullAccess) OkGreen.copy(alpha = 0.12f) else WarnAmber.copy(alpha = 0.12f))
                .border(1.dp, if (fullAccess) OkGreen else WarnAmber, RoundedCornerShape(8.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (fullAccess) {
                    "Vgate vLinker FD+ / Симулятор: полный доступ ко всем ECU (0x750, 0x7C0, 0x7C4)"
                } else {
                    "Ограниченный режим: подключён не Vgate vLinker FD+ BT4.0. Настройки Body ECU (0x750/0x7C0) скрыты, так как дешёвые ELM327 не поддерживают расширенную адресацию."
                },
                fontSize = 11.sp,
                color = if (fullAccess) OkGreen else WarnAmber
            )
        }

        // Command execution status banner
        if (tweakStatus != null) {
            val (ok, msg) = tweakStatus
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (ok) OkGreen.copy(alpha = 0.15f) else DangerRed.copy(alpha = 0.15f))
                    .border(1.dp, if (ok) OkGreen else DangerRed, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (if (ok) "OK: " else "ERR: ") + msg,
                    fontSize = 12.sp,
                    color = if (ok) OkGreen else DangerRed,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismissStatus) { Text("OK", fontSize = 11.sp) }
            }
        }

        val visibleTweaks = com.alexqem.chr.core.tweaks.CaristaTweaks.ALL.filter {
            fullAccess || !it.requiresVLinker
        }

        visibleTweaks.forEach { spec ->
            var checked by remember(spec.id) { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CardBg)
                    .border(1.dp, BorderLine, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(spec.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(spec.description, fontSize = 11.sp, color = TextSecondary)
                    Text("${spec.ecu} · ${spec.protocol}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = ChartTick)
                }
                if (spec.isSwitch) {
                    Switch(
                        checked = checked,
                        onCheckedChange = {
                            checked = it
                            onRunTweak(spec, it)
                        }
                    )
                } else {
                    Button(
                        onClick = { onRunTweak(spec, true) },
                        colors = ButtonDefaults.buttonColors(containerColor = CardBgElevated, contentColor = TextPrimary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Выполнить", fontSize = 11.sp)
                    }
                }
            }
        }

        // Protocol Debug Log
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CardBg)
                .border(1.dp, BorderLine, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Protocol Debug Log", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                TextButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(tweakLogs.joinToString("\n")))
                }) {
                    Text("Копировать лог", fontSize = 11.sp, color = CoolantBlue)
                }
            }
            Text(
                text = if (tweakLogs.isEmpty()) "Команды и ответы ECU появятся здесь..." else tweakLogs.joinToString("\n"),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgDark, RoundedCornerShape(6.dp))
                    .padding(8.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun SessionHeaderPills(state: LiveSessionState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mode pill
        val modeText = when {
            !state.isRunning -> "ОЖИДАНИЕ"
            state.mode == "cold" -> "❄ ХОЛОДНЫЙ ПУСК"
            else -> "ПРОГРЕТЫЙ"
        }
        val modeColor = if (state.mode == "cold") ColdBlue else WarnAmber
        Pill(text = modeText, color = modeColor)

        // Connection pill
        val connText = when {
            state.isSim -> "СИМУЛЯТОР"
            state.isRunning -> "ОНЛАЙН"
            else -> "НЕТ СВЯЗИ"
        }
        val connColor = if (state.isRunning) OkGreen else TextSecondary
        Pill(text = connText, color = connColor)

        Spacer(modifier = Modifier.weight(1f))

        // Timer
        val ts = state.lastSample?.ts?.toInt() ?: 0
        val mm = ts / 60
        val ss = ts % 60
        Text(
            text = String.format(Locale.US, "%02d:%02d", mm, ss),
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

@Composable
fun TelemetryCardsGrid(state: LiveSessionState) {
    val sample = state.lastSample

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Coolant Card
            val isWarm = (sample?.coolant ?: 0.0) >= VehicleConstants.WARMED_UP_MIN_COOLANT_C
            StatCard(
                modifier = Modifier.weight(1f),
                title = "ОЖ (Coolant)",
                sub = "термостат ${VehicleConstants.THERMOSTAT_OPEN_MIN_C.toInt()}..${VehicleConstants.THERMOSTAT_OPEN_MAX_C.toInt()} °C",
                value = sample?.coolant?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                unit = "°C",
                highlight = isWarm,
                highlightColor = OkGreen,
                icon = Icons.Default.Thermostat
            )
            // Warm-up rate
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Скорость ОЖ",
                sub = "за 60 сек",
                value = state.warmUpRateCPerMin?.let { String.format(Locale.US, "%+.1f", it) } ?: "—",
                unit = "°C/мин",
                highlight = false,
                icon = Icons.Default.Speed
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // RPM
            val rpmVal = sample?.rpm ?: 0.0
            val isIdleHigh = rpmVal > VehicleConstants.IDLE_RPM_MAX && (sample?.coolant ?: 0.0) >= VehicleConstants.WARMED_UP_MIN_COOLANT_C
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Двигатель",
                sub = "ХХ норма ${VehicleConstants.IDLE_RPM_MIN.toInt()}..${VehicleConstants.IDLE_RPM_MAX.toInt()}",
                value = sample?.rpm?.let { String.format(Locale.US, "%.0f", it) } ?: "—",
                unit = "rpm",
                highlight = isIdleHigh,
                highlightColor = WarnAmber,
                icon = Icons.Default.ElectricCar
            )
            // Oil Temp
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Масло ДВС",
                sub = "oil_temp",
                value = sample?.oilTemp?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                unit = "°C",
                highlight = false,
                icon = Icons.Default.OilBarrel
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Fuel Loop
            val loopLabel = when (sample?.fuelStatus?.toInt()) {
                1 -> "OPEN · прогрев"
                2 -> "CLOSED"
                4 -> "OPEN · нагрузка"
                else -> "—"
            }
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Петля смеси",
                sub = "fuel_status",
                value = loopLabel,
                unit = "",
                highlight = sample?.fuelStatus == 2.0,
                highlightColor = OkGreen,
                icon = Icons.Default.Loop
            )
            // Catalyst
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Катализатор",
                sub = "light-off ~350 °C",
                value = sample?.catalyst?.let { String.format(Locale.US, "%.0f", it) } ?: "—",
                unit = "°C",
                highlight = false,
                icon = Icons.Default.LocalFireDepartment
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Trims STFT / LTFT
            val stftStr = sample?.stft?.let { String.format(Locale.US, "%+.1f", it) } ?: "—"
            val ltftStr = sample?.ltft?.let { String.format(Locale.US, "%+.1f", it) } ?: "—"
            val ltftWarn = (sample?.ltft ?: 0.0) > VehicleConstants.TRIM_NORMAL_LIMIT_PCT || (sample?.ltft ?: 0.0) < -VehicleConstants.TRIM_NORMAL_LIMIT_PCT
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Коррекции",
                sub = "STFT / LTFT",
                value = "$stftStr / $ltftStr",
                unit = "%",
                highlight = ltftWarn,
                highlightColor = WarnAmber,
                icon = Icons.Default.Tune
            )
            // Voltage
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Бортсеть",
                sub = "генератор",
                value = sample?.voltage?.let { String.format(Locale.US, "%.2f", it) } ?: "—",
                unit = "V",
                highlight = false,
                icon = Icons.Default.BatteryChargingFull
            )
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    sub: String,
    value: String,
    unit: String,
    highlight: Boolean,
    highlightColor: Color = OkGreen,
    icon: ImageVector
) {
    Card(
        modifier = modifier
            .border(1.dp, if (highlight) highlightColor else BorderLine, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = if (highlight) highlightColor else TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                if (unit.isNotBlank()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(unit, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(sub, color = TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(99.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ControlPanel(
    state: LiveSessionState,
    useSimulator: Boolean,
    selectedDeviceName: String,
    onToggleSimulator: (Boolean) -> Unit,
    onSelectDeviceClick: () -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderLine, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Параметры подключения", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

            Spacer(modifier = Modifier.height(8.dp))

            // Simulator toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Симулятор", color = TextPrimary, fontWeight = FontWeight.Medium)
                    Text("Тест без машины (кривая 8NR-FTS)", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = useSimulator,
                    onCheckedChange = onToggleSimulator,
                    enabled = !state.isRunning
                )
            }

            if (!useSimulator) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSelectDeviceClick,
                    enabled = !state.isRunning,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(selectedDeviceName, maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Action Button
            Button(
                onClick = {
                    if (state.isRunning) onStopSession() else onStartSession()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isRunning) DangerRed else OkGreen
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = if (state.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.isRunning) "ОСТАНОВИТЬ ЗАПИСЬ" else "НАЧАТЬ СЕССИЮ ЛОГА",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }

            if (state.statusMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.statusMessage,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}
