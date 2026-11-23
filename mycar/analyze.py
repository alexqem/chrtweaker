"""Per-session analysis: reads logs/*.csv, writes stats/<session>_summary.md."""

import csv
import json
import math
from collections import defaultdict
from pathlib import Path

from mycar import config


def _load(path: Path):
    with open(path, newline="") as f:
        rows = [{k: _f(v) for k, v in r.items()} for r in csv.DictReader(f)]
    return rows


def _f(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def _stats(values):
    xs = [v for v in values if v is not None and not math.isnan(v)]
    if not xs:
        return None
    r = lambda x: round(x, 1)
    return {"min": r(min(xs)), "max": r(max(xs)), "mean": round(sum(xs) / len(xs), 2)}


def analyze_session(csv_path: Path) -> Path:
    rows = _load(csv_path)
    if not rows:
        raise ValueError(f"{csv_path.name}: empty session")

    meta_path = csv_path.with_suffix(".meta.json")
    meta = json.loads(meta_path.read_text()) if meta_path.exists() else {}

    # warm-up duration: first ts when coolant >= WARM
    warm_at = next((r["ts"] for r in rows if r["coolant"] is not None and r["coolant"] >= config.WARM_COOLANT_MIN_C), None)

    # phase split: idle phase = speed 0
    cols = [c for c in rows[0] if c != "ts"]
    per_col = {c: _stats([r[c] for r in rows]) for c in cols}

    # derived boost kPa (map - baro)
    boost = _stats([r["map"] - r["baro"] for r in rows if r["map"] is not None and r["baro"] is not None])

    coolant_rate = None
    t0c, t_end_c = rows[0]["coolant"], rows[-1]["coolant"]
    if t0c is not None and t_end_c is not None and rows[-1]["ts"] > 0:
        coolant_rate = round((t_end_c - t0c) / (rows[-1]["ts"] / 60), 2)  # °C/min avg
        t0c, t_end_c = round(t0c, 1), round(t_end_c, 1)

    n = config.NORMS
    findings = []
    if meta.get("mode") != "cold":
        findings.append("⚠ session started warm (coolant > 40 °C) — not a cold-start test")
    if warm_at is None:
        findings.append(f"⚠ coolant never reached {n['warm_target_c']} °C in this session")
    rpm_idle = per_col.get("rpm")
    lo, hi = n["idle_rpm_cvt"]
    post_warm = None
    if warm_at is not None:
        post_warm = _stats([r["rpm"] for r in rows if r["ts"] >= warm_at and r["rpm"] is not None])
    if post_warm and post_warm["max"] > hi + 50 and all(r["speed"] == 0 for r in rows):
        findings.append(f"• elevated idle after warm-up up to {post_warm['max']:.0f} rpm (norm {lo}–{hi})")
    for trim in ("stft", "ltft"):
        s = per_col.get(trim)
        if s and (s["min"] < n["stft_ltft_normal_pct"][0] or s["max"] > n["stft_ltft_normal_pct"][1]):
            findings.append(f"• {trim.upper()} exceeded ±10% band: {s['min']:+.1f}% … {s['max']:+.1f}%")
    if boost and boost["max"] > 0:
        findings.append(f"• positive boost observed: max {boost['max']:.1f} kPa")

    summary = {
        "session": csv_path.name,
        "mode": meta.get("mode"),
        "start_coolant_c": t0c,
        "end_coolant_c": t_end_c,
        "warm_up_duration_s": warm_at,
        "coolant_rate_c_per_min_avg": coolant_rate,
        "boost_kpa": boost,
        "per_parameter": per_col,
        "findings": findings,
    }

    STATS_DIR = config.STATS_DIR
    STATS_DIR.mkdir(parents=True, exist_ok=True)
    out = STATS_DIR / f"{csv_path.stem}_summary.md"
    out.write_text(render_markdown(summary, n), encoding="utf-8")
    (out.with_suffix(".json")).write_text(json.dumps(summary, ensure_ascii=False, indent=2, default=str))
    return out


def render_markdown(s, n) -> str:
    lines = [
        f"# Summary: {s['session']}",
        f"- Mode: **{s['mode']}**",
        f"- Start coolant: {s['start_coolant_c']} °C → end: {s['end_coolant_c']} °C",
        f"- Warm-up (≥{n['warm_target_c']} °C): {fmt_s(s['warm_up_duration_s'])}",
        f"- Avg coolant rate: {s['coolant_rate_c_per_min_avg']} °C/min",
        f"- Boost: {s['boost_kpa']}",
        "",
        "| parameter | min | mean | max |",
        "|---|---|---|---|",
    ]
    for name, st in s["per_parameter"].items():
        if st:
            lines.append(f"| {name} | {st['min']} | {st['mean']} | {st['max']} |")
    if s["findings"]:
        lines += ["", "## Findings"] + s["findings"]
    lines += ["", f"_Reference norms: mycar/docs/03-cold-start-norms.md (idle CVT {n['idle_rpm_cvt'][0]}–{n['idle_rpm_cvt'][1]} rpm, "
              f"trims ±{abs(n['stft_ltft_normal_pct'][0])}%, thermostat {n['thermostat_open_c'][0]}–{n['thermostat_open_c'][1]} °C)_"]
    return "\n".join(lines)


def fmt_s(v):
    if v is None:
        return "not reached"
    return f"{v:.0f} s (~{v / 60:.1f} min)"


def analyze_latest() -> Path:
    csvs = sorted(config.LOGS_DIR.glob("*.csv"))
    if not csvs:
        raise FileNotFoundError("No session logs in logs/ — run `python -m mycar capture --sim` first.")
    return analyze_session(csvs[-1])
