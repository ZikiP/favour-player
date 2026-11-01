# Favour Player

Android 自同步资料库 + 直连播放器。

App 从你自己的 favour-sync 服务拉取 `/api/items` 快照并缓存到本地，在 App 内搜索、按演员/最爱筛选、看详情、直接起播。
不依赖 favour-sync 的网页界面，也不需要从 PWA 唤起。

## 数据流

```
桌面扩展 → favour-sync 服务(/api/items) → App 同步并缓存 → App 内浏览 → 原生播放
```

- 视频由手机直连 CDN，不经过电脑中继。
- favour-sync 的 PWA 可以保留，但 App 不依赖它。
- 旧的 `favourplayer://` 深链仍可用（可选）。

## 功能

- 同步 + 本地缓存 + 离线展示；失败可重试。
- 资料库：收藏 / 演员 / 最爱、搜索、卡片（封面、番号、演员、日期、最爱、播放）、详情弹窗。
- 详情页或卡片播放键 → `PlayerActivity` 解析并直连播放。
- 手动播放（收藏页或 `.m3u8` 直链）；浏览器分享文本可直接进播放器。
- 横竖屏适配：网格按宽度自适应，详情弹窗横屏左右 / 竖屏上下，播放器控件独立适配。
- 封面用 Coil 加载并缓存；失败回退番号色块。

## 下载 / CI

推送到 `main` 后，GitHub Actions 自动构建 debug APK。

- 最新滚动调试包：<https://github.com/ZikiP/favour-player/releases/tag/debug-latest>
- 直接下载：<https://github.com/ZikiP/favour-player/releases/download/debug-latest/app-debug.apk>
- `v*` 标签会生成正式命名的 Release；每次运行的 APK 也作为 Actions artifact 保留 30 天。

签名说明：

- CI 用缓存中的 debug keystore 签名，跨 CI 构建保持一致。
- 它和你本地 `assembleDebug` 的签名不同；覆盖安装前先卸载：
  `adb uninstall com.ziki.favourplayer`，再安装下载的 APK（反向切换同理）。
- 若 Actions 缓存被清理，CI 会生成新签名，届时卸载一次即可。

## 首次配置

- 同步服务地址：填你自己的 favour-sync 地址，例如
  `https://<your-device>.<tailnet>.ts.net/favour`（代码里不内置任何个人地址）。
- 访问令牌：`favour-sync` 的 `data/token.txt`，或部署输出。
- 手机需能访问该地址：Tailscale 登录同一账号；若同时开 FlClash，需要让 `*.ts.net` 绕过代理。
- 配置保存在 App 私有 SharedPreferences（明文），设置里可清除。

## 构建

需要 JDK 17–21、Android SDK 35。

```bash
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。

`local.properties` 里的 `sdk.dir` 指向本机 Android SDK；该文件已被 gitignore。

## 结构

```
app/src/main/java/com/ziki/favourplayer/
  MainActivity.kt      资料库入口 + 分享文本
  FavourApp.kt         Coil 图片加载器
  Brand.kt             品牌标记
  SyncModels.kt        LibraryItem / Snapshot / JSON 解析
  SyncStore.kt         配置、缓存、/api/items 同步
  LibraryViewModel.kt  同步状态、离线缓存、配置
  LibraryScreen.kt     顶部栏、搜索、Tabs、网格、空态/错误
  LibraryCards.kt      卡片、演员卡、详情弹窗
  LibrarySettings.kt   首次配置、同步设置、手动播放
  PlayerActivity.kt    解析状态机 + 深链
  Resolver.kt          页面/直链解析、UUID、雪碧图配置
  PlayerScreen.kt      ExoPlayer + 页面组装
  PlayerControls.kt    播放器控件
  PlayerSeekBar.kt     自绘进度条
  PlayerGestures.kt    手势（底部控件区隔离）
  Hud.kt               手势/倍速提示
  SpritePreview.kt     雪碧图加载、缓存、预览
  Quality.kt           清晰度
  Formatting.kt        时间/倍速格式化
  Theme.kt             色板
```

## 验证状态

- `assembleDebug` 通过（Coil / ViewModel / 资料库）。
- 播放器：真机已验证解析、4 档清晰度、进度、手势、雪碧图、进度条单次 seek / 无回跳、深链。
- 资料库：mock `/api/items` 已验证返回数据；App 的同步 / 横竖屏 / 详情 / 起播待手机重连后做真机截图验证。

## 已知限制 / 维护点

- 数据源必须用 `OkHttpDataSource`；`DefaultHttpDataSource` 在真机会被 surrit CDN 返回 403。
- 手机必须开代理/VPN 才能访问 `missav.live` / `surrit.com`；公开测试流 `demo.unified-streaming.com` 可直连。
- 只读同步：App 不回写桌面收藏；合集 / 排序 / 多选暂未实现。
- 离线时展示上次缓存；首次使用需要服务在线。
- 令牌明文保存在私有 SharedPreferences。
- 封面来自第三方 CDN；失败显示番号占位。
- 本项目仅用于个人调试；公开仓库不代表对内容来源的版权或 ToS 授权。
