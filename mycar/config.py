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

# --- Vehicle profiles: what transfers across Toyota models (see README) ---
# Standard Mode-01 telemetry is OBD-II-generic (any 2008+ gasoline car).
# Model-specific parts:
#   * NORMS — engine/transmission-dependent (idle, thermostat, trim verdicts);
#   * CVT probe — KWP-era Toyota CVTs answer 21D9/2182; 2016+ UDS ECUs use
#     Mode 22 (221627) which is NOT implemented yet (probe stays silent);
#   * ECU tweaks — verified on NGX10 only (docs/ECU_TWEAKS.md).
# Select: MYCAR_PROFILE in .env, `capture --profile NAME`, or analyze/db pick
# the profile up from the session's meta.json.
PROFILES = {
    "8nrfts": {
        "label": "Toyota C-HR NGX10 · 1.2T 8NR-FTS · K114 CVT",
        "short": "Toyota C-HR NGX10 · 1.2T 8NR-FTS",
        "verified": True,          # all norms below verified on this car
        "cvt_probe": True,
        "cold_start_analysis": True,
        "norms": {},               # base NORMS are the 8NR-FTS values
    },
    "2zrfe": {
        "label": "Toyota 1.8 NA 2ZR-FE (Corolla / Auris, CVT/MT)",
        "short": "Toyota · 1.8 2ZR-FE (Corolla / Auris)",
        "verified": False,         # norms are Toyota-manual grade, not road-verified
        "cvt_probe": True,         # K313 CVT answers the same 21D9/2182 family
        "cold_start_analysis": True,
        "norms": {
            "idle_rpm_cvt": (620, 720),    # 2ZR-FE warm idle sits below the 1.2T
            "thermostat_open_c": (80, 86), # Toyota common 82-86 °C
        },
    },
    "2zrfxe": {
        "label": "Toyota 1.8 Hybrid 2ZR-FXE (C-HR NGX50 / Corolla Hybrid / Prius)",
        "short": "Toyota · 1.8 Hybrid 2ZR-FXE",
        "verified": False,
        "cvt_probe": True,
        "cold_start_analysis": False,      # engine auto-stop breaks cold-start logic
        "norms": {},
    },
    "generic": {
        "label": "Any OBD-II gasoline car — standard Mode-01 telemetry only",
        "short": "OBD-II · generic gasoline",
        "verified": False,
        "cvt_probe": False,                # Toyota 21xx probe makes no sense here
        "cold_start_analysis": True,
        "norms": {},
    },
}
PROFILE_NAME = (os.environ.get("MYCAR_PROFILE") or "8nrfts").strip().lower()
PROFILE = PROFILES.get(PROFILE_NAME)
if PROFILE is None:
    raise SystemExit(f"Unknown MYCAR_PROFILE '{PROFILE_NAME}'. Known: {', '.join(PROFILES)}")

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

_BASE_NORMS = dict(NORMS)


def set_profile(name: str) -> None:
    """Switch vehicle profile (norms/CVT probe/analysis flags) at runtime."""
    global PROFILE_NAME, PROFILE, NORMS
    key = (name or "").strip().lower()
    prof = PROFILES.get(key)
    if prof is None:
        raise SystemExit(f"Unknown profile '{name}'. Known: {', '.join(PROFILES)}")
    PROFILE_NAME, PROFILE = key, prof
    NORMS = {**_BASE_NORMS, **prof["norms"]}


set_profile(PROFILE_NAME)  # apply the env-selected profile at import
