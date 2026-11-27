# Architecture & Bundle Design

## Overview
C-HR Logger for Android is structured as a modular, reactive, foreground-capable telemetry logging application for the Toyota C-HR (8NR-FTS 1.2T engine, NGX10 platform). It directly replicates the vehicle monitoring logic from the Python `mycar` suite into a standalone Android solution.

## Architecture Layers

```
                      +-----------------------------+
                      |         UI Layer            |
                      | (Compose / ViewModel / State)
                      +--------------+--------------+
                                     |
                                     v
                      +-----------------------------+
                      |       Service Layer         |
                      | (ObdLoggingService + Lock)  |
                      +-------+--------------+------+
                              |              |
                +-------------+              +-------------+
                v                                          v
+-------------------------------+          +-------------------------------+
|         Domain / Core         |          |          Data / Storage       |
| - ElmProtocol (State Machine) |          | - SessionFileManager (CSV)    |
| - Sensors (J1979 Decoders)    |          | - ChrDatabase (Room SQLite)   |
| - SimElmConnection (Mock)     |          | - LtftTrendCalculator         |
| - BleGattManager (Transport)  |          +-------------------------------+
+-------------------------------+
```

### 1. Bundle & Packaging Design
- **Android App Bundle (.aab):** Configured via AGP with language, density, and ABI splits enabled.
- **Package by Feature & Layer:**
  - `core.ble`: Low-level Bluetooth Low Energy transport and scanner.
  - `core.elm`: ELM327 protocol state machine, tokenizing, and ISO-TP reassembly.
  - `core.sensors`: SAE J1979 PID definitions and scaling formulas.
  - `core.sim`: Exact mathematical simulator replicating engine warm-up.
  - `core.storage`: File output for CSV streams and JSON metadata.
  - `core.database`: Room SQLite entities (`sessions`, `samples`) and trend analytics.
  - `service`: Android Foreground Service maintaining continuous polling and wake lock.
  - `ui`: Declarative Jetpack Compose UI with dark automotive palette.

### 2. State & Concurrency Model
- **Kotlin Coroutines & StateFlow:** Communication between the service and UI uses thread-safe `StateFlow<LiveSessionState>`.
- **Channel Buffer:** BLE notification bytes accumulate into an internal buffer until the prompt character `>` (`0x3E`) is received.
- **Foreground Service Execution:** Uses `connectedDevice` service type with `PARTIAL_WAKE_LOCK` to ensure continuous 1 Hz logging when the phone screen is locked.
- **Batch Database Writes:** Samples are buffered in memory and committed in batches to prevent SQLite write lock contention on flash storage.
