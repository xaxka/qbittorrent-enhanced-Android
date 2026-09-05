# qbittorrentAndroid

[English](README_EN.md)

qBittorrent Android 原生客户端（单一发行版，内置引擎）。

应用**内置本地 `qbittorrent-enhanced-nox` 引擎**：由 CI 从源码交叉编译，作为子进程
随应用启动，安装即用、无需服务器。同时它也是一个完整的远程控制客户端：可通过
原版 qBittorrent Web API v2 连接并控制任意远程 qBittorrent 服务器
（设置 → 服务器连接，多配置切换，支持自签名证书）。

**UI 使用 [LibreTorrent](https://github.com/proninyaroslav/libretorrent)**
（原生 Material 3 / Material You 动态配色，无 WebView、不嵌入 WebUI），
**功能参考 [qBitController](https://github.com/Bartuzen/qBitController)** 与 LibreTorrent 实现。

## 功能

* **内置引擎，开箱即用**：随应用启动、开机自启、看门狗保活（引擎意外退出自动重启）；
  也可切换连接任意 qBittorrent 4.x/5.x 服务器（多配置档案、自签名证书）
* **种子管理**：LibreTorrent 风格列表；13 种状态筛选、11 种排序、分类与标签双维度；
  批量暂停/恢复/校验/删除；qBC 级添加参数（重命名、保存路径、内容布局、停止条件、
  限速/分享限制等）
* **种子详情**：总览（重命名/改保存位置）、内容文件优先级、Tracker 增删改、节点、
  Pieces 热图；单种子限速与分享限制
* **RSS 与搜索**：RSS 订阅树、文章阅读、一键下载、自动下载规则；引擎搜索插件检索、
  插件管理（RSS 板块可在设置中关闭）
* **qBittorrent 设置编辑器**：在线读取并写回引擎全部配置（与 WebUI Options 同一 API，
  修改实时生效）；编辑界面**动态生成**，qBittorrent 升级新增配置项时无需更新应用即可编辑
* **DHT 引导 DoH 解析**：设置中可开启 DNS over HTTPS（默认关闭；阿里/DNSPod/Cloudflare/
  Google/Quad9/自定义），加密解析 DHT 引导节点、绕过运营商 DNS 污染，每次引擎启动自动刷新
  引导列表（用户手动改过的引导表不会被覆盖）
* **工具集**：全局/备用速度限制、统计弹窗、日志查看器、应用内更新检查（手动 + 每日静默）
* **局域网 WebUI**：同网段设备可直接浏览器访问内置引擎
* **13 种语言**、Material You 动态配色、沉浸式边到边布局、官方自适应图标

## 下载

APK 由 GitHub Actions 自动编译、签名并发布到
[Releases](https://github.com/xixka/qbittorrentAndroid/releases)。发行版说明只保留
构建信息（分支 / 提交 / 构建时间 / 版本），其余说明都在这里：

* 按 **ABI 拆分**：`arm64-v8a`、`armeabi-v7a`、`x86_64`，每个 APK 都内置引擎
  （`arm64-v8a`：绝大多数手机/平板；`armeabi-v7a`：32 位 ARM 设备；
  `x86_64`：x86_64 设备/模拟器）
* 所有发布的 APK 均**已签名**（仓库内置公开 CI 密钥 `app/ci-signing.keystore`，
  仅用于侧载更新签名一致性，不是机密），可直接覆盖升级
* 应用内"检查更新"会读取 CI 发布的版本信息并跳转到对应架构的 APK

源码编译由 CI 完成，无需本地搭建环境；工作流见
`.github/workflows/android.yml`。

## 许可

AGPL-3.0（见 `LICENSE`）。第三方归属见 `NOTICE.md`。
