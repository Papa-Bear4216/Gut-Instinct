import { createServer } from "node:http";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const NODE = "gut-instinct";
const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const enginePath = join(
  root,
  "contextual-coach/app/src/main/kotlin/com/registry/coach/engine/PatternEngine.kt",
);
const denylistPath = join(root, "contextual-coach/app/src/main/res/raw/sensitive_apps.json");

export function isLoopbackAddress(addr) {
  return addr === "127.0.0.1" || addr === "::1" || addr === "::ffff:127.0.0.1";
}

function envelope(body, result) {
  return { v: 1, inReplyTo: body.id, from: NODE, to: body.from, ok: true, result };
}

function readContract() {
  const source = readFileSync(enginePath, "utf8");
  const match = source.match(/const val MIN_EVIDENCE = (\d+)/);
  if (!match) return { error: "coach contract unreadable" };
  const denylist = JSON.parse(readFileSync(denylistPath, "utf8"));
  const sensitivePackages = Array.isArray(denylist.packages) ? denylist.packages.length : 0;
  if (sensitivePackages < 1) return { error: "coach denylist is empty" };
  return { minEvidence: Number(match[1]), sensitivePackages };
}

export function handleGutDelegate(body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) {
    return { status: 400, body: { error: "envelope must be a JSON object" } };
  }
  if (body.to !== NODE || body.v !== 1) {
    return { status: 400, body: { error: "envelope is not addressed to gut-instinct" } };
  }
  if (body.action === "ping") {
    return { status: 200, body: envelope(body, { node: NODE, action: "ping" }) };
  }
  if (body.action === "contract") {
    try {
      const contract = readContract();
      if (contract.error) return { status: 500, body: { error: contract.error } };
      return { status: 200, body: envelope(body, { node: NODE, action: "contract", ...contract }) };
    } catch {
      return { status: 500, body: { error: "coach contract unreadable" } };
    }
  }
  return { status: 400, body: { error: "gut-instinct accepts ping or contract" } };
}

function readBody(req, limit) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on("data", (chunk) => {
      size += chunk.length;
      if (size > limit) {
        reject(new Error("delegate body too large"));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    req.on("error", () => reject(new Error("body stream failed")));
  });
}

function send(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    "Content-Type": "application/json",
    "Content-Length": Buffer.byteLength(payload),
  });
  res.end(payload);
}

export function startGutDelegate(port) {
  const server = createServer(async (req, res) => {
    const path = (req.url ?? "/").split("?")[0];
    if (req.method === "GET" && path === "/health") {
      send(res, 200, { ok: true, node: NODE });
      return;
    }
    if (req.method !== "POST" || path !== "/mishmash/delegate") {
      send(res, 404, { error: "Not Found" });
      return;
    }
    if (!isLoopbackAddress(req.socket.remoteAddress)) {
      send(res, 403, { error: "delegate is only accepted from this PC" });
      return;
    }
    try {
      const raw = await readBody(req, 64 * 1024);
      const decoded = raw ? JSON.parse(raw) : {};
      const outcome = handleGutDelegate(decoded);
      send(res, outcome.status, outcome.body);
    } catch (err) {
      send(res, 400, { error: err instanceof Error ? err.message : "bad body" });
    }
  });
  return new Promise((resolve) => {
    server.listen(port, "127.0.0.1", () => resolve(server));
  });
}

const entry = process.argv[1] ?? "";
if (entry.endsWith("delegate.mjs")) {
  const port = Number(process.env.MISHMASH_PORT || 39404);
  startGutDelegate(port).then(() => {
    console.log(`gut-instinct mishmash on 127.0.0.1:${port}`);
  });
}
