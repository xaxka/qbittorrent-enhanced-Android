# qbittorrent-enhanced-Android

[English](README_EN.md)

Android 原生 qBittorrent 客户端：内置由 CI 交叉编译的 `qbittorrent-enhanced-nox`
引擎，安装即用；也可作为远程控制端，通过 Web API v2 连接任意 qBittorrent 4.x/5.x
服务器（多配置档案、自签名证书）。

UI 基于 [LibreTorrent](https://github.com/proninyaroslav/libretorrent)
（原生 Material 3 / Material You，无 WebView），功能对齐
[qBitController](https://github.com/Bartuzen/qBitController)。

## 功能

- **内置引擎**：随应用启动、开机自启、看门狗保活；局域网 WebUI
- **种子管理**：状态筛选、排序、分类/标签、多选批量操作（队列优先级、移动保存位置等）
- **种子详情**：状态仪表盘、文件勾选与优先级、Tracker 增删改、节点、分块图、
  单种子限速与分享限制
- **RSS 与搜索**：订阅树、自动下载规则、引擎搜索与插件管理
- **设置编辑器**：动态生成，覆盖引擎全部配置项，修改实时生效
- **其他**：全局/备用限速、统计、日志、应用内更新；简体中文与英文界面

## 下载

[Releases](https://github.com/xaxka/qbittorrent-enhanced-Android/releases) 提供按
ABI 拆分的已签名 APK（arm64-v8a / armeabi-v7a / x86_64，均内置引擎），可直接覆盖
安装；应用内也提供检查更新。

CI 从源码编译，无需本地环境（工作流见 `.github/workflows/android.yml`）。

## 许可

AGPL-3.0（见 `LICENSE`），第三方归属见 `NOTICE.md`。
