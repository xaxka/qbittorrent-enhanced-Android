# NOTICE

This project is licensed under the GNU AGPL-3.0 (see `LICENSE`).

## Third-party components

* **LibreTorrent** — https://github.com/proninyaroslav/libretorrent (GPL-3.0)
  UI/UX inspiration: Material 3 torrent-list card design, status filter tabs,
  detail pages layout and the default theme palette. No source files were copied;
  the Gradle wrapper is the standard Gradle distribution wrapper.

* **OpenListAndroid** — https://github.com/xaxka/OpenListAndroid (AGPL-3.0)
  `.github/scripts/build-qbt-nox-bionic.sh` is derived from OpenListAndroid's
  bionic cross-compilation pipeline for `qbittorrent-enhanced-nox`
  (NDK r27c + Qt 6.8 static + OpenSSL 3.5 + libtorrent RC_1_2 + zlib-ng).
  The runtime approach (packaging the engine as `libqbittorrent-nox.so` in
  jniLibs, launching via `ProcessBuilder` with `LD_LIBRARY_PATH`) follows the
  same project's design.

* **qBittorrent Enhanced Edition** — https://github.com/c0re100/qBittorrent-Enhanced-Edition
  (GPL) and **qBittorrent** — https://github.com/qbittorrent/qBittorrent (GPL):
  the `enhanced` flavor bundles a `qbittorrent-enhanced-nox` binary built from
  the pinned source commit `44ee266a575600d04788623b6939e47443d27ed1`
  (release-5.2.3.10) during CI. The client communicates with it over the
  standard qBittorrent Web API v2.

* **AndroidX / Material Components** (Apache-2.0), **Retrofit / OkHttp / Gson**
  (Apache-2.0) — used as libraries.
