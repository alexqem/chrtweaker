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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexqem.chr.core.common.VehicleConstants
import com.alexqem.chr.core.database.SessionEntity
import com.alexqem.chr.core.model.SensorSample
import com.alexqem.chr.core.model.VehicleProfile
import com.alexqem.chr.core.model.VehicleProfileRegistry
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
    val tweakStates by viewModel.tweakStates.collectAsState()
    val tweakLogs by viewModel.tweakLogs.collectAsState()
    val selectedProfileId by viewModel.selectedProfileId.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val allSessions by viewModel.allSessions.collectAsState()
    val selectedHistorySession by viewModel.selectedHistorySession.collectAsState()
    val selectedHistorySamples by viewModel.selectedHistorySamples.collectAsState()

    var showDevicePicker by remember { mutableStateOf(false) }
    var showProfilePicker by remember { mutableStateOf(false) }
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(enabled = !state.isRunning) { showProfilePicker = true }
                    ) {
                        Text(
                            activeProfile.shortLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Select vehicle profile",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
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
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("HUD", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = activeTab == 3,
                    onClick = { activeTab = 3 },
                    text = { Text("История", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (activeTab == 3) {
                SessionsHistoryTab(
                    sessions = allSessions,
                    selectedSession = selectedHistorySession,
                    selectedSamples = selectedHistorySamples,
                    onSelectSession = { viewModel.selectHistorySession(it) },
                    onShareSession = { session -> viewModel.shareSessionBundle(session, onShareCsv) }
                )
            } else if (activeTab == 2) {
                HudTab(state = state, profile = activeProfile)
            } else if (activeTab == 1) {
                EcuTweaksTab(
                    isVLinker = viewModel.isVLinkerConnected(),
                    isSim = useSimulator,
                    tweakStatus = tweakStatus,
                    tweakStates = tweakStates,
                    tweakLogs = tweakLogs,
                    onRunTweak = { spec, enabled -> viewModel.runTweak(spec, enabled) },
                    onDismissStatus = { viewModel.clearTweakStatus() }
                )
            } else {
            // Status bar pills
            SessionHeaderPills(state)

            Spacer(modifier = Modifier.height(14.dp))

            // 2-Column Telemetry Grid
            TelemetryCardsGrid(state, activeProfile)

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
                                    Text(dev.address, color = TextSecondary, fontSize = 12.sp)
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

    // Profile Picker Dialog
    if (showProfilePicker) {
        AlertDialog(
            onDismissRequest = { showProfilePicker = false },
            title = { Text("Профиль автомобиля", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isAuto = selectedProfileId == VehicleProfileRegistry.AUTO_PROFILE_ID
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isAuto) AccentBlue.copy(alpha = 0.15f) else BgDark)
                            .clickable {
                                viewModel.selectProfile(VehicleProfileRegistry.AUTO_PROFILE_ID)
                                showProfilePicker = false
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Автоопределение (VIN / CalID)", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Определяется по Mode 09 при старте сессии", color = TextSecondary, fontSize = 11.sp)
                        }
                        if (isAuto) Icon(Icons.Default.Check, contentDescription = null, tint = AccentBlue)
                    }
                    VehicleProfileRegistry.all().forEach { prof ->
                        val selected = selectedProfileId == prof.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) AccentBlue.copy(alpha = 0.15f) else BgDark)
                                .clickable {
                                    viewModel.selectProfile(prof.id)
                                    showProfilePicker = false
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(prof.shortLabel, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(prof.label, color = TextSecondary, fontSize = 11.sp)
                            }
                            if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = AccentBlue)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProfilePicker = false }) { Text("Закрыть") }
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
fun EcuTweaksTab(
    isVLinker: Boolean,
    isSim: Boolean,
    tweakStatus: TweakUiState?,
    tweakStates: Map<String, Boolean>,
    tweakLogs: List<String>,
    onRunTweak: (com.alexqem.chr.core.tweaks.TweakSpec, Boolean) -> Unit,
    onDismissStatus: () -> Unit
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val fullAccess = isVLinker || isSim
    val isPending = tweakStatus is TweakUiState.Pending

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
                fontSize = 12.sp,
                color = if (fullAccess) OkGreen else WarnAmber
            )
        }

        // Command execution status banner
        if (tweakStatus != null) {
            val (bannerColor, prefix, msg) = when (tweakStatus) {
                is TweakUiState.Pending -> Triple(CoolantBlue, "⏳ ", tweakStatus.message)
                is TweakUiState.Success -> Triple(OkGreen, "✅ ", tweakStatus.message)
                is TweakUiState.Error -> Triple(DangerRed, "❌ ", tweakStatus.message)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(bannerColor.copy(alpha = 0.15f))
                    .border(1.dp, bannerColor, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = prefix + msg,
                    fontSize = 12.sp,
                    color = bannerColor,
                    modifier = Modifier.weight(1f)
                )
                if (tweakStatus !is TweakUiState.Pending) {
                    TextButton(onClick = onDismissStatus) { Text("OK", fontSize = 12.sp) }
                }
            }
        }

        val visibleTweaks = com.alexqem.chr.core.tweaks.EcuTweaks.ALL.filter {
            fullAccess || !it.requiresVLinker
        }

        visibleTweaks.forEach { spec ->
            val checked = tweakStates[spec.id] ?: false
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
                    Text(spec.description, fontSize = 12.sp, color = TextSecondary)
                    Text("${spec.ecu} · ${spec.protocol}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = ChartTick)
                }
                if (spec.isSwitch) {
                    Switch(
                        checked = checked,
                        enabled = !isPending,
                        onCheckedChange = { onRunTweak(spec, it) }
                    )
                } else {
                    Button(
                        enabled = !isPending,
                        onClick = { onRunTweak(spec, true) },
                        colors = ButtonDefaults.buttonColors(containerColor = CardBgElevated, contentColor = TextPrimary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Выполнить", fontSize = 12.sp)
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
                    Text("Копировать лог", fontSize = 12.sp, color = CoolantBlue)
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
fun HudTab(state: LiveSessionState, profile: VehicleProfile) {
    val s = state.lastSample
    var isMirrored by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    fun fmt(v: Double?, digits: Int = 0): String =
        if (v != null && !v.isNaN()) String.format(Locale.US, "%.${digits}f", v) else "—"

    val coolantColor = when {
        s?.coolant == null -> TextSecondary
        s.coolant < 60.0 -> ColdBlue
        s.coolant in 82.0..95.0 -> OkGreen
        s.coolant > 100.0 -> DangerRed
        else -> WarnAmber
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(profile.shortLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("Экран не гаснет, пока открыт HUD", fontSize = 11.sp, color = TextSecondary)
            }
            Button(
                onClick = { isMirrored = !isMirrored },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMirrored) CoolantBlue.copy(alpha = 0.2f) else CardBgElevated,
                    contentColor = if (isMirrored) CoolantBlue else TextPrimary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(if (isMirrored) "Зеркало: ВКЛ" else "Зеркало (HUD)", fontSize = 12.sp)
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.graphicsLayer { scaleX = if (isMirrored) -1f else 1f }
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HudGaugeCard("СКОРОСТЬ", fmt(s?.speed), "км/ч", AccentBlue, Modifier.weight(1f))
                HudGaugeCard("ОБОРОТЫ", fmt(s?.rpm), "RPM", RpmRed, Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HudGaugeCard("ОЖ", fmt(s?.coolant, 1), "°C", coolantColor, Modifier.weight(1f))
                HudGaugeCard("МАСЛО", fmt(s?.oilTemp, 1), "°C", OilAmber, Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HudGaugeCard("STFT", fmt(s?.stft, 1), "%", StftRose, Modifier.weight(1f))
                HudGaugeCard("LTFT", fmt(s?.ltft, 1), "%", LtftOrange, Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HudGaugeCard("АКБ", fmt(s?.voltage, 2), "V", VoltTeal, Modifier.weight(1f))
                HudGaugeCard(
                    "CVT",
                    if (profile.cvtProbe) fmt(s?.cvtTemp, 1) else "N/A",
                    if (profile.cvtProbe) "°C" else "",
                    CatLime,
                    Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun HudGaugeCard(label: String, value: String, unit: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(1.dp, BorderLine, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = accent)
            Spacer(modifier = Modifier.width(6.dp))
            Text(unit, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 5.dp))
        }
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
fun TelemetryCardsGrid(state: LiveSessionState, profile: VehicleProfile) {
    val sample = state.lastSample

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Coolant Card
            val isWarm = (sample?.coolant ?: 0.0) >= VehicleConstants.WARMED_UP_MIN_COOLANT_C
            StatCard(
                modifier = Modifier.weight(1f),
                title = "ОЖ (Coolant)",
                sub = "термостат ${profile.thermostatMinC.toInt()}..${profile.thermostatMaxC.toInt()} °C",
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
            val isIdleHigh = rpmVal > profile.idleMaxRpm && (sample?.coolant ?: 0.0) >= VehicleConstants.WARMED_UP_MIN_COOLANT_C
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Двигатель",
                sub = "ХХ норма ${profile.idleMinRpm.toInt()}..${profile.idleMaxRpm.toInt()}",
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
        Text(text = text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                    Text("Тест без машины (кривая 8NR-FTS)", color = TextSecondary, fontSize = 12.sp)
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
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
fun SessionsHistoryTab(
    sessions: List<SessionEntity>,
    selectedSession: SessionEntity?,
    selectedSamples: List<SensorSample>,
    onSelectSession: (SessionEntity?) -> Unit,
    onShareSession: (SessionEntity) -> Unit
) {
    if (selectedSession != null) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onSelectSession(null) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Назад к списку", fontSize = 12.sp)
                }
                IconButton(onClick = { onShareSession(selectedSession) }) {
                    Icon(Icons.Default.Share, contentDescription = "Share session", tint = AccentBlue)
                }
            }
            Text(
                text = "${selectedSession.csvFile.removeSuffix(".csv")} · ${selectedSession.rows} строк",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (selectedSamples.isEmpty()) {
                Text("Нет сэмплов в базе для этой сессии.", color = TextSecondary, fontSize = 12.sp)
            } else {
                TelemetryChartsSection(history = selectedSamples)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sessions.isEmpty()) {
            Text(
                "Сохранённых сессий пока нет. Запустите сессию во вкладке Телеметрия.",
                color = TextSecondary,
                fontSize = 13.sp
            )
        } else {
            sessions.forEach { session ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardBg)
                        .border(1.dp, BorderLine, RoundedCornerShape(10.dp))
                        .clickable { onSelectSession(session) }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.csvFile.removeSuffix(".csv"),
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                        val coolantStr = session.startCoolantC?.let { String.format(Locale.US, "%.1f°C", it) } ?: "—"
                        val warmStr = session.warmAtS?.let { String.format(Locale.US, " · прогрев %.0fс", it) } ?: ""
                        Text(
                            text = "${session.mode} · старт $coolantStr · ${session.rows} строк$warmStr",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = { onShareSession(session) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share session", tint = CoolantBlue)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
