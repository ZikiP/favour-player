# Favour Player

Android 资料库 + 直连播放器。收藏由 Favour 扩展推送到常驻的中间服务，App 只从中间服务拉取最新快照。

## 数据流

```
Favour 扩展 --PUT /api/items--> 中间服务(常驻) --GET /api/items--> FavourPlayer
                                                              --> 本地快照 --> 资料库
                                                              --> 原生播放(设备直连 CDN)
```

- App 不再提供接收服务或前台服务，只保留拉取能力。
- 手机只需要在拉取时开启 Tailscale，平时可以完全离线浏览本地缓存。
- 中间服务统一保存最新一份快照；扩展和 App 都不需要知道对方的地址。

## 功能

- **同步**：只从中间服务拉取；启动、回到前台、网络恢复（包括 Tailscale 刚连上）时自动拉取，也可手动刷新。
- **测试连接**：App 和扩展都可以只读测试地址/令牌是否可用，不会修改数据。
- **界面**：暗色影院风格、琥珀主色、底部导航；海报覆盖卡片；继续观看；全屏详情；沉浸式播放器；分组设置页。
- 资料库：收藏 / 合集 / 演员 / 最爱；搜索、按最近添加/最近打开/最早/标题排序；按演员、系列、标签、未看筛选。
- 卡片：纯海报展示封面、编号、最爱与播放；标题、演员、日期、类型和已看都在详情页/筛选里查看。
- 详情页展示系列、标签、演员、时长、来源、上次打开。
- 合集：按作品的「系列」字段自动汇总，显示封面与作品数，点开即可筛选该系列。
- 标签：扩展里的「类型」和自定义标签统一展示在卡片/详情，并可一键筛选。
- 演员：显示扩展同步的演员头像、喜爱标记与排序值（只读展示）。
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

### 中间服务部署

中间服务使用 `PUT/GET /api/items`（Bearer 令牌），保存扩展推送的最新快照；建议通过 Tailscale Serve 暴露为 `https://<hub-host>/favour`。

部署要点：

- 服务监听 `127.0.0.1:8789`，数据保存在 `favour-sync/data/items.json`，令牌在 `favour-sync/data/token.txt`。
- Tailscale Serve：`tailscale serve --bg --set-path /favour http://127.0.0.1:8789`。
- 让服务随主机启动（计划任务或系统服务），保证扩展推送时它在线。

### App 端

1. 设置 → **中间服务** → **编辑**：服务地址填 `https://<hub-host>/favour`，令牌填中间服务令牌。
2. 点「测试连接」可先验证地址和令牌（只读 GET，不修改数据）；再保存或立即拉取。
3. 之后每次打开 App、回到前台或网络恢复（包括 Tailscale 刚连上）都会自动拉取；10 秒内只触发一次。
4. 拉取失败时继续使用本地缓存，顶部提示失败状态；下次触发会重试。
5. 手机与中间服务需在同一 Tailscale 网络；中间服务需常开。
6. **手机不能填 `127.0.0.1`**：那只会指向手机自己。手机必须用中间服务的 Tailscale HTTPS 地址。
7. App 没有前台服务、常驻通知或接收端口；只保留拉取。

### 扩展端

1. 扩展 → **同步设置** → **填入本机服务**（浏览器和中间服务在同一台机器时），地址会自动填 `http://127.0.0.1:8789`；或手动添加目标。
2. 令牌填中间服务令牌，点「测试连接」验证连通和令牌。
3. 保存后自动推送；本机推送不经过 Tailscale，也不受代理影响。
4. 浏览器不在中间服务机器上时，地址改填 `https://<hub-host>/favour`（填到 `/favour`，不要带 `/api/items`）。
5. App 不再接收直推，扩展只需要推送到中间服务。

注意：

- 数据是单向的：扩展 → 中间服务 → App；App 不回写收藏。
- 中间服务只保留最新一份快照，没有历史版本或冲突合并。
- 令牌明文保存在 App 私有 SharedPreferences / 扩展 `chrome.storage`；泄露后请更换中间服务令牌并同步更新两端。

## 缓存

设置 → **封面缓存 / 视频缓存**：

- **收藏信息（文字）**：标题、编号、演员、标签、最爱等随快照保存在 App 私有目录（`filesDir/snapshot.json`），离线可浏览、搜索和查看详情；体积小，位置固定。
- **封面**：开启后，每次拉取会后台预缓存全部封面；上限 128MB / 256MB / 512MB / 1GB / 2GB（LRU），位置可选内部/外部。离线时从磁盘缓存加载。
- **视频片段**：可开关，上限 512MB / 1GB / 2GB / 4GB / 8GB（LRU）；缓存 HLS 分片与播放列表，减少重复下载、改善拖动/重播。
- 封面与视频共用**存储位置**：内部存储 / 外部存储（App 专属缓存目录，卸载时随 App 清理；不支持任意 SAF 目录）。更换位置会清空旧位置的缓存。
- 每个类别显示已用/上限/数量和实际路径，可单独清理；封面也可手动点「缓存全部封面」补全。
- 默认：封面开启 512MB、视频开启 2GB、内部存储。
- 视频缓存**不保证完整离线**：解析页面仍需网络，且分片不保证全部命中。

## 解析规则

默认规则在 `app/src/main/assets/resolver_rules.json`，适用于“页面 HTML 中出现 `host + UUID + seek/playlist`”的通用模式，不绑定任何站点。

- App 设置 → **解析规则**，可粘贴 `{"rules":[...]}` 追加规则；自定义规则优先。
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

- `v0.0.1` 是旧的“App 拉取”版本；`v0.0.2` 起为直推版本；`v0.0.3` 起带视频缓存；`v0.0.4` 起支持收藏文字/封面离线缓存；`v0.0.5` 起支持中间服务拉取；`v0.0.6` 起支持连接测试；`v0.0.7` 起移除直推，仅保留中间服务拉取；`v0.0.8` 起采用暗色影院式界面、全屏详情、沉浸播放器和继续观看；`v0.0.9` 起资料库改为纯海报卡片，修复演员别名匹配并加入 Paparazzi 测试；`v0.0.10` 起收紧顶部标题栏、详情页演员改为自动换行并显示头像，快照改为原子写入。
- `main` 每次推送：`debug-latest`，versionName = `<version.properties 的 versionName>-dev.<run_number>`，versionCode = GitHub run number。
- tag 构建：versionName = tag 去掉 `v`，生成同名 Release。
- 发版：改 `version.properties` → `git tag v0.0.10` → `git push origin v0.0.10`。
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
  SyncBus.kt           快照事件
  SyncStore.kt         中间服务配置、本地快照
  SyncModels.kt        LibraryItem / Snapshot / JSON 解析
  PullClient.kt        从中间服务拉取/测试快照
  LibraryViewModel.kt  拉取状态、快照、自动同步
  LibraryScreen.kt     首页、底部导航、搜索/排序、继续观看、空态
  LibraryCards.kt      海报覆盖卡片、继续观看卡片、演员卡
  LibrarySettings.kt   手动播放弹窗 + 输入样式
  DetailScreen.kt      全屏详情页（封面/演员/信息/简介/标签）
  SettingsScreen.kt    分组设置页 + 同步状态卡
  PlaybackStore.kt     本地播放进度（继续观看/断点续播）
  PlayerPrefs.kt       默认倍速/控制栏/双击跳转偏好
  PlaybackSettings.kt  播放设置弹窗
  SyncSettings.kt      中间服务地址/令牌/测试
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

扩展侧改动在 `favour-plugin` 的 `manager/`（同步设置、目标列表、连接测试）和 `shared/sync-targets.js`。

## 验证状态

- `assembleDebug` 通过（仅中间服务拉取 / 连接测试 / 资料库 / 规则解析 / 收藏文字·封面·视频缓存）。
- 中间服务：健康检查、Tailscale `/favour` 路由、带令牌 GET、未授权 401 已验证。
- 扩展连接测试：本机快捷填入、全部连通、单个目标 401 的界面与状态均已用无头 Edge + 模拟接收端验证（测试只 GET，不写入）。
- 播放器：此前真机已验证解析、清晰度、进度、手势、雪碧图、进度条单次 seek/无回跳。
- 界面重构（0.0.8）：暗色影院主题、底部导航、海报卡、继续观看、全屏详情、沉浸播放器、分组设置已实现，`assembleDebug` 通过。
- 合集/标签/已看/演员头像：模型和中转服务字段已就绪；已用临时数据验证中转服务保留字段，待扩展同步后 App 拉取。
- 卡片/演员：Paparazzi 无设备渲染测试与 SnapshotParser 测试通过（别名剥离、头像 key、系列/标签/已看字段）。
- App 拉取/离线缓存/横竖屏/新界面：真机视觉与交互验证待设备连接。

## 已知限制

- 中间服务需要可达；手机在拉取时需要开启 Tailscale（中间服务需常开）。
- 扩展和中间服务在同一台机器时用 `http://127.0.0.1:8789`；手机不能用回环地址，必须用 Tailscale HTTPS 地址。
- 中间服务只保留最新一份快照，没有历史版本或冲突合并；数据单向（扩展 → 中间服务 → App）。
- 中间服务使用一个共享令牌；令牌泄露后需更换并同步更新扩展和 App。
- 封面只在开启封面缓存且拉取过一次后才会离线可用；关闭后未浏览过的封面在离线时显示占位。
- 缓存位置只支持 App 内部/外置缓存目录，不支持任意 SAF 文件夹；收藏文字固定保存在应用私有目录。
- 视频缓存不保证完整离线（解析页面仍需网络）。
- App 是只读端：最爱/已看/演员喜爱与排序值可以展示和筛选，但只能在扩展里修改；编辑、删除、备份、多选等扩展功能不在 App 内。
- 已看状态、演员头像/喜爱/排序需要扩展更新后同步一次；系列和类型标签使用快照里的 `extras`，已有数据即可显示。
- App 的「继续观看」使用手机本地播放进度，不回写扩展。
- 数据源必须用 `OkHttpDataSource`；`DefaultHttpDataSource` 在真机会被视频 CDN 返回 403。
- 本项目仅用于个人调试；公开仓库不代表对内容来源的版权或 ToS 授权。
