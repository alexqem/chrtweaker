"""Cold-start capture loop: connect -> poll -> CSV + JSON meta into logs/.

Raw driver (mycar/elm.py): per-query errors are logged and blanked; a serial
failure streak aborts the session cleanly (CSV + meta are still written).
SIGTERM/Ctrl+C are unwound by finally-blocks so the port is closed properly
(cheap ELM clones hang if the process dies with the port open).
"""

import csv
import json
import time
from datetime import datetime, timezone

import serial

from mycar.elm import ElmError
from mycar import config, sensors, toyota

MAX_CONSECUTIVE_FAILURES = 6  # failed queries in a row before giving up

try:  # bleak errors are NOT OSError — must be caught for soft reconnects
    from bleak.exc import BleakError
    SERIAL_ERRORS = (OSError, serial.SerialException, BleakError)
except ImportError:  # bleak missing -> serial-only install
    SERIAL_ERRORS = (OSError, serial.SerialException)


def _num(value):
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _session_paths(mode: str):
    LOGS_DIR = config.LOGS_DIR
    LOGS_DIR.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    base = LOGS_DIR / f"{stamp}_{mode}_{config.CAR['body']}"
    return base.with_suffix(".csv"), base.with_suffix(".meta.json")


def fmt(v, nd=1):
    return f"{v:.{nd}f}" if isinstance(v, (int, float)) else ("—" if v is None else f"{v}")


def _poll_multi(conn, groups) -> dict[str, list[int]] | None:
    """One CAN request per group of PIDs. Returns {pid: data} or None on the
    slightest problem — caller falls back to one query per PID."""
    out = {}
    try:
        for g in groups:
            got = conn.query_multi(g)
            if not got:
                return None
            out.update(got)
    except SERIAL_ERRORS:
        return None
    return out


def auto_analyze(csv_path, log):
    """Print the session summary right after capture (manual `analyze` still works)."""
    try:
        from mycar import analyze
        out = analyze.analyze_session(csv_path)
        log(f"Auto-analysis: {out}")
    except Exception as e:  # analysis must never break the session
        log(f"auto-analysis skipped: {e}")


def save_session(csv_path, meta_path, meta, aborted, log):
    """Write meta.json and mirror the session into the DB.

    Called from capture's finally-block so interrupted sessions (Ctrl+C)
    keep their meta + DB rows — only a hard crash loses them.
    """
    if meta is None:  # failed before the session even started
        return
    if aborted:
        log(f"⚠ Session aborted: {aborted}")
        meta["aborted"] = aborted
    if csv_path.exists():
        meta["rows"] = sum(1 for _ in open(csv_path)) - 1
    meta["finished_at"] = datetime.now(timezone.utc).isoformat()
    meta_path.write_text(json.dumps(meta, ensure_ascii=False, indent=2))
    log(f"Saved: {csv_path.name} + {meta_path.name}")
    try:
        from mycar import db
        sid = db.import_session(csv_path)
        if sid:
            log(f"DB: mirrored into {db.DB_PATH.name} (session #{sid})")
    except Exception as e:  # DB failure must never lose the session
        log(f"⚠ DB mirror skipped: {e}")


def capture(port: str | None, simulate: bool, max_minutes: float, sim_time_scale: float = 1.0,
            dashboard: bool = True, dashboard_port: int = config.DASHBOARD_PORT,
            multi: bool = True, hold: bool = False, tail_s: float | None = None, log=print) -> str:
    """Run one cold-start session. Returns path of the written CSV.

    hold=False (default): the process exits when the session ends (the
    dashboard dies with it — run analyze right after). hold=True: the
    dashboard stays live until its 1 h TTL; the console blocks meanwhile.
    tail_s overrides how long to keep logging after warm-up (docs/06 wants
    a 3-5 min warm-idle tail; default config.COOLDOWN_AFTER_WARM_S).
    """
    from mycar import connection

    dash = None
    if dashboard:
        from mycar import dashboard as dash_mod
        try:
            dash = dash_mod.start(dashboard_port)
            log(f"Dashboard: http://127.0.0.1:{dashboard_port}")
        except OSError as e:
            log(f"⚠ Dashboard skipped ({e}) — session continues.")
    state = dash.state if dash else None

    last_err = None
    for attempt in range(config.CONNECT_ATTEMPTS):
        try:
            conn = connection.connect(port, simulate, sim_time_scale, log=log)
            break
        except (RuntimeError, ElmError, ConnectionError) as e:
            last_err = e
            log(f"  connect attempt {attempt + 1}/{config.CONNECT_ATTEMPTS} failed: {e}")
            time.sleep(config.CONNECT_RETRY_SLEEP_S)
    else:
        raise last_err

    csv_path = meta_path = meta = None  # save_session() guards on these
    aborted = None
    try:
        # --- session mode from the first coolant reading -------------------
        first_coolant = None
        for _ in range(config.COOLANT_PROBE_ATTEMPTS):
            data = conn.query_mode01(sensors.COOLANT_PID, sensors.COOLANT_N)
            v = sensors.decode("coolant", data)
            if v is not None:
                first_coolant = v
                break
            time.sleep(1)

        if first_coolant is None:
            raise RuntimeError("No COOLANT_TEMP response — check connection")

        mode = "cold" if first_coolant <= config.COLD_COOLANT_MAX_C else "warm"
        csv_path, meta_path = _session_paths(mode)

        car_id = None  # VIN / CalID / ECU name — 'JDM calibration' LTFT check
        try:
            car_id = toyota.read_car_id(conn)
            if car_id and car_id.get("vin"):
                log(f"  car id: VIN {car_id['vin']}  cal {car_id.get('calid') or '—'}")
        except Exception as e:
            log(f"  car id skipped: {e}")

        meta = {
            "car": config.CAR,
            "car_id": car_id,
            "mode": mode,
            "started_at": datetime.now(timezone.utc).isoformat(),
            "start_coolant_c": first_coolant,
            "thresholds": {
                "cold_max_c": config.COLD_COOLANT_MAX_C,
                "warm_min_c": config.WARM_COOLANT_MIN_C,
                "sample_interval_s": config.SAMPLE_INTERVAL_S,
            },
            "custom_pids": {"cvt_temp": f"{'/'.join(toyota.CVT_CMDS)} via functional 7DF (raw -> cvt_raw)"},
            "warm_at_s": None,
        }
        if state:
            state.set_meta(**{k: meta[k] for k in ("mode", "started_at", "start_coolant_c", "warm_at_s")})
        log(f"Session mode: {mode}  (start coolant {first_coolant:.0f} °C)")

        started_at = time.monotonic()
        cvt_misses, cvt_skip = 0, False  # cvt: give up after CVT_GIVE_UP_CYCLES empty tries
        tail = config.COOLDOWN_AFTER_WARM_S if tail_s is None else tail_s
        with open(csv_path, "w", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=sensors.COLUMNS)
            writer.writeheader()

            warmed = False
            fail_streak = 0
            reconnects = 0
            multi_enabled = multi
            multi_fails = 0
            groups = toyota.multi_groups(sensors.POLL)
            while True:
                elapsed = time.monotonic() - started_at
                if elapsed >= max_minutes * 60:
                    log(f"Max session time reached ({max_minutes:.0f} min).")
                    break

                ts = time.monotonic() - started_at
                values = None
                if multi_enabled:
                    got = _poll_multi(conn, groups)
                    if got is not None:
                        values = {name: sensors.decode(name, got.get(pid))
                                  for name, pid, n in sensors.POLL}
                        fail_streak = 0
                        multi_fails = 0
                    else:
                        multi_fails += 1
                        if multi_fails >= 3:
                            multi_enabled = False
                            log("  multi-request not answered as expected — one query per PID for this session")
                if values is None:
                    values = {}
                    for name, pid, n in sensors.POLL:
                        try:
                            data = conn.query_mode01(pid, n)
                        except SERIAL_ERRORS as e:
                            data = None
                            log(f"  ! serial error on {name}: {e}")
                        values[name] = sensors.decode(name, data)
                        fail_streak = fail_streak + 1 if data is None else 0

                cvt_val, cvt_raw = (None, None)
                if not cvt_skip:
                    try:
                        cvt_val, cvt_raw = conn.query_cvt_and_raw()
                        # NOTE: no fail_streak here — cvt_temp is experimental and
                        # legitimately silent on adapters/ECUs without 01B4/21D9/2182
                    except SERIAL_ERRORS as e:
                        log(f"  ! serial error on cvt: {e}")
                        fail_streak += 1
                    if cvt_raw is None:
                        cvt_misses += 1
                        if cvt_misses >= config.CVT_GIVE_UP_CYCLES:
                            cvt_skip = True
                            log(f"  cvt: silent for {cvt_misses} cycles — skipping for the rest of "
                                f"the session (01B4/21D9/2182 are dead on this clone)")
                    else:
                        cvt_misses = 0  # raw payload seen: keep polling, decode may still improve
                values["cvt_temp"] = cvt_val
                values["cvt_raw"] = cvt_raw

                writer.writerow({"ts": round(ts, 1), **values})
                if state:
                    state.push({"ts": round(ts, 1), **values})

                if fail_streak >= MAX_CONSECUTIVE_FAILURES:
                    log("  link lost — reconnecting…")
                    try:
                        conn.close()
                    except Exception:
                        pass
                    try:
                        conn = connection.connect(port, simulate, sim_time_scale, log=log)
                        fail_streak = 0
                        reconnects += 1
                        if reconnects > config.MAX_RECONNECTS:
                            aborted = f"connection keeps dropping — gave up after {config.MAX_RECONNECTS} reconnects"
                            break
                        continue
                    except Exception as e:
                        aborted = f"reconnect failed: {e}"
                        break

                coolant = values["coolant"]
                warm_note = ""
                if not warmed and coolant is not None and coolant >= config.WARM_COOLANT_MIN_C:
                    warmed = True
                    meta["warm_at_s"] = round(ts, 1)
                    if state:
                        state.set_meta(warm_at_s=meta["warm_at_s"])
                    warm_note = f"  ✔ warmed at {ts:.0f}s"
                log(
                    f"[{ts:6.0f}s] coolant {fmt(coolant):>5} °C | oil {fmt(values['oil_temp']):>5} | "
                    f"rpm {fmt(values['rpm'], 0):>5} | maf {fmt(values['maf']):>4} | "
                    f"stft {fmt(values['stft']):>5}% | ltft {fmt(values['ltft']):>5}% | "
                    f"cvt {fmt(values['cvt_temp']):>5}{warm_note}"
                )

                if warmed and ts - meta["warm_at_s"] >= tail:
                    log("Warm-up + cooldown window complete.")
                    break
                time.sleep(config.SAMPLE_INTERVAL_S)

        meta["completed"] = True  # absent in meta if the loop was interrupted
        try:  # health snapshot at session end while the link is still up
            meta["dtc"] = toyota.read_dtc(conn)
            found = [f"{m}: {', '.join(c)}" for m, c in meta["dtc"].items() if c]
            log("DTC after session: " + ("; ".join(found) if found else "no codes (03/07 clean)"))
        except Exception as e:
            log(f"DTC read skipped: {e}")
        return str(csv_path)
    finally:
        # the session is ALWAYS saved: an interrupted capture (Ctrl+C) must
        # still write meta.json + the DB mirror (the CSV is already on disk)
        save_session(csv_path, meta_path, meta, aborted, log)
        if meta and meta.get("completed"):
            auto_analyze(csv_path, log)
        conn.close()
        if state:
            state.set_meta(finished=True, finished_at=datetime.now(timezone.utc).isoformat())
        if dash and hold:
            dash.wait(log=log)  # page stays live until the 1 h TTL frees the port
        elif dash:
            log("Dashboard closes with the process (use --hold to keep it live up to 1 h).")
