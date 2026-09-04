# qbittorrentAndroid

[简体中文](README.md)

Native Android client for [qBittorrent](https://github.com/qbittorrent/qBittorrent) —
a single edition with a bundled engine:

| Description |
|-------------|
| **Bundles a local `qbittorrent-enhanced-nox` engine** cross-compiled from source by CI, started as a child process and controlled through the Web API — works out of the box, no server needed — while remaining a full remote-control client for any qBittorrent server (Settings → Server, multiple profiles, optional HTTPS with trust-all for self-signed certs). |

The UI is a **native Material 3 interface (Material You dynamic colors on by
default)** in the style of
[LibreTorrent](https://github.com/proninyaroslav/libretorrent) — no WebView, no
WebUI embedding. Feature set modelled after
[qBitController](https://github.com/Bartuzen/qBitController) and LibreTorrent.

## Features

* Login against any qBittorrent 4.x/5.x WebUI (SID cookie flow, auto re-login)
* Torrent list with LibreTorrent-style cards: progress, state, speeds, seeds/peers, ratio
* Filtering & sorting: 13 status filters, 11 sort fields (name / size / progress /
  ETA / ratio / download speed / upload speed / uploaded / added date /
  completion date / peers), added-date filter, **categories and tags as two
  independent filter dimensions**
* Add torrents from URL / magnet link / local `.torrent` file (intent-sharing
  supported) with the full qBitController parameter set (rename, category,
  save path, content layout, stop condition, per-torrent limits, start paused…)
* Bulk actions: pause / resume / force start / recheck / reannounce / delete
  (with optional file removal), set category and tags
* **Torrent details**: overview (rename, change save location), content files
  (priorities), trackers (add/remove/edit), peers, **pieces state heatmap**;
  per-torrent speed & share limits, super seeding
* **Statistics panel**: user / cache / performance statistics (same data as
  qBitController, LibreTorrent-style presentation)
* **RSS**: full subscription-tree management (add/rename/move/delete),
  article reader, mark-as-read, one-tap download, **automatic download rules**
  (must/must-not contain, regex, smart episode filter, category, save path,
  affected feeds)
* **Search engine**: runs the server's search plugins by category, one-tap
  download of results; plugin management (install/uninstall/update/enable)
* **Log viewer**: engine execution log (log/main) with level filtering
* **Multi-server management**: qBitController-style server profiles with
  one-tap switching, editing and deletion; switch between the bundled
  engine and remote servers at any time
* **Full qBittorrent preferences editor** (Settings → *qBittorrent settings*):
  reads the live configuration of the connected instance and writes edits back
  through the exact WebUI Options API — **covering all 217 qBittorrent Enhanced
  WebUI Options keys** across seven tabs (Downloads / Speed / BitTorrent /
  Connection / WebUI / RSS / Advanced); changes apply live without restart.
  A dedicated `prefs-coverage` CI gate keeps the coverage from regressing
* Global speed limits and alternative speed limits (tap the download/upload
  speed rows of the drawer's transfer stats)
* Fully automatic engine lifecycle: starts with the app, **boot autostart**,
  **watchdog keep-alive** (auto-restarts the engine when it dies), retry
  prompt only when the engine fails to start, LAN WebUI access
* Immersive edge-to-edge layout (gesture-navigation-bar inset aware)
* **In-app update check** against the GitHub Releases published by CI
  (manual check + non-intrusive daily automatic check)
* **13 languages**: 简体中文, 繁體中文, English, Русский, Deutsch, Français,
  Español, Português (Brasil), 日本語, 한국어, Türkçe, Italiano, Tiếng Việt —
  with per-app language support (Android 13+)
* Official qBittorrent launcher icon (adaptive + themed monochrome)

## Download

APKs are built, signed and published automatically by GitHub Actions to
[Releases](https://github.com/xixka/qbittorrentAndroid/releases):

* Split **per ABI**: `arm64-v8a`, `armeabi-v7a`,
  `x86_64` (each with its own `versionCode` offset; every APK bundles the
  engine)
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
