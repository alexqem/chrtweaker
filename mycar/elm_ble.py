"""BLE ELM327 driver (bleak) — the transport iOS OBD-приложение uses on iOS.

Cheap clones have a broken macOS SPP profile but a perfectly healthy BLE
side (service 0000fff0, fff1 = notify/write, fff2 = write when present). This class
wraps bleak with a synchronous, RawELM-compatible interface so capture.py
works over either transport. The query layer is shared (mycar/elm.py
ElmBase); only the transport plumbing below is BLE-specific.

An asyncio event loop runs in a background thread; sync send() calls are
forwarded with run_coroutine_threadsafe.
"""

import asyncio
import threading
import time

from mycar.elm import ElmBase, ElmError, PROMPT  # noqa: F401  (re-export for connection.py)

NOTIFY_CHAR = "0000fff1-0000-1000-8000-00805f9b34fb"
WRITE_CHAR = "0000fff2-0000-1000-8000-00805f9b34fb"
WRITE_NOTIFY_CHAR = NOTIFY_CHAR  # backwards-compatible alias


def scan_ble(timeout: float = 6.0) -> str | None:
    """Find an OBD adapter advertising an ELM-style BLE service. Returns address."""
    try:
        from bleak import BleakScanner
    except ImportError:
        return None

    async def _scan():
        found = await BleakScanner.discover(timeout=timeout, return_adv=True)
        best = None
        for addr, (d, adv) in found.items():
            name = (adv.local_name or d.name or "").upper()
            uuids = " ".join(str(u).lower() for u in adv.service_uuids)
            if "fff0" in uuids or "18f0" in uuids:
                # OBD/ELM-ish names preferred; an anonymous FFF0 advertiser is a fallback
                score = 2 if ("OBD" in name or "ELM" in name) else 1
                if best is None or score > best[0]:
                    best = (score, addr)
        return best[1] if best else None

    try:
        return asyncio.run(_scan())
    except Exception:
        return None


class ElmBLE(ElmBase):
    """Synchronous BLE ELM327 driver, same surface as RawELM."""

    supports_physical_atsh = False  # clone firmware fake-OKs ATSH (stub)

    def __init__(self, address: str, timeout: float = 3.0, log=print):
        from bleak import BleakClient

        self.address = address
        self.timeout = timeout
        self._notify_char = NOTIFY_CHAR
        self._write_char = NOTIFY_CHAR
        self._buf = bytearray()
        self._lock = threading.Lock()
        self._connected = False
        self._log = log

        self._loop = asyncio.new_event_loop()
        self._thread = threading.Thread(target=self._loop.run_forever,
                                        daemon=True, name="elm-ble")
        self._thread.start()

        self._client = BleakClient(address, timeout=15.0, disconnected_callback=self._on_drop)
        last_err = None
        for attempt in range(1, 4):  # clones sometimes drop the first link
            try:
                self._run(self._connect_async())
                if self._connected:
                    break
            except Exception as e:
                last_err = e
                self._run(self._sleep_async(2.0))
        if not self._connected:
            raise ConnectionError(f"BLE connect to {address} failed: {last_err}")

        self.init()  # same contract as RawELM: initialized on construction

    async def _sleep_async(self, s: float):
        await asyncio.sleep(s)

    # ---------------- asyncio plumbing ----------------

    def _run(self, coro, wait=None):
        fut = asyncio.run_coroutine_threadsafe(coro, self._loop)
        return fut.result(wait or (self.timeout + 10))

    async def _connect_async(self):
        def on_notify(_handle, data: bytearray):
            with self._lock:
                self._buf.extend(data)

        await self._client.connect()
        # Discover FFF2 write characteristic if present (vLinker FD+ / dual-char ELM)
        notify_uuid = NOTIFY_CHAR
        write_uuid = NOTIFY_CHAR
        for service in getattr(self._client, "services", []) or []:
            chars = {str(c.uuid).lower(): c for c in getattr(service, "characteristics", [])}
            if WRITE_CHAR in chars and NOTIFY_CHAR in chars:
                notify_uuid = NOTIFY_CHAR
                write_uuid = WRITE_CHAR
                break
        self._notify_char = notify_uuid
        self._write_char = write_uuid
        await self._client.start_notify(self._notify_char, on_notify)
        self._connected = True

    def _on_drop(self, *_args):
        self._connected = False

    # ---------------- transport (ElmBase contract) ----------------

    def send(self, cmd: str, wait: float | None = None) -> str:
        """Write cmd, collect notifications until the '>' prompt.

        `wait` is a settle pause AFTER the write (same semantics as RawELM —
        clones need a moment after ATZ etc.), not a write timeout.
        """
        if not self._connected:
            raise ConnectionError("BLE link dropped")
        with self._lock:
            self._buf.clear()
        self._run(self._client.write_gatt_char(self._write_char, cmd.encode() + b"\r"))
        if wait:
            time.sleep(wait)
        deadline = time.monotonic() + self.timeout
        while time.monotonic() < deadline and self._connected:
            with self._lock:
                if PROMPT in self._buf:
                    break
            time.sleep(0.02)
        with self._lock:
            out = bytes(self._buf).decode(errors="replace").strip()
        if out.upper().startswith(cmd.upper()):  # strip echo if ATE0 failed
            out = out[len(cmd):]
        return out.strip()

    def init(self):
        """Shared gentle init, with per-AT logging (BLE latency is visible)."""
        from mycar.elm import INIT_SEQUENCE
        for at, w in INIT_SEQUENCE:
            r = self.send(at, w)
            self._log(f"  elm init {at} -> {r[:40]!r}")
        super().init_probe()

    def alive(self) -> bool:
        return self._connected

    def close(self):
        try:
            self._run(self._shutdown_async(), wait=3)
        except Exception:
            pass
        self._loop.call_soon_threadsafe(self._loop.stop)

    async def _shutdown_async(self):
        try:
            await self._client.stop_notify(self._char)
        except Exception:
            pass
        try:
            await self._client.disconnect()
        except Exception:
            pass
