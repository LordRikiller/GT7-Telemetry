# GT7 Telemetry — Android (standalone)

A native Android live pit-instrument for **Gran Turismo 7** on PS5/PS4 —
the sibling of [FH6-Telemetry](https://github.com/LordRikiller/FH6-Telemetry),
with the same dashboards, options and in-app updater, speaking GT7's
protocol instead of Forza's.

**The app is fully standalone**: the phone binds the UDP socket, asks the
console for the stream and decrypts it itself. No PC bridge, no Python, no
exe. Enter the PS5's IP and drive.

## How it works

- A foreground service (`TelemetryService`) binds **UDP 33740**, sends a
  1-byte `'A'` heartbeat to the console's **port 33739** (on start, every
  100 packets and whenever the stream goes quiet), and runs the receive
  loop on its own thread, surviving screen-off so a mounted phone keeps
  reading. GT7 answers with encrypted telemetry at 60 Hz.
- `Packet.parse()` decrypts each 296-byte "Simulator Interface" packet
  (Salsa20, key `"Simulator Interface Packet GT7 ver 0.0"[:32]`, nonce from
  the plaintext seed at 0x40) and decodes it at the community-documented
  offsets — proven by unit tests against PyCryptodome-generated ciphertext.
- `TelemetryRepository` bridges the receiver to the Compose UI via
  `StateFlow`.
- The dashboard renders the instrument: big speed/gear, RPM band scaled to
  each car's real rev-limiter (GT7 broadcasts it), estimated live lap +
  last/best/delta, tyre-temperature pods, fuel/oil/boost, shift lights.
  Auto-selects a per-marque cluster (Ferrari, Porsche, GT-R, …) from the
  car you're driving, or pick one manually — same 20 layouts as the FH6 app.

## Setup

1. On the PS5: **Settings → Network → Connection Status** — note the
   console's IPv4 address.
2. Launch the app and type that address into the setup card (or Settings →
   PlayStation).
3. Phone and console must be on the **same Wi-Fi**. Start GT7 and drive —
   there is nothing to enable in-game; the console streams to whoever asks.
   The setup card disappears the moment the first packet arrives.

## Project layout

```
app/src/main/java/com/gt7telemetry/
├── Salsa20.kt              Dependency-free Salsa20 (validated against PyCryptodome)
├── TelemetryFrame.kt       Packet.parse() (decrypt + 296-byte decode), Frame, LapTimer
├── TelemetryRepository.kt  Singleton bridge: two StateFlows (Frame, Status)
├── TelemetryService.kt     Foreground service: UDP 33740, heartbeats, receive loop
├── MainActivity.kt         Service start, keep-screen-on, notif permission, Compose host
├── car/CarCatalog.kt       CarCode -> name/manufacturer (assets/gt7_car_catalog.json)
├── dash/                   The cluster engine (9 families × 20 marque themes)
├── settings/               DataStore prefs: PS5 IP, dash mode/layout, units
├── update/                 In-app updater (manifest check → download → install)
└── ui/                     Compose dashboard, settings screen, theme, formatting

app/src/test/java/com/gt7telemetry/   Salsa20 + packet-offset tests (JVM, no device)
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
`.github/workflows/release.yml`, which builds the signed APK, attaches it to
a GitHub Release, deploys the update worker and publishes the APK + manifest
to its KV — the same pipeline as FH6 Telemetry.

The app updates itself: on launch it checks
`https://gt7-updates.fh6rik.workers.dev/latest.json` and, if a newer
`versionCode` is offered, downloads the APK and hands it to the system
installer via a `FileProvider`. The endpoint goes live on the first release
after the `CF_API_TOKEN` repo secret is set (see `infra/update-worker/`).

## Scope — this is v1 (live dashboard)

**In:** standalone heartbeat + decrypt + parse, full live instrument,
km/h·mph and °C·°F toggles, screen-wake, per-car auto clusters, in-app
updater.

**Not yet (phase 2):** session recording, lap breakdown, route trace,
two-session compare, tuning-report export — same roadmap as the FH6 app.

MIT. Not affiliated with Sony Interactive Entertainment / Polyphony Digital.
