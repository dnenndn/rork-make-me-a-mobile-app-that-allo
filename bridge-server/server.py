"""
PLC Panel Studio — LAN bridge server (Python 3 standard library only).

Run:   python server.py            (PORT env var optional, default 8080)
Test:  curl http://<your-ip>:8080/api/health

Contract (must match PlcProtocol.kt in the Android app):
  GET  /api/health              -> {ok, cpu, ioOk, ioTotal, uptime}
  POST /api/read   {addresses}  -> {values: {address: int}}
  POST /api/write  {address, value} -> {ok, value}

Ships a demo PLC with the same seal-in start/stop ladder logic as the app's
built-in simulator:
  - any non-stop input > 0  latches the motor ON
  - any "stop"-like input > 0 (name contains "stop" or ends in ".4") drops it
  - MW*/AW*/QW* analogue addresses animate while the motor runs

To drive a REAL PLC, replace the bodies of read_tags() and write_tags()
with your protocol calls (e.g. Modbus TCP via `pymodbus`).
"""

import json
import math
import os
import re
import socket
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(os.environ.get("PORT", "8080"))

# ---------------------------------------------------------------------------
# PLC adapter — the ONLY part you need to change for real hardware.
# ---------------------------------------------------------------------------

_inputs = {}          # written by the app (buttons, selectors)
_outputs = {}         # derived by ladder logic
_motor_sealed = False
_started_at = time.time()


def _is_stop_like(address: str) -> bool:
    lowered = address.lower()
    return "stop" in lowered or address.endswith(".4")


def _elapsed_seconds() -> int:
    return int(time.time() - _started_at)


def _recompute_logic() -> None:
    global _motor_sealed
    stop_pressed = any(v > 0 for k, v in _inputs.items() if _is_stop_like(k))
    start_pressed = any(v > 0 for k, v in _inputs.items() if not _is_stop_like(k))
    if stop_pressed:
        _motor_sealed = False
    elif start_pressed:
        _motor_sealed = True
    _outputs["__motor"] = 1 if _motor_sealed else 0


def _derive(address: str) -> int:
    running = _outputs.get("__motor", 0)
    if re.match(r"^(mw|aw|qw)", address, re.IGNORECASE):
        if running == 0:
            return 0
        seed = abs(hash(address)) % 7
        wave = 50 + 45 * math.sin(_elapsed_seconds() / 3 + seed)
        return max(0, min(100, round(wave)))
    if "fault" in address.lower():
        return 0
    return running


def read_tags(addresses):
    """Replace with real device reads (returns a dict of address -> int)."""
    _recompute_logic()
    values = {}
    for address in addresses:
        if address in _inputs:
            values[address] = _inputs[address]
        elif address in _outputs:
            values[address] = _outputs[address]
        else:
            values[address] = _derive(address)
    return values


def write_tags(address, value):
    """Replace with real device writes (returns the value actually stored)."""
    _inputs[address] = value
    _recompute_logic()
    return value


# ---------------------------------------------------------------------------
# HTTP plumbing — no changes needed below.
# ---------------------------------------------------------------------------


def _lan_addresses():
    results = []
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            ip = info[4][0]
            if not ip.startswith("127.") and ip not in results:
                results.append(ip)
    except OSError:
        pass
    return results


class BridgeHandler(BaseHTTPRequestHandler):

    def _send_json(self, status, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self._send_json(204, {})

    def do_GET(self):
        if self.path.split("?")[0] == "/api/health":
            up_minutes = _elapsed_seconds() // 60
            self._send_json(200, {
                "ok": True,
                "cpu": 6 + (_elapsed_seconds() % 9),
                "ioOk": 128,
                "ioTotal": 128,
                "uptime": f"{up_minutes // 60}h {up_minutes % 60}m",
            })
        else:
            self._send_json(404, {"ok": False, "error": f"No route for GET {self.path}"})

    def do_POST(self):
        path = self.path.split("?")[0]
        try:
            length = int(self.headers.get("Content-Length", "0"))
            raw = self.rfile.read(length) if length else b"{}"
            body = json.loads(raw or b"{}")
        except (ValueError, json.JSONDecodeError):
            self._send_json(400, {"ok": False, "error": "Invalid JSON"})
            return

        if path == "/api/read":
            addresses = [str(a) for a in body.get("addresses", []) if isinstance(a, str)]
            self._send_json(200, {"values": read_tags(addresses)})
        elif path == "/api/write":
            address = str(body.get("address", ""))
            if not address:
                self._send_json(400, {"ok": False, "error": "address is required"})
                return
            value = int(body.get("value") or 0)
            self._send_json(200, {"ok": True, "value": write_tags(address, value)})
        else:
            self._send_json(404, {"ok": False, "error": f"No route for POST {path}"})

    def log_message(self, fmt, *args):
        print(f"{self.address_string()} - {fmt % args}")


if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", PORT), BridgeHandler)
    print(f"PLC bridge listening on port {PORT}")
    for ip in _lan_addresses():
        print(f"  Add this device in the app ->  {ip}:{PORT}")
    print(f"  (local)                    ->  localhost:{PORT}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("Shutting down.")
