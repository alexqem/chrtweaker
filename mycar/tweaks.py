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
    verified: bool = True


def _load_tweaks() -> list[TweakSpec]:
    tweaks_path = ASSETS_DIR / "tweaks.json"
    if not tweaks_path.exists():
        raise FileNotFoundError(
            f"Missing {tweaks_path}. Run from repo root; shared JSON lives in android/app/src/main/assets/"
        )
    raw = json.loads(tweaks_path.read_text(encoding="utf-8"))
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
            verified=bool(item.get("verified", True)),
        )
        for item in raw
    ]


TWEAKS: list[TweakSpec] = _load_tweaks()

TWEAK_MAP: dict[str, TweakSpec] = {t.id: t for t in TWEAKS}


def list_tweaks() -> list[dict]:
    return [asdict(t) for t in TWEAKS]


def _parse_read_byte(read_cmd: str, raw_reply: str) -> Optional[str]:
    upper = raw_reply.upper()
    if any(m in upper for m in ("7F", "ERROR", "NO DATA", "UNABLE", "STOPPED", "?")):
        return None
    if len(read_cmd) < 4:
        return None
    svc = f"{int(read_cmd[:2], 16) + 0x40:02X}"
    pid = read_cmd[2:4].upper()
    tokens = raw_reply.replace("\r", " ").replace("\n", " ").split()
    tokens = [t.upper() for t in tokens]
    for i, tok in enumerate(tokens):
        if tok == svc and i + 2 < len(tokens) and tokens[i + 1] == pid:
            try:
                return f"{int(tokens[i + 2], 16):02X}"
            except ValueError:
                pass
        if tok == f"{svc}{pid}" and i + 1 < len(tokens):
            try:
                return f"{int(tokens[i + 1], 16):02X}"
            except ValueError:
                pass
    return None


def read_all_switches(elm_send_fn: Optional[Callable[[str], str]] = None) -> dict:
    """Read current ON/OFF state and ECU support for all switch tweaks."""
    states: dict[str, bool] = {}
    supported: dict[str, bool] = {}
    logs: list[str] = []
    ts = datetime.now().strftime("%H:%M:%S")
    switches = [t for t in TWEAKS if t.kind == "switch" and t.read_cmd]
    if elm_send_fn is None:
        return {"states": states, "supported": supported, "logs": [f"[{ts}] OFFLINE | no ELM connection"]}

    current_hdr = None
    try:
        for spec in switches:
            hdr = spec.ecu.replace("0x", "")
            if current_hdr != hdr:
                elm_send_fn(f"ATSH {hdr}")
                current_hdr = hdr
            raw = (elm_send_fn(spec.read_cmd) or "").strip()
            byte_hex = _parse_read_byte(spec.read_cmd, raw)
            if byte_hex is not None:
                is_on = byte_hex == spec.write_on_cmd[-2:].upper()
                supported[spec.id] = True
                states[spec.id] = is_on
                logs.append(f"[{ts}] READ | {spec.id} | {spec.read_cmd} -> {byte_hex} ({'ON' if is_on else 'OFF'})")
            else:
                supported[spec.id] = False
                logs.append(f"[{ts}] UNSUPPORTED | {spec.id} | {spec.read_cmd} -> {raw}")
    finally:
        try:
            elm_send_fn("ATSH 7DF")
        except Exception:
            pass
    return {"states": states, "supported": supported, "logs": logs}


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
