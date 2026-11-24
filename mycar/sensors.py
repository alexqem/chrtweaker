"""Poll list: which PIDs we capture every cycle. Order defines CSV columns.

Raw pyserial driver (mycar/elm.py) — no OBD libraries. Each entry:
    (csv_column, ("m01", pid_hex, n_data_bytes))   standard Mode 01
CVT fluid temp is Toyota-custom (Mode 21: 21D9 via functional 7DF, fallback
2182) — handled separately in capture via conn.query_cvt_and_raw(); its raw
payload goes to the `cvt_raw` debug column (decode variants: mycar/toyota.py).

Torque Pro CSV template (keep for adding more PIDs):
    Torque PID "2182"  -> mode 21 pid 82  -> toyota.probe_cvt()
    Torque equation    -> variant in toyota._atf_variants()
    Torque header 7E0  -> physical addressing (needs a real-ATSH adapter)
"""

# (column, pid, expected DATA bytes — not counting the 41 xx service echo)
POLL = [
    ("rpm", "0C", 2),
    ("speed", "0D", 1),
    ("coolant", "05", 1),
    ("oil_temp", "5C", 1),
    ("iat", "0F", 1),
    ("baro", "33", 1),
    ("maf", "10", 2),
    ("load", "04", 1),
    ("stft", "06", 1),
    ("ltft", "07", 1),
    ("fuel_status", "03", 2),  # 1=open loop, 2=closed (norming context for trims)
    ("catalyst", "3C", 2),     # catalyst temp B1S1, °C (light-off during cold start)
    ("timing", "0E", 1),
    ("throttle", "45", 1),     # relative throttle
    ("voltage", "42", 2),
    ("runtime", "1F", 2),
]
# removed 2026-11-24 (empirical sweep on the car): map (0x0B) and rail_p (0x23,
# D-4T) never answer on NGX10 — polling them only slowed every cycle down.

COLUMNS = ["ts"] + [n for n, _, _ in POLL] + ["cvt_temp", "cvt_raw"]

# non-numeric sample columns: raw hex debug payloads, never float-parsed
TEXT_COLUMNS = frozenset({"cvt_raw"})

# first coolant probe (session mode decision) — derived from POLL, not duplicated
COOLANT_PID, COOLANT_N = next((p, n) for name, p, n in POLL if name == "coolant")


# ---------------- decoders (SAE J1979) ----------------

def decode(name: str, data: list[int]) -> float | None:
    """Decode raw data bytes of a Mode 01 PID to engineering units."""
    if not data:
        return None
    if name == "rpm":
        return (data[0] * 256 + data[1]) / 4
    if name == "speed":
        return float(data[0])
    if name in ("coolant", "oil_temp", "iat"):
        return data[0] - 40
    if name == "baro":
        return float(data[0])
    if name == "maf":
        return (data[0] * 256 + data[1]) / 100
    if name == "load":
        return data[0] * 100 / 255
    if name in ("stft", "ltft"):
        return (data[0] - 128) * 100 / 128
    if name == "fuel_status":
        return float(data[0])
    if name == "catalyst":
        return (data[0] * 256 + data[1]) / 10
    if name == "timing":
        return (data[0] - 128) / 2
    if name == "throttle":
        return data[0] * 100 / 255
    if name == "voltage":
        return (data[0] * 256 + data[1]) / 1000
    if name == "runtime":
        return float(data[0] * 256 + data[1])
    return None
