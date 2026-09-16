"""
PLC Panel Studio — LAN bridge server (Python 3, zero required dependencies).

Run (demo PLC, no hardware):
    python server.py

Run against a REAL Siemens PLC over PROFINET/Ethernet (S7 protocol):
    python server.py --plc 192.168.0.1
    (or:  set PLC_IP=192.168.0.1  then  python server.py  on Windows CMD)

The phone cannot speak PROFINET directly — this bridge sits on the LAN next
to the PLC, translates S7comm into the app's simple HTTP contract, and serves
it on port 8080 by default.

Contract (must match PlcProtocol.kt in the Android app):
  GET  /api/health              -> {ok, cpu, ioOk, ioTotal, uptime}
  POST /api/read   {addresses}  -> {values: {address: int}}
  POST /api/write  {address, value} -> {ok, value}

Supported tag addresses (map them in the app's Assign Tag sheet):
  Bits:    I12.0 (input)   Q8.1 (output)   M10.3 (merker/flag)
  Words:   IW64  QW66  MW20
  Double:  MD40
  Data blocks: DB10.DBX2.3 (bit)  DB10.DBW4 (word)  DB10.DBD6 (dword)

Demo PLC (default): same seal-in start/stop ladder logic as the app's
built-in simulator — any non-stop input > 0 latches the motor ON, any
"stop"-like input (name contains "stop" or ends in ".4") drops it, and
MW/AW/QW analogue addresses animate while the motor runs.

Siemens notes (S7-1200 / S7-1500):
  - In TIA Portal, enable "Permit access with PUT/GET communication from
    remote partner" in the PLC's Protection & Security settings.
  - Rack 0 / Slot 1 is typical for 1200/1500 (S7-300/400 often 0/2).
  - The snap7 package is only needed for a real PLC:  pip install python-snap7
"""

import argparse
import json
import math
import os
import re
import socket
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# ---------------------------------------------------------------------------
# Drivers — demo simulator (default) and Siemens S7 over PROFINET.
# ---------------------------------------------------------------------------


class DemoDriver:
    """No hardware needed: seal-in ladder logic + animated analogue values."""

    name = "demo"

    def __init__(self):
        self.inputs = {}          # written by the app (buttons, selectors)
        self.outputs = {}         # derived by ladder logic
        self.motor_sealed = False
        self.started_at = time.time()

    def _elapsed_seconds(self) -> int:
        return int(time.time() - self.started_at)

    @staticmethod
    def _is_stop_like(address: str) -> bool:
        lowered = address.lower()
        return "stop" in lowered or address.endswith(".4")

    def _recompute_logic(self) -> None:
        stop_pressed = any(v > 0 for k, v in self.inputs.items() if self._is_stop_like(k))
        start_pressed = any(v > 0 for k, v in self.inputs.items() if not self._is_stop_like(k))
        if stop_pressed:
            self.motor_sealed = False
        elif start_pressed:
            self.motor_sealed = True
        self.outputs["__motor"] = 1 if self.motor_sealed else 0

    def _derive(self, address: str) -> int:
        running = self.outputs.get("__motor", 0)
        if re.match(r"^(mw|aw|qw)", address, re.IGNORECASE):
            if running == 0:
                return 0
            seed = abs(hash(address)) % 7
            wave = 50 + 45 * math.sin(self._elapsed_seconds() / 3 + seed)
            return max(0, min(100, round(wave)))
        if "fault" in address.lower():
            return 0
        return running

    def health(self) -> dict:
        up_minutes = self._elapsed_seconds() // 60
        return {
            "ok": True,
            "cpu": 6 + (self._elapsed_seconds() % 9),
            "ioOk": 128,
            "ioTotal": 128,
            "uptime": f"{up_minutes // 60}h {up_minutes % 60}m",
        }

    def read_tags(self, addresses) -> dict:
        self._recompute_logic()
        values = {}
        for address in addresses:
            if address in self.inputs:
                values[address] = self.inputs[address]
            elif address in self.outputs:
                values[address] = self.outputs[address]
            else:
                values[address] = self._derive(address)
        return values

    def write_tags(self, address, value) -> int:
        self.inputs[address] = value
        self._recompute_logic()
        return value


class S7Driver:
    """Siemens S7 over PROFINET/Ethernet via Snap7 (pip install python-snap7)."""

    name = "s7"

    def __init__(self, ip, rack=0, slot=1, port=102):
        try:
            import snap7
            from snap7 import util as s7util
            from snap7.type import Areas
        except ImportError as error:
            raise RuntimeError(
                "S7 driver needs the Snap7 package:  pip install python-snap7"
            ) from error

        self.ip = ip
        self.rack = rack
        self.slot = slot
        self.port = port
        self.started_at = time.time()
        self._snap7 = snap7
        self._s7util = s7util
        self._Areas = Areas
        self.client = snap7.client.Client()
        self._connected = False

    # -- connection ---------------------------------------------------------

    def _ensure_connected(self):
        if self._connected and self.client.get_connected():
            return
        self.client.connect(self.ip, self.rack, self.slot, self.port)
        if not self.client.get_connected():
            raise ConnectionError(f"Could not reach PLC at {self.ip}")
        self._connected = True
        print(f"Connected to PLC at {self.ip} (rack {self.rack}, slot {self.slot})")

    # -- address parsing ----------------------------------------------------

    _PATTERNS = (
        (re.compile(r"^I(\d+)\.(\d+)$"), "PE", None),
        (re.compile(r"^Q(\d+)\.(\d+)$"), "PA", None),
        (re.compile(r"^M(\d+)\.(\d+)$"), "MK", None),
        (re.compile(r"^IW(\d+)$"), "PE", 2),
        (re.compile(r"^QW(\d+)$"), "PA", 2),
        (re.compile(r"^MW(\d+)$"), "MK", 2),
        (re.compile(r"^MD(\d+)$"), "MK", 4),
        (re.compile(r"^DB(\d+)\.DBX(\d+)\.(\d+)$"), "DB", 1),
        (re.compile(r"^DB(\d+)\.DBB(\d+)$"), "DB", 1),
        (re.compile(r"^DB(\d+)\.DBW(\d+)$"), "DB", 2),
        (re.compile(r"^DB(\d+)\.DBD(\d+)$"), "DB", 4),
    )

    @classmethod
    def parse(cls, address: str) -> dict:
        """Returns {area, db, start, bit, size} or raises ValueError."""
        for pattern, area, size in cls._PATTERNS:
            match = pattern.match(address.upper().replace(" ", ""))
            if not match:
                continue
            groups = [int(g) for g in match.groups()]
            if area == "DB":
                db, start = groups[0], groups[1]
                bit = groups[2] if len(groups) > 2 else None
                if bit is not None and bit > 7:
                    raise ValueError(f"Bit {bit} out of range in '{address}'")
            else:
                db = 0
                if size is None:          # I/Q/M bit form
                    start, bit = groups
                else:                     # word/double form
                    start, bit = groups[0], None
            return {"area": area, "db": db, "start": start, "bit": bit, "size": size or 1}
        raise ValueError(
            f"Unsupported address '{address}'. Use I/Q/M bits, IW/QW/MW/MD words "
            "or DB<n>.DBX/DBW/DBD data-block tags."
        )

    def _area_code(self, name: str):
        return getattr(self._Areas, name)

    def _read_raw(self, tag: dict) -> bytearray:
        self._ensure_connected()
        return bytearray(
            self.client.read_area(self._area_code(tag["area"]), tag["db"], tag["start"], tag["size"])
        )

    def _write_raw(self, tag: dict, data: bytearray) -> None:
        self._ensure_connected()
        self.client.write_area(self._area_code(tag["area"]), tag["db"], tag["start"], bytes(data))

    # -- tag I/O ------------------------------------------------------------

    def health(self) -> dict:
        self._ensure_connected()
        up_minutes = int(time.time() - self.started_at) // 60
        info = {}
        try:
            info = self.client.get_cpu_info() or {}
        except Exception:
            pass
        module_name = info.get("module_type_name") or info.get("ModuleTypeName") or ""
        return {
            "ok": True,
            "cpu": None,
            "ioOk": None,
            "ioTotal": None,
            "uptime": f"{up_minutes // 60}h {up_minutes % 60}m",
            "plc": module_name or self.ip,
        }

    def read_tags(self, addresses) -> dict:
        values = {}
        for address in addresses:
            tag = self.parse(address)
            raw = self._read_raw(tag)
            if tag["bit"] is not None:
                values[address] = 1 if self._s7util.get_bool(raw, 0, tag["bit"]) else 0
            elif tag["size"] == 2:
                values[address] = self._s7util.get_int(raw, 0)
            elif tag["size"] == 4:
                values[address] = self._s7util.get_dint(raw, 0)
            else:
                values[address] = raw[0]
        return values

    def write_tags(self, address, value) -> int:
        tag = self.parse(address)
        raw = self._read_raw(tag)  # read-modify-write preserves neighbouring bits
        if tag["bit"] is not None:
            self._s7util.set_bool(raw, 0, tag["bit"], value != 0)
            stored = 1 if value != 0 else 0
        elif tag["size"] == 2:
            self._s7util.set_int(raw, 0, int(value))
            stored = int(value)
        elif tag["size"] == 4:
            self._s7util.set_dint(raw, 0, int(value))
            stored = int(value)
        else:
            raw[0] = int(value) & 0xFF
            stored = int(value) & 0xFF
        self._write_raw(tag, raw)
        return stored


# ---------------------------------------------------------------------------
# HTTP plumbing — no changes needed below.
# ---------------------------------------------------------------------------

PORT = int(os.environ.get("PORT", "8080"))


def make_driver():
    args = _parse_cli()
    if args.plc:
        return S7Driver(args.plc, args.rack, args.slot, args.plc_port)
    return DemoDriver()


def _parse_cli():
    parser = argparse.ArgumentParser(description="PLC Panel Studio LAN bridge")
    parser.add_argument("--plc", default=os.environ.get("PLC_IP", ""),
                        help="IP of the Siemens PLC (enables the S7/PROFINET driver)")
    parser.add_argument("--rack", type=int, default=int(os.environ.get("PLC_RACK", "0")))
    parser.add_argument("--slot", type=int, default=int(os.environ.get("PLC_SLOT", "1")))
    parser.add_argument("--plc-port", type=int, default=int(os.environ.get("PLC_PORT", "102")))
    return parser.parse_args()


DRIVER = None  # set in main()


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
            try:
                self._send_json(200, DRIVER.health())
            except Exception as error:
                self._send_json(503, {"ok": False, "error": str(error)})
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

        try:
            if path == "/api/read":
                addresses = [str(a) for a in body.get("addresses", []) if isinstance(a, str)]
                self._send_json(200, {"values": DRIVER.read_tags(addresses)})
            elif path == "/api/write":
                address = str(body.get("address", ""))
                if not address:
                    self._send_json(400, {"ok": False, "error": "address is required"})
                    return
                value = int(body.get("value") or 0)
                self._send_json(200, {"ok": True, "value": DRIVER.write_tags(address, value)})
            else:
                self._send_json(404, {"ok": False, "error": f"No route for POST {path}"})
        except ValueError as error:
            self._send_json(400, {"ok": False, "error": str(error)})
        except Exception as error:
            self._send_json(503, {"ok": False, "error": str(error)})

    def log_message(self, fmt, *args):
        print(f"{self.address_string()} - {fmt % args}")


def main():
    global DRIVER
    DRIVER = make_driver()
    server = ThreadingHTTPServer(("0.0.0.0", PORT), BridgeHandler)
    print(f"PLC bridge listening on port {PORT}  (driver: {DRIVER.name})")
    if DRIVER.name == "s7":
        print(f"  Target PLC: {DRIVER.ip} (rack {DRIVER.rack}, slot {DRIVER.slot})")
    for ip in _lan_addresses():
        print(f"  Add this device in the app ->  {ip}:{PORT}")
    print(f"  (local)                    ->  localhost:{PORT}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("Shutting down.")


if __name__ == "__main__":
    main()
