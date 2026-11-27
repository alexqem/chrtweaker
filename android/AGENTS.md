# AGENTS.md — Engineering Rules for AI Agents

Guidelines for autonomous agents modifying the `chr/android` codebase.

---

## 1. Core Mission & Vehicle Context
- **Vehicle:** Toyota C-HR NGX10 (2020 JDM RHD, 8NR-FTS 1.2L Turbo, K114 CVT).
- **Goal:** Autonomous cold-start diagnostic logging over BLE without carrying a laptop.
- **Twin Repo:** Python driver in `../mycar/`. Protocol formulas, PID lists, and CSV contracts must remain 100% interoperable.

---

## 2. Hard Invariants & Rules

1. **Clone-Safe Gentle Init:**  
   Never omit delays during adapter init (`ATZ` 3s, `ATE0` 1s, `ATL0` 1s, `ATS1` 1s, `ATH0` 1s, `ATSP6` 1s). Aggressive probing bricks cheap clone firmware.
2. **Unsupported PIDs:**  
   Do not add MAP (`0x0B`) or Rail Pressure (`0x23`) to live polling — the 8NR-FTS ECM ignores them and polling causes timeouts.
3. **Constants First (KISS / DRY):**  
   Never hardcode magic numbers, UUIDs, thresholds, or timeouts. Place them in `core/common/Constants.kt` (`BleConstants`, `ElmConstants`, `VehicleConstants`, `SessionConstants`, `SensorConstants`).
4. **Data Integrity:**  
   - CSV header and column ordering must match `SensorSample.CSV_HEADER`.
   - Missing/unpolled sensor samples must emit an empty string (`""`), never `0.0`.
   - `ts` is session-relative elapsed seconds rounded to 1 decimal place.
5. **Foreground Execution:**  
   OBD-II polling belongs in `ObdLoggingService` with `PARTIAL_WAKE_LOCK`. Never run continuous polling loops directly in ViewModels or Activities.
6. **Verification Gate:**  
   Every code change must pass both APK and App Bundle compilation from the CLI before committing:
   ```bash
   export JAVA_HOME=/opt/homebrew/opt/openjdk@17
   export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
   ./gradlew assembleDebug bundleDebug
   ```

---

## 3. Key Component Map

| Path | Responsibility |
|---|---|
| `core/common/Constants.kt` | Single source of truth for protocol, thresholds, and sensor scales |
| `core/sensors/Sensors.kt` | SAE J1979 decoders and active polling list |
| `core/elm/ElmProtocol.kt` | Init state machine, Mode 01 multi-PID, Mode 09 identity, DTC parser |
| `core/elm/ElmParser.kt` | Fast regex tokenizing and payload extraction |
| `core/ble/BleGattManager.kt` | GATT client, notification buffer (`>`), and prompt synchronization |
| `core/sim/SimElmConnection.kt`| Offline mathematical engine warm-up simulator |
| `core/database/` | Room SQLite database (`chr.db`), DAO, and LTFT trend calculator |
| `service/ObdLoggingService.kt` | Android Foreground Service, lifecycle, and CSV streaming |
| `ui/` | Jetpack Compose dark automotive dashboard |

| `core/tweaks/CaristaTweaks.kt` | Carista-derived ECU tweaks (`0x750`/`0x7C0`/`0x7C4`/`0x7B0`/`0x7E1`) and vLinker gating |
| `ui/TelemetryCharts.kt` | Mobile-friendly Jetpack Compose Canvas live telemetry charts |
