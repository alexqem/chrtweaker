"""Toyota knowledge distilled from the Car Scanner reverse in ../rea.

Everything here is offline and OPTIONAL: Mode-21 decode variants, multi-request
parsing, DTC decode + lookup. The standard capture path keeps working even if
every experimental query in this module returns nothing.

Sources (rea/):
  docs/toyota_pids_catalog.md B0 — 21D9/2182/21AA/21F1/21F3 variants (SBI/DL/formula)
  docs/toyota_code_snippets.md   — decode_pid_value / decode_dtc / parse_mode03 / multi
  docs/toyota_dtc_protocol.md    — DTC CAN format (count byte after 43), ECU names
  docs/toyota_commands.md §1.3   — functional 7DF workaround for ATSH-less clones
  decompiled/...decompiled.cs    — DTCDecoder.DecodeCAN (count-first confirmed)

EXPERIMENTAL: no Mode-21 query has been seen answering on the actual car+dongle
yet (ATSH is a stub on the clone; functional 7DF for mode 21 is untested).
Decoders are candidate-based and raw bytes are always preserved (cvt_raw CSV
column) so reality can be checked against these tables later.
"""

import json
import re
from enum import IntEnum, StrEnum
from pathlib import Path

DATA_DIR = Path(__file__).parent / "data"


class ObdMode(IntEnum):
    """SAE J1979 / Toyota modes this project touches."""
    LIVE_DATA = 0x01
    FREEZE_FRAME = 0x02
    STORED_DTC = 0x03
    CLEAR_DTC = 0x04
    PENDING_DTC = 0x07
    VEHICLE_INFO = 0x09
    PERMANENT_DTC = 0x0A
    TOYOTA_EXT = 0x21


class DtcSystem(StrEnum):
    """First letter of a DTC code (DTCItemV2 two-bit prefix)."""
    POWERTRAIN = "P"
    CHASSIS = "C"
    BODY = "B"
    NETWORK = "U"


POSITIVE_RESPONSE_OFFSET = 0x40  # request service + 0x40 = positive response echo

# ---------------------------------------------------------------- low level


def hex_tokens(reply: str) -> list[str]:
    """All 2-hex-digit byte tokens of an ELM reply (ATH0 world), uppercased.

    Noise words (SEARCHING..., OK, ?) are dropped; 3-digit CAN headers are
    NOT matched here — payload parsing runs with ATH0 (no headers).
    """
    return [t for t in re.split(r"[^0-9A-Fa-f]+", reply.upper())
            if len(t) == 2 and re.fullmatch(r"[0-9A-F]{2}", t)]


def parse_full_payload(reply: str, marker: str) -> list[int] | None:
    """Bytes after the positive-response echo (e.g. marker '61D9').

    If two ECUs answer (7E8 + 7E9), the payload stops at the second echo.
    """
    m = (marker[0:2], marker[2:4])
    toks = hex_tokens(reply)
    for i in range(len(toks) - 1):
        if toks[i] == m[0] and toks[i + 1] == m[1]:
            rest = toks[i + 2:]
            for j in range(len(rest) - 1):
                if rest[j] == m[0] and rest[j + 1] == m[1]:
                    rest = rest[:j]
                    break
            return [int(t, 16) for t in rest]
    return None


def decode_pid_value(payload, sbi, dl, *, mul=1.0, div=1.0, ofs=0.0,
                     signed=False) -> float:
    """Generic Car Scanner ByteSet decode: value = raw*MUL/DIV + OFS.

    sbi = start byte index in payload (AFTER the response marker),
    dl = data length in bytes (1..4; 0 = to the end).
    """
    n = dl if dl else len(payload) - sbi
    n = min(n, 4)
    b = bytes(payload[sbi:sbi + n])
    raw = int.from_bytes(b, "big", signed=signed)
    return raw * mul / div + ofs


def marker_for(cmd: str) -> str:
    """Positive-response echo for a request: '01B4'->'41B4', '21D9'->'61D9',
    '0902'->'4902', '03'->'43' (service + POSITIVE_RESPONSE_OFFSET, then PID)."""
    return f"{int(cmd[:2], 16) + POSITIVE_RESPONSE_OFFSET:02X}" + cmd[2:]


# ------------------------------------------- CVT / ATF temperature (Mode 21)

def _legacy_007(raw: int) -> float:
    # obd2australia 2182 variant (kept as a candidate, never confirmed here)
    return ((raw * 0.07 - 400) / 10 - 32) * 5 / 9


def _atf_variants(cmd: str) -> list[tuple[str, int, int, object]]:
    """(label, sbi, dl, formula) in documented priority for a JDM C-HR."""
    if cmd == "01B4":
        # Mode-01 extension, goes over functional 7DF like any 01xx — the
        # clone-friendly path (rea: OBD-II/EOBD ~2010-2022 CAN profile)
        return [
            ("01B4 v.15 · SBI=0 DL=2 · raw/256-40", 0, 2, lambda r: r / 256 - 40),
            ("01B4 v.16 · SBI=0 DL=1 · raw-40", 0, 1, lambda r: r - 40),
        ]
    if cmd == "21D9":
        return [
            ("JOBD JDM CAN · SBI=4 DL=2 · raw/256-40", 4, 2, lambda r: r / 256 - 40),
            ("Vitz CVT · SBI=3 DL=1 · raw-40", 3, 1, lambda r: r - 40),
            ("JOBD var · SBI=0 DL=2 · raw/256-40", 0, 2, lambda r: r / 256 - 40),
            ("JOBD var · SBI=2 DL=2 · raw/256-40", 2, 2, lambda r: r / 256 - 40),
            ("JOBD var · SBI=6 DL=2 · raw/256-40", 6, 2, lambda r: r / 256 - 40),
        ]
    if cmd == "221627":
        return [
            ("221627 TNGA UDS · SBI=0 DL=2 · raw/256-40", 0, 2, lambda r: r / 256 - 40),
        ]
    return [
        ("2182 Pan · SBI=0 DL=2 · raw/256-40", 0, 2, lambda r: r / 256 - 40),
        ("2182 Post-Converter · SBI=2 DL=2 · raw/256-40", 2, 2, lambda r: r / 256 - 40),
        ("2182 obd2australia · SBI=0 DL=2 · 0.07F->C", 0, 2, _legacy_007),
    ]


# plausible decoded ATF/CVT range, °C (candidate filter)
ATF_PLAUSIBLE_C = (-40.0, 160.0)


def decode_atf(cmd: str, payload: list[int]) -> tuple[float | None, list[tuple[str, float]]]:
    """All plausible (−40..160 °C) decode candidates for a Mode-21 ATF reply.

    Returns (primary_value, [(label, value), ...]) — primary is the first
    documented variant that yields a physically plausible number.
    """
    lo, hi = ATF_PLAUSIBLE_C
    cands = []
    for label, sbi, dl, fn in _atf_variants(cmd):
        if len(payload) < sbi + dl:
            continue
        raw = int.from_bytes(bytes(payload[sbi:sbi + dl]), "big")
        v = round(fn(raw), 1)
        if lo <= v <= hi:
            cands.append((label, v))
    return (cands[0][1] if cands else None), cands


# commands tried for the cvt_temp column, in order (functional 7DF — no ATSH).
# 01B4 first: it is a plain Mode-01 request — the transport this clone handles
# perfectly (rea: Toyota OBD-II/EOBD ~2010-2022 CAN profile).
CVT_CMDS = ("01B4", "21D9", "2182", "221627")


def probe_cvt(conn, physical: bool = False) -> tuple[float | None, str | None]:
    """One best-effort CVT/ATF query. conn needs .query_raw()/.send().

    Tries the documented functional-addressing commands (01B4 first — Mode-01,
    then 21D9/2182). The first command that answers is latched on the
    connection object and tried first on later polls. `physical=True`
    additionally tries the same commands with ATSH7E0 (real adapters) and
    always restores 7DF. Never raises on protocol level — returns (None, None).
    """
    order = [c for c in CVT_CMDS]
    hint = getattr(conn, "_cvt_cmd", None)
    if hint in order:
        order.remove(hint)
        order.insert(0, hint)

    for cmd in order:
        r = conn.query_raw(cmd)
        payload = parse_full_payload(r, marker_for(cmd)) if r else None
        if payload:
            conn._cvt_cmd = cmd
            val, _ = decode_atf(cmd, payload)
            return val, bytes(payload).hex()

    if physical:
        try:
            if "OK" in (conn.send("ATSH7E0") or ""):
                for cmd in order:
                    r = conn.query_raw(cmd)
                    payload = parse_full_payload(r, marker_for(cmd)) if r else None
                    if payload:
                        conn._cvt_cmd = cmd
                        val, _ = decode_atf(cmd, payload)
                        return val, bytes(payload).hex()
        finally:
            conn.send("ATSH7DF")
    return None, None


def _parse_ecu_ascii(raw: str | None, *header_bytes: str) -> str | None:
    """Extract printable ASCII from a UDS (62 F1 90) or KWP (5A 88 01) reply."""
    if not raw:
        return None
    tokens = [t.upper() for t in raw.replace("\r", " ").replace("\n", " ").split()]
    h_upper = [h.upper() for h in header_bytes]
    match_idx = -1
    for i in range(len(tokens) - len(h_upper) + 1):
        if tokens[i:i + len(h_upper)] == h_upper:
            match_idx = i + len(h_upper)
            break
    if match_idx == -1:
        compact = "".join(h_upper)
        for i, tok in enumerate(tokens):
            if tok == compact:
                match_idx = i + 1
                break
    if match_idx == -1 or match_idx >= len(tokens):
        return None
    chars = []
    for tok in tokens[match_idx:]:
        try:
            b = int(tok, 16)
        except ValueError:
            continue
        if 0x20 <= b <= 0x7E:
            chars.append(chr(b))
    text = "".join(chars).strip()
    return text or None


def probe_ecu_versions(conn, physical: bool = False, hybrid: bool = False) -> dict[str, str]:
    """Read ECU part numbers via UDS 22F190 (with 3E00+1001) or KWP 1A8801."""
    if not physical:
        return {}
    addrs = ["7C0", "700", "750", "7C4", "7B0", "7A0", "780", "730"]
    if hybrid:
        addrs.extend(["7D2", "7E2"])
    versions: dict[str, str] = {}
    try:
        for hdr in addrs:
            conn.send(f"ATSH{hdr}")
            conn.send("3E00")
            conn.send("1001")
            r22 = conn.query_raw("22F190")
            ascii22 = _parse_ecu_ascii(r22, "62", "F1", "90")
            if ascii22:
                versions[hdr] = ascii22
                continue
            r1a = conn.query_raw("1A8801")
            ascii1a = _parse_ecu_ascii(r1a, "5A", "88", "01")
            if ascii1a:
                versions[hdr] = ascii1a
    finally:
        conn.send("ATSH7DF")
    return versions


def probe_extended_capabilities(conn, physical: bool = False, hybrid: bool = False) -> dict[str, str]:
    """Probe extended ECU PIDs from shared profiles.json when physical ATSH works."""
    if not physical:
        return {}
    from mycar import config

    found: dict[str, str] = {}
    by_hdr: dict[str, list[dict]] = {}
    for p in config.EXTENDED_PROBES:
        if p.get("hybrid_only") and not hybrid:
            continue
        by_hdr.setdefault(p["header"], []).append(p)

    try:
        for hdr, probes in by_hdr.items():
            # ELM clones ack ATSH with OK even when stubbed; real check is query_raw below
            conn.send(f"ATSH{hdr}")
            for p in probes:
                note = "verified" if p.get("verified") else "platform-twin candidate, not verified on NGX10"
                r = conn.query_raw(p["cmd"])
                if r:
                    found[f"{hdr}:{p['cmd']}"] = f"{p['name']} ({note})={r.strip()}"
    finally:
        conn.send("ATSH7DF")
    return found


# ----------------------------------------------- multi-request (Mode 01)

MAX_MULTI_PIDS = 6  # Car Scanner OBDRequestQueueOptimizer.OptimizeV2 limit


def multi_groups(poll: list[tuple], size: int = MAX_MULTI_PIDS) -> list[list[tuple[str, int]]]:
    """[(name, pid, n), ...] -> groups of (pid, n) for query_multi()."""
    return [[(pid, n) for _, pid, n in poll[i:i + size]]
            for i in range(0, len(poll), size)]


def query_multi(conn, spec: list[tuple[str, int]]) -> dict[str, list[int]] | None:
    """One CAN request for several Mode-01 PIDs: ['010C','010D'] -> '010C0D0F'.

    Returns {pid: data_bytes} or None if the adapter/ECU did not answer in the
    expected shape (caller falls back to one-PID queries). spec entries are
    (pid_hex, n_data_bytes); the request echoes each PID in order
    (41 0C <2B> 0D <1B> ...).
    """
    cmd = "01" + "".join(pid for pid, _ in spec)
    r = conn.query_raw(cmd)
    if not r:
        return None
    return parse_multi(r, spec)


def parse_multi(reply: str, spec: list[tuple[str, int]]) -> dict[str, list[int]] | None:
    """Parse a multi-PID reply; tolerate line wraps and two ECUs answering."""
    toks = hex_tokens(reply)
    for i, t in enumerate(toks):
        if t != "41":
            continue
        pos, out, ok = i + 1, {}, True
        for pid, nd in spec:
            if pos >= len(toks) or toks[pos] != pid.upper():
                ok = False
                break
            pos += 1
            if pos + nd > len(toks):
                ok = False
                break
            out[pid] = [int(x, 16) for x in toks[pos:pos + nd]]
            pos += nd
        if ok and out:
            return out
    return None


# ----------------------------------------------------------------- DTC

ECU_NAMES = {  # CAN11bitHelper.GetECUName — Toyota block (rea/docs/toyota_dtc_protocol.md §5)
    "7E8": "Engine (ECM)",
    "7E9": "Transmission (TCM)",
    "7B8": "ABS",
    "788": "SRS/Airbag",
    "7C8": "Dashboard",
    "758": "Gateway",
    "7CC": "Climate",
    "7A9": "EMPS (power steering)",
    "799": "Radar cruise",
    "798": "Radar cruise",
    "72E": "Start/stop",
}

_DTC_SYSTEMS = (DtcSystem.POWERTRAIN, DtcSystem.CHASSIS,
                DtcSystem.BODY, DtcSystem.NETWORK)


# --------------------------------------------------------- DTC read modes

# (Mode 03 stored / 07 pending / 0A permanent) as hex strings
DTC_MODES = (f"{ObdMode.STORED_DTC:02X}",
             f"{ObdMode.PENDING_DTC:02X}",
             f"{ObdMode.PERMANENT_DTC:02X}")


def decode_dtc(pair: tuple[int, int] | bytes) -> str:
    """2 raw bytes -> 'P0133' (DTCItemV2.DecodeRawCode)."""
    b0, b1 = pair[0], pair[1]
    return (f"{_DTC_SYSTEMS[(b0 >> 6) & 0b11]}"
            f"{(b0 >> 4) & 0b11}{b0 & 0xF:X}{(b1 >> 4) & 0xF:X}{b1 & 0xF:X}")


def _pairs_from_bytes(b: list[int]) -> list[tuple[int, int]]:
    out = []
    for i in range(0, len(b) - 1, 2):
        p = (b[i], b[i + 1])
        if p not in ((0x00, 0x00), (0xAA, 0xAA)):
            out.append(p)
    return out


def parse_dtc_block(tokens: list[str]) -> list[tuple[int, int]]:
    """Byte tokens after the '43'/'47'/'4A' marker -> DTC byte pairs.

    CAN format is count-first (confirmed in DTCDecoder.DecodeCAN): one count
    byte, then 2-byte codes, 00 00 / AA AA skipped. If the count does not fit
    the block (firmware variance on clones) fall back to plain pairs.
    """
    b = [int(t, 16) for t in tokens if re.fullmatch(r"[0-9A-F]{2}", t)]
    if not b:
        return []
    count = b[0]
    if 1 <= count <= 8 and len(b) >= 1 + 2 * count:
        ps = [(b[1 + 2 * i], b[2 + 2 * i]) for i in range(count)]
        if all(p not in ((0x00, 0x00), (0xAA, 0xAA)) for p in ps):
            return ps
        return [p for p in ps if p not in ((0x00, 0x00), (0xAA, 0xAA))]
    return _pairs_from_bytes(b[1:] if len(b) % 2 else b)


def parse_dtc_reply(reply: str, marker: str = "43") -> list[tuple[str, str]]:
    """Full ELM reply (ATH1: '7E8 43 01 33 ...') -> [(ecu_header, 'P0133'), ...].

    Handles line wraps (continuation lines join the previous block) and
    several ECUs answering. Unknown headers are kept as-is.
    """
    result: list[tuple[str, str]] = []
    ecu = None           # last seen CAN header
    block: list[str] | None = None
    block_ecu = None     # ECU the current block belongs to
    for line in reply.splitlines():
        toks = [t.upper() for t in re.split(r"[^0-9A-Fa-f]+", line) if t]
        toks = [t for t in toks if re.fullmatch(r"[0-9A-F]{2,4}", t)]
        if not toks:
            continue
        i = 0
        if re.fullmatch(r"[0-9A-F]{3,4}", toks[0]) and len(toks) > 1:
            ecu = toks[0][-3:]
            i = 1
        rest = toks[i:]
        if marker in rest:
            j = rest.index(marker)
            if block:
                result += [(block_ecu, decode_dtc(p)) for p in parse_dtc_block(block)]
            block, block_ecu = rest[j + 1:], ecu
        elif block is not None:
            block += rest  # wrapped continuation
        # else: stray line before any marker — ignore
    if block:
        result += [(block_ecu, decode_dtc(p)) for p in parse_dtc_block(block)]
    return result


_DTC_DB = None


def load_dtc_db() -> dict[str, dict]:
    """code -> {'toyota':..., 'generic':..., 'ru':...} (lazy, ~0.6 MB)."""
    global _DTC_DB
    if _DTC_DB is None:
        raw = json.loads((DATA_DIR / "toyota_dtc.json").read_text(encoding="utf-8"))
        _DTC_DB = {e["code"]: e for e in raw}
    return _DTC_DB


def dtc_lookup(code: str) -> dict | None:
    """Lookup with Toyota-priority: Toyota text wins over generic (P1xxx trap:
    generic DB describes P1346 as fuel level, Toyota means VVT)."""
    e = load_dtc_db().get(code.upper())
    if not e:
        return None
    return {
        "text": e.get("toyota") or e.get("generic") or "",
        "generic_text": e.get("generic") if e.get("toyota") else "",
        "ru": e.get("ru"),
    }


# ------------------------------------------- supported PIDs (Mode 01 masks)

SUPPORTED_NAMES = {  # известные PID для подписи в отчёте probe
    0x03: "fuel system status", 0x04: "load", 0x05: "coolant", 0x06: "STFT",
    0x07: "LTFT", 0x0B: "MAP", 0x0C: "rpm", 0x0D: "speed", 0x0E: "timing",
    0x0F: "IAT", 0x10: "MAF", 0x11: "throttle", 0x1F: "runtime",
    0x21: "distance with MIL", 0x23: "rail pressure (D-4T)", 0x2F: "fuel level",
    0x33: "baro", 0x3C: "catalyst temp B1S1", 0x42: "voltage", 0x45: "throttle rel",
    0x46: "ambient temp", 0x5C: "oil temp", 0x5D: "injection timing",
    0x5E: "fuel rate", 0xB4: "ATF/CVT temp (Toyota ext.)",
}


def parse_supported_pids(data, base_pid: int = 0) -> list[int]:
    """4-byte bitmask -> PIDs it declares. Bit 0 (MSB of byte 0) = base+1."""
    supported = []
    for i in range(32):
        if (data[i // 8] >> (7 - i % 8)) & 1:
            supported.append(base_pid + i + 1)
    return supported


def scan_supported(conn, max_blocks: int = 7) -> list[int]:
    """Walk the 0100/0120/0140/… bitmask chain (functional, no ATSH).

    Stops when a block declares no next block (PID base+0x20) or does not
    answer. Returns all supported PIDs (0x01..0xFF range).
    """
    out: list[int] = []
    base = 0x00
    for _ in range(max_blocks):
        r = conn.query_raw(f"01{base:02X}")
        payload = parse_full_payload(r, marker_for(f"01{base:02X}")) if r else None
        if not payload or len(payload) < 4:
            break
        pids = parse_supported_pids(payload[:4], base)
        out += pids
        if base + 0x20 not in pids:
            break
        base += 0x20
    return out


# ------------------------------------------------- car identity (Mode 09)

def assemble_ecu(reply: str) -> dict[str, list[int]]:
    """ATH1 reply -> {ecu_header: message bytes}, ISO-TP reassembled PER ECU.

    The clone prints one CAN frame per line ('7E8 10 13 49 04 …', '7E8 21 …')
    and interleaves the streams of answering ECUs — parsing ATH0 text as one
    message truncates (real case: CalID cut to '896'). A header-less reply
    (sim / ATH0) is returned whole under key ''.
    """
    frames: list[tuple[str, int, list[int]]] = []
    for line in reply.replace("\r", "\n").split("\n"):
        toks = [t for t in re.split(r"[^0-9A-Fa-f]+", line.upper()) if t]
        if not toks:
            continue
        if len(toks[0]) == 3:  # '7E8' CAN header -> ATH1 frame line
            try:
                pci = int(toks[1], 16)
                data = [int(t, 16) for t in toks[2:]]
            except (IndexError, ValueError):
                continue
            frames.append((toks[0], pci, data))
        else:  # no header: one reassembled ATH0 message (sim/single ECU)
            try:
                frames.append(("", 0, [int(t, 16) for t in toks]))
            except ValueError:
                continue

    out: dict[str, list[int]] = {}
    buf: dict[str, list[int]] = {}
    want: dict[str, int] = {}
    for hdr, pci, data in frames:
        if not hdr:  # ATH0 world: tokens already are the message
            out.setdefault("", []).extend(data)
            continue
        kind = pci >> 4
        if kind == 0:  # single frame
            out[hdr] = data[: pci & 0x0F]
            want.pop(hdr, None)
            buf.pop(hdr, None)
        elif kind == 1:  # first frame: 12-bit total length
            want[hdr] = ((pci & 0x0F) << 8) | (data[0] if data else 0)
            buf[hdr] = data[1:]
        elif kind == 2 and hdr in want:  # consecutive frame: all 7 bytes are data
            buf[hdr] += data
            if len(buf[hdr]) >= want[hdr]:
                out[hdr] = buf[hdr][: want.pop(hdr)]
                buf.pop(hdr, None)
    return out


def _mode09_payload(msg: list[int] | None, pid: str) -> list[int] | None:
    """Bytes after the 49 xx marker inside one assembled message."""
    if not msg:
        return None
    m = marker_for("09" + pid)  # e.g. '4902'
    mark = (int(m[0:2], 16), int(m[2:4], 16))
    for i in range(len(msg) - 1):
        if msg[i] == mark[0] and msg[i + 1] == mark[1]:
            return msg[i + 2:]  # instance/count byte first, then data
    return None


def parse_mode09(reply: str, pid: str = "02", ecu: str | None = None) -> str | None:
    """Mode 09 reply -> ASCII string. Payload = count/instance byte + ASCII.

    `ecu` picks a stream by CAN header ('7E8' = Engine, '7E9' = Transmission);
    default = first stream (works for sim/ATH0 single-message replies).
    """
    streams = assemble_ecu(reply)
    payload = _mode09_payload(streams.get(ecu) if ecu else next(iter(streams.values()), None), pid)
    if not payload:
        return None
    s = "".join(chr(x) for x in payload[1:] if 0x20 <= x <= 0x7E).strip()
    return s or None


def parse_mode09_hex(reply: str, pid: str = "06", ecu: str | None = None) -> str | None:
    """Mode 09 reply -> hex data string (CVN 0906 is binary, not ASCII)."""
    streams = assemble_ecu(reply)
    payload = _mode09_payload(streams.get(ecu) if ecu else next(iter(streams.values()), None), pid)
    if not payload or len(payload) < 2:
        return None
    return bytes(payload[1:]).hex().upper() or None


def read_dtc(conn) -> dict[str, list[str]]:
    """Stored/pending/permanent codes (Mode 03/07/0A) at session end. Never raises.

    Returns {"03": ["7E8: P0133", ...], ...}; empty list = clean that mode.
    """
    out: dict[str, list[str]] = {}
    for mode in DTC_MODES:
        try:
            r = conn.query_raw(mode)
            if not r or "NO DATA" in r.upper():
                out[mode] = []
                continue
            out[mode] = [f"{ecu}: {code}" for ecu, code in parse_dtc_reply(r, marker_for(mode))]
        except Exception:
            out[mode] = []
    return out


def read_car_id(conn) -> dict:
    """VIN / CalID / CVN / ECU name (Mode 09) — per-ECU, functional, safe.

    Goes through ATH1 + ISO-TP reassembly (see assemble_ecu for why ATH0
    truncates). ECM (7E8) values land in the main keys, transmission (7E9)
    extras in *_tcm. CalID/CVN test the 'JDM calibration' LTFT hypothesis.
    Any failure -> None field; never raises.
    """
    out: dict = {}
    try:
        conn.send("ATH1", wait=0.2)
    except Exception:
        pass
    try:
        for key, pid in (("vin", "02"), ("calid", "04"), ("cvn", "06"), ("ecu_name", "0A")):
            try:
                r = conn.query_raw("09" + pid)
                parse = parse_mode09_hex if key == "cvn" else parse_mode09
                main = parse(r or "", pid, ecu="7E8") or parse(r or "", pid)
                out[key] = main
                tcm = parse(r or "", pid, ecu="7E9")
                if tcm and tcm != main:
                    out[key + "_tcm"] = tcm
            except Exception:
                out[key] = None
    finally:
        try:
            conn.send("ATH0", wait=0.2)  # never leave headers ON for capture
        except Exception:
            pass
    return out


# ------------------------------------------------- probe menu (Mode 21)

PROBE_MENU = [
    ("01B4", "ATF/CVT temperature (Mode-01 extension, functional 7DF — clone-friendly)"),
    ("21D9", "ATF/CVT temperature (JOBD JDM CAN, functional 7DF)"),
    ("2182", "Transmission fluid pan / post-converter temp"),
    ("21AA", "Engine oil temperature (JOBD)"),
    ("21F1", "Initial coolant / initial air temperature (JOBD)"),
    ("21F3", "Injection volume / injection timing (JOBD)"),
    ("210C", "RPM in Mode-21 form (JOBD CheckConnectionCommand)"),
]


INTERESTING = {  # for the 'not answering' footnote of the empirical sweep
    "0B": "MAP", "23": "rail pressure (D-4T)", "5C": "oil temp",
    "B4": "ATF/CVT temp (Toyota ext.)",
}


def sweep_report(conn) -> str:
    """Empirical Mode-01 sweep 00..FF + raw Mode 09 (ATH1, per-ECU).

    Bitmasks under-report on this car (0x5C is hidden but alive), so the only
    honest map is asking every PID. Slow (~2 min) — via `probe --sweep`.
    """
    lines = ["## Empirical sweep: Mode-01 00..FF (answering PIDs)", ""]
    try:
        conn.send("ATH1", wait=0.3)
    except Exception:
        pass
    try:
        hits, misses = [], set()
        for pid in range(0x00, 0x100):
            cmd = f"01{pid:02X}"
            try:
                r = (conn.query_raw(cmd) or "").strip()
            except Exception as e:
                r = f"<error: {e}>"
            up = r.upper()
            if "41" in up and "NO DATA" not in up:
                hits.append((cmd, r))
            else:
                misses.add(pid)  # NO DATA / '?' / error
        for cmd, r in hits:
            lines.append(f"### {cmd}\n{r}")
        dead = [f"0x{p:02X} {INTERESTING[f'{p:02X}']}"
                for p in sorted(misses) if f"{p:02X}" in INTERESTING]
        lines += ["", f"hits: {len(hits)} of 256",
                  f"NOT answering (of interesting): {', '.join(dead) or '—'}", ""]
        lines.append("## Mode 09 raw (identity / CVN / performance counters)")
        for cmd in ("0900", "0902", "0904", "0906", "0908", "090A"):
            lines += [f"### {cmd}", (conn.query_raw(cmd) or "(no data)").strip()]
        return "\n".join(lines)
    finally:
        try:
            conn.send("ATH0", wait=0.3)
        except Exception:
            pass


def probe_report(conn, sweep: bool = False) -> str:
    """Run the full experiment suite: car identity, supported-PID scan and the
    Mode-21 menu (with ATH1 to see which ECU answers) + decode candidates.
    sweep=True adds the empirical 00..FF sweep (~2 min)."""
    lines = ["# mycar probe — Toyota experiment menu", ""]

    lines.append("## Car identity (Mode 09)")
    for key, val in read_car_id(conn).items():
        lines.append(f"{key}: {val if val else '(no data)'}")
    lines.append("")

    lines.append("## Supported Mode-01 PIDs (0100 bitmask chain)")
    pids = scan_supported(conn)
    if pids:
        for p in pids:
            name = SUPPORTED_NAMES.get(p, "")
            lines.append(f"  0x{p:02X}" + (f"  {name}" if name else ""))
        missing = [f"0x{p:02X} {SUPPORTED_NAMES.get(p, '')}" for p in SUPPORTED_NAMES
                   if p not in pids and p in (0x0B, 0xB4, 0x23, 0x5C)]
        if missing:
            lines.append(f"  not supported (of interesting): {', '.join(missing)}")
    else:
        lines.append("  (no bitmask answers)")
    lines.append("")

    if sweep:
        lines.append(sweep_report(conn))
        lines.append("")

    lines.append("## Toyota Mode-21 / Mode-01 menu (raw replies + decode candidates)")
    try:
        conn.send("ATH1")  # headers ON: see which ECU answers
        for cmd, about in PROBE_MENU:
            r = conn.query_raw(cmd)
            lines.append(f"### {cmd} — {about}")
            lines.append(f"reply: {r if r else '(no data / not supported)'}")
            if r:
                payload = parse_full_payload(r, marker_for(cmd))
                if payload is None:
                    lines.append(f"payload: <response marker {marker_for(cmd)} not found>")
                elif cmd in CVT_CMDS:
                    val, cands = decode_atf(cmd, payload)
                    lines.append(f"payload bytes: {bytes(payload).hex()}")
                    lines.append(f"primary decode: {val}")
                    for label, v in cands:
                        lines.append(f"  candidate {label} = {v}")
                else:
                    lines.append(f"payload bytes: {bytes(payload).hex()}")
            lines.append("")
    finally:
        # never leave ATH1 behind: it breaks the standard Mode-01 parsers
        try:
            conn.send("ATH0")
        except Exception:
            pass
    return "\n".join(lines)
