# AGENTS.md — AI 代理协作指南

## 项目简介

Android 原生 qBittorrent 客户端：内置由 CI 交叉编译的 `qbittorrent-enhanced-nox` 引擎（以 `libqbittorrent-nox.so` 随 APK 分发），也可作为远程控制端经 Web API v2 连接任意 qBittorrent 4.x/5.x 服务器。UI 移植自 LibreTorrent（原生 View + Material 3，非 Compose）。

## 技术栈与结构

- 纯 Kotlin（无 Java 源码）、Groovy DSL（`build.gradle`，非 kts）；AGP 8.5.2、Kotlin 1.9.24、JDK 17、compileSdk 34 / minSdk 26
- ViewBinding + ViewModel + StateFlow（手写 `data/ServiceLocator`，无 Hilt、无 Compose）；Retrofit + OkHttp + Gson（Web API v2）、Coil（图片/SVG）
- 包名 `io.github.xixka.qbittorrent`，单模块 `app/`：
  - `api/` Web API v2 客户端（QBApiClient / QBApiService / QBExceptions）
  - `qbt/` 内置引擎生命周期：LocalEngineManager / LocalEngineService（ProcessBuilder 从 nativeLibraryDir exec 引擎）、BootReceiver、NoxConfig
  - `ui/` 按功能分包（main / detail / rss / search / qbsettings 动态设置编辑器 / settings / log / customviews 等）
  - `model/`、`data/`（仓库层与偏好）、`util/`
- `.github/scripts/build-qbt-nox-bionic.sh` 引擎交叉编译脚本；`config/` + `scripts/` 设置项覆盖门禁

## 构建与 CI（仅作参考，本地禁止执行）

- workflow：`.github/workflows/android.yml`；push master/main、PR、手动触发；重复推送会取消在途构建
- `qbt-nox` job：`bash .github/scripts/build-qbt-nox-bionic.sh <ABI> <OPENSSL_TARGET> <out> <prefix>` —— 依次编译 zlib-ng → OpenSSL 3.5.1 → Boost → Qt 6.8.3（static）→ libtorrent 1.2.20 → qBittorrent-Enhanced-nox（固定 commit），NDK r27c + ccache，产出 `libqbittorrent-nox.so` + `libc++_shared.so` 并做 bionic 动态链接校验
- `build` job：`python3 scripts/check_prefs_coverage.py`（设置项覆盖门禁）→ `./gradlew assembleRelease -PversionName=<引擎版本> -PversionCode=<UTC epoch 秒> --stacktrace` → 校验签名与引擎 → 发布 dev Release（arm64-v8a / armeabi-v7a / x86_64 三个分包 APK，固定文件名）
- 改动是否可用以 GitHub CI 编译通过为准

## 硬性工作规则

- 禁止本地编译：不要在本地运行任何构建/编译/测试命令（gradlew、cmake、python 门禁等）；改动是否可用以 GitHub CI 编译通过为准
- 每完成一个改动立即 commit 并 push，再进行下一项改动
- 所有提交使用 xaxka 身份（本 clone 已配置 user.name=xaxka，user.email=73456104+xaxka@users.noreply.github.com，不要改动）
- 任务结束后清理本地 clone

## 代码约定

- 源码注释、KDoc 与默认 `res/values/strings.xml` 均为英文，中文在 `res/values-zh-rCN`；提交信息用英文 Conventional Commits（如 `fix(search): ... (round-74)`）
- Gradle 与 workflow 内含大量英文长注释解释设计决策（版本方案、签名、ABI splits、libtorrent 选型），修改时保持该风格
- MVVM：ViewModel 暴露 `StateFlow<UiState>`（UiState 为带默认值的 data class）；日志用 `android.util.Log` + companion object 的 `TAG` 常量
- 版本号由 CI 以 `-PversionName` / `-PversionCode` 注入（versionName=引擎版本如 5.2.3.10，versionCode=UTC epoch 秒），本地回退 1.0.0/1；不要改动该方案
- release 签名用入库的公开自签名 `app/ci-signing.keystore`（密码 ci-qbt-android），确保 CI 包之间可直接覆盖安装

## 关键文档/敏感区

- 动手前先读 `README.md`（功能与发布机制）与 `NOTICE.md`（AGPL-3.0 及 LibreTorrent/qBittorrent 的归属与许可义务，UI 移植部分须保留出处）
- `app/src/main/jniLibs/`（nox 引擎 .so）为 CI 产物、不入库；本地缺失时引擎功能自动降级（isSupported 判定），不要手工放置或提交二进制
- 设置编辑器受 CI 门禁约束：`ui/qbsettings/QBPrefSchema.kt` 必须覆盖 `config/qb_enhanced_prefs_required.txt` 的全部约 219 个引擎 WebUI 键（`scripts/check_prefs_coverage.py` 校验，未覆盖即构建失败）；再生成参考清单的步骤见该脚本 docstring
- libtorrent 锁定 1.2.20 而非 2.x 是有意为之（2.x mmap 存储在低内存设备上 RSS 膨胀），升级前必读 `android.yml` 顶部 env 注释
- `.github/workflows/android.yml` 顶部 env 集中定义全部依赖版本与固定 commit（QBT_REF / LT_REF / QT_VER 等），是引擎构建的唯一事实来源
