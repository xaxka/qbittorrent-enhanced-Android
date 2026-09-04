# qbittorrentAndroid

[English](README_EN.md)

qBittorrent Android 原生客户端（单一发行版，内置引擎）。

应用**内置本地 `qbittorrent-enhanced-nox` 引擎**：由 CI 从源码交叉编译，作为子进程
随应用启动，安装即用、无需服务器。同时它也是一个完整的远程控制客户端：可通过
原版 qBittorrent Web API v2 连接并控制任意远程 qBittorrent 服务器
（设置 → 服务器连接，多配置切换，支持自签名证书）。

界面为**原生 Material 3（Material You 动态配色默认开启）**，交互风格参照
[LibreTorrent](https://github.com/proninyaroslav/libretorrent)——无 WebView、不嵌入
WebUI。所有功能实现参照 [qBitController](https://github.com/Bartuzen/qBitController)
与 LibreTorrent。

## 功能

* 登录任意 qBittorrent 4.x/5.x WebUI（SID Cookie 流程，自动重登录）
* 种子列表：LibreTorrent 风格卡片（进度、状态、速度、种子/节点、分享率）
* 筛选与排序：13 种状态筛选（全部/下载中/做种/已完成/已暂停/活动/停滞/校验/移动…）、
  11 种排序（名称/大小/进度/剩余时间/分享率/下载速度/上传速度/已上传/添加时间/完成时间/节点数）、
  按添加日期过滤、**分类与标签双过滤维度**
* 添加种子：链接 / 磁力 / 本地 `.torrent` 文件（支持系统分享），qBitController 级
  参数设置（重命名、分类、保存路径、内容布局、停止条件、限速/分享限制、添加后暂停等）
* 批量操作：暂停/恢复/强制开始/重新校验/强制汇报/删除（可同时删除文件）、设置分类与标签
* **种子详情页**：总览（重命名、修改保存位置）、内容文件（优先级）、Tracker
  （增/删/改）、节点、**块（Pieces）状态热图**；单种子限速与分享限制、超级做种
* **统计弹窗**：点按抽屉“监听端口”/“DHT 节点”行即可查看用户/缓存/性能统计
  （qBitController 同款数据）
* **RSS 订阅**：订阅树管理（增/删/改/移动）、文章阅读、标记已读、一键下载，
  **自动下载规则**（包含/排除关键词、正则、智能剧集过滤、分类、保存路径、生效源）
* **搜索引擎**：调用服务器搜索插件，按分类检索，结果一键下载；插件管理
  （安装/卸载/更新/启停）
* **日志查看器**：引擎运行日志（log/main），按级别过滤
* **多服务器管理**：qBitController 式服务器配置档案，一键切换、编辑、删除；
  可随时在内置引擎与远程服务器之间切换
* **完整 qBittorrent 设置编辑器**（设置 → *qBittorrent 设置*）：
  读取在线引擎的全部配置并写回——**覆盖 qBittorrent Enhanced WebUI Options
  全部 217 个配置项**（下载/速度/BitTorrent/连接/WebUI/RSS/高级七个分页），
  修改实时生效无需重启；CI 设有专门的 `prefs-coverage` 校验步骤保证配置覆盖
  不回退
* 全局速度限制与备用速度限制（点按抽屉内传输统计的下载/上传速度行即可调整）
* 引擎全自动管理：随应用启动、**开机自启**、**看门狗保活**
  （引擎意外退出时自动重启）、启动失败时才提示重试；局域网 WebUI 访问
* 沉浸式全面屏布局（边到边、手势导航栏内边距自适应）
* **应用内更新检查**：对照 CI 发布的 GitHub Releases（手动检查 + 每日静默检查）
* **13 种语言**：简体中文、繁體中文、English、Русский、Deutsch、Français、Español、
  Português (Brasil)、日本語、한국어、Türkçe、Italiano、Tiếng Việt（支持 Android 13+
  按应用设置语言）
* 官方 qBittorrent 启动图标（自适应 + 单色主题图标）

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
