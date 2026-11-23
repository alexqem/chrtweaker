"""Offline ECU simulator with the same interface as RawELM (mycar/elm.py).

Realistic cold-start dynamics at SIM_SPEED=1; time_scale speeds it up
(60.0 = one real second plays one simulated minute).

The simulator speaks the same reply shapes as the real adapter (hex strings
with response markers), so capture's parse path — including toyota.probe_cvt
and multi-requests — is exercised end to end.
"""

import time

from mycar import toyota
from mycar.sensors import decode


class SimConnection:
    """Duck-types RawELM: query_raw / query_mode01 / query_multi /
    query_cvt_and_raw / close."""

    def __init__(self, time_scale: float = 1.0):
        self.time_scale = max(time_scale, 0.001)
        self._t0 = time.monotonic()

    # --- simulated time ----------------------------------------------
    def _sim_s(self) -> float:
        return (time.monotonic() - self._t0) * self.time_scale

    def _sim_min(self) -> float:
        return self._sim_s() / 60.0

    # --- RawELM-compatible API ---------------------------------------
    def query_mode01(self, pid: str, n_data: int) -> list[int] | None:
        m = self._sim_min()
        s = self._sim_s()
        # realistic cold-start curve: fast rise first 6 min, then asymptote
        coolant = 5 + 83 * min(m / 14, 1) ** 0.7
        rpm = 1250 - 550 * min(m / 6, 1)
        maf = max(1.2, 3.1 - 1.6 * min(m / 6, 1))
        stft = 8 - 14 * min(m / 7, 1)
        ltft = max(-3, 1 - 0.5 * m)
        map_kpa = 32 + 8 * max(0, 1 - m / 2)
        baro = 101

        table = {
            "0C": (rpm * 4,),                   # raw = rpm*4 (J1979)
            "0D": (0,),
            "05": (coolant + 40,),
            "5C": (coolant - 6 + 40,),
            "0F": (5 + m * 0.8 + 40,),
            "0B": (map_kpa,),
            "33": (baro,),
            "10": (maf * 100,),                 # raw = value*100
            "04": ((22 - 8 * min(m / 6, 1)) * 2.55,),
            "06": (stft * 1.28 + 128,),
            "07": (ltft * 1.28 + 128,),
            "23": ((6000 + 2000 * min(m / 10, 1)) / 10,),
            "0E": (8 + 3 * min(m / 5, 1) + 128,),
            "45": (2.5 * 2.55,),
            "42": (13.9 * 1000,),
            "1F": (min(int(s), 65535),),
        }
        if pid not in table:
            return None
        # scale 16-bit-style values into the byte layout the decoders expect
        raw_val = table[pid][0]
        if n_data == 2:
            a = max(0, min(65535, int(raw_val)))
            return [a // 256, a % 256]
        return [max(0, min(255, int(raw_val))) & 0xFF]

    def query_multi(self, spec: list[tuple[str, int]]) -> dict[str, list[int]] | None:
        out = {}
        for pid, nd in spec:
            d = self.query_mode01(pid, nd)
            if d is None:
                return None
            out[pid] = d
        return out

    def query_raw(self, cmd: str) -> str | None:
        """Synthetic ELM text for the experiment paths (toyota.probe_*).

        Emulates a JDM-ish car: Mode-01 answers (including the 01B4 ATF
        extension), Mode 09 gives identity; Mode-21 stays silent (as on the
        real clone). Everything else: NO DATA.
        """
        cmd = cmd.upper()
        if cmd == "01B4":
            c = round(min(5 + self._sim_min() * 3.5, 65), 1)  # CVT warms slower
            raw = int(round((c + 40) * 256))                  # 01B4 v.15: /256-40
            return f"41 B4 {raw >> 8:02X} {raw & 0xFF:02X}"
        if cmd in ("0100", "0120", "0140", "0160", "0180", "01A0"):
            base = int(cmd[2:], 16)
            return "41 " + cmd[2:] + " " + " ".join(f"{b:02X}" for b in self._mask(base))
        ids = {
            "0902": "SIM000000000000001",   # 17-char VIN-shaped
            "0904": "SIM-CAL-8NR-FTS",
            "090A": "Engine-ECM-SIM",
        }
        if cmd in ids:
            ascii_hex = " ".join(f"{ord(ch):02X}" for ch in ids[cmd])
            return f"49 {cmd[2:]} 01 {ascii_hex}"
        return None

    def _mask(self, base: int) -> list[int]:
        """4-byte supported-PID bitmask for one 0x20 block (Mode 01 chain)."""
        from mycar import sensors
        pids = {int(p, 16) for _, p, _ in sensors.POLL} | {0xB4}
        m = 0
        for i in range(32):
            pid = base + i + 1
            ok = pid in pids or (pid == base + 0x20
                                 and any(p > base + 0x20 for p in pids))
            if ok:
                m |= 1 << (31 - i)
        return [(m >> s) & 0xFF for s in (24, 16, 8, 0)]

    def query_cvt_and_raw(self) -> tuple[float | None, str | None]:
        return toyota.probe_cvt(self)

    def send(self, cmd: str, wait: float | None = None) -> str:
        return "OK" if cmd.upper().startswith("AT") else (self.query_raw(cmd) or "")

    def restore_header(self):
        pass

    def close(self):
        pass
