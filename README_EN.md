# qbittorrentAndroid

[简体中文](README.md)

Native Android client for [qBittorrent](https://github.com/qbittorrent/qBittorrent) —
a single edition with a bundled engine.

The app **bundles a local `qbittorrent-enhanced-nox` engine**: cross-compiled
from source by CI and started as a child process with the app — works out of
the box, no server needed. At the same time it is a full remote-control
client: it can connect to and drive any qBittorrent server through the
original qBittorrent Web API v2 (Settings → Server connection, multiple
profiles, self-signed certificate support).

The **UI uses [LibreTorrent](https://github.com/proninyaroslav/libretorrent)**
(native Material 3 / Material You dynamic colors, no WebView, no WebUI
embedding); **features are modelled after
[qBitController](https://github.com/Bartuzen/qBitController)** and LibreTorrent.

## Features

* **Bundled engine, zero setup**: starts with the app, boot autostart, watchdog
  keep-alive (auto-restart when the engine dies); or switch to any qBittorrent
  4.x/5.x server (multiple profiles, self-signed certificates)
* **Torrent management**: LibreTorrent-style list; 13 status filters, 11 sort
  fields, categories and tags as filter dimensions; bulk pause/resume/recheck/
  delete; full qBC-style add dialog (rename, save path, content layout, stop
  condition, speed & share limits…)
* **Torrent details**: overview (rename, change save location), file priorities,
  tracker add/edit/remove, peers, pieces heatmap; per-torrent speed & share limits
* **RSS and search**: RSS subscription tree, article reader, one-tap download,
  automatic download rules; server-side search plugins with plugin management
  (the RSS section can be hidden from Settings)
* **qBittorrent preferences editor**: reads the live configuration and writes
  edits back through the exact WebUI Options API (applies instantly); the editor
  is **generated dynamically** — settings added by future qBittorrent versions
  can be edited without updating the app
* **Toolbox**: global/alternative speed limits, statistics popup, log viewer,
  in-app update check (manual + daily automatic)
* **LAN WebUI**: other devices on the same network can reach the bundled engine
  from a browser
* **English and Chinese (Simplified/Traditional)**, Material You dynamic colors, immersive edge-to-edge layout,
  official adaptive icon

## Download

APKs are built, signed and published automatically by GitHub Actions to
[Releases](https://github.com/xixka/qbittorrentAndroid/releases). Release notes
carry build information only (branch / commit / build time / version) —
everything else is documented here:

* Split **per ABI**: `arm64-v8a`, `armeabi-v7a`, `x86_64` — every APK bundles
  the engine (`arm64-v8a`: most phones/tablets; `armeabi-v7a`: 32-bit ARM
  devices; `x86_64`: devices/emulators)
* All published APKs are **signed** with the committed CI key
  (`app/ci-signing.keystore` — a public, sideload-only key that guarantees a
  stable signature so releases can update each other in place; it is *not* a
  secret)
* The in-app update checker reads the release metadata published by CI and
  opens the APK matching the device's ABI

Everything is compiled by CI (see `.github/workflows/android.yml`) — no local
build environment required.

## License

AGPL-3.0 (see `LICENSE`). Third-party attributions in `NOTICE.md`.
