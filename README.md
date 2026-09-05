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
* **工具集**：全局/备用速度限制、统计弹窗、日志查看器、应用内更新检查（手动 + 每日静默）
* **局域网 WebUI**：同网段设备可直接浏览器访问内置引擎
* **13 种语言**、Material You 动态配色、沉浸式边到边布局、官方自适应图标

## 常见问题：DHT 节点为 0（中国移动等网络）

抽屉顶部"端口 / 内存 / DHT 节点"一行里 DHT 节点长期为 0，几乎都发生在
中国移动（以及部分电信/联通）宽带与蜂窝网络下，根因与修复如下：

**根因（运营商网络侧）**

* **DNS 污染**：qBittorrent 内置的 3 个 DHT 引导节点中，
  `dht.libtorrent.org` 与 `router.bittorrent.com` 的明文 DNS 应答在国内被
  劫持到 facebook / twitter / ntt 的地址（GFW 行为，transmission#8664、
  XTLS/BBS#18 等均有记录），仅 `dht.transmissionbt.com` 能正确解析。
  引导节点全军覆没后 DHT 永远无法完成首次"入网"，节点数停在 0。
* **CGNAT 与 UDP 跨境 QoS**：移动网络无公网 IPv4、跨境 UDP 包被限速丢弃，
  会拖慢入网速度，但不是 0 的直接原因（DHT 出方向请求仍可通）。

**本应用的修复（无需手动操作）**

内置引擎是 qBittorrent-Enhanced（上游没有该能力），其
`BitTorrent\Session\DHTBootstrapNodes` 配置可替换 libtorrent 的引导节点表。
应用在每次引擎启动时以"**缺失才写入**"的方式注入一份对国内网络友好的引导表：

* 保留唯一能正确解析的域名 `dht.transmissionbt.com:6881`；
* 追加全部引导节点的 **IP 直写**（`212.129.33.59:6881`、`87.98.162.88:6881`
  = transmission，`185.157.221.247:25401` = libtorrent，
  `67.215.246.10:6881` = router.bittorrent.com）——IP 直写完全绕开被污染的
  DNS，首次引导必达其一；
* 只要任一节点应答，libtorrent 会立刻从全网学到真实节点并随会话状态持久化，
  此后即使引导 IP 失效也不影响。

用户在"qBittorrent 设置编辑器 → 连接 → 自定义引导节点"（或 WebUI）里改过的
值**不会被覆盖**——该键只在配置中不存在时才写入一次，老用户升级后第一次重启
引擎即自动生效。若想手工调整，可用"设置编辑器"中的 `dht_bootstrap_nodes`
（每行一个 `host:port`）。

**仍不理想时的建议**

* 尽量让引擎**长驻**（默认已带看门狗保活）：节点表越攒越多，重启也不怕；
* Wi-Fi 下在路由器上给 6881/UDP 做端口映射（或确认 qB 的 UPnP/NAT-PMP 开启，
  设置编辑器 `upnp`），可入方向连接能显著增加节点与 Peer；
* 蜂窝网络下开启 IPv6（移动已大规模部署）：IPv6 路径通常不受 IPv4 侧
  QoS 影响；
* 若使用代理，确认代理为 UDP 全代理（SOCKS5 UDP 转发），否则 DHT 流量不走
  代理时同样可能被丢。

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
