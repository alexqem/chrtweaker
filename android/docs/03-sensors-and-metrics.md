# Sensors & Diagnostics Metrics

## 1. Polled Sensors (SAE J1979)

All telemetry fields match the column order and formulas used by `mycar/sensors.py`.

| Metric | PID | Bytes | Formula | Units | Reference Norm |
|---|---|---|---|---|---|
| Engine RPM (`rpm`) | `0C` | 2 | `(A * 256 + B) / 4.0` | RPM | Idle: 680–780 |
| Vehicle Speed (`speed`) | `0D` | 1 | `A` | km/h | 0 at cold idle |
| Coolant ECT (`coolant`) | `05` | 1 | `A - 40.0` | °C | Target: $\ge 80$ °C |
| Engine Oil Temp (`oil_temp`) | `5C` | 1 | `A - 40.0` | °C | Follows coolant |
| Intake Air Temp (`iat`) | `0F` | 1 | `A - 40.0` | °C | Ambient + 5–10 °C |
| Barometric Pressure (`baro`) | `33` | 1 | `A` | kPa | ~101 kPa |
| MAF Mass Flow (`maf`) | `10` | 2 | `(A * 256 + B) / 100.0` | g/s | 1.6–2.0 g/s warm idle |
| Calculated Load (`load`) | `04` | 1 | `A * 100.0 / 255.0` | % | 18–25% warm idle |
| Short Term Fuel Trim (`stft`)| `06` | 1 | `(A - 128) * 100.0 / 128.0` | % | $\pm 10$% normal |
| Long Term Fuel Trim (`ltft`) | `07` | 1 | `(A - 128) * 100.0 / 128.0` | % | Adaptation drift tracking |
| Fuel System Status (`fuel_status`)| `03` | 2 | `A` | enum | 1=Open, 2=Closed loop |
| Catalyst Temp B1S1 (`catalyst`)| `3C` | 2 | `(A * 256 + B) / 10.0` | °C | Light-off: ~350 °C |
| Ignition Timing (`timing`) | `0E` | 1 | `(A - 128) / 2.0` | ° BTDC | ~8–12° warm idle |
| Relative Throttle (`throttle`)| `45` | 1 | `A * 100.0 / 255.0` | % | Clean butterfly check |
| Control Module Voltage (`voltage`)| `42` | 2 | `(A * 256 + B) / 1000.0` | V | 13.8–14.4 V charging |
| Engine Runtime (`runtime`) | `1F` | 2 | `A * 256 + B` | sec | Cumulative runtime |

---

## 2. Session Lifecycle & Thresholds

- **Cold Start Classification:** Initial coolant $\le 40.0$ °C. If $> 40.0$ °C, marked as warm session.
- **Warm-Up Detection:** First timestamp where `coolant >= 80.0 °C`. Logged in `meta.json` as `warm_at_s`.
- **Warming Rate:** Computed over a sliding 60-second window: $\Delta ECT / \Delta t$ in °C/min.
- **Cooldown Tail:** After warm-up is reached, logging continues for 60 seconds to observe warm idle stability before graceful shutdown.
- **Safety Ceiling:** Maximum session timeout capped at 45 minutes.

---

## 3. CSV File Contract

```csv
ts,rpm,speed,coolant,oil_temp,iat,baro,maf,load,stft,ltft,fuel_status,catalyst,timing,throttle,voltage,runtime,cvt_temp,cvt_raw
```
Missing or unpolled values output an empty string, preserving columnar alignment for Pandas / SQLite ingestion.
