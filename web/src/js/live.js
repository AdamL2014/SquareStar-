// Živé spojení: SSE (/api/stream) -> při výpadku automaticky polling (/api/status) -> opětovné připojení.
export function startLive(cfg, { onData, onState }) {
  const base = cfg.apiBase || "";
  let es = null, timer = null, retry = 1000, lastOk = 0, closed = false;

  const ok = (d) => { lastOk = Date.now(); retry = 1000; onData(d); onState("live"); };
  const fail = () => onState(Date.now() - lastOk > 15000 ? "offline" : "stale");

  async function poll() {
    try {
      const r = await fetch(base + "/api/status", { cache: "no-store" });
      if (!r.ok) throw new Error(r.status);
      ok(await r.json());
    } catch { fail(); }
  }
  function startPolling() { if (!timer) { poll(); timer = setInterval(poll, cfg.pollMs); } }
  function stopPolling() { clearInterval(timer); timer = null; }

  function connect() {
    if (closed || cfg.stream === false || !("EventSource" in window)) return startPolling();
    es = new EventSource(base + "/api/stream");
    es.addEventListener("status", (e) => { stopPolling(); try { ok(JSON.parse(e.data)); } catch { /* špatný paket */ } });
    es.addEventListener("offline", () => fail());
    es.onerror = () => {
      es.close(); es = null;
      startPolling();                                   // dokud se SSE nevrátí, jedeme pollingem
      setTimeout(connect, retry); retry = Math.min(retry * 2, 30000);
    };
  }
  document.addEventListener("visibilitychange", () => { if (!document.hidden) poll(); });
  connect();
  return () => { closed = true; es?.close(); stopPolling(); };
}
