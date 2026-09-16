/**
 * PLC Panel Studio — LAN bridge server (Node.js, zero dependencies).
 *
 * Run:   node server.js            (PORT env var optional, default 8080)
 * Test:  curl http://<your-ip>:8080/api/health
 *
 * Contract (must match PlcProtocol.kt in the Android app):
 *   GET  /api/health              -> {ok, cpu, ioOk, ioTotal, uptime}
 *   POST /api/read   {addresses}  -> {values: {address: int}}
 *   POST /api/write  {address, value} -> {ok, value}
 *
 * Out of the box this ships a demo PLC with the same seal-in start/stop
 * ladder logic as the app's built-in simulator:
 *   - any non-stop input > 0  latches the motor ON
 *   - any "stop"-like input > 0 (name contains "stop" or ends in ".4") drops it
 *   - MW/AW/QW analogue addresses animate while the motor runs
 *
 * To drive a REAL PLC, replace the bodies of readTags() and writeTags()
 * with your protocol calls (e.g. Modbus TCP via the `modbus-serial` package,
 * or S7 via `nodes7`). Everything else stays as-is.
 */

const http = require('http');
const os = require('os');

const PORT = Number(process.env.PORT || 8080);

// ---------------------------------------------------------------------------
// PLC adapter — the ONLY part you need to change for real hardware.
// ---------------------------------------------------------------------------

const inputs = new Map();   // written by the app (buttons, selectors)
const outputs = new Map();  // derived by ladder logic
let motorSealed = false;
const startedAt = Date.now();

function isStopLike(address) {
  return address.toLowerCase().includes('stop') || address.endsWith('.4');
}

function elapsedSeconds() {
  return Math.floor((Date.now() - startedAt) / 1000);
}

/** Seal-in circuit: start latches, stop drops. */
function recomputeLogic() {
  let stopPressed = false;
  let startPressed = false;
  for (const [address, value] of inputs) {
    if (value > 0) {
      if (isStopLike(address)) stopPressed = true;
      else startPressed = true;
    }
  }
  if (stopPressed) motorSealed = false;
  else if (startPressed) motorSealed = true;
  outputs.set('__motor', motorSealed ? 1 : 0);
}

/** Fallback value for addresses that were never written. */
function derive(address) {
  const running = outputs.get('__motor') || 0;
  const seed = Math.abs(hashCode(address));
  if (/^(mw|aw|qw)/i.test(address)) {
    if (running === 0) return 0;
    return Math.max(0, Math.min(100, Math.round(50 + 45 * Math.sin(elapsedSeconds() / 3 + (seed % 7)))));
  }
  if (address.toLowerCase().includes('fault')) return 0;
  return running;
}

function hashCode(text) {
  let hash = 0;
  for (let i = 0; i < text.length; i++) {
    hash = (hash << 5) - hash + text.charCodeAt(i);
    hash |= 0;
  }
  return hash;
}

/** Replace with real device reads (returns a map of address -> int). */
function readTags(addresses) {
  recomputeLogic();
  const values = {};
  for (const address of addresses) {
    if (inputs.has(address)) values[address] = inputs.get(address);
    else if (outputs.has(address)) values[address] = outputs.get(address);
    else values[address] = derive(address);
  }
  return values;
}

/** Replace with real device writes (returns the value actually stored). */
function writeTags(address, value) {
  inputs.set(address, value);
  recomputeLogic();
  return value;
}

// ---------------------------------------------------------------------------
// HTTP plumbing — no changes needed below.
// ---------------------------------------------------------------------------

function lanAddresses() {
  const results = [];
  for (const nets of Object.values(os.networkInterfaces())) {
    for (const net of nets || []) {
      if (net.family === 'IPv4' && !net.internal) results.push(net.address);
    }
  }
  return results;
}

function sendJson(res, status, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    'Content-Type': 'application/json',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type'
  });
  res.end(body);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', chunk => {
      data += chunk;
      if (data.length > 1_000_000) reject(new Error('Payload too large'));
    });
    req.on('end', () => {
      if (!data) return resolve({});
      try { resolve(JSON.parse(data)); } catch { reject(new Error('Invalid JSON')); }
    });
    req.on('error', reject);
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);

  if (req.method === 'OPTIONS') {
    sendJson(res, 204, {});
    return;
  }

  try {
    if (req.method === 'GET' && url.pathname === '/api/health') {
      const upMinutes = Math.floor(elapsedSeconds() / 60);
      sendJson(res, 200, {
        ok: true,
        cpu: 6 + (elapsedSeconds() % 9),
        ioOk: 128,
        ioTotal: 128,
        uptime: `${Math.floor(upMinutes / 60)}h ${upMinutes % 60}m`
      });
      return;
    }

    if (req.method === 'POST' && url.pathname === '/api/read') {
      const body = await readBody(req);
      const addresses = Array.isArray(body.addresses) ? body.addresses.map(String) : [];
      sendJson(res, 200, { values: readTags(addresses) });
      return;
    }

    if (req.method === 'POST' && url.pathname === '/api/write') {
      const body = await readBody(req);
      const address = String(body.address || '');
      if (!address) {
        sendJson(res, 400, { ok: false, error: 'address is required' });
        return;
      }
      const value = Number(body.value) || 0;
      const stored = writeTags(address, Math.round(value));
      sendJson(res, 200, { ok: true, value: stored });
      return;
    }

    sendJson(res, 404, { ok: false, error: `No route for ${req.method} ${url.pathname}` });
  } catch (error) {
    sendJson(res, 400, { ok: false, error: error.message || 'Bad request' });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`PLC bridge listening on port ${PORT}`);
  for (const ip of lanAddresses()) {
    console.log(`  Add this device in the app ->  ${ip}:${PORT}`);
  }
  console.log(`  (local)                      ->  localhost:${PORT}`);
});
