#!/usr/bin/env bash
#
# qbittorrent-enhanced-nox —— Android (bionic) 动态链接版交叉编译
#
# 背景：上游 c0re100 的 musl 静态二进制在 Android 上 DNS 全灭（musl resolver 硬编码
# /etc/resolv.conf，缺失时回退 127.0.0.1:53 无监听），且 App 内 SOCKS5 代理无
# UDP ASSOCIATE，DHT（UDP）流量被 libtorrent 交给代理后全部丢弃。
#
# 本脚本改用 NDK 工具链链接 bionic：getaddrinfo → netd（继承系统 Private DNS /
# DNS64 / VPN DNS），DHT/peer/tracker 全部直连，DNS 与 DHT 双双根治。
# 依赖链与上游 cross_build.sh 对齐（Qt6 静态 + openssl-linked + libtorrent RC_1_2 +
# Boost 纯头文件 + zlib-ng(compat)），仅把 musl 静态换成 bionic 动态：
# 产物是 PIE 可执行文件，动态依赖为 bionic 系统库 + libc++_shared.so
# （Qt 在 Android 强制 c++_shared；该 .so 随产物一并输出，由 App 经
# LD_LIBRARY_PATH=nativeLibraryDir 提供给子进程）。
#
# 用法（由 .github/workflows/build.yml 调用）：
#   bash build-qbt-nox-bionic.sh <ABI> <OPENSSL_TARGET> <OUT_DIR> <PREFIX_DIR>
# 例：
#   bash build-qbt-nox-bionic.sh arm64-v8a android-arm64 \
#        "$GITHUB_WORKSPACE/qbt-out" "$GITHUB_WORKSPACE/qbt-prefix"
#
# 环境变量（可选覆盖）：
#   ANDROID_NDK_HOME / ANDROID_NDK_ROOT  NDK 路径（必填其一）
#   ANDROID_PLATFORM    默认 android-24（Qt 6.8 最低支持 API 24）
#   QT_VER / OPENSSL_VER / BOOST_VER / ZLIB_NG_VER
#   QBT_REF / LT_REF    固定 commit SHA（默认与上游 release-5.2.3.10 配方一致）
#   NDK_CCACHE          如 "ccache" 则启用编译缓存（NDK 工具链原生支持）
#
# 各阶段幂等（以安装产物为标记），配合 actions/cache 可断点续跑。

set -euo pipefail

ABI="${1:?usage: build-qbt-nox-bionic.sh <ABI> <OPENSSL_TARGET> <OUT_DIR> <PREFIX_DIR>}"
OPENSSL_TARGET="${2:?missing OPENSSL_TARGET}"
OUT_DIR="${3:?missing OUT_DIR}"
PREFIX_DIR="${4:?missing PREFIX_DIR}"

QT_VER="${QT_VER:-6.8.3}"
OPENSSL_VER="${OPENSSL_VER:-3.5.1}"
BOOST_VER="${BOOST_VER:-1.86.0}"
ZLIB_NG_VER="${ZLIB_NG_VER:-2.3.3}"
QBT_REPO="${QBT_REPO:-https://github.com/c0re100/qBittorrent-Enhanced-Edition.git}"
# release-5.2.3.10（与 App QBittorrentSpec.EMBEDDED_VERSION 一致）
QBT_REF="${QBT_REF:-44ee266a575600d04788623b6939e47443d27ed1}"
LT_REPO="${LT_REPO:-https://github.com/arvidn/libtorrent.git}"
# 上游 cross_build.sh 的 LIBTORRENT_BRANCH=RC_1_2 固定到具体 commit
LT_REF="${LT_REF:-c5ff6c3186a92ddec01f6f0a8146aaedb4a1c3f9}"
ANDROID_PLATFORM="${ANDROID_PLATFORM:-android-24}"

NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [ -z "$NDK" ] || [ ! -d "$NDK" ]; then
  echo "ERROR: ANDROID_NDK_HOME/ANDROID_NDK_ROOT 未设置或不存在: $NDK" >&2
  exit 1
fi
TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake"
NDK_HOST_PREBUILT="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
API_LEVEL="${ANDROID_PLATFORM#android-}"
# qtbase 的 QtPlatformAndroid.cmake 要求 ANDROID_SDK_ROOT 作为 CMake 变量（无环境变量回退），
# 且目录必须存在（jar 缺失仅告警）；默认取 GH runner 预装路径。
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}"

JOBS="$(nproc)"
[ "$JOBS" -gt 2 ] || JOBS=2

# 源码/下载/构建树放在 prefix 的同名 .build 目录（不进 actions/cache，每轮重建）
BUILD_ROOT="$PREFIX_DIR.build"
SRC_DIR="$BUILD_ROOT/src"
DL_DIR="$BUILD_ROOT/downloads"
WORK_DIR="$BUILD_ROOT/work"
HOST_QT="$PREFIX_DIR/qt-host/$QT_VER/gcc_64"   # aqt 预编译桌面版（host 工具）
QT_PREFIX="$PREFIX_DIR/qt"         # 交叉编译安装的静态 Qt

# 阶段完成标记：仅用于「版本已由 prefix 缓存 key 锁定」的阶段（zlib-ng/OpenSSL/
# Boost/Qt host/Qt android）。libtorrent 与 qBt 的 ref 不在 prefix 缓存 key 内，
# 永不标记、每次重建，避免换 ref 后用到陈旧产物。
stage_done() { [ -f "$PREFIX_DIR/.stage-$1" ] && { log "$1 已完成（缓存命中），跳过"; return 0; } || return 1; }
mark_done()  { touch "$PREFIX_DIR/.stage-$1"; }

mkdir -p "$OUT_DIR" "$PREFIX_DIR" "$SRC_DIR" "$DL_DIR" "$WORK_DIR"

# 动态依赖白名单：bionic 系统库 + libc++_shared.so（Qt 在 Android 强制 c++_shared，
# 该库随产物输出并由 App 在拉起子进程时经 LD_LIBRARY_PATH 提供）
ALLOWED_NEEDED='^(libc\.so|libm\.so|libdl\.so|liblog\.so|libandroid\.so|libc\+\+_shared\.so)$'

log() { printf '\n========== %s ==========\n' "$*" >&2; }
fetch() {  # fetch <url> <dest>
  curl -fSL --retry 5 --retry-delay 3 --connect-timeout 20 "$1" -o "$2"
}

cmake_common=(
  -G Ninja
  -DCMAKE_BUILD_TYPE=Release
  -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN_FILE"
  -DANDROID_ABI="$ABI"
  -DANDROID_PLATFORM="$ANDROID_PLATFORM"
  -DANDROID_STL=c++_shared
  -DCMAKE_POSITION_INDEPENDENT_CODE=ON
  -DCMAKE_INSTALL_PREFIX="$PREFIX_DIR"
)

# NDK 工具链的 ccache 接入：必须作为 CMake 变量传递（-DNDK_CCACHE），环境变量不生效
NDK_CCACHE_ARGS=()
if command -v ccache >/dev/null 2>&1; then
  NDK_CCACHE_ARGS=(-DNDK_CCACHE=ccache)
  cmake_common+=("${NDK_CCACHE_ARGS[@]}")
fi

log "环境：ABI=$ABI OPENSSL_TARGET=$OPENSSL_TARGET API=$API_LEVEL"
log "版本：Qt=$QT_VER OpenSSL=$OPENSSL_VER Boost=$BOOST_VER zlib-ng=$ZLIB_NG_VER"
log "refs：qBt=$QBT_REF libtorrent=$LT_REF"
log "NDK=$NDK  jobs=$JOBS"
df -h "$PREFIX_DIR" >&2 || true

# ---------------------------------------------------------------- zlib-ng
build_zlib_ng() {
  stage_done zlib-ng && return
  log "构建 zlib-ng $ZLIB_NG_VER（ZLIB_COMPAT）"
  local src="$SRC_DIR/zlib-ng-$ZLIB_NG_VER"
  if [ ! -d "$src" ]; then
    fetch "https://github.com/zlib-ng/zlib-ng/archive/refs/tags/$ZLIB_NG_VER.tar.gz" \
      "$DL_DIR/zlib-ng-$ZLIB_NG_VER.tar.gz"
    mkdir -p "$src"
    tar -xzf "$DL_DIR/zlib-ng-$ZLIB_NG_VER.tar.gz" --strip-components=1 -C "$src"
  fi
  rm -rf "$WORK_DIR/zlib-ng"
  cmake -S "$src" -B "$WORK_DIR/zlib-ng" "${cmake_common[@]}" \
    -DBUILD_SHARED_LIBS=OFF -DZLIB_COMPAT=ON -DWITH_GTEST=OFF
  cmake --build "$WORK_DIR/zlib-ng" --parallel "$JOBS"
  cmake --install "$WORK_DIR/zlib-ng"
  rm -rf "$WORK_DIR/zlib-ng"
  mark_done zlib-ng
}

# ---------------------------------------------------------------- OpenSSL
build_openssl() {
  stage_done openssl && return
  log "构建 OpenSSL $OPENSSL_VER（$OPENSSL_TARGET 静态）"
  local src="$SRC_DIR/openssl-$OPENSSL_VER"
  if [ ! -d "$src" ]; then
    fetch "https://github.com/openssl/openssl/releases/download/openssl-$OPENSSL_VER/openssl-$OPENSSL_VER.tar.gz" \
      "$DL_DIR/openssl-$OPENSSL_VER.tar.gz"
    mkdir -p "$src"
    tar -xzf "$DL_DIR/openssl-$OPENSSL_VER.tar.gz" --strip-components=1 -C "$src"
  fi
  (
    cd "$src"
    export PATH="$NDK_HOST_PREBUILT/bin:$PATH"
    ./Configure "$OPENSSL_TARGET" \
      -static -fPIC no-tests no-shared no-docs \
      -D__ANDROID_API__="$API_LEVEL" \
      --prefix="$PREFIX_DIR"
    make -j"$JOBS" >/dev/null
    make install_sw
  )
  mark_done openssl
}

# ---------------------------------------------------------------- Boost（纯头文件）
install_boost_headers() {
  stage_done boost && return
  log "安装 Boost $BOOST_VER 头文件"
  local src="$SRC_DIR/boost-$BOOST_VER"
  if [ ! -d "$src" ]; then
    local dest="$DL_DIR/boost-$BOOST_VER.tar.bz2"
    fetch "https://archives.boost.io/release/$BOOST_VER/source/boost_${BOOST_VER//./_}.tar.bz2" "$dest" ||
      fetch "https://sourceforge.net/projects/boost/files/boost/$BOOST_VER/boost_${BOOST_VER//./_}.tar.bz2/download" "$dest"
    mkdir -p "$src"
    tar -xjf "$dest" --strip-components=1 -C "$src"
  fi
  mkdir -p "$PREFIX_DIR/include"
  rm -rf "$PREFIX_DIR/include/boost"
  cp -r "$src/boost" "$PREFIX_DIR/include/"
  mark_done boost
}

# ---------------------------------------------------------------- Qt host 工具（aqt 预编译）
install_qt_host() {
  stage_done qt-host && return
  log "安装 Qt $QT_VER 桌面版 host 工具（aqt）"
  local aqt_bin=""
  if command -v aqt >/dev/null 2>&1; then
    aqt_bin="$(command -v aqt)"
  else
    # 用独立 venv 安装（路径确定；pipx 的 PIPX_BIN_DIR 在 runner 上可能不在 ~/.local/bin）
    local venv="$BUILD_ROOT/aqt-venv"
    python3 -m venv "$venv"
    "$venv/bin/pip" -q install aqtinstall
    aqt_bin="$venv/bin/aqt"
  fi
  echo "aqt: $aqt_bin" >&2
  "$aqt_bin" install-qt -O "$PREFIX_DIR/qt-host" linux desktop "$QT_VER" \
    --archives qtbase qttools icu
  # Qt 6.8 起 host 工具（moc/rcc 等）安装在 libexec/；旧版本在 bin/
  test -x "$HOST_QT/libexec/moc" || test -x "$HOST_QT/bin/moc"
  mark_done qt-host
}

# ---------------------------------------------------------------- Qt（Android 静态）
# qtbase Android 裸进程补丁：nox 以子进程运行（无 JVM），而 qtbase 的 Android
# 专属后端直接 JNI 调用 Java API，QJniEnvironment 挂接 NULL JavaVM → 空指针
# SIGSEGV（真机 code=139，已在 qemu-user + Android 系统镜像下双重复现定位）。
# 两处地雷（与 Termux qt6-qtbase 无 JVM 补丁同思路，均改为 Unix 实现）：
#
# 1. 时区：Q_OS_ANDROID 强制 QAndroidTimeZonePrivate（JNI 调
#    java/util/TimeZone.getDefault）；任何 systemTimeZone 查询必炸。
#    补丁：
#      - qtimezone.cpp          后端选择去掉 ANDROID 分支（落到 Q_OS_UNIX→QTz）
#      - qtimezoneprivate_p.h   QTzTimeZonePrivate 声明不再排除 Android
#      - corelib/CMakeLists.txt Android 也编译 qtimezoneprivate_tz.cpp
#    运行时 TZ=UTC（App 侧注入）→ POSIX 规则→有效 UTC 时区。
#
# 2. QStandardPaths：Android 实现全部经 QAndroidApplication::context() JNI；
#    QCoreApplication 构造期 Android 专属 QLoggingRegistry::initializeRules()
#    必调 QStandardPaths::locate(GenericConfigLocation, ...)（找 qtlogging.ini）
#    → 启动即炸。补丁：corelib/CMakeLists.txt 的 ANDROID 块改编译
#    qstandardpaths_unix.cpp（纯 XDG 环境变量/$HOME 实现，无 JNI）。
#
# 3. QJniObject/QJniEnvironment 空引用守卫（首炸点，三轮同源）：
#    QCoreApplicationPrivate::init() → appVersion()（Android 路径）构造临时
#    QJniObject 包裹 NULL context → 作用域结束析构时 ~QJniObjectPrivate()
#    无条件调 getJniEnv()，而 getJniEnv() 不检查 javaVM() 空指针 →
#    `vm->GetEnv()` 空指针解引用（实测 crash PC 即此处，JNI_VERSION_1_6）。
#    补丁：
#      - qjnienvironment.cpp getJniEnv()：javaVM() 为空直接返回 nullptr；
#        TLS 析构 DetachCurrentThread 同样加空守卫
#      - qjniobject.cpp ~QJniObjectPrivate()：无全局引用可释放时早退，
#        不再触碰 JNI
#
# 两个 android 原实现文件仍编译但无引用（静态库成员不进最终链接）。
patch_qt_bare_process() {
  local src="$1"
  python3 - "$src" <<'PYEOF'
import sys

root = sys.argv[1]

def patch(rel_path, replacements):
    path = f"{root}/{rel_path}"
    with open(path, encoding="utf-8") as f:
        text = f.read()
    for old, new in replacements:
        count = text.count(old)
        if count == 0:
            sys.exit(f"qtbase bare-process patch: pattern not found in {rel_path}: {old!r}")
        text = text.replace(old, new)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
    print(f"qtbase bare-process patch: {rel_path} OK ({len(replacements)} hunk(s))")

patch("src/corelib/time/qtimezone.cpp", [
    # 两处后端选择（默认时区 + 指定 IANA id）都去掉 Android/JNI 分支
    ("#elif defined(Q_OS_ANDROID)\n    return new QAndroidTimeZonePrivate();\n", ""),
    ("#elif defined(Q_OS_ANDROID)\n    return new QAndroidTimeZonePrivate(ianaId);\n", ""),
])

patch("src/corelib/time/qtimezoneprivate_p.h", [
    ("#if defined(Q_OS_UNIX) && !defined(Q_OS_DARWIN) && !defined(Q_OS_ANDROID)\n",
     "#if defined(Q_OS_UNIX) && !defined(Q_OS_DARWIN)\n"),
])

patch("src/corelib/CMakeLists.txt", [
    # 时区：Android 也编译 tzfile 后端
    ("qt_internal_extend_target(Core CONDITION QT_FEATURE_timezone AND UNIX AND NOT ANDROID AND NOT APPLE\n    SOURCES\n        time/qtimezoneprivate_tz.cpp\n",
     "qt_internal_extend_target(Core CONDITION QT_FEATURE_timezone AND UNIX AND NOT APPLE\n    SOURCES\n        time/qtimezoneprivate_tz.cpp\n"),
    # QStandardPaths：ANDROID 块改用 Unix 实现（XDG 环境变量，无 JNI）
    ("io/qstandardpaths_android.cpp\n", "io/qstandardpaths_unix.cpp\n"),
])

patch("src/corelib/kernel/qjnienvironment.cpp", [
    # getJniEnv()：无 JVM（裸 exec）时 javaVM() 为 NULL，直接解引用 vm->GetEnv 必炸；
    # 返回 nullptr，由调用方按需判空（无 JVM 时不存在有效的 jobject/jclass，
    # 合法调用路径不会走到 env 解引用）
    ("    JavaVM *vm = QtAndroidPrivate::javaVM();\n    const jint ret = vm->GetEnv((void**)&jniEnv, JNI_VERSION_1_6);\n",
     "    JavaVM *vm = QtAndroidPrivate::javaVM();\n    if (!vm)\n        return nullptr; // bare exec (no JVM): JNI unavailable\n    const jint ret = vm->GetEnv((void**)&jniEnv, JNI_VERSION_1_6);\n"),
    # TLS 析构的 DetachCurrentThread 同样可能拿到 NULL vm（未 attach 过时不会
    # 到这，但守卫无害）
    ("        QtAndroidPrivate::javaVM()->DetachCurrentThread();\n",
     "        if (JavaVM *vm = QtAndroidPrivate::javaVM())\n            vm->DetachCurrentThread();\n"),
])

patch("src/corelib/kernel/qjniobject.cpp", [
    # ~QJniObjectPrivate()：原实现无条件 getJniEnv()（在判空之前）——
    # 包裹 NULL 对象的临时 QJniObject（如 QCoreApplication::appVersion() 的
    # context）析构即炸；无全局引用可释放时早退，不触碰 JNI
    ("    ~QJniObjectPrivate() {\n        JNIEnv *env = QJniEnvironment::getJniEnv();\n        if (m_jobject)\n            env->DeleteGlobalRef(m_jobject);\n        if (m_jclass && m_own_jclass)\n            env->DeleteGlobalRef(m_jclass);\n    }\n",
     "    ~QJniObjectPrivate() {\n        if (!m_jobject && !(m_jclass && m_own_jclass))\n            return; // nothing to release; avoids JNI (bare exec has no JVM)\n        JNIEnv *env = QJniEnvironment::getJniEnv();\n        if (!env)\n            return;\n        if (m_jobject)\n            env->DeleteGlobalRef(m_jobject);\n        if (m_jclass && m_own_jclass)\n            env->DeleteGlobalRef(m_jclass);\n    }\n"),
])

patch("src/plugins/tls/openssl/qtlsbackend_openssl.cpp", [
    # systemCaCertificates()：Android 分支经 JNI 调 QtNative.getSSLCertificates
    # （qsslsocket_openssl_android.cpp fetchSslCertificateData），Session 初始化
    # 即触发（qemu 实测第 4 崩点）。裸进程无 JVM → 改为加载 App 侧从
    # AndroidCAStore（KeyStore Java API，含用户自装证书）导出的 PEM 信任束，
    # 路径由 SSL_CERT_FILE 环境变量传入；libtorrent 的 OpenSSL 默认验证路径
    # 读同一变量（HTTPS tracker 证书校验一并修复）。变量缺失时回退空列表
    # （与旧 musl 静态版行为一致，不劣化）。
    ("#elif defined(Q_OS_ANDROID)\n    const QList<QByteArray> certData = fetchSslCertificateData();\n    for (auto certDatum : certData)\n        systemCerts.append(QSslCertificate::fromData(certDatum, QSsl::Der));\n",
     "#elif defined(Q_OS_ANDROID)\n"
     "    // bare exec (no JVM): fetchSslCertificateData() requires JNI. Load the\n"
     "    // PEM trust bundle exported by the embedding app from AndroidCAStore\n"
     "    // instead (SSL_CERT_FILE env; OpenSSL's default verify paths -- e.g.\n"
     "    // libtorrent HTTPS tracker validation -- honor the same variable).\n"
     "    const QString caBundlePath = qEnvironmentVariable(\"SSL_CERT_FILE\");\n"
     "    if (!caBundlePath.isEmpty())\n"
     "        systemCerts.append(QSslCertificate::fromPath(caBundlePath, QSsl::Pem));\n"),
])

patch("src/network/kernel/qnetworkproxy_android.cpp", [
    # QNetworkProxyFactory::systemProxyForQuery()：Android 实现经 JNI 注册/
    # 查询系统代理（ProxyInfoObject ctor callStaticMethod registerReceiver）；
    # QNetworkAccessManager 首个请求（favicon/RSS/GeoIP 下载）必触发。裸进程
    # 无 JVM → 直接报告无代理（NoProxy = 直连，与本应用无代理策略一致）
    ("    QList<QNetworkProxy> proxyList;\n    if (!proxyInfoInstance)\n        return proxyList;\n",
     "    QList<QNetworkProxy> proxyList;\n    // bare exec (no JVM): Android system proxy requires JNI; report no proxy\n    return {QNetworkProxy::NoProxy};\n    if (!proxyInfoInstance)\n        return proxyList;\n"),
])
PYEOF
}

# ---------------------------------------------------------------- libtorrent 补丁
#
# Android 假「监听 IP 失败」告警抑制：
# libtorrent RC_1_2 在 TORRENT_ANDROID && __ANDROID_API__ >= 24 时，enum_routes()
# 为硬编码存根（enum_net.cpp 尾部：netlink 对 app 进程不可用，上游有意放弃），
# 恒返回 operation_not_supported；reopen_listen_sockets() 把这个预期内失败发成
# listen_failed_alert（device 为空、endpoint 默认构造），qBittorrent 侧即误报：
#   Failed to listen on IP. IP: "0.0.0.0". Port: "TCP/0".
#   Reason: "Operation not supported on transport endpoint"
# 实际监听经 unspecified-address 回退正常工作（同函数 Android 分支本就以
# routes 为空为前提保留 0.0.0.0 绑定，qb WebUI/DHT/传输均正常）。补丁：Android
# 下跳过枚举且不发告警，其他平台行为不变。
patch_libtorrent_android() {
  local src="$1"
  python3 - "$src" <<'PYEOF'
import sys

root = sys.argv[1]

def patch(rel_path, replacements):
    path = f"{root}/{rel_path}"
    with open(path, encoding="utf-8") as f:
        text = f.read()
    for old, new in replacements:
        if new in text:
            continue  # 已应用（幂等：脚本可能对同一源码树多次执行）
        count = text.count(old)
        if count != 1:
            sys.exit(f"libtorrent android patch: pattern count={count} in {rel_path}: {old[:72]!r}")
        text = text.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
    print(f"libtorrent android patch: {rel_path} OK")

patch("src/session_impl.cpp", [
    ("\t\t\tauto const routes = enum_routes(m_io_service, ec);\n"
     "\t\t\tif (ec && m_alerts.should_post<listen_failed_alert>())\n"
     "\t\t\t{\n"
     "\t\t\t\tm_alerts.emplace_alert<listen_failed_alert>(\"\"\n"
     "\t\t\t\t\t, operation_t::enum_route, ec, socket_type_t::tcp);\n"
     "\t\t\t}\n",
     "#if defined TORRENT_ANDROID && __ANDROID_API__ >= 24\n"
     "\t\t\t// Android API >= 24: enum_routes() is a hardcoded stub that always\n"
     "\t\t\t// returns operation_not_supported (netlink is unavailable to app\n"
     "\t\t\t// processes; see enum_net.cpp). Listening still works via the\n"
     "\t\t\t// unspecified-address fallback, so this failure is expected and\n"
     "\t\t\t// benign -- suppress the listen_failed_alert that qBittorrent\n"
     "\t\t\t// otherwise logs as a CRITICAL \"Failed to listen on IP\" error.\n"
     "\t\t\tauto const routes = std::vector<ip_route>();\n"
     "#else\n"
     "\t\t\tauto const routes = enum_routes(m_io_service, ec);\n"
     "\t\t\tif (ec && m_alerts.should_post<listen_failed_alert>())\n"
     "\t\t\t{\n"
     "\t\t\t\tm_alerts.emplace_alert<listen_failed_alert>(\"\"\n"
     "\t\t\t\t\t, operation_t::enum_route, ec, socket_type_t::tcp);\n"
     "\t\t\t}\n"
     "#endif\n"),
])
PYEOF
}

build_qt_android() {
  # stage 名含补丁代数：qtbase 裸进程补丁改动后必须强制重建（源码树在
  # $PREFIX_DIR.build 不进缓存，每轮全新解压，补丁总是对 pristine 源码应用；
  # 但已安装产物与标记随 prefix 缓存恢复，仅 bump stage 名可破除跳过）
  stage_done qt-android-v2 && return
  log "构建 qtbase $QT_VER（Android $ABI 静态：Core/Network/Sql/Xml）"
  local src="$SRC_DIR/qtbase-$QT_VER"
  rm -rf "$src"
  if [ ! -d "$src" ]; then
    local major="${QT_VER%.*}"
    fetch "https://download.qt.io/official_releases/qt/$major/$QT_VER/submodules/qtbase-everywhere-src-$QT_VER.tar.xz" \
      "$DL_DIR/qtbase-$QT_VER.tar.xz"
    mkdir -p "$src"
    tar -xJf "$DL_DIR/qtbase-$QT_VER.tar.xz" --strip-components=1 -C "$src"
  fi
  # Android 无 JVM 的裸进程必须避开 JNI 时区/QStandardPaths 后端（见 patch_qt_bare_process 注释）
  patch_qt_bare_process "$src"
  # 用 qtbase 自带 configure 包装脚本（与上游 cross_build.sh 同源）：
  # 特性旗标由脚本翻译成正确的 QT_FEATURE_*，避免手写变量出错；
  # -- 之后是透传给 CMake 的参数（NDK 工具链 + Android 三件套）。
  local bdir="$WORK_DIR/qt"
  rm -rf "$bdir"
  mkdir -p "$bdir"
  (
    cd "$bdir"
    "$src/configure" \
      -prefix "$QT_PREFIX" \
      -qt-host-path "$HOST_QT" \
      -release -static -c++std c++17 \
      -optimize-size \
      -feature-optimize_full \
      -openssl -openssl-linked \
      -no-gui -no-dbus -no-widgets \
      -no-feature-testlib \
      -no-feature-animation \
      -nomake examples -nomake tests \
      -- \
      -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN_FILE" \
      -DANDROID_ABI="$ABI" \
      -DANDROID_PLATFORM="$ANDROID_PLATFORM" \
      -DANDROID_STL=c++_shared \
      -DANDROID_SDK_ROOT="$ANDROID_SDK_ROOT" \
      -DCMAKE_FIND_ROOT_PATH="$PREFIX_DIR" \
      -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
      -DOPENSSL_ROOT_DIR="$PREFIX_DIR" \
      -DCMAKE_PREFIX_PATH="$PREFIX_DIR" \
      "${NDK_CCACHE_ARGS[@]}"
  )
  cmake --build "$bdir" --parallel "$JOBS"
  cmake --install "$bdir"
  rm -rf "$bdir"
  mark_done qt-android-v2
}

# ---------------------------------------------------------------- libtorrent
build_libtorrent() {
  log "构建 libtorrent-rasterbar（$LT_REF）"
  local src="$SRC_DIR/libtorrent"
  if [ ! -d "$src/.git" ]; then
    git init -q "$src"
    git -C "$src" remote add origin "$LT_REPO"
    git -C "$src" fetch -q --depth 1 origin "$LT_REF"
    git -C "$src" checkout -q FETCH_HEAD
    git -C "$src" log -1 --oneline >&2 || true
  fi
  # Android 假「监听 IP 失败」告警抑制（enum_routes 存根，见函数头注释）
  patch_libtorrent_android "$src"
  rm -rf "$WORK_DIR/libtorrent"
  # NDK 工具链把 FIND_ROOT_PATH_MODE_* 设为 ONLY（find 仅在 NDK 内搜索），
  # 必须把依赖 prefix 追加进 CMAKE_FIND_ROOT_PATH（工具链会 APPEND NDK），
  # 否则 CMAKE_PREFIX_PATH 被重根、FindBoost 等模块模式搜索全部落空。
  cmake -S "$src" -B "$WORK_DIR/libtorrent" "${cmake_common[@]}" \
    -DBUILD_SHARED_LIBS=OFF \
    -Dstatic_runtime=ON \
    -DCMAKE_CXX_STANDARD=17 \
    -DCMAKE_CXX_FLAGS="-Wno-c++11-narrowing-const-reference -Wno-c++11-narrowing" \
    -DCMAKE_PREFIX_PATH="$PREFIX_DIR" \
    -DCMAKE_FIND_ROOT_PATH="$PREFIX_DIR" \
    -DBoost_ROOT="$PREFIX_DIR" \
    -DBoost_NO_SYSTEM_PATHS=ON \
    -Dbuild_tests=OFF -Dbuild_examples=OFF -Dbuild_tools=OFF -Dpython-bindings=OFF
  cmake --build "$WORK_DIR/libtorrent" --parallel "$JOBS"
  cmake --install "$WORK_DIR/libtorrent"
  rm -rf "$WORK_DIR/libtorrent"
}

# ---------------------------------------------------------------- qbittorrent 补丁
#
# peer 黑白名单文件缺失噪声抑制：
# qbittorrent-enhanced 的 peer_filter_session_plugin 在启动时对 data 目录下的
# peer_blacklist.txt / peer_whitelist.txt 逐一检查，缺失即各记一条 NORMAL 日志：
#   'peer_blacklist.txt' doesn't exist. The corresponding filter is disabled.
#   'peer_whitelist.txt' doesn't exist. The corresponding filter is disabled.
# 该两文件为可选的进阶功能（regex 规则过滤 peer，须 adb 手工放置到应用私有
# data 目录），绝大多数用户永不创建，文件缺失即禁用本就是预期默认态——每次
# 启动刷两条日志属纯噪声。补丁：缺失时静默禁用（不发日志）；文件存在时的
# 提示（规则数 INFO / 无有效规则 WARNING）保持不变，不影响真正使用该功能的
# 用户。缓存 key 须随本补丁代数同步 bump（见 build.yml -bare-v8）。
patch_qbt_android() {
  local src="$1"
  python3 - "$src" <<'PYEOF'
import sys

root = sys.argv[1]

def patch(rel_path, replacements):
    path = f"{root}/{rel_path}"
    with open(path, encoding="utf-8") as f:
        text = f.read()
    for old, new in replacements:
        if new in text:
            continue  # 已应用（幂等：脚本可能对同一源码树多次执行）
        count = text.count(old)
        if count != 1:
            sys.exit(f"qbt android patch: pattern count={count} in {rel_path}: {old[:72]!r}")
        text = text.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
    print(f"qbt android patch: {rel_path} OK")

patch("src/base/bittorrent/peer_filter_session_plugin.hpp", [
    ("  if (!QFile::exists(filter_file)) {\n"
     "    LogMsg(u\"'%1' doesn't exist. The corresponding filter is disabled.\"_s.arg(filename), Log::NORMAL);\n"
     "\n"
     "    return nullptr;\n"
     "  }\n",
     "  if (!QFile::exists(filter_file)) {\n"
     "    // [OpenListAndroid] absence is the expected default state (optional\n"
     "    // power-user feature; requires manually placing the file in the app\n"
     "    // private data dir); stay silent instead of logging on every startup.\n"
     "    // Messages for existing files (rule count / no-valid-rules warning)\n"
     "    // are unchanged.\n"
     "    return nullptr;\n"
     "  }\n"),
])
PYEOF
}

# ---------------------------------------------------------------- qbittorrent-nox
build_qbittorrent() {
  log "构建 qbittorrent-enhanced-nox（$QBT_REF）"
  local src="$SRC_DIR/qbt"
  if [ ! -d "$src/.git" ]; then
    git init -q "$src"
    git -C "$src" remote add origin "$QBT_REPO"
    git -C "$src" fetch -q --depth 1 origin "$QBT_REF"
    git -C "$src" checkout -q FETCH_HEAD
    git -C "$src" log -1 --oneline >&2 || true
  fi
  # peer 黑白名单文件缺失噪声抑制（见 patch_qbt_android 头注释）
  patch_qbt_android "$src"
  rm -rf "$WORK_DIR/qbt"
  cmake -S "$src" -B "$WORK_DIR/qbt" \
    -G Ninja \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_CXX_STANDARD=17 \
    -DCMAKE_CXX_FLAGS="-Wno-c++11-narrowing-const-reference -Wno-c++11-narrowing" \
    -DGUI=OFF -DSTACKTRACE=OFF -DTESTING=OFF \
    -DBUILD_SHARED_LIBS=OFF \
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN_FILE" \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM="$ANDROID_PLATFORM" \
    -DANDROID_STL=c++_shared \
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
    -DCMAKE_INSTALL_PREFIX="$PREFIX_DIR" \
    -DQT_HOST_PATH="$HOST_QT" \
    -DCMAKE_PREFIX_PATH="$PREFIX_DIR;$QT_PREFIX" \
    -DCMAKE_FIND_ROOT_PATH="$PREFIX_DIR;$QT_PREFIX" \
    -DBoost_ROOT="$PREFIX_DIR" \
    -DBoost_NO_SYSTEM_PATHS=ON \
    -DZLIB_ROOT="$PREFIX_DIR" \
    -DOPENSSL_ROOT_DIR="$PREFIX_DIR" \
    -DCMAKE_EXE_LINKER_FLAGS="-Wl,-z,max-page-size=16384" \
    "${NDK_CCACHE_ARGS[@]}"
  cmake --build "$WORK_DIR/qbt" --parallel "$JOBS"
  cmake --install "$WORK_DIR/qbt"
  test -f "$PREFIX_DIR/bin/qbittorrent-nox"
  rm -rf "$WORK_DIR/qbt"
}

# ---------------------------------------------------------------- 产物校验与落盘
install_output() {
  local bin="$PREFIX_DIR/bin/qbittorrent-nox"
  local out="$OUT_DIR/$ABI/libqbittorrent-nox.so"
  mkdir -p "$OUT_DIR/$ABI"

  # 捆绑 libc++_shared.so（Qt 强制 c++_shared；App 侧以 LD_LIBRARY_PATH 指向同目录）
  # 注意 sysroot 库目录名：armeabi-v7a 是 arm-linux-androideabi（无 v7a 前缀）
  local triple
  case "$ABI" in
    arm64-v8a) triple=aarch64-linux-android ;;
    armeabi-v7a) triple=arm-linux-androideabi ;;
    x86_64) triple=x86_64-linux-android ;;
  esac
  local stl="$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/$triple/libc++_shared.so"
  if [ ! -f "$stl" ]; then
    stl="$(find "$NDK/toolchains/llvm/prebuilt" -type f -name libc++_shared.so | grep "/$triple/" | head -1)"
  fi
  test -n "$stl" && test -f "$stl" || { echo "ERROR: libc++_shared.so not found in NDK ($triple)" >&2; exit 1; }
  install -m 644 "$stl" "$OUT_DIR/$ABI/libc++_shared.so"

  # strip 缩体积（无调试需求的发行形态）
  "$NDK_HOST_PREBUILT/bin/llvm-strip" "$bin"

  echo "---- file ----"
  file "$bin" >&2 || true
  echo "---- ELF header ----"
  "$NDK_HOST_PREBUILT/bin/llvm-readelf" -h "$bin" | grep -E 'Class:|Machine:|Type:' >&2 || true
  echo "---- NEEDED ----"
  "$NDK_HOST_PREBUILT/bin/llvm-readelf" -d "$bin" | grep NEEDED >&2 || echo '(no NEEDED)' >&2

  # 契约校验 1：e_type=DYN（jniLibs 打包要求）+ 含 PT_INTERP 段（证明是可执行文件；
  # PIE 与共享库的 e_type 同为 DYN，readelf 无法区分，须以 INTERP 段判别）
  local etype
  etype="$("$NDK_HOST_PREBUILT/bin/llvm-readelf" -h "$bin" | awk '/Type:/{print $2}')"
  if [ "$etype" != "DYN" ]; then
    echo "ERROR: 期望 e_type=DYN，实际: $etype" >&2
    exit 1
  fi
  if ! "$NDK_HOST_PREBUILT/bin/llvm-readelf" -l "$bin" | grep -q INTERP; then
    echo "ERROR: 无 PT_INTERP 段（是共享库而非可执行文件，无法 exec）" >&2
    exit 1
  fi
  "$NDK_HOST_PREBUILT/bin/llvm-readelf" -l "$bin" | grep -A1 INTERP >&2 || true

  # 契约校验 2：动态依赖只能是 bionic 系统库 + libc++_shared.so（出现 Qt/ssl 等即失败）
  local bad=""
  bad="$("$NDK_HOST_PREBUILT/bin/llvm-readelf" -d "$bin" \
    | sed -n 's/.*Shared library: \[\(.*\)\]/\1/p' \
    | grep -Ev "$ALLOWED_NEEDED" || true)"
  if [ -n "$bad" ]; then
    echo "ERROR: 存在非 bionic 系统库依赖：$bad" >&2
    exit 1
  fi

  # 信息项：getaddrinfo 应以动态符号导入（bionic → netd 的 DNS 通路；带版本后缀如 @LIBC）
  if "$NDK_HOST_PREBUILT/bin/llvm-readelf" --dyn-syms "$bin" 2>/dev/null | grep -q "getaddrinfo"; then
    echo "getaddrinfo: 动态导入 ✓（DNS 走 bionic/netd）" >&2
  else
    echo "WARN: 未发现 getaddrinfo 动态导入，请人工核查 QtNetwork 的 DNS 路径" >&2
  fi

  install -m 755 "$bin" "$out"
  sha256sum "$out" >&2
  ls -l "$out" >&2
}

build_zlib_ng
build_openssl
install_boost_headers
install_qt_host
build_qt_android
build_libtorrent
build_qbittorrent
install_output

log "完成：$OUT_DIR/$ABI/libqbittorrent-nox.so"
