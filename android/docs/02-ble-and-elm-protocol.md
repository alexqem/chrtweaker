# BLE & ELM327 Protocol

## 1. Bluetooth Low Energy Transport

### GATT Profile
- **Service UUID:** `0000fff0-0000-1000-8000-00805f9b34fb` (shorthand `0xFFF0` / `0x18F0`).
- **Communication Characteristic:** `0000fff1-0000-1000-8000-00805f9b34fb` (`0xFFF1`).
  - Supports both `WRITE_NO_RESPONSE` and `NOTIFY`.
  - Notifications are enabled via CCCD (`00002902-0000-1000-8000-00805f9b34fb`).
- **MTU:** 512 bytes requested upon connection.

### Device Scoring
Scanner scores devices to avoid spurious connections:
- **Score 2:** Device advertises `0xFFF0` AND local name contains `"OBD"` or `"ELM"`.
- **Score 1:** Device advertises `0xFFF0` with anonymous or generic name.

---

## 2. ELM327 Gentle Initialization

Clone BLE adapters easily crash on aggressive baud rate searching or unsupported AT commands. The driver executes a strictly timed gentle sequence:

| Command | Delay | Purpose |
|---|---|---|
| `ATZ` | 3000 ms | Soft reset adapter chip |
| `ATE0` | 1000 ms | Echo off |
| `ATL0` | 1000 ms | Linefeeds off (CR only) |
| `ATS1` | 1000 ms | Spaces on (required for hex tokenization) |
| `ATH0` | 1000 ms | Headers off (standard functional query) |
| `ATSP6` | 1000 ms | Protocol 6: ISO 15765-4 CAN (11 bit ID, 500 kbaud) |
| `0100` | 4000 ms | Vehicle connection confirmation |

---

## 3. Query Modes & Reassembly

- **Mode 01 Standard:** `01{PID}\r`. Expects `41 {PID} [DATA...]`.
- **Multi-PID Query:** Groups up to 16 PIDs into a single request (`010C0D050F...`). Parsed line-by-line using token search.
- **Mode 09 Vehicle ID:**
  - Temporarily sets `ATH1` to enable 11-bit CAN arbitration IDs (`7E8` for ECM).
  - Queries `0902` (VIN), `0904` (Calibration ID), `090A` (ECU Name).
  - Handles ISO-TP Single Frame and Multi-Frame (First Frame `0x10`, Consecutive Frame `0x20`) reassembly.
  - Restores `ATH0` before live capture loop starts.
- **Mode 03/07/0A Trouble Codes:**
  - Queried at session end.
  - Decodes 2-byte trouble codes into standard alphanumeric identifiers (`P0133`, `C0040`, etc.).
