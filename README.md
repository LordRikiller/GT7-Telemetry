# GT7 Telemetry — Android (standalone)

A native Android live pit-instrument for **Gran Turismo 7** on PS5/PS4 —
the sibling of [FH6-Telemetry](https://github.com/LordRikiller/FH6-Telemetry),
with the same dashboards, options and in-app updater, speaking GT7's
protocol instead of Forza's.

**The app is fully standalone**: the phone binds the UDP socket, asks the
console for the stream and decrypts it itself. No PC bridge, no Python, no
exe. Enter the PS5's IP and drive.

## Install

1. Download `gt7-telemetry-<version>.apk` from the
   [latest release](https://github.com/LordRikiller/GT7-Telemetry/releases/latest)
   on the phone (Android 8.0+).
2. Open the downloaded file. The first time, Android asks you to allow
   **installs from unknown apps** for your browser or file manager — allow
   it, then confirm the install.
3. That's the only manual install you'll ever do: from then on the app
   offers new versions itself (Settings → **App updates**, plus a prompt on
   launch when one is waiting).

## Set up — takes a minute

1. On the PS5: **Settings → Network → Connection Status** — note the
   console's IPv4 address.
2. Launch the app and type that address into the setup card (or later via
   Settings → **PlayStation**).
3. Phone and console must be on the **same Wi-Fi network**. Start GT7 and
   drive — there is nothing to enable in-game; the console streams to
   whoever asks. The setup card disappears the moment the first packet
   arrives.

## Using the app

Once packets flow, mount the phone and drive. Five screens: the
**dashboard** (the main instrument), the **data logger** (LOG), the
**setup sheet** (TUNE), the **AI race engineer** (AI) and **Settings**
(⚙). Both landscape and portrait have full layouts everywhere.

**What the instrument shows**

- Big **speed** (km/h or mph) and **gear**, with GT7's **suggested gear**
  when the game offers one.
- **RPM band scaled to the car's real rev-limiter** (GT7 broadcasts it),
  with shift lights as you approach the cut.
- **Lap timing** — live estimated lap, last/best and delta, plus **fuel per
  lap and laps of fuel remaining** once the app has seen a full lap.
- **Tyre-temperature pods** for all four corners, **water/oil temperature**,
  fuel level and boost.
- Status flags when the game reports them: **TCS active, handbrake,
  rev limiter**.

**Clusters.** The instrument is drawn from 52 marque-styled layouts across
9 gauge families, each modelled on the manufacturer's top-of-the-line
dashboard — a 992 GT3 RS, a Mustang GTD, a GR010 Hybrid, a 787B … Every
manufacturer in the 575-car catalog resolves to one, directly or via an
alias (tuners map to their base marque, the VGT design houses to the Gran
Turismo cluster). In **Auto** (the default) the app recognises the car
you're driving from its self-refreshing catalog and picks the matching
cluster — jump from a GT-R into a Porsche and the dash follows. **Manual**
keeps whichever layout you choose.

**Browse them all:** [docs/clusters.html](docs/clusters.html) renders every
layout with the app's exact colours and gauge geometry
([live preview](https://htmlpreview.github.io/?https://github.com/LordRikiller/GT7-Telemetry/blob/main/docs/clusters.html)).
The page is generated — run `python3 tools/gen_showcase.py` after adding a
layout so it never drifts from the code.

**The LOG button — a proper data logger.** Every full lap is recorded
automatically at 60 Hz: throttle, brake, steering angle, speed, gear, RPM,
tyre temperatures, racing line and lateral/longitudinal G. Completed laps
are also **saved to the phone** — the HISTORY view keeps the last 200 laps
across app restarts and play sessions, each stamped with the car, date and
declared tyre compound, and any lap (live or stored) exports as a
**full-rate CSV** via the share sheet. The screen shows live scrolling
input traces (throttle green, brake red, steering white — the classic
logger convention), the session's lap list with per-lap stats (full-throttle
/ braking / coasting shares, max G, delta to best), and for any lap a track
map drawn from the car's position — coloured by speed so braking zones pop
out — above the full lap traces. Steering needs GT7 ≥ 1.42 (the app asks
the console for the extended telemetry packet and falls back automatically
on older firmware).

**The TUNE button — the car's setup, as far as it can be known.** GT7
never transmits the settings sheet itself, but the stream reveals more
than you'd think, and the app collects all of it per car: the **fitted
gear ratios** (broadcast directly) with an **estimated final drive** and
speed-at-limiter per gear (recovered from engine vs wheel speed), the
**rev limiter** and the game's calculated top speed for the tune, **static
ride height** (measured whenever you're at a standstill) and how low the
body compresses under load, suspension travel used per corner, tyre radii,
turbo + peak boost, EV detection, tank size and which driver aids were
seen active. What physically never leaves the console — ARB, dampers,
camber/toe, diff numbers, downforce clicks, power/ECU — has a describe-it-
once field, and everything (measured + described) flows into the AI
briefing automatically.

**The AI button — your race engineer.** The app builds a *briefing*: car,
setup (measured + described), the lap table and a downsampled best-lap
trace, framed as a request for concrete tuning changes. Two ways to use
it, both free of surprises:

- **Share it** to any AI app you already pay for (ChatGPT, Claude, Gemini,
  Copilot…) via the Android share sheet — zero API cost.
- **Built-in engineer**: paste your own API key (Anthropic, or Google's
  Gemini free tier) and press Analyse. Exactly one capped request per
  press — no loop, no background usage, so a full analysis costs a few
  cents at most. The key never leaves the phone.

GT7's stream doesn't broadcast the tuning sheet itself, so there's a setup
notes field — describe your tune once and every briefing includes it.

**The ⚙ button** (top of the dash) opens Settings:

| Section | What it does |
|---|---|
| PlayStation | The console's LAN IP the app asks for telemetry |
| Dashboard | Auto (match the car) / Manual, and the layout list |
| Units | km/h ↔ mph, °C ↔ °F |
| App updates | Current version, check / download / install |

The screen stays awake while the dashboard is up, and a foreground service
keeps reading telemetry even if the screen turns off, so a mounted phone
picks up mid-race without missing a beat.

**If the setup card won't go away**

- Phone and console must be on the same network — guest Wi-Fi or an access
  point with *client/AP isolation* blocks the UDP stream.
- Double-check the IP against the console (it can change after a router
  reboot; a DHCP reservation for the PS5 helps).
- GT7 streams while you're driving — on some menu/replay screens the
  stream goes quiet; get on track.

## How it works

- A foreground service (`TelemetryService`) binds **UDP 33740**, sends a
  1-byte `'~'` heartbeat to the console's **port 33739** (on start, every
  100 packets and whenever the stream goes quiet), and runs the receive
  loop on its own thread, surviving screen-off so a mounted phone keeps
  reading. GT7 answers with encrypted telemetry at 60 Hz. `'~'` requests
  the 344-byte extended packet (steering angle, chassis G, suspension
  travel — GT7 ≥ 1.42). The heartbeat character is never mixed mid-session
  (the console pins the stream format to it); a Settings toggle switches
  to the legacy `'A'`/296-byte packet for a pre-1.42 game that won't
  answer `'~'` at all.
- `Packet.parse()` decrypts each "Simulator Interface" packet (Salsa20,
  key `"Simulator Interface Packet GT7 ver 0.0"[:32]`, nonce from the
  plaintext seed at 0x40 — XOR constant per format: 296 B `0xDEADBEAF`,
  316 B `0xDEADBEEF`, 344 B `0x55FABB4F`) and decodes it at the
  community-documented offsets — proven by unit tests against
  PyCryptodome-generated ciphertext.
- `LapRecorder` samples every frame into per-lap column arrays, splits on
  GT7's lap counter and stamps each lap with the game's official time; the
  logger UI and the race-engineer briefing both read from it.
- `TelemetryRepository` bridges the receiver to the Compose UI via
  `StateFlow`.
- The dashboard renders the instrument: big speed/gear, RPM band scaled to
  each car's real rev-limiter (GT7 broadcasts it), estimated live lap +
  last/best/delta, tyre-temperature pods, fuel/oil/boost, shift lights.
  Auto-selects a per-marque cluster (Ferrari, Porsche, GT-R, …) from the
  car you're driving, or pick one manually — 52 marque layouts.

## Project layout

```
app/src/main/java/com/gt7telemetry/
├── Salsa20.kt              Dependency-free Salsa20 (validated against PyCryptodome)
├── TelemetryFrame.kt       Packet.parse() (decrypt + 296/344-byte decode), Frame, LapTimer
├── TelemetryRepository.kt  Singleton bridge: two StateFlows (Frame, Status)
├── TelemetryService.kt     Foreground service: UDP 33740, '~' heartbeats, receive loop
├── MainActivity.kt         Service start, keep-screen-on, notif permission, Compose host
├── car/CarCatalog.kt       CarCode -> name/manufacturer (assets/gt7_car_catalog.json)
├── car/SetupProbe.kt       Measures the obtainable setup (ratios, ride height, …)
├── dash/                   The cluster engine (9 families × 52 marque themes)
├── logger/LapRecorder.kt   60 Hz per-lap trace store + lap summaries
├── engineer/               Race-engineer briefing builder + one-shot API client
├── settings/               DataStore prefs: PS5 IP, dash, units, engineer key
├── update/                 In-app updater (manifest check → download → install)
└── ui/                     Compose dashboard, data logger, engineer, settings

app/src/test/java/com/gt7telemetry/   Salsa20 + packet-offset tests (JVM, no device)
docs/clusters.html                    Generated showcase of every cluster layout
tools/gen_showcase.py                 Regenerates the showcase from ClusterTheme.kt
infra/update-worker/                  Cloudflare Worker behind the update endpoint
```

## Build

Open the folder in **Android Studio** (Koala / 2024.1+), let it sync, and
Run on a device. The Gradle wrapper is committed, so from the command line
(with `ANDROID_HOME` / `local.properties` pointing at an SDK):

```bash
./gradlew :app:assembleDebug      # build the APK -> app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest  # run the crypto + packet-parser unit tests
```

- minSdk 26, targetSdk 34, Kotlin 2.0.20, Compose (BOM 2024.09.00), AGP 8.5.2,
  Gradle 8.14.3, JVM 17. Dependencies are managed via `gradle/libs.versions.toml`.
- Package / applicationId: `com.gt7telemetry`.

## Releases & in-app updates

Pushing a semver tag (`git tag v0.1.0 && git push origin v0.1.0`) triggers
`.github/workflows/release.yml` — or run it from the Actions tab with a
version number. It builds the APK, verifies the signature against the
release keystore, attaches it to a GitHub Release and publishes the APK +
manifest to the update endpoint's KV — the same pipeline as FH6 Telemetry.
The workflow preflights the `CF_API_TOKEN` secret against the endpoint
before building, so a bad token fails in seconds, not after the build.

The app updates itself: on launch it checks
`https://gt7-updates.fh6rik.workers.dev/latest.json` and, if a newer
`versionCode` is offered, downloads the APK and hands it to the system
installer via a `FileProvider`. The Cloudflare Worker behind the endpoint
lives in `infra/update-worker/` and is deployed once, outside CI.

## Scope

**In:** standalone heartbeat + decrypt + parse (296 & 344-byte extended
packets), full live instrument, per-car auto clusters, km/h·mph and °C·°F
toggles, screen-wake, in-app updater, 60 Hz per-lap data logger (inputs,
line, G), track map, AI race-engineer briefing (share sheet or your own
Anthropic/Gemini key — one capped request per analysis).

**Not yet:** two-session compare, persistent lap storage across app
restarts, a read-back tuning sheet (GT7 doesn't broadcast setup values —
the briefing carries your own setup description instead).

MIT. Not affiliated with Sony Interactive Entertainment / Polyphony Digital.
