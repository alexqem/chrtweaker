"""SQLite log store: every capture session is mirrored into logs/chr.db.

CSV stays the append-only raw log; SQLite is the queryable mirror for
cross-session summaries (trends: warm-up time, trims, idle rpm over weeks).
`python -m mycar db` imports any new CSVs and prints a compact summary.

Mini-columnar layout: one narrow table per sample row (sensors.COLUMNS) and
one row per session (meta JSON kept verbatim).
"""

import csv
import json
import sqlite3
from datetime import datetime, timezone
from pathlib import Path

from mycar import config, sensors

DB_PATH = config.LOGS_DIR / "chr.db"

TEXT_COLUMNS = sensors.TEXT_COLUMNS  # single source: sensors.COLUMNS typing


def _samples_ddl() -> str:
    """samples table DDL derived from sensors.COLUMNS (single source of truth)."""
    cols = ", ".join(
        f"{c} TEXT" if c in TEXT_COLUMNS else f"{c} REAL"
        for c in sensors.COLUMNS if c != "ts"
    )
    return (
        "CREATE TABLE IF NOT EXISTS samples (\n"
        "    session_id INTEGER REFERENCES sessions(id),\n"
        "    ts REAL,\n"
        f"    {cols}\n"
        ");"
    )


SCHEMA = f"""
CREATE TABLE IF NOT EXISTS sessions (
    id INTEGER PRIMARY KEY,
    csv_file TEXT UNIQUE,
    started_at TEXT, finished_at TEXT, mode TEXT,
    start_coolant_c REAL, warm_at_s REAL,
    rows INTEGER, aborted TEXT, meta_json TEXT
);
{_samples_ddl()}
CREATE INDEX IF NOT EXISTS idx_samples_session ON samples(session_id);
CREATE INDEX IF NOT EXISTS idx_sessions_started ON sessions(started_at);
"""


def _connect() -> sqlite3.Connection:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.executescript(SCHEMA)
    return conn


def import_session(csv_path) -> int | None:
    """Mirror one CSV + its .meta.json into the DB. Returns session id.

    Skips if this csv_file is already imported (idempotent).
    """
    csv_path = str(Path(csv_path).resolve())  # idempotent across rel/abs spellings
    meta_path = csv_path.replace(".csv", ".meta.json")
    meta = {}
    try:
        meta = json.load(open(meta_path, encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        pass

    with _connect() as conn:
        cur = conn.execute("SELECT id FROM sessions WHERE csv_file = ?", (csv_path,))
        if cur.fetchone():
            return None

        cur = conn.execute(
            "INSERT INTO sessions (csv_file, started_at, finished_at, mode, "
            "start_coolant_c, warm_at_s, rows, aborted, meta_json) "
            "VALUES (?,?,?,?,?,?,?,?,?)",
            (
                csv_path,
                meta.get("started_at"),
                meta.get("finished_at"),
                meta.get("mode"),
                meta.get("start_coolant_c"),
                meta.get("warm_at_s"),
                meta.get("rows"),
                meta.get("aborted"),
                json.dumps(meta, ensure_ascii=False),
            ),
        )
        sid = cur.lastrowid

        cols = [c for c in sensors.COLUMNS if c != "ts"]
        placeholders = ",".join("?" * (len(cols) + 2))
        rows = []
        with open(csv_path, newline="") as f:
            for r in csv.DictReader(f):
                vals = []
                for c in cols:
                    v = r.get(c)
                    vals.append(None if v in ("", None) else v)
                rows.append((sid, r.get("ts"), *vals))
        conn.executemany(
            f"INSERT INTO samples (session_id, ts, {','.join(cols)}) VALUES ({placeholders})",
            rows,
        )
    return sid


def import_all() -> int:
    """Import every logs/*.csv not yet in the DB. Returns number imported."""
    n = 0
    for p in sorted(config.LOGS_DIR.glob("*.csv")):
        if import_session(p) is not None:
            n += 1
    return n


def summary() -> str:
    """Cross-session summary: one compact line per session + trends."""
    with _connect() as conn:
        sessions = conn.execute(
            "SELECT id, csv_file, started_at, mode, start_coolant_c, warm_at_s, rows, aborted "
            "FROM sessions ORDER BY started_at"
        ).fetchall()
        if not sessions:
            return "DB is empty — run capture or `python -m mycar db` to import logs/."

        out = ["# Session DB summary (logs/chr.db)", ""]
        out.append("| date | mode | start ECT | warm-up | rows | idle rpm | LTFT idle mean |")
        out.append("|---|---|---|---|---|---|---|")
        for sid, csv_file, started_at, mode, start_c, warm_s, rows, aborted in sessions:
            date = (started_at or "")[:16].replace("T", " ")
            warm = f"{warm_s/60:.1f} min" if warm_s is not None else "—"
            # idle stats are post-warm ONLY: without warm_at_s the cold fast-idle
            # (1200+ rpm) would poison the long-term trend
            idle = None
            if warm_s is not None:
                idle = conn.execute(
                    "SELECT AVG(rpm), AVG(ltft) FROM samples "
                    "WHERE session_id=? AND ts >= ? AND rpm IS NOT NULL",
                    (sid, warm_s),
                ).fetchone()
            rpm_s = f"{idle[0]:.0f}" if idle and idle[0] else "—"
            ltft_s = f"{idle[1]:+.1f}%" if idle and idle[1] is not None else "—"
            flag = " ⚠aborted" if aborted else ""
            out.append(f"| {date} | {mode or '—'}{flag} | {start_c or '—'} °C | {warm} | {rows or '—'} | {rpm_s} | {ltft_s} |")

        # simple trends over all sessions
        trend = conn.execute(
            "SELECT COUNT(DISTINCT session_id), "
            "AVG(CASE WHEN ltft IS NOT NULL THEN ltft END), "
            "MIN(ltft), MAX(ltft) FROM samples"
        ).fetchone()
        lean = config.NORMS["toyota_trim_dtc_lean_pct"]
        rich = config.NORMS["toyota_trim_dtc_rich_pct"]
        out.append("")
        out.append(
            f"Trend over {trend[0]} sessions: LTFT overall mean {trend[1]:+.1f}% "
            f"(min {trend[2]:+.1f}%, max {trend[3]:+.1f}%) — DTC Toyota: "
            f"P0171 ≥ +{lean}%, P0172 ≤ {rich}%"
            if trend[1] is not None else
            "No trim data yet."
        )
        return "\n".join(out)


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()
