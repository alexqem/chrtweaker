"""Raw ELM327 driver over pyserial — no OBD libraries.

Why raw: cheap BT clones hang when libraries do aggressive init (baud probing
with \\x7F\\x7F, protocol auto-detect loops). This driver does ONE gentle init
and stays polite: echo off, spaces on (easy parsing), headers off, explicit
CAN protocol. Reads until the '>' prompt with a timeout, never probes.

Reply format with ATE0/ATS1/ATH0 on CAN 11bit:
    "41 0C 12 A7"  -> service echo 41, pid 0C, data bytes
Toyota custom PIDs (Mode 21): mycar/toyota.py — functional 7DF first, optional
physical 7E0 attempt for adapters with working ATSH.

ElmBase holds the transport-agnostic query layer shared with the BLE driver
(mycar/elm_ble.py); subclasses provide send()/close().
"""

import re
import threading
import time

import serial

BAUD = 38400
PROMPT = b">"

# one gentle init, shared by both transports (cmd, settle seconds)
INIT_SEQUENCE = (
    ("ATZ", 3.0),
    ("ATE0", 1.0),
    ("ATL0", 1.0),
    ("ATS1", 1.0),
    ("ATH0", 1.0),
    ("ATSP6", 1.0),
)

# replies meaning "asked, nothing useful came back"
NEGATIVE_MARKERS = ("UNABLE", "NO DATA", "?", "STOPPED", "CAN ERROR")

IGNITION_HINT = "Turn the ignition ON (two presses of START without brake)."


class ElmError(Exception):
    pass


class ElmBase:
    """Transport-agnostic ELM327 query layer.

    Subclasses must provide send(cmd, wait=None) -> str and close().
    """

    supports_physical_atsh = True  # USB/WiFi yes; the BLE clone fake-OKs ATSH

    # ---------------- query ----------------

    def query_raw(self, cmd: str) -> str | None:
        """Raw PID query, header already correct. Returns reply text or None."""
        r = self.send(cmd)
        if not r or any(m in r.upper() for m in NEGATIVE_MARKERS):
            return None
        return r

    def query_mode01(self, pid: str, n_data: int) -> list[int] | None:
        """Standard Mode 01 query (header 7DF). Returns data bytes or None."""
        r = self.query_raw(f"01{pid}")
        if r is None:
            return None
        return self._payload(r, "41", pid, n_data)

    def query_multi(self, spec: list[tuple[str, int]]) -> dict[str, list[int]] | None:
        """Several Mode-01 PIDs in one CAN request (toyota.query_multi)."""
        from mycar import toyota
        return toyota.query_multi(self, spec)

    def query_cvt_and_raw(self) -> tuple[float | None, str | None]:
        """CVT/ATF temp + raw payload hex (toyota.probe_cvt, tries 01B4/21D9/2182).

        Physical 7E0 addressing is attempted too when the transport can do it
        (real USB/WiFi adapters have working ATSH; the BLE clone fake-OKs it,
        which is harmless — probe_cvt restores 7DF afterwards).
        """
        from mycar import toyota
        return toyota.probe_cvt(self, physical=self.supports_physical_atsh)

    # ---------------- init ----------------

    def init(self):
        """Gentle shared init, then confirm the CAR answers (not just adapter)."""
        for at, w in INIT_SEQUENCE:
            self.send(at, w)
        self.init_probe()

    def init_probe(self):
        """Confirm the CAR answers (not just the adapter)."""
        r = self.query_raw("0100")
        if r is None or "UNABLE" in r.upper():
            raise ElmError(
                "Adapter answers, but the car does not (0100 -> UNABLE TO CONNECT).\n"
                + IGNITION_HINT
            )

    # ---------------- parsing ----------------

    @staticmethod
    def _payload(reply: str, service: str, pid: str, n_data: int) -> list[int] | None:
        """Extract the data bytes from a reply like '41 0C 12 A7' (multi-line ok)."""
        for line in reply.splitlines():
            tokens = line.strip().upper().split()
            # allow BUS INIT / SEARCHING prefixes before the response
            while tokens and not re.fullmatch(r"[0-9A-F]{2}", tokens[0]):
                tokens.pop(0)
            if len(tokens) < 2 + n_data:
                continue
            if tokens[0] == service and tokens[1] == pid.upper():
                return [int(t, 16) for t in tokens[2:2 + n_data]]
        return None


class RawELM(ElmBase):
    """Minimal serial ELM327 driver. One instance = one capture session."""

    def __init__(self, port: str, baud: int = BAUD, timeout: float = 4.0):
        self._lock = threading.Lock()  # capture thread + dashboard probes
        try:
            self.s = serial.Serial(port, baud, timeout=1.0, write_timeout=3.0)
        except serial.SerialException as e:
            raise ElmError(f"cannot open {port}: {e}") from e
        self.timeout = timeout
        self.init()

    # ---------------- low level ----------------

    def _read_until_prompt(self) -> str:
        deadline = time.monotonic() + self.timeout
        buf = b""
        while time.monotonic() < deadline:
            chunk = self.s.read(256)
            if chunk:
                buf += chunk
                if PROMPT in buf:
                    break
            elif buf and PROMPT in buf:
                break
        out = buf.replace(b"\x00", b"").decode(errors="replace")
        return out.strip()

    def send(self, cmd: str, wait: float | None = None) -> str:
        """Send an AT command or PID request; return cleaned reply text."""
        with self._lock:
            self.s.reset_input_buffer()
            self.s.write(cmd.encode() + b"\r")
            self.s.flush()
            if wait:
                time.sleep(wait)  # give cheap clones a moment (BT latency)
            reply = self._read_until_prompt()
        # strip our own echo if the adapter has echo ON (ATE0 failed)
        if reply.upper().startswith(cmd.upper()):
            reply = reply[len(cmd):]
        return reply.strip()

    def close(self):
        try:
            with self._lock:
                self.send("ATZ")  # clean reset: adapter forgets the session
                self.s.close()
        except Exception:
            pass


# ---------------- decoders (SAE J1979) ----------------
# Toyota Mode-21 decoders live in mycar/toyota.py (Car Scanner catalog).
