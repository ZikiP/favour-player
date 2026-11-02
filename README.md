# Favour Player

Android 资料库 + 直连播放器。收藏数据由 Favour 扩展先推送到一台常驻的中间服务，手机/平板在需要时拉取最新快照。

## 数据流

```
Favour 扩展 --PUT /api/items--> 中间服务(常驻) --GET /api/items--> FavourPlayer
                                                              --> 本地快照 --> 资料库
                                                              --> 原生播放(设备直连 CDN)
```

- 中间服务运行在常开的电脑或迷你主机上，负责保存最新一份快照。
- 移动设备只需要在拉取时开启 Tailscale；平时可以完全离线浏览本地缓存。
- 也保留原有的“扩展直接推送到 App”模式，两种模式二选一。

## 功能

- **同步**：中间服务拉取（推荐）或直接接收；启动、回到前台、网络恢复时自动拉取，也可手动同步。
- 资料库：收藏 / 演员 / 最爱、搜索、卡片（封面、编号、演员、日期、最爱、播放）、详情弹窗。
- 播放：卡片或详情直接起播；手动播放、浏览器分享文本仍可用。
- 横竖屏适配；解析规则可在 App 内编辑。
- 同步完成后资料库自动刷新；离线展示上次快照。
- **离线缓存**：收藏文字、封面、视频片段可分别控制；封面与视频的大小和位置可调，显示占用并可清理。

## 下载 / CI

推送到 `main` 后，GitHub Actions 自动构建 debug APK。

- 最新滚动调试包：<https://github.com/ZikiP/favour-player/releases/tag/debug-latest>
- 直接下载：<https://github.com/ZikiP/favour-player/releases/download/debug-latest/app-debug.apk>
- `v*` 标签会生成正式命名的 Release。

签名说明：

- 仓库包含公开的 **debug keystore**：`keystore/favour-debug.jks`（口令 `favourdebug`），本地与 CI 共用，保证更新签名一致。
- 只用于调试，不可用于商店或正式发布。
- 旧的自动 debug 签名安装包需卸载一次：`adb uninstall com.ziki.favourplayer`。

## 同步

### 中间服务（推荐）

中间服务使用 `PUT/GET /api/items`（Bearer 令牌），保存扩展推送的最新快照；建议通过 Tailscale Serve 暴露为 `https://<hub-host>/favour`。

部署要点：

- 服务监听 `127.0.0.1:8789`，数据保存在 `favour-sync/data/items.json`，令牌在 `favour-sync/data/token.txt`。
- Tailscale Serve：`tailscale serve --bg --set-path /favour http://127.0.0.1:8789`。
- 让服务随主机启动（计划任务或系统服务），保证扩展推送时它在线。

App 端：

1. 设置 → **同步** → **中间服务**：服务地址填 `https://<hub-host>/favour`，令牌填中间服务令牌。
2. 保存或立即拉取。
3. 之后每次打开 App、回到前台或网络恢复（包括 Tailscale 刚连上）都会自动拉取；10 秒内只触发一次。
4. 拉取失败时继续使用本地缓存，顶部提示失败状态；下次触发会重试。
5. 手机与中间服务需在同一 Tailscale 网络；中间服务需常开。

扩展端：

1. 扩展 → **同步设置** → **添加目标**。
2. 地址填中间服务的 `https://<hub-host>/favour`，令牌与 App 相同，保存后自动推送。
3. 地址填到 `/favour`，不要带 `/api/items`。
4. 可以添加多个目标（例如再加一台直连设备）；并行推送，单个失败不影响其他目标。

### 直接接收（可选）

- App 设置 → 同步 → **直接接收**：开启接收服务（默认 `8790`），复制 App 显示的地址和令牌。
- 扩展 → 同步设置 → 添加目标：填 App 的地址 `http://<phone-IP>:8790` 和令牌。
- 需要 App 前台服务常驻通知；手机和电脑需在同一 Tailscale 或局域网。
- 适合设备常在线、想实时收到推送的场景；与中间服务模式二选一，切换时会自动停止/启动前台服务。

注意：

- 令牌明文保存在 App 私有 SharedPreferences / 扩展 `chrome.storage`；泄露后请更换中间服务令牌并同步更新两端。
- 数据是单向的：扩展 → 中间服务 → App；App 不回写收藏。
- 中间服务模式不需要 App 前台服务，因此没有常驻通知。

## 缓存

设置 → 同步 → **缓存设置**：

- **收藏信息（文字）**：标题、编号、演员、标签、最爱等随快照保存在 App 私有目录（`filesDir/snapshot.json`），离线可浏览、搜索和查看详情；体积小，位置固定。
- **封面**：开启后，每次接收或拉取同步会后台预缓存全部封面；上限 128MB / 256MB / 512MB / 1GB / 2GB（LRU），位置可选内部/外部。离线时从磁盘缓存加载。
- **视频片段**：可开关，上限 512MB / 1GB / 2GB / 4GB / 8GB（LRU）；缓存 HLS 分片与播放列表，减少重复下载、改善拖动/重播。
- 封面与视频共用**存储位置**：内部存储 / 外部存储（App 专属缓存目录，卸载时随 App 清理；不支持任意 SAF 目录）。更换位置会清空旧位置的缓存。
- 每个类别显示已用/上限/数量和实际路径，可单独清理；封面也可手动点「缓存全部封面」补全。
- 默认：封面开启 512MB、视频开启 2GB、内部存储。
- 视频缓存**不保证完整离线**：解析页面仍需网络，且分片不保证全部命中。

## 解析规则

默认规则在 `app/src/main/assets/resolver_rules.json`，适用于“页面 HTML 中出现 `host + UUID + seek/playlist`”的通用模式，不绑定任何站点。

- App 设置 → 同步 → 解析规则，可粘贴 `{"rules":[...]}` 追加规则；自定义规则优先。
- 主要字段：`hosts`（空数组 = 任意站点）、`sourcePatterns`、`hostGroup` / `uuidGroup`、`masterUrlTemplate`、`sprite`、`refererMode`、`titleMode`。
- 模板占位符：`{host}`、`{uuid}`；雪碧图 URL 额外支持 `{sheet}`。

通用示例（`example.com`，仅示意）：

```json
{
  "rules": [
    {
      "id": "example-site",
      "name": "Example site",
      "hosts": ["example.com"],
      "sourcePatterns": [
        "https?://([^/\"']+)/([0-9a-fA-F-]{32,36})/(?:seek|playlist)"
      ],
      "hostGroup": 1,
      "uuidGroup": 2,
      "masterUrlTemplate": "https://{host}/{uuid}/playlist.m3u8",
      "sprite": {
        "enabled": true,
        "anchor": "pic_num",
        "window": 700,
        "frameCountKey": "pic_num",
        "widthKey": "width",
        "heightKey": "height",
        "colsKey": "col",
        "rowsKey": "row",
        "urlTemplate": "https://{host}/{uuid}/seek/_{sheet}.jpg"
      },
      "refererMode": "page-origin",
      "titleMode": "page-title"
    }
  ]
}
```

## 版本规则

- `v0.0.1` 是旧的“App 拉取”版本；`v0.0.2` 起为直推版本；`v0.0.3` 起带视频缓存；`v0.0.4` 起支持收藏文字/封面离线缓存与可调大小、位置；`v0.0.5` 起支持中间服务拉取。
- `main` 每次推送：`debug-latest`，versionName = `<version.properties 的 versionName>-dev.<run_number>`，versionCode = GitHub run number。
- tag 构建：versionName = tag 去掉 `v`，生成同名 Release。
- 发版：改 `version.properties` → `git tag v0.0.5` → `git push origin v0.0.5`。
- 本地构建默认读 `version.properties`；CI 通过 `-PversionName` / `-PversionCode` 覆盖。

## 构建

需要 JDK 17–21、Android SDK 35。

```bash
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。

## 结构

```
app/src/main/java/com/ziki/favourplayer/
  MainActivity.kt      资料库入口 + 分享文本 + 前台恢复拉取
  FavourApp.kt         Application（接入封面缓存加载器）
  Brand.kt             品牌标记
  SyncBus.kt           快照/服务状态事件
  SyncStore.kt         同步模式、接收/拉取配置、令牌、本地快照
  SyncServer.kt        App 上的 HTTP 接收服务（直接接收模式）
  SyncService.kt       前台服务(保活 + 常驻通知，直接接收模式)
  SyncModels.kt        LibraryItem / Snapshot / JSON 解析
  PullClient.kt        从中间服务拉取快照
  LibraryViewModel.kt  同步状态、快照、拉取、设备地址
  LibraryScreen.kt     顶部栏、搜索、Tabs、网格、空态
  LibraryCards.kt      卡片、演员卡、详情弹窗
  LibrarySettings.kt   手动播放弹窗
  SyncSettings.kt      同步设置弹窗（中间服务/直接接收）
  CacheStore.kt        缓存配置(封面/视频开关·上限·位置)
  CoverCache.kt        Coil 磁盘缓存 + 同步后预缓存封面
  MediaCache.kt        Media3 SimpleCache + LRU
  CacheSettings.kt     收藏文字/封面/视频缓存设置弹窗
  RulesSettings.kt     解析规则编辑弹窗
  ResolveRules.kt      规则模型与 JSON 解析
  RuleStore.kt         默认规则(asset) + 用户规则(prefs)
  PlayerActivity.kt    解析状态机 + 深链
  Resolver.kt          按规则解析页面/直链、UUID、雪碧图
  PlayerScreen.kt      ExoPlayer + 页面组装
  PlayerControls.kt    播放器控件
  PlayerSeekBar.kt     自绘进度条
  PlayerGestures.kt    手势(底部控件区隔离)
  Hud.kt               手势/倍速提示
  SpritePreview.kt     雪碧图加载、缓存、预览
  Quality.kt           清晰度
  Formatting.kt        时间/倍速格式化
  Theme.kt             色板
app/src/main/assets/resolver_rules.json  默认解析规则示例
```

扩展侧改动在 `favour-plugin` 的 `manager/`（同步设置、目标列表）和 `shared/sync-targets.js`。

## 验证状态

- `assembleDebug` 通过（中间服务拉取 / 直接接收 / 资料库 / 规则解析 / 收藏文字·封面·视频缓存）。
- 中间服务：健康检查、Tailscale `/favour` 路由、带令牌 GET、未授权 401 已验证。
- 播放器：此前真机已验证解析、清晰度、进度、手势、雪碧图、进度条单次 seek/无回跳。
- 资料库/同步/缓存：真机验证待设备重连（启动拉取、离线缓存、切换模式、横竖屏）。

## 已知限制

- 中间服务模式需要中间服务可达；手机在拉取时需要开启 Tailscale（中间服务需常开）。
- 中间服务只保留最新一份快照，没有历史版本或冲突合并；数据单向（扩展 → 中间服务 → App）。
- 中间服务使用一个共享令牌；令牌泄露后需更换并同步更新扩展和 App。
- 直接接收模式需要 App 前台服务常驻；中间服务模式不需要。
- 封面只在开启封面缓存且同步过一次后才会离线可用；关闭后未浏览过的封面在离线时显示占位。
- 缓存位置只支持 App 内部/外置缓存目录，不支持任意 SAF 文件夹；收藏文字固定保存在应用私有目录。
- 视频缓存不保证完整离线（解析页面仍需网络）。
- 数据源必须用 `OkHttpDataSource`；`DefaultHttpDataSource` 在真机会被视频 CDN 返回 403。
- 本项目仅用于个人调试；公开仓库不代表对内容来源的版权或 ToS 授权。
