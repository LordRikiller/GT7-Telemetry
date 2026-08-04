# gt7-updates worker

Serves the in-app update endpoint at
`https://gt7-updates.fh6rik.workers.dev` (Rik Cloudflare account,
`535cbeaa83ca309dd532ff386358e08f`), backed by the `gt7-updates` KV
namespace (`47e2996d1a5c4a738f4322af7e080304`):

- `GET /latest.json` — the update manifest the app polls on launch
- `GET /app.apk`     — the latest signed APK

This mirrors the FH6 Telemetry update setup exactly (`fh6-updates` worker).

The release workflow (`.github/workflows/release.yml`) deploys this worker
idempotently and pushes each release's APK + manifest into KV. It needs the
repo secret `CF_API_TOKEN` with **Workers KV Storage:Edit** (for the publish)
and **Workers Scripts:Edit** (for the deploy) on the Rik account. Until the
secret is set, both steps no-op and releases still land on GitHub.

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
