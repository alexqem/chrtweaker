"""In-process live dashboard: FastAPI + WebSocket, self-terminating after 1 h.

capture() feeds State.push(); the browser page (mycar/dashboard.html) renders
charts (Chart.js from CDN) and live stat cards. The server runs in a daemon
thread and shuts itself down after DASHBOARD_TTL_S so it never leaks a port.
"""

import asyncio
import threading
import time
from collections import deque
from pathlib import Path

import uvicorn
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import HTMLResponse

from mycar import config, sensors

DASHBOARD_TTL_S = 3600  # hard lifetime: 1 hour, then the port is freed
PAGE = Path(__file__).with_name("dashboard.html").read_text(encoding="utf-8")


class State:
    """Thread-safe buffer between capture and the websocket.

    Rows carry a monotonically increasing `seq` so websocket clients can
    resume reliably even after old rows are evicted from the deque (an
    index-based rows[sent:] slice would silently stop delivering at maxlen).
    """

    def __init__(self, maxlen: int = 6000):
        # 1 Hz sampling; 6000 rows ≈ 100 min — the whole session (45 min cap +
        # tail + hold window) always fits, so a page reload shows the full
        # current-session charts from the snapshot.
        self._lock = threading.Lock()
        self._rows: deque = deque(maxlen=maxlen)
        self._meta: dict = {}
        self._seq = 0

    def push(self, row: dict):
        with self._lock:
            self._seq += 1
            self._rows.append({**row, "seq": self._seq})

    def set_meta(self, **meta):
        with self._lock:
            self._meta.update(meta)

    def snapshot(self):
        with self._lock:
            return list(self._rows), dict(self._meta)


def make_app(state: State) -> FastAPI:
    app = FastAPI(title="mycar dashboard")

    @app.get("/")
    def index():
        return HTMLResponse(PAGE)

    @app.websocket("/ws")
    async def ws(sock: WebSocket):
        await sock.accept()
        rows, meta = state.snapshot()
        await sock.send_json({
            "type": "snapshot",
            "meta": meta,
            "columns": sensors.COLUMNS,
            "rows": rows,
        })
        last_seq = rows[-1]["seq"] if rows else 0
        try:
            while True:
                await asyncio.sleep(1.0)
                rows, meta = state.snapshot()
                new = [r for r in rows if r["seq"] > last_seq]
                if new:
                    last_seq = new[-1]["seq"]
                    await sock.send_json({"type": "samples", "rows": new, "meta": meta})
                else:
                    await sock.send_json({"type": "samples", "rows": [], "meta": meta})
        except (WebSocketDisconnect, RuntimeError):
            pass

    return app


def start(port: int = config.DASHBOARD_PORT) -> "Dashboard":
    state = State()
    server = uvicorn.Server(uvicorn.Config(
        make_app(state), host="127.0.0.1", port=port, log_level="warning",
    ))
    thread = threading.Thread(target=server.run, daemon=True, name="dashboard")
    thread.start()
    return Dashboard(state, server, thread)


class Dashboard:
    def __init__(self, state: State, server, thread):
        self.state = state
        self._server = server
        self._thread = thread
        self._started = time.monotonic()

    def wait(self, log=print):
        """Block until the 1 h TTL expires (server self-stops). Ctrl+C exits early."""
        log(f"Dashboard stays live until {time.strftime('%H:%M', time.localtime(time.time() + max(0, DASHBOARD_TTL_S - (time.monotonic() - self._started))))} (then closes itself). Ctrl+C to exit earlier.")
        try:
            while time.monotonic() - self._started < DASHBOARD_TTL_S:
                time.sleep(1)
        except KeyboardInterrupt:
            log("Bye.")
        finally:
            self.stop()

    def stop(self):
        self._server.should_exit = True
