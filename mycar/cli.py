"""CLI: python -m mycar {doctor|capture|analyze}"""

import argparse

from mycar import config


def main():
    ap = argparse.ArgumentParser(prog="mycar", description="Cold-start OBD-II monitoring for Toyota C-HR NGX10 (8NR-FTS)")
    sub = ap.add_subparsers(dest="cmd", required=True)

    sub.add_parser("doctor", help="check environment: python, obd lib, serial ports, bluetooth hints")

    cp = sub.add_parser("capture", help="record a cold-start session to logs/")
    cp.add_argument("--port", help="serial port, e.g. /dev/cu.usbserial-XXXX or socket://192.168.0.10:35000")
    cp.add_argument("--sim", action="store_true", help="offline simulator (no car/adapter)")
    cp.add_argument("--sim-speed", type=float, default=1.0, help="sim time scale: 60 = 1 real s plays 1 sim minute")
    cp.add_argument("--max-minutes", type=float, default=config.MAX_SESSION_MINUTES)
    cp.add_argument("--dashboard", action=argparse.BooleanOptionalAction, default=True,
                    help="live web dashboard (default on; self-closes after 1 h)")
    cp.add_argument("--dashboard-port", type=int, default=config.DASHBOARD_PORT)
    cp.add_argument("--no-multi", action="store_true",
                    help="disable multi-PID requests (one query per PID; auto-fallback anyway)")
    cp.add_argument("--hold", action="store_true",
                    help="keep the dashboard alive after the session (up to 1 h TTL); "
                         "blocks the console meanwhile (default: exit with the session)")
    cp.add_argument("--tail", type=float, default=None, metavar="SEC",
                    help=f"seconds to keep logging after warm-up (default {config.COOLDOWN_AFTER_WARM_S})")

    az = sub.add_parser("analyze", help="summarize the latest session into stats/")
    az.add_argument("file", nargs="?", help="specific logs/<session>.csv (default: latest)")

    sub.add_parser("db", help="SQLite mirror: import new CSVs + cross-session summary")

    dp = sub.add_parser("dtc", help="read + decode diagnostic trouble codes (Mode 03/07/0A)")
    dp.add_argument("--port", help="adapter (default: auto BLE)")
    dp.add_argument("--mode", choices=["03", "07", "0A", "all"], default="all",
                    help="03=stored, 07=pending, 0A=permanent (default all)")
    dp.add_argument("--raw", action="store_true", help="also print raw ELM replies")
    dp.add_argument("--hex", help="offline: decode from a raw reply string instead of the car")
    dp.add_argument("--sim", action="store_true", help="offline simulator (no car/adapter)")

    pp = sub.add_parser("probe", help="experimental: try Toyota Mode-21 PIDs, show decode candidates")
    pp.add_argument("--port", help="adapter (default: auto BLE)")
    pp.add_argument("--sim", action="store_true", help="offline simulator (no car/adapter)")
    pp.add_argument("--sweep", action="store_true",
                    help="add the empirical Mode-01 00..FF sweep + raw Mode 09 (~2 min on the car)")

    tp = sub.add_parser("trend", help="cross-session LTFT / warm-up trends from logs/chr.db")

    args = ap.parse_args()

    if args.cmd == "doctor":
        doctor()
    elif args.cmd == "capture":
        import signal
        import sys

        from mycar.capture import capture

        # line-buffered console log: with nohup/redirect Python 3.14 buffers
        # stdout up to ~1 MB — a killed process loses every diagnostic line
        sys.stdout.reconfigure(line_buffering=True)

        # graceful stop: SIGTERM/Ctrl+C must unwind finally-blocks so the
        # serial port is closed properly (cheap ELM clones hang otherwise)
        signal.signal(signal.SIGTERM, lambda *_: (_ for _ in ()).throw(SystemExit(130)))
        try:
            capture(args.port, args.sim, args.max_minutes, args.sim_speed,
                    args.dashboard, args.dashboard_port, multi=not args.no_multi,
                    hold=args.hold, tail_s=args.tail)
        except (KeyboardInterrupt, SystemExit):
            print("Stopped.")
    elif args.cmd == "analyze":
        from mycar.analyze import analyze_session, analyze_latest
        from pathlib import Path
        path = analyze_session(Path(args.file)) if args.file else analyze_latest()
        print(path.read_text(encoding="utf-8"))
    elif args.cmd == "db":
        from mycar import db
        n = db.import_all()
        print(f"Imported {n} new session(s) into {db.DB_PATH}")
        print(db.summary())
    elif args.cmd == "dtc":
        run_dtc(args)
    elif args.cmd == "probe":
        run_probe(args)
    elif args.cmd == "trend":
        from mycar import db
        print(db.trend())


def run_dtc(args):
    from mycar import toyota

    modes = list(toyota.DTC_MODES) if args.mode == "all" else [args.mode]

    if args.hex:
        # offline: --hex content is treated as one raw reply for the chosen mode
        for m in modes:
            print(f"== Mode {m} (offline hex) ==")
            _print_dtc(args.hex, toyota.marker_for(m))
        return

    from mycar import connection
    conn = connection.connect(args.port, args.sim, log=print)
    try:
        conn.send("ATH1")  # see which ECU answers (7E8 Engine / 7E9 Transmission)
        for m in modes:
            print(f"== Mode {m} ==")
            r = conn.send(m)
            if args.raw:
                print(r or "(no reply)")
            if not r or "NO DATA" in r.upper() or "?" in r:
                print("  no codes")
                continue
            _print_dtc(r, toyota.marker_for(m))
    finally:
        try:
            conn.send("ATH0")  # never leave headers ON for the next session
        except Exception:
            pass
        conn.close()


def _print_dtc(reply: str, marker: str):
    from mycar import toyota

    items = toyota.parse_dtc_reply(reply, marker)
    if not items:
        print("  no codes")
        return
    for ecu, code in items:
        ecu_name = toyota.ECU_NAMES.get((ecu or "").upper(), ecu or "?")
        info = toyota.dtc_lookup(code) or {}
        text = info.get("text") or "—"
        print(f"  {code}  [{ecu_name}]  {text}")
        if info.get("generic_text"):
            print(f"        generic: {info['generic_text']}")
        if info.get("ru"):
            print(f"        ru: {info['ru']}")


def run_probe(args):
    from datetime import datetime

    from mycar import config, connection, toyota

    conn = connection.connect(args.port, args.sim, log=print)
    try:
        report = toyota.probe_report(conn, sweep=args.sweep)
    finally:
        conn.close()
    print(report)
    config.STATS_DIR.mkdir(parents=True, exist_ok=True)
    out = config.STATS_DIR / f"probe_{datetime.now().strftime('%Y%m%d_%H%M%S')}.txt"
    out.write_text(report, encoding="utf-8")
    print(f"Saved: {out}")


def doctor():
    import glob
    import platform

    import serial

    print("python     :", platform.python_version())
    print("driver     : raw pyserial", getattr(serial, "VERSION", "3.x"))
    try:
        import bleak
        print("bleak      :", getattr(bleak, "__version__", "ok"), "(BLE transport)")
    except ImportError:
        print("bleak      : MISSING — `pip install bleak`; BLE transport unavailable")
    print("car profile:", config.CAR["make"], config.CAR["model"], config.CAR["body"], config.CAR["year"], config.CAR["engine"])
    print("ble address:", config.BLE_ADDRESS or "(not set — auto-scan; copy .env.example to .env)")
    print("paths      :", config.LOGS_DIR, config.STATS_DIR)

    ports = sorted(p for p in glob.glob("/dev/cu.*"))
    if ports:
        print("serial ports:")
        for p in ports:
            print("  ", p)
    else:
        print("serial ports: none found (normal for a BLE-only dongle)")
    print(
        "\nmacOS notes:\n"
        "- Transport is BLE (service FFF0) — DO NOT pair the dongle in System Settings:\n"
        "  the classic SPP profile of cheap clones is broken on macOS and pairing can\n"
        "  hang the dongle until it is unplugged and replugged (details: docs/04)\n"
        "- Bluetooth permission is granted to the terminal app (Terminal/iTerm/VS Code),\n"
        "  not to python — allow it in System Settings -> Privacy & Security -> Bluetooth\n"
        "- The clone serves ONE client: close CarScanner / release the iPhone first\n"
        "- If the link is silent: unplug+replug the adapter (BT cycle rarely helps)\n"
        "- WiFi adapters: use --port socket://<ip>:35000\n"
        "- Quick transport test: .venv/bin/python ../obdii_test/ble_elm.py\n"
        "- Full guide: docs/04-macos-bluetooth-elm327.md"
    )


if __name__ == "__main__":
    main()
