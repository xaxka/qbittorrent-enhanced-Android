# qbittorrentAndroid

Native Android client for [qBittorrent](https://github.com/qbittorrent/qBittorrent) in
two editions sharing one code base:

| Edition | Package | Description |
|---------|---------|-------------|
| **qBittorrent** (`standard` flavor) | `io.github.xixka.qbittorrent` | Pure remote-control client. Talks the **unmodified qBittorrent Web API v2** (default port **8080**, configurable host/port/username/password, optional HTTPS with trust-all for self-signed certs). |
| **qBittorrent Enhanced** (`enhanced` flavor) | `io.github.xixka.qbittorrent.enhanced` | Same client, plus a **bundled local `qbittorrent-enhanced-nox` engine** compiled from source by CI (bionic dynamic-linked build; started as a child process and controlled through the very same Web API). |

The UI is a **native Material 3 interface in the style of
[LibreTorrent](https://github.com/proninyaroslav/libretorrent)** — no WebView, no
WebUI embedding.

## Features

* Login against any qBittorrent 4.x/5.x WebUI (SID cookie flow, auto re-login)
* Torrent list with LibreTorrent-style cards: progress, state, speeds, seeds/peers, ratio
* Status filter tabs, sorting, categories
* Add torrents from URL / magnet link / local `.torrent` file (intent-sharing supported)
* Pause / resume / force start / recheck / reannounce
* Delete with optional file removal
* File priorities (skip / normal / high / maximum), sequential download, super seeding
* Torrent details: overview properties, content files, trackers (add/remove), peers
* Global speed limits, alternative speed limits toggle
* Multi-select bulk actions with a contextual action bar
* Enhanced edition: local engine management (start/stop, run at boot, LAN access mode)
* Enhanced edition: the bundled engine **starts together with the app** and the client
  auto-connects to it (127.0.0.1 + seeded WebUI credentials) — zero manual setup
* Edge-to-edge Material 3 layout, LibreTorrent-parity (inset-aware list, drawer, dividers)
* **In-app update check** against the GitHub Releases published by CI
  (menu → *Check for updates*, plus a non-intrusive daily automatic check)
* **13 languages**: English, 简体中文, 繁體中文, Русский, Deutsch, Français, Español,
  Português (Brasil), 日本語, 한국어, Türkçe, Italiano, Tiếng Việt — with per-app
  language support (Android 13+)
* Official qBittorrent launcher icon (adaptive + themed monochrome)

## Remote control API

The client implements the upstream Web API v2 unchanged, e.g.:

* `POST /api/v2/auth/login`
* `GET  /api/v2/torrents/info`, `properties`, `files`, `trackers`, `peers`
* `POST /api/v2/torrents/add|pause|resume|delete|recheck|reannounce|filePrio|…`
* `GET  /api/v2/transfer/info`, `POST /api/v2/transfer/setDownloadLimit|…`

Server address, port (default 8080), base path, username and password are configured
in **Settings**; nothing is hard-coded and no credentials are stored in the repository.

## Building

CI (`.github/workflows/android.yml`) cross-compiles `qbittorrent-enhanced-nox`
from the pinned qBittorrent-Enhanced-Edition source with the bionic pipeline of
[OpenListAndroid](https://github.com/xaxka/OpenListAndroid) (NDK r27c, Qt 6.8
static, OpenSSL 3.5, libtorrent 1.2, zlib-ng), packages it as
`libqbittorrent-nox.so` + `libc++_shared.so` into jniLibs, then builds both flavors.
A fast `compile-check` job builds the standard debug APK first so Kotlin/resource
errors surface within minutes:

```
./gradlew assembleStandardRelease assembleEnhancedRelease
```

### Release artifacts

* Enhanced edition is split **per ABI**: `arm64-v8a`, `armeabi-v7a`, `x86_64`
  (each with its own `versionCode` offset; the universal APK is kept for the
  ABI-independent standard edition only).
* All published APKs are **signed** with the committed CI key
  (`app/ci-signing.keystore` — a public, sideload-only key that guarantees a
  stable signature so releases can update each other in place; it is *not* a
  secret). There are no unsigned APKs.
* The in-app update checker reads the version info (`Version: YY.MM.DD
  (versionCode N)` + asset names) from the GitHub `dev` release that CI
  republishes on every build.

## License

AGPL-3.0 (see `LICENSE`). Third-party attributions in `NOTICE.md`.
