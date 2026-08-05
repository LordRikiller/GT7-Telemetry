# gt7-updates worker

Serves the in-app update endpoint at
`https://gt7-updates.fh6rik.workers.dev` (Rik Cloudflare account,
`535cbeaa83ca309dd532ff386358e08f`), backed by the `gt7-updates` KV
namespace (`47e2996d1a5c4a738f4322af7e080304`):

- `GET /latest.json` — the update manifest the app polls on launch
- `GET /app.apk`     — the latest signed APK

This mirrors the FH6 Telemetry update setup exactly (`fh6-updates` worker).

The worker is **already deployed and serving** (first published manually on
2026-08-05 with v0.2.0 in KV) — same as `fh6-updates`, the worker itself is
managed outside CI with the commands below.

The release workflow (`.github/workflows/release.yml`) pushes each release's
APK + manifest into KV, exactly like the FH6-Telemetry repo: it authenticates
with the `CF_API_TOKEN` repo secret — a **Workers KV Storage:Edit** token on
the Rik account. That token's scope covers every KV namespace on the account,
so the **same token value used in FH6-Telemetry works here** — add it as
`CF_API_TOKEN` in this repo's Actions secrets. Until it's set, the publish
step no-ops and releases still land on GitHub.

Manual deploy, if ever needed:

```bash
curl -X PUT "https://api.cloudflare.com/client/v4/accounts/535cbeaa83ca309dd532ff386358e08f/workers/scripts/gt7-updates" \
  -H "Authorization: Bearer $CF_API_TOKEN" \
  -F "metadata=@metadata.json;type=application/json" \
  -F "worker.js=@worker.js;type=application/javascript+module"
curl -X POST "https://api.cloudflare.com/client/v4/accounts/535cbeaa83ca309dd532ff386358e08f/workers/scripts/gt7-updates/subdomain" \
  -H "Authorization: Bearer $CF_API_TOKEN" \
  -H "Content-Type: application/json" --data '{"enabled":true}'
```
