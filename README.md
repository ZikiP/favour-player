# Favour Player

Android 资料库 + 直连播放器。收藏数据由 Favour 扩展**直接推送**到手机上的 App，不再经过电脑上的 favour-sync 服务。

## 数据流

```
Favour 扩展(手机同步) --PUT /api/items--> FavourPlayer 前台接收服务
        --> 本地快照 --> App 资料库 --> 原生播放(手机直连 CDN)
```

- 电脑只负责运行扩展；不需要再开 favour-sync 页面/服务。
- 手机与电脑需在同一 Tailscale 或局域网。

## 功能

- **接收同步**：App 内置 HTTP 接收服务（默认端口 `8790`），Bearer 令牌，前台服务常驻通知。
- 资料库：收藏 / 演员 / 最爱、搜索、卡片（封面、编号、演员、日期、最爱、播放）、详情弹窗。
- 播放：卡片或详情直接起播；手动播放、浏览器分享文本仍可用。
- 横竖屏适配；封面 Coil 缓存；解析规则可在 App 内编辑。
- 同步完成后资料库自动刷新；离线展示上次快照。

## 下载 / CI

推送到 `main` 后，GitHub Actions 自动构建 debug APK。

- 最新滚动调试包：<https://github.com/ZikiP/favour-player/releases/tag/debug-latest>
- 直接下载：<https://github.com/ZikiP/favour-player/releases/download/debug-latest/app-debug.apk>
- `v*` 标签会生成正式命名的 Release。

签名说明：

- 仓库包含公开的 **debug keystore**：`keystore/favour-debug.jks`（口令 `favourdebug`），本地与 CI 共用，保证更新签名一致。
- 只用于调试，不可用于商店或正式发布。
- 旧的自动 debug 签名安装包需卸载一次：`adb uninstall com.ziki.favourplayer`。

## 接收同步（首次）

1. 打开 App；接收服务会自动启动（通知栏常驻）。
2. 右上角设置 → **接收同步**：复制接收地址（`http://<手机IP>:8790`）和令牌；可改端口、重新生成令牌或关闭服务。
3. Favour 扩展 → **手机同步**：填接收地址 + 令牌，保存并点「立即同步」。
4. 地址列表会显示 Tailscale（`100.x`）和局域网（`192.168.x`）地址；选电脑能访问的那个。
5. 数据保存在 App 私有目录，离线可看上次快照。

注意：

- 关闭 App 后前台服务仍会运行；不想常驻可在设置里关闭。
- 令牌明文保存在 App 私有 SharedPreferences；泄露后重新生成即可，扩展需同步更新。
- 旧的 favour-sync 服务（8789）已停用、计划任务已 Disabled；`favour-sync/data` 里的数据和令牌文件保留，但不再参与同步。

## 解析规则

默认规则在 `app/src/main/assets/resolver_rules.json`，适用于“页面 HTML 中出现 `host + UUID + seek/playlist`”的通用模式，不绑定任何站点。

- App 设置 → 接收同步 → 解析规则，可粘贴 `{"rules":[...]}` 追加规则；自定义规则优先。
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

- `v0.0.1` 是旧的“App 拉取”版本；从 **`v0.0.2`** 起为直推版本。
- `main` 每次推送：`debug-latest`，versionName = `<version.properties 的 versionName>-dev.<run_number>`，versionCode = GitHub run number。
- tag 构建：versionName = tag 去掉 `v`，生成同名 Release。
- 发版：改 `version.properties` → `git tag v0.0.2` → `git push origin v0.0.2`。
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
  MainActivity.kt      资料库入口 + 分享文本 + 通知权限
  FavourApp.kt         Coil 图片加载器
  Brand.kt             品牌标记
  SyncBus.kt           快照/服务状态事件
  SyncStore.kt         接收配置、令牌、本地快照
  SyncServer.kt        手机上的 HTTP 接收服务
  SyncService.kt       前台服务(保活 + 常驻通知)
  SyncModels.kt        LibraryItem / Snapshot / JSON 解析
  LibraryViewModel.kt  接收状态、快照、设备地址
  LibraryScreen.kt     顶部栏、搜索、Tabs、网格、空态
  LibraryCards.kt      卡片、演员卡、详情弹窗
  LibrarySettings.kt   接收设置、手动播放
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

扩展侧改动在 `favour-plugin` 的 `manager/`（同步弹窗改为直推地址 + 令牌），未提交该仓库。

## 验证状态

- `assembleDebug` 通过（接收服务/前台服务/资料库/规则解析）。
- 播放器：此前真机已验证解析、清晰度、进度、手势、雪碧图、进度条单次 seek/无回跳。
- 资料库/接收同步：真机验证待手机重连（需要电脑扩展向手机推送一次）。

## 已知限制

- App 需运行（前台服务常驻）才能接收；可在设置关闭。
- 手机与电脑需在同一 Tailscale/LAN；接收地址用手机实际 IP。
- 数据接口是 HTTP + Bearer（局域网/Tailscale 内）；令牌泄露请重新生成。
- 只读接收：App 不回写扩展；合集/排序/多选暂未实现。
- 数据源必须用 `OkHttpDataSource`；`DefaultHttpDataSource` 在真机会被视频 CDN 返回 403。
- 旧 favour-sync 的 `/favour` Tailscale 路由仍存在但已 502，可另行移除。
- 本项目仅用于个人调试；公开仓库不代表对内容来源的版权或 ToS 授权。
