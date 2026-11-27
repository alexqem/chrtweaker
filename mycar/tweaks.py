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
        title="Автоскладывание зеркал",
        description="Зеркала сами складываются при закрытии машины",
        ecu="0x750",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 21/3B",
        kind="switch",
        read_cmd="21A7",
        write_on_cmd="3BA701",
        write_off_cmd="3BA700",
    ),
    TweakSpec(
        id="comfort_open_windows",
        title="Открыть окна с ключа",
        description="Удерживайте кнопку открытия на брелоке, чтобы опустить все стёкла",
        ecu="0x750",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 21/3B",
        kind="switch",
        read_cmd="21B2",
        write_on_cmd="3BB201",
        write_off_cmd="3BB200",
    ),
    TweakSpec(
        id="comfort_close_windows",
        title="Закрыть окна с ключа",
        description="Удерживайте кнопку закрытия на брелоке, чтобы поднять все стёкла",
        ecu="0x750",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 21/3B",
        kind="switch",
        read_cmd="21B3",
        write_on_cmd="3BB301",
        write_off_cmd="3BB300",
    ),
    TweakSpec(
        id="blink_hazards_lock",
        title="Мигание при закрытии",
        description="Аварийка мигает, когда вы закрываете или открываете машину",
        ecu="0x750",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 21/3B",
        kind="switch",
        read_cmd="21A1",
        write_on_cmd="3BA101",
        write_off_cmd="3BA100",
    ),
    TweakSpec(
        id="beep_lock_unlock",
        title="Звук при закрытии",
        description="Короткий писк при постановке на охрану и снятии с неё",
        ecu="0x750",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 21/3B",
        kind="switch",
        read_cmd="21A2",
        write_on_cmd="3BA201",
        write_off_cmd="3BA200",
    ),
    TweakSpec(
        id="eco_indicator",
        title="Подсказка ECO на приборке",
        description="Показывает зелёный значок ECO при экономной езде",
        ecu="0x7C0",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 21/3B",
        kind="switch",
        read_cmd="21C1",
        write_on_cmd="3BC101",
        write_off_cmd="3BC100",
    ),
    TweakSpec(
        id="ac_eco_mode",
        title="Эко-режим кондиционера",
        description="Кондиционер работает мягче, чтобы экономить топливо",
        ecu="0x7C4",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 21/3B",
        kind="switch",
        read_cmd="21D1",
        write_on_cmd="3BD101",
        write_off_cmd="3BD100",
    ),
    TweakSpec(
        id="rear_seatbelt_chime",
        title="Пищалка задних ремней",
        description="Звуковое напоминание, если сзади не пристегнулись",
        ecu="0x7C0",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 21/3B",
        kind="switch",
        read_cmd="21C4",
        write_on_cmd="3BC401",
        write_off_cmd="3BC400",
    ),
    TweakSpec(
        id="turn_signal_3",
        title="Поворотник: 3 мигания",
        description="Заводской режим при лёгком касании рычага",
        ecu="0x750",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 3B",
        kind="button",
        read_cmd=None,
        write_on_cmd="3BA503",
    ),
    TweakSpec(
        id="turn_signal_4",
        title="Поворотник: 4 мигания",
        description="4 мигания при лёгком касании рычага",
        ecu="0x750",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 3B",
        kind="button",
        read_cmd=None,
        write_on_cmd="3BA504",
    ),
    TweakSpec(
        id="turn_signal_5",
        title="Поворотник: 5 миганий",
        description="Удобно для перестроения в городе — мигает 5 раз",
        ecu="0x750",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 3B",
        kind="button",
        read_cmd=None,
        write_on_cmd="3BA505",
    ),
    TweakSpec(
        id="turn_signal_6",
        title="Поворотник: 6 миганий",
        description="6 миганий при лёгком касании рычага",
        ecu="0x750",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 3B",
        kind="button",
        read_cmd=None,
        write_on_cmd="3BA506",
    ),
    TweakSpec(
        id="turn_signal_7",
        title="Поворотник: 7 миганий",
        description="Увеличенное время сигнала при перестроении",
        ecu="0x750",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 3B",
        kind="button",
        read_cmd=None,
        write_on_cmd="3BA507",
    ),
    TweakSpec(
        id="turn_signal_9",
        title="Поворотник: 9 миганий",
        description="9 миганий при лёгком касании рычага",
        ecu="0x750",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 3B",
        kind="button",
        read_cmd=None,
        write_on_cmd="3BA509",
    ),
    TweakSpec(
        id="turn_signal_11",
        title="Поворотник: 11 миганий",
        description="Максимум (11 миганий) при лёгком касании рычага",
        ecu="0x750",
        protocol="ISO 15765-4 CAN (ATSP6) · KWP 3B",
        kind="button",
        read_cmd=None,
        write_on_cmd="3BA50B",
    ),
    TweakSpec(
        id="abs_inspection",
        title="Сервисный режим ABS",
        description="Только для СТО: проверка тормозов и ABS на стенде",
        ecu="0x7B0",
        protocol="ISO 15765-4 CAN (ATSP6) · UDS 10 03",
        kind="button",
        read_cmd=None,
        write_on_cmd="31010200",
    ),
    TweakSpec(
        id="atf_reset",
        title="Сброс счётчика масла CVT",
        description="Нажимать только после замены масла в вариаторе",
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

    last_exc = None
    for attempt in range(2):
        try:
            elm_send_fn(header_cmd)
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
