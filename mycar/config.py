"""Car profile, paths, thresholds. Single source of truth (DRY).

Machine-specific secrets (the dongle's BLE address) live in .env — see
.env.example. Never commit .env.
"""

import json
import os
import warnings
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
ASSETS_DIR = PROJECT_ROOT / "android" / "app" / "src" / "main" / "assets"
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
def _load_profiles_and_norms() -> tuple[dict, dict]:
    """Load vehicle profiles and base norms from shared Android assets JSON."""
    profiles_path = ASSETS_DIR / "profiles.json"
    if not profiles_path.exists():
        raise FileNotFoundError(
            f"Missing {profiles_path}. Run from repo root; shared JSON lives in android/app/src/main/assets/"
        )
    raw = json.loads(profiles_path.read_text(encoding="utf-8"))
    base_norms = {
        k: (tuple(v) if isinstance(v, list) else v)
        for k, v in raw["base_norms"].items()
    }
    profiles = {}
    for p in raw["profiles"]:
        pid = p["id"]
        profiles[pid] = {
            "label": p["label"],
            "short": p["short"],
            "verified": bool(p.get("verified", False)),
            "cvt_probe": bool(p.get("cvt_probe", True)),
            "hybrid_probe": bool(p.get("hybrid_probe", False)),
            "cold_start_analysis": bool(p.get("cold_start_analysis", True)),
            "vin_prefixes": list(p.get("vin_prefixes", [])),
            "calid_prefixes": list(p.get("calid_prefixes", [])),
            "norms": {
                k: (tuple(v) if isinstance(v, list) else v)
                for k, v in p.get("norms", {}).items()
            },
        }
    return profiles, base_norms


PROFILES, NORMS = _load_profiles_and_norms()
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

# --- Reference norms (loaded from profiles.json, overridden per profile) ---
_BASE_NORMS = dict(NORMS)


def resolve_profile(name: str | None) -> tuple[str, dict]:
    """Resolve profile by name, falling back to 'generic' for unknown profiles."""
    key = (name or "").strip().lower()
    if key in PROFILES:
        return key, PROFILES[key]
    return "generic", PROFILES["generic"]


def set_profile(name: str) -> None:
    """Switch vehicle profile (norms/CVT probe/analysis flags) at runtime."""
    global PROFILE_NAME, PROFILE, NORMS
    raw_key = (name or "").strip().lower()
    if raw_key not in PROFILES:
        warnings.warn(
            f"Unknown profile '{name}' — falling back to 'generic'. Known: {', '.join(PROFILES)}",
            stacklevel=2,
        )
    PROFILE_NAME, PROFILE = resolve_profile(name)
    NORMS = {**_BASE_NORMS, **PROFILE["norms"]}


set_profile(PROFILE_NAME)  # apply the env-selected profile at import
