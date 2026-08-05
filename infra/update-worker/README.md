# gt7-updates worker

Serves the in-app update endpoint at
`https://gt7-updates.fh6rik.workers.dev` (Rik Cloudflare account,
`535cbeaa83ca309dd532ff386358e08f`), backed by the `gt7-updates` KV
namespace (`47e2996d1a5c4a738f4322af7e080304`):

- `GET /latest.json` — the update manifest the app polls on launch
- `GET /app.apk`     — the latest signed APK

This mirrors the FH6 Telemetry update setup exactly (`fh6-updates` worker).

The worker is **already deployed and serving** (first published manually on
2026-08-05 with v0.2.0 in KV). The release workflow
(`.github/workflows/release.yml`) re-deploys it idempotently and pushes each
release's APK + manifest into KV. It authenticates with either repo secret:

- `CF_API_TOKEN` — a scoped token with **Workers Scripts:Edit** +
  **Workers KV Storage:Edit** on the Rik account, or
- `CF_AUTH_EMAIL` + `CF_AUTH_KEY` — the Cloudflare Global API Key pair.

Until one is set, the publish step no-ops and releases still land on GitHub.

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
