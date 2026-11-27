# C-HR OBD-II Android Logger

Autonomous Android application for cold-start diagnostic monitoring and high-rate telemetry logging on the **Toyota C-HR NGX10** (8NR-FTS 1.2T engine, CVT K114). Built to capture complete sessions directly to CSV and SQLite without needing a laptop in the car.

---

## Features

- **Clone-Safe BLE Transport:** Automated scanner for ELM327 BLE adapters (`0xFFF0` / `0xFFF1`) with gentle initialization to prevent adapter lockups.
- **Full SAE J1979 Telemetry:** 16 live sensors polled at 1 Hz (RPM, Coolant, Oil, IAT, MAF, Load, STFT, LTFT, Closed Loop, Catalyst, Voltage, Timing).
- **Toyota Custom Probing:** Probes Mode 01/21 CVT fluid temperature (`01B4`, `21D9`, `2182`) and Mode 09 vehicle identity (VIN, CalID, ECU Name).
- **Background Logging Service:** Foreground service with partial wake lock logs reliably while the screen is off.
- **Automatic Session Completion:** Detects thermostat warm-up threshold ($\ge 80$ °C), logs a 60-second stabilization tail, reads DTCs (Modes 03/07/0A), and closes cleanly.
- **Built-in Offline Simulator:** Full replica of the 8NR-FTS engine warm-up curve for rapid testing without a physical adapter or vehicle.
- **Dual Export:** Streams real-time CSV to storage and mirrors into Room SQLite (`chr.db`) with cross-session LTFT adaptation drift tracking.

---

## Architecture & Code Quality

- **Modern Bundle Architecture:** Configured for both APK (`assembleDebug`) and Android App Bundle (`bundleDebug`).
- **DRY / KISS Design:** Clean separation of concerns (Core, Database, Storage, Service, UI) with zero hardcoded magic numbers. All protocol timings and thresholds are centralized in `core/common/Constants.kt`.
- **Jetpack Compose + Material 3:** Dark automotive palette optimized for in-car glanceability.

---

## Command-Line Build & Run (No Android Studio Needed)

### 1. Build APK & App Bundle
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools

# Build Debug APK (app/build/outputs/apk/debug/app-debug.apk)
./gradlew assembleDebug

# Build App Bundle (app/build/outputs/bundle/debug/app-debug.aab)
./gradlew bundleDebug
```

### 2. Install to Connected Device
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. Build with Docker
```bash
docker build -t chr-android-builder .
docker run --rm -v $(pwd):/app chr-android-builder
```

---

## Documentation

Detailed specifications are available in the [`docs/`](docs/) directory:
- [01-architecture.md](docs/01-architecture.md) — Bundle structure, reactive data flow, and concurrency model.
- [02-ble-and-elm-protocol.md](docs/02-ble-and-elm-protocol.md) — GATT specs, gentle AT sequence, and ISO-TP reassembly.
- [03-sensors-and-metrics.md](docs/03-sensors-and-metrics.md) — J1979 formulas, scaling constants, and CSV contract.
- [04-build-and-cli.md](docs/04-build-and-cli.md) — CLI commands, Docker setup, and CI tips.
