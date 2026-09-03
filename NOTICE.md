# NOTICE

This project is licensed under the GNU AGPL-3.0 (see `LICENSE`).

## Third-party components

* **LibreTorrent** — https://github.com/proninyaroslav/libretorrent (GPL-3.0)
  The UI is a port of LibreTorrent's Material 3 interface: theme system,
  component styles, drawable icons, list/card/dialog/menu layouts and the
  EmptyRecyclerView/EmptyListPlaceholder custom views are copied (GPL-3.0,
  compatible with this project's AGPL-3.0 one-way) from the source above and
  adapted to the qBittorrent Web API data layer. Original copyright belongs to
  Yaroslav Pronin and LibreTorrent contributors.

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
  standard qBittorrent Web API v2. The launcher icon uses the **official
  qBittorrent logo** (`src/icons/qbittorrent-tray.svg` and the monochrome
  `qbittorrent-tray-dark.svg`, GPL) converted to Android vector drawables
  with all transforms baked into the path data.

* **AndroidX / Material Components** (Apache-2.0), **Retrofit / OkHttp / Gson**
  (Apache-2.0) — used as libraries.
