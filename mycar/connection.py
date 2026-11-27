"""Connection factory: BLE ELM327 (preferred) or raw serial, or simulator.

Cheap clones have a broken macOS SPP profile — macOS connects the RFCOMM
channel and the dongle then stops serving anyone. iOS OBD-приложение on iOS uses
the BLE side (service 0000fff0), which is rock solid. So: BLE first, raw
serial only if a --port is explicitly given, socket:// for WiFi adapters.
"""

import glob
import re

from mycar import config
from mycar.elm import ElmError
from mycar.elm_ble import ElmBLE, scan_ble

# bare macOS BLE UUID (CoreBluetooth style) as --port, without the ble: prefix
BLE_UUID_RE = re.compile(
    r"^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$"
)


def list_ports() -> list[str]:
    """Candidate serial ports on macOS (paired BT/USB adapters show as /dev/cu.*)."""
    return sorted(
        p for p in glob.glob("/dev/cu.*")
        if not any(x in p for x in ("debug-console", "wlan-debug", "Bluetooth-Incoming"))
    )


def connect(port: str | None = None, simulate: bool = False, sim_time_scale: float = 1.0, log=print):
    """Return a connection object (ElmBLE / RawELM / SimConnection).

    - port None      -> auto-detect: BLE address from .env, BLE scan, then serial
    - port "/dev/cu.*"              -> raw serial (USB ELM327)
    - port "socket://<ip>:35000"    -> WiFi ELM327
    - port "ble:<address>"          -> explicit BLE address
    - port "<bare BLE UUID>"        -> same as ble:<address>
    - simulate=True  -> offline simulator (no car needed); sim_time_scale
                        speeds up simulated dynamics (60.0 = 1 real s = 1 sim min)
    """
    if simulate:
        from mycar.sim import SimConnection
        return SimConnection(time_scale=sim_time_scale)

    if port and port.startswith("socket://"):
        from mycar.elm import RawELM
        return RawELM(port)

    if port and port.startswith("ble:"):
        return ElmBLE(port[4:], log=log)

    if port and BLE_UUID_RE.match(port):
        return ElmBLE(port, log=log)

    if port is None:
        # --- 0) known dongle address from .env (fast + deterministic) -----
        if config.BLE_ADDRESS:
            log(f"BLE adapter (from .env MYCAR_BLE_ADDRESS): {config.BLE_ADDRESS}")
            try:
                return ElmBLE(config.BLE_ADDRESS, log=log)
            except Exception as e:
                log(f"  BLE connect failed ({e}) — scanning instead")
        # --- 1) BLE (the transport cheap clones actually serve well) ------
        log("scanning BLE for OBD adapter…")
        addr = scan_ble(timeout=6.0)
        if addr:
            log(f"BLE adapter found: {addr}")
            try:
                return ElmBLE(addr, log=log)
            except Exception as e:
                log(f"  BLE connect failed ({e}) — trying serial")
        # --- 2) serial candidates (USB ELM327 etc.) ----------------------
        port = _serial_pick()
        if port is None:
            raise ElmError(
                "No OBD adapter found: neither BLE (service FFF0) nor serial /dev/cu.*.\n"
                "Plug the adapter into the DLC3; if it was just powered on, replug it."
            )
        log(f"serial adapter: {port}")

    return _serial(port, log)


def _serial_pick() -> str | None:
    candidates = [p for p in list_ports()
                  if any(k in p.lower() for k in ("elm", "obd", "usbserial", "usbmodem"))]
    return candidates[0] if candidates else None


def _serial(port: str, log=print):
    from mycar.elm import RawELM
    return RawELM(port)  # init() raises ElmError with clear guidance if silent
