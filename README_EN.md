# qbittorrent-enhanced-Android

[简体中文](README.md)

Native Android client for [qBittorrent](https://github.com/qbittorrent/qBittorrent):
bundles a CI cross-compiled `qbittorrent-enhanced-nox` engine that works out of
the box, and doubles as a full remote client for any qBittorrent 4.x/5.x server
over Web API v2 (multiple profiles, self-signed certificates).

UI based on [LibreTorrent](https://github.com/proninyaroslav/libretorrent)
(native Material 3 / Material You, no WebView), features modelled after
[qBitController](https://github.com/Bartuzen/qBitController).

## Features

- **Bundled engine**: starts with the app, boot autostart, watchdog keep-alive;
  LAN WebUI
- **Torrent management**: status filters, sorting, categories/tags, bulk
  actions (queue priority, set location, …)
- **Torrent details**: state dashboard, file checkboxes & priorities, trackers,
  peers, pieces map, per-torrent speed & share limits
- **RSS & search**: subscription tree, auto-download rules, engine search with
  plugin management
- **Preferences editor**: dynamically generated, covers every engine setting,
  applies instantly
- **DoH bootstrap** (optional): resolves DHT bootstrap nodes over encrypted
  DNS, bypassing carrier DNS pollution
- **Extras**: global/alternative speed limits, statistics, log viewer, in-app
  updates; English and Simplified Chinese UI

## Download

[Releases](https://github.com/xaxka/qbittorrent-enhanced-Android/releases) hosts
signed, per-ABI APKs (arm64-v8a / armeabi-v7a / x86_64, each bundling the
engine) that install/upgrade in place; the app can also check for updates.

Everything is compiled by CI (see `.github/workflows/android.yml`) — no local
build environment required.

## License

AGPL-3.0 (see `LICENSE`). Third-party attributions in `NOTICE.md`.
