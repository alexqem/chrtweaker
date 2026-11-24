"""Car profile, paths, thresholds. Single source of truth (DRY).

Machine-specific secrets (the dongle's BLE address) live in .env — see
.env.example. Never commit .env.
"""

import os
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
LOGS_DIR = PROJECT_ROOT / "logs"
STATS_DIR = PROJECT_ROOT / "stats"
DOCS_DIR = PROJECT_ROOT / "docs"


def _load_env_file(path: Path) -> None:
    """Minimal .env loader (KEY=VALUE, # comments). Existing env wins."""
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError:
        return
    for line in lines:
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        os.environ.setdefault(key.strip(), value.strip().strip("'\""))


_load_env_file(PROJECT_ROOT / ".env")

# --- machine-specific (from .env) ---
BLE_ADDRESS = os.environ.get("MYCAR_BLE_ADDRESS") or None  # dongle BLE UUID (macOS)
BT_PIN = os.environ.get("MYCAR_BT_PIN") or None            # classic BT pairing PIN

CAR = {
    "make": "Toyota",
    "model": "C-HR",
    "body": "NGX10",
    "year": 2020,
    "market": "JDM (RHD)",
    "engine": "8NR-FTS",
    "displacement_cc": 1197,
    "transmission": "CVT (K114) or 6MT",
    "protocol_hint": "ISO 15765-4 CAN 11/500 (id '6')",
}

# --- Session thresholds (tune here, not in logic) ---
COLD_COOLANT_MAX_C = 40.0    # coolant below this at connect  => cold start session
WARM_COOLANT_MIN_C = 80.0    # coolant reached this          => warm-up complete
COOLDOWN_AFTER_WARM_S = 60   # keep logging this long after reaching WARM
SAMPLE_INTERVAL_S = 1.0      # one full poll cycle per second
MAX_SESSION_MINUTES = 45     # hard stop, protects against forgotten dongle

# --- Session flow knobs (retry/abort policy) ---
CONNECT_ATTEMPTS = 3         # adapter connect attempts before giving up
CONNECT_RETRY_SLEEP_S = 3.0  # pause between connect attempts
COOLANT_PROBE_ATTEMPTS = 5   # first-coolant reads to decide cold/warm mode
CVT_GIVE_UP_CYCLES = 5       # empty cvt tries before skipping it for the session
MAX_RECONNECTS = 20          # link drop reconnects before aborting the session

# --- Dashboard ---
DASHBOARD_PORT = 8600

# --- Reference norms (from mycar/docs/03, 05) — used by analyze ---
NORMS = {
    "idle_rpm_cvt": (680, 780),        # warm idle, P/N, A/C off (Toyota SSM)
    "warm_coolant_c": (75, 100),       # normal operating window
    "thermostat_open_c": (80, 84),     # main thermostat, closed below 77
    "stft_ltft_normal_pct": (-10, 10), # healthy combined trim guideline
    "toyota_trim_dtc_lean_pct": 48,    # P017100 threshold (Toyota)
    "toyota_trim_dtc_rich_pct": -35,   # P017200 threshold (Toyota)
    "maf_at_3000rpm_gs": (4.0, 14.0),  # ECT>=75C, P/N, A/C off (Toyota SSM)
    "fuel_pressure_idle_kpa": (4000, 18000),   # rail_p (PID 23) is kPa in sensors/CSV
    "fuel_pressure_3000_kpa": (8000, 18000),
    "battery_v_idle": (13.2, 14.8),    # charging system at warm idle; 9.x V is a
                                       # cranking-drop floor, not an idle norm
    "battery_v_cranking_min": 9.6,     # min during starter cranking (Toyota)
    "cold_start_max_c": COLD_COOLANT_MAX_C,
    "warm_target_c": WARM_COOLANT_MIN_C,
}
