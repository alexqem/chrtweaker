"""Toyota C-HR NGX10 (2016-2020) ECU tweaks & service actions.

Single source of truth for Body (0x750), Combination Meter (0x7C0),
HVAC (0x7C4), ABS (0x7B0), and CVT/TCM (0x7E1) customizations.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime
from typing import Callable, Optional


@dataclass(frozen=True)
class TweakSpec:
    id: str
    title: str
    description: str
    ecu: str
    protocol: str
    kind: str  # "switch" (readable/writable) or "button" (one-shot / multi-value)
    read_cmd: Optional[str]
    write_on_cmd: str
    write_off_cmd: Optional[str] = None
    requires_vlinker: bool = True


TWEAKS: list[TweakSpec] = [
    TweakSpec(
        id="auto_fold_mirrors",
        title="Auto-Fold Mirrors",
        description="Fold/unfold side mirrors on lock/unlock (0x750 Body ECU)",
        ecu="0x750",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 21/3B",
        kind="switch",
        read_cmd="21A7",
        write_on_cmd="3BA701",
        write_off_cmd="3BA700",
    ),
    TweakSpec(
        id="comfort_open_windows",
        title="Remote Window Open",
        description="Hold Unlock on keyfob to open all windows (0x750 Body ECU)",
        ecu="0x750",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 21/3B",
        kind="switch",
        read_cmd="21B2",
        write_on_cmd="3BB201",
        write_off_cmd="3BB200",
    ),
    TweakSpec(
        id="comfort_close_windows",
        title="Remote Window Close",
        description="Hold Lock on keyfob to close all windows (0x750 Body ECU)",
        ecu="0x750",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 21/3B",
        kind="switch",
        read_cmd="21B3",
        write_on_cmd="3BB301",
        write_off_cmd="3BB300",
    ),
    TweakSpec(
        id="blink_hazards_lock",
        title="Blink Hazards on Lock",
        description="Flash hazard lights on remote lock/unlock (0x750 Body ECU)",
        ecu="0x750",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 21/3B",
        kind="switch",
        read_cmd="21A1",
        write_on_cmd="3BA101",
        write_off_cmd="3BA100",
    ),
    TweakSpec(
        id="beep_lock_unlock",
        title="Beep on Lock/Unlock",
        description="Audible buzzer chirp on remote lock/unlock (0x750 Body ECU)",
        ecu="0x750",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 21/3B",
        kind="switch",
        read_cmd="21A2",
        write_on_cmd="3BA201",
        write_off_cmd="3BA200",
    ),
    TweakSpec(
        id="eco_indicator",
        title="Eco Drive Indicator",
        description="Show Eco Drive light on Combination Meter (0x7C0)",
        ecu="0x7C0",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 21/3B",
        kind="switch",
        read_cmd="21C1",
        write_on_cmd="3BC101",
        write_off_cmd="3BC100",
    ),
    TweakSpec(
        id="ac_eco_mode",
        title="A/C Eco Mode",
        description="Enable Eco climate control profile (0x7C4 HVAC ECU)",
        ecu="0x7C4",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 21/3B",
        kind="switch",
        read_cmd="21D1",
        write_on_cmd="3BD101",
        write_off_cmd="3BD100",
    ),
    TweakSpec(
        id="rear_seatbelt_chime",
        title="Rear Seatbelt Chime",
        description="Enable/disable rear seatbelt warning chime (0x7C0 Meter)",
        ecu="0x7C0",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 21/3B",
        kind="switch",
        read_cmd="21C4",
        write_on_cmd="3BC401",
        write_off_cmd="3BC400",
    ),
    TweakSpec(
        id="turn_signal_3",
        title="One-Touch Turn Signal: 3 Flashes",
        description="Factory default lane-change blinker count (0x750 Body ECU)",
        ecu="0x750",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 3B",
        kind="button",
        read_cmd=None,
        write_on_cmd="3BA503",
    ),
    TweakSpec(
        id="turn_signal_5",
        title="One-Touch Turn Signal: 5 Flashes",
        description="Set lane-change blinker to 5 flashes (0x750 Body ECU)",
        ecu="0x750",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 3B",
        kind="button",
        read_cmd=None,
        write_on_cmd="3BA505",
    ),
    TweakSpec(
        id="turn_signal_7",
        title="One-Touch Turn Signal: 7 Flashes",
        description="Set lane-change blinker to 7 flashes (0x750 Body ECU)",
        ecu="0x750",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 3B",
        kind="button",
        read_cmd=None,
        write_on_cmd="3BA507",
    ),
    TweakSpec(
        id="abs_inspection",
        title="ABS Inspection Mode",
        description="Enter ABS/VSC maintenance inspection mode (0x7B0 ABS ECU)",
        ecu="0x7B0",
        protocol="ISO 15765-4 CAN (ATSP6) · UDS 10 03",
        kind="button",
        read_cmd=None,
        write_on_cmd="31010200",
    ),
    TweakSpec(
        id="atf_reset",
        title="CVT / ATF Thermal Reset",
        description="Reset K114 CVT fluid thermal degradation counter (0x7E1 TCM)",
        ecu="0x7E1",
        protocol="ISO 15765-4 CAN (ATSP6) · UDS 31 01",
        kind="button",
        read_cmd=None,
        write_on_cmd="31010F00",
    ),
]

TWEAK_MAP: dict[str, TweakSpec] = {t.id: t for t in TWEAKS}


def list_tweaks() -> list[dict]:
    return [asdict(t) for t in TWEAKS]


def execute_tweak(
    tweak_id: str,
    enabled: bool = True,
    elm_send_fn: Optional[Callable[[str], str]] = None,
) -> dict:
    spec = TWEAK_MAP.get(tweak_id)
    ts = datetime.now().strftime("%H:%M:%S")
    if spec is None:
        return {
            "ok": False,
            "tweak_id": tweak_id,
            "message": f"Unknown tweak: {tweak_id}",
            "log_line": f"[{ts}] ERROR unknown tweak '{tweak_id}'",
        }

    cmd = spec.write_on_cmd if enabled or spec.write_off_cmd is None else spec.write_off_cmd
    header_cmd = f"ATSH {spec.ecu.replace('0x', '')}"

    if elm_send_fn is None:
        # Offline / simulator mode: return deterministic mock positive response
        pos_resp = f"{hex(int(cmd[:2], 16) + 0x40)[2:].upper()}{cmd[2:4]}"
        log_line = (
            f"[{ts}] SIM | {spec.protocol} | {header_cmd} -> OK | "
            f"TX: {cmd} | RX: {pos_resp}"
        )
        return {
            "ok": True,
            "tweak_id": spec.id,
            "ecu": spec.ecu,
            "protocol": spec.protocol,
            "command": f"{header_cmd} ; {cmd}",
            "response": pos_resp,
            "message": f"{spec.title}: applied (simulator)",
            "log_line": log_line,
        }

    try:
        elm_send_fn(header_cmd)
        raw_resp = elm_send_fn(cmd).strip()
        is_err = any(m in raw_resp.upper() for m in ("7F", "ERROR", "NO DATA", "UNABLE", "?"))
        ok = not is_err
        log_line = (
            f"[{ts}] {'OK' if ok else 'FAIL'} | {spec.protocol} | {header_cmd} | "
            f"TX: {cmd} | RX: {raw_resp}"
        )
        return {
            "ok": ok,
            "tweak_id": spec.id,
            "ecu": spec.ecu,
            "protocol": spec.protocol,
            "command": f"{header_cmd} ; {cmd}",
            "response": raw_resp,
            "message": f"{spec.title}: {'succeeded' if ok else 'rejected (' + raw_resp + ')'}",
            "log_line": log_line,
        }
    except Exception as exc:
        log_line = f"[{ts}] EXC | {spec.protocol} | {header_cmd} | TX: {cmd} | ERR: {exc}"
        return {
            "ok": False,
            "tweak_id": spec.id,
            "ecu": spec.ecu,
            "protocol": spec.protocol,
            "command": f"{header_cmd} ; {cmd}",
            "response": str(exc),
            "message": f"{spec.title}: error ({exc})",
            "log_line": log_line,
        }
