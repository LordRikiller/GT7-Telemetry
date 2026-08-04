export default {
  async fetch(request, env) {
    const path = new URL(request.url).pathname;
    if (path === "/latest.json") {
      const m = await env.KV.get("manifest");
      if (!m) return new Response("no manifest", { status: 404 });
      return new Response(m, {
        headers: {
          "content-type": "application/json",
          "cache-control": "no-cache",
          "access-control-allow-origin": "*",
        },
      });
    }
    if (path === "/app.apk") {
      const apk = await env.KV.get("apk", "arrayBuffer");
      if (!apk) return new Response("no apk", { status: 404 });
      return new Response(apk, {
        headers: {
          "content-type": "application/vnd.android.package-archive",
          "content-disposition": "attachment; filename=gt7-telemetry.apk",
        },
      });
    }
    if (path === "/") return new Response("GT7 Telemetry update endpoint\n", { status: 200 });
    return new Response("not found", { status: 404 });
  },
};
