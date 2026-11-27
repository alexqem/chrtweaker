"""Toyota C-HR NGX10 (2016-2020) ECU tweaks & service actions.

Single source of truth for Body (0x750), Combination Meter (0x7C0),
HVAC (0x7C4), ABS (0x7B0), and CVT/TCM (0x7E1) customizations.
"""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from datetime import datetime
from typing import Callable, Optional

from mycar.config import ASSETS_DIR


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
    session_cmd: Optional[str] = None


def _load_tweaks() -> list[TweakSpec]:
    raw = json.loads((ASSETS_DIR / "tweaks.json").read_text(encoding="utf-8"))
    return [
        TweakSpec(
            id=item["id"],
            title=item["title"],
            description=item["description"],
            ecu=item["ecu"],
            protocol=item["protocol"],
            kind=item["kind"],
            read_cmd=item.get("read_cmd"),
            write_on_cmd=item["write_on_cmd"],
            write_off_cmd=item.get("write_off_cmd"),
            requires_vlinker=bool(item.get("requires_vlinker", True)),
            session_cmd=item.get("session_cmd"),
        )
        for item in raw
    ]


TWEAKS: list[TweakSpec] = _load_tweaks()

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

    last_exc = None
    for attempt in range(2):
        try:
            elm_send_fn(header_cmd)
            if spec.session_cmd:
                elm_send_fn(spec.session_cmd)
            raw_resp = elm_send_fn(cmd).strip()
            is_err = any(m in raw_resp.upper() for m in ("7F", "ERROR", "NO DATA", "UNABLE", "STOPPED", "?"))
            ok = bool(raw_resp) and not is_err  # empty reply to a CAN write is a failure, not success
            if ok or attempt == 1:
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
                    "message": f"{spec.title}: {'выполнено' if ok else 'ошибка (' + raw_resp + ')'}",
                    "log_line": log_line,
                }
        except Exception as exc:
            last_exc = exc
        import time
        time.sleep(0.25)

    log_line = f"[{ts}] EXC | {spec.protocol} | {header_cmd} | TX: {cmd} | ERR: {last_exc}"
    return {
        "ok": False,
        "tweak_id": spec.id,
        "ecu": spec.ecu,
        "protocol": spec.protocol,
        "command": f"{header_cmd} ; {cmd}",
        "response": str(last_exc),
        "message": f"{spec.title}: error ({last_exc})",
        "log_line": log_line,
    }
