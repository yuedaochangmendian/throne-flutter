# Throne ↔ B-box Flutter UI 功能对接清单

对象仓库：

| 角色 | 仓库 / 分支 | 本地路径 |
|---|---|---|
| Flutter UI | `giturass/B-box` **`newui`** 分支（FlClash 风格 UI） | `/workspace/B-box/flutter` |
| 内核宿主 | `throneproj/ThroneForAndroid` `main`（VERSION_NAME 2.0.4） | `/workspace/ThroneForAndroid` |
| 桥接层 | 新增 `app/src/main/java/io/throne/bbox/` | `/workspace/ThroneForAndroid/app/src/main/java/io/throne/bbox` |

桥接沿用发布版既定的两个通道 `io.ericlee.bfa/box`、`io.ericlee.bfa/events`，因此 Dart 端只需
按新协议取一次 `ready` 里的 `capabilities`，不需要重写 UI 与内核的通信框架。

---

## 前置发现（先读这段）

1. `yuedaochangmendian/throne-flutter` 当前主干**只包含液态玻璃补丁**（`patches/`、`preview/`），
   `app/src`、`docs/` 都不在树里；`v2.0.4-bbox-ui` 标签指向的树同样没有宿主源码。也就是说发布说明里
   提到的 `app/src/main/java/io/throne/bbox/` 桥接层**没有随仓库公开**。本次改造按发布说明声明的
   包路径重建了这一层，可直接拷回 `throne-flutter` 的宿主工程。
2. `giturass/B-box` 默认分支是 `newui`（Flutter UI），另一个分支体是 SFA 原生——**别拿错分支**。
3. Throne 不是「一份 sing-box JSON 配置」模型，而是**设置注册表 + 结构化 profile + 生成器**。
   因此 UI 里所有「配置全文」语义都不能照搬，见第 2 类。

---

## 第 1 类：UI 有、Throne 没有 → 删除（不是置灰，是移除入口）

`ready` 新增的 `capabilities` 是唯一开关（`Capabilities.kt`），UI 侧统一在 `BoxCapabilities.of()`
读取，na­v 入口写在 `pages/tools.dart`、底部页签写在 `main.dart`。

| UI 条目 | capability | Throne 实况 | 处理 |
|---|---|---|---|
| Terminal / SSH | `terminal` | 无本地 SSH 客户端、无 `BoxSSH` | 从 Tools 移除 |
| Requests（抓包、MITM、改写规则、CA 证书） | `capture` | 依赖 libbox 私有 `openUICapture` 等扩展 | 从 Tools 移除 |
| Remote control | `remote` | 无远程会话（所有 `*.*` 都带 `require(!isRemote)` 语义） | 从 Tools 移除 |
| Reports（crash / oom / power） | `reports` | 无三个 ReportManager | 从 Tools 移除 |
| Network quality / STUN test | `quality` / `stun` | 有 URL 测速但没有这套轮询管线 | 从 Tools 移除 |
| Endpoints（tailscale / openvpn / openconnect / usbip） | `endpoints` | 均不存在；WARP 走的是 Core 配置，不是 endpoint | 从 Tools 移除 |
| Proxy groups 卡片 + Proxies 页签 | `proxyGroups` | 无 Clash 模式选择器，`groups.*` 无从实现 | 卡片与页签一并隐藏 |
| Outbound mode 卡片 | 由 `dashboard.modes` 决定 | 无 rule/global/direct 开关 | 已按空数组自然隐藏 |
| Memory 卡片 | `memory` | ThroneCore 不对外暴露运行时内存 | 隐藏 |
| Auto redirect | — | 无对应概念 | 删除该开关 |
| Dynamic notification | — | Throne 通知固定由服务侧驱动，无可切换的“动态”语义 | 删除该开关，改由「通知显示分组名」承接语义 |
| Core 内存保护 / 核心内存回收 / 内存上限 | — | 无 OOM killer 开关 | 删除四项 |
| Power reports | — | 无电源报告 | 删除 |
| Deprecation warnings | — | 无 libbox 弃用告警通道 | 删除 |
| Managed app list / Rescan / Application query（Shizuku、ROOT 查询模式） | `perAppManaged` / `rootQuery` | 分应用名单只由 PackageManager 提供 | 移除三处入口；`perAppManaged` 恒为 false |
| 每个 profile 的 Network / DNS / Routing rules / Rule sets / Log 配置页 | — | Throne 是全局路由档 + 全局 DNS 设置，per-profile 这些字段不存在 | 改为全局 Throne 页面（见第 3 类），不再在 profile 下挂子页 |

> 没有把页面文件删掉，而是**断开入口**：`tools.dart` 之外，`taildrop`/`endpoints`/`terminal` 仍被
> `main.dart` 的 intent 流程引用，直接删文件会连带影响 Shanzhai。遗留的死代码可在能力清单稳定后清理。

---

## 第 2 类：两边都有 → 对接去重（同一份存储，不另起一份）

桥接层 `SettingsSchema.kt` 用一张表把旧 B-box key 直接读写到 Throne 自己的 store，
`pages/access.dart`、`SystemProxyPanel`、`Dashboard` 等界面**一行不改**即可继续工作。

| UI 旧 key | Throne 真实存储 | 说明 |
|---|---|---|
| `perAppEnabled` | `Key.PROXY_APPS` | 分应用主开关 |
| `perAppMode` | `Key.BYPASS_MODE`（**语义反相**，1=绕过选中 ⇔ bypassMode=true，2=仅代理选中 ⇔ false） | 转换写在桥接层，UI 不用改 |
| `perAppList` | `Key.INDIVIDUAL`（换行分隔包名） | 列表 ⇄ 多行文本互转 |
| `allowBypass` | `Key.DISABLE_PRIVATE_RANGE_BYPASS`（**取反**） | Throne 默认绕过私有网段 |
| `dynamicNotification` | `Key.SHOW_GROUP_IN_NOTIFICATION` | 仅语义承接，UI 上已换名 |
| `batteryExempt` | `PowerManager.isIgnoringBatteryOptimizations` | 只读；点击仍跳系统设置 |
| `queryMode` | 常量 `"ROOT"` | 保留字段，避免 UI 判空崩溃 |
| `settings.set` + `restart` | `Action.RELOAD` | 只有白名单 key 允许自动重启服务（`autoRestartable`），与上游 3 个自动重启项对齐 |
| `dashboard.lan.*` / Intranet IP 卡片 | `inbound_address`（`::` ⇄ `127.0.0.1`）+ `inbound_socks_port` | libbox 的 LAN proxy 换成 Throne 的混合入境 **Allow LAN** |
| `systemProxy.set` | `Key.APPEND_HTTP_PROXY` | 仅 VPN 模式且 API ≥ 29，否则返回 “System HTTP proxy is unavailable” |
| `logs.*` | `/system/bin/logcat` | Throne 核心日志就在 logcat，原生 LogcatFragment 同源 |
| `apps.list` / `apps.icon` | PackageManager | 替代 Shizuku 扫描器 |
| `profiles.list` | Room `groups` 表（订阅组） | 见下方映射说明 |
| `profiles.select` | `GroupRepo.setCurrent` + `DataStore.selectedProxy` | |
| `profiles.save` | `GroupRepo.add/save` + `SubscriptionQueue.subscribeUrl` | 新建时直接触发首次拉取 |
| `profiles.update` | `SubscriptionQueue.refreshGroup` | |
| `profiles.delete` | `GroupRepo.delete`，退回单条 outbound 时走 `ProfileManager.deleteProfile` | |
| `profiles.config` / `profiles.write` / `config.check` / `config.format` | 当前 outbound 的 `outboundJson` | 语义已收窄，见第 4 类第 2 条 |
| `preferences.*`（外观、排序、卡片） | 本地 SharedPreferences `throne_ui` | 与核心配置完全隔离，改外观不再触发重载提示 |
| Dashboard “Apply / Restart required” | `log_level`、`log_buf_size` 需要进程重启时才提示 | 其余一律走 reload |
| `updates.check` | `BuildConfig.IN_APP_UPDATER` | fdroid 变体直接报 “Updates are not available in this build” |

---

## 第 3 类：Throne 有、UI 没有 → 新增（沿用现有 Sheet 样式）

全部写在新增的 `flutter/lib/pages/throne.dart`，入口挂在 **General** 下（与原 Service / Core
同级），控件复用 `ListItem.toggle / .input / .options / .open`，标题自动 `tr()`，译文补在
`l10n.dart` 的 `_zh` map；新增 boolean/数字/枚举项不需要改动 `box.dart`。

| 新页面 | 新增的 Throne 设置项 |
|---|---|
| **Service**（改写现存页） | 服务模式 VPN / 仅代理、绕过私有网段、通知显示分组名、显示直连速度、网络变化/唤醒重置连接、开机自动连接、最近任务隐藏、按流量计费、删除免确认 |
| **Inbound** | 混合端口、随机入境端口、允许局域网访问、DNS 入境端口、入境认证（用户名/密码） |
| **TUN** | MTU、TUN IPv6、TUN IPv4/IPv6 CIDR、绕过私有网段、经 TUN 路由 |
| **DNS** | 远程 DNS、直连 DNS、各自禁用 IPv6 解析、默认 DNS 服务器（remote/direct）、DNS 路由、FakeDNS、FakeIP 禁用 IPv6、系统 hosts、预置 hosts、底层 DNS、缓存开关与容量 |
| **Routing** | 出站域名策略、域名策略、规则集镜像源、广告拦截、规则集自动更新（开关 + 间隔）、WARP 总开关 |
| **Core** | 日志级别、日志缓冲区、流量统计、禁用单配置统计、跳过证书校验、Clash API（开关/端口/监听地址/密钥）、核心 API（开关/端口）、NTP（开关/服务器/端口） |
| **Testing** | 测试地址、超时、并发测试数、直连测试地址、测速模式与超时、出口国家查询与查询服务方 |
| **Subscriptions** | UA、走代理拉取、允许不安全 TLS、TLS/HTTP 版本、发送设备标识、自动更新开关/间隔、遵循服务端间隔、失败清空、变更报告、允许停止正在使用的配置 |
| **Updates** | 允许测试版（仅 `IN_APP_UPDATER` 构建显示） |

一致性约定（本次新增都遵守）：

1. 开关用 `SettingToggle`，本质上与 `NativeToggle` 同源，走 `settings.set`；
2. 选项用 `ListItem.options`，枚举值直接写 Throne 的原始字符串（`ipv4_only`、`smux`……），不另造映射；
3. 每组都给 `generateSection`，需要说明的在 `footer` 里写一行提示，和现有页一致；
4. 所有新文案进 `_zh`，中文优先，缺译文自动回退英文。

---

## 第 4 类：尚未闭环（明确标注，别当成已完成）

1. **连接列表（connections）**：Throne 的 sing-box API 默认开在 9091，理论上可以拉取
   `/connections`，但桥接当前返回 `connections: false`，UI 不显示该入口。要接就得处理
   `core_box_api_secret` 的鉴权与轮询节流。
2. **`profiles.write` 的语义收窄**：B-box 写的是整份配置，Throne 只有单条 outbound 的 JSON。
   当前实现写的是**当前选中 outbound** 的 `outboundJson`，因此 profile 级编辑器实际是
   outbound 编辑器——这与 Throne 原生的「原始 JSON 编辑」一致，但**不是**配置全文。
3. **路由档（Route profiles）、规则、订阅组高级项、自动选择器、WARP 注册键**：这些是 Throne 最重
   的功能，当前只暴露了全局开关，**管理界面尚未落到 Flutter**（仍在原生 Activity）。下一步建议：
   先把 `RouteRepo` / `SubscriptionOptions` 的 CRUD 补到桥接，再照 `throne.dart` 的样式加列表页。
4. **备份 / WebDAV**：`.thrbackup` 体系未接，UI 的 Backup 页还依赖 `backup.*` method。
5. **`Proxies` 页签被隐藏后**，Throne 的配置列表需要一个 Flutter 页面承接，短期内靠原生
   `ConfigurationFragment` 兜底。

---

## 合入步骤

1. 把 `ThroneForAndroid/app/src/main/java/io/throne/bbox/`（含 `flutter/` 子包）拷进宿主工程；
2. Manifest 里把 launcher 与 leanback banner 指向 `io.throne.bbox.flutter.BboxFlutterActivity`；
3. Flutter module 用现成 AAR（或按 `docs/bbox-flutter.md` 重建），`AndroidManifest` 保留原有
   `io.flutter` meta 即可，`configureFlutterEngine` 会自动挂上两个通道；
4. 把 `B-box/flutter/lib` 下改动过的文件（`box.dart`、`main.dart`、`capabilities.dart`、
   `pages/tools.dart`、`pages/settings.dart`、`pages/dashboard.dart`、`pages/access.dart`、
   `pages/throne.dart`、`l10n.dart`）同步进同一个 module；
5. 桥接层只在主进程工作（Room + PackageManager），`:bg` 进程不初始化；核心仍由 `CoreRuntime`
   在 `:bg` 加载，这一点没有被改动。

---

## 本次改动的文件清单

**宿主（Throne 工程，`app/src/main/java/io/throne/bbox/`，全部新增）**

| 文件 | 作用 |
|---|---|
| `SettingsSchema.kt` | 90 个桥接 key 的注册表：类型、默认值、写入副作用（None / Reload / AppRestart）、是否允许自动重启服务；含旧 UI key 到 Throne 存储的别名 |
| `Capabilities.kt` | `ready` 里的能力清单，UI 裁剪的唯一来源 |
| `ServiceBridge.kt` | 服务的绑定、状态、启动/停止/重载、速率与流量、`dashboard` 快照；同时兼容 VPN 与仅代理两个 Service |
| `ProfilesBridge.kt` | 订阅组 → profile wire、选择、增删、`profiles.config` / `write` 落到 outbound JSON、混合入境的 LAN 视图 |
| `AppsBridge.kt` | PackageManager 版应用列表与图标，替代 Shizuku 扫描器 |
| `UiPreferences.kt` | 外观类偏好独立存储，改外观不再触发核心重载 |
| `Platform.kt` | 电池优化状态、本机地址等宿主信息 |
| `BoxBridge.kt` | MethodChannel + EventChannel 的分发主体、日志 tail、设置写入后的联动 |
| `flutter/BboxFlutterActivity.kt` | FlutterActivity 与生命周期挂载 |

**UI（B-box `flutter/lib`）**

| 文件 | 改动 |
|---|---|
| `capabilities.dart` | 新增：能力清单读取（空清单 = 未改造宿主，不裁剪） |
| `box.dart` | `ready` 结果里解析 `capabilities` |
| `main.dart` | Proxies 页签按 `proxyGroups` 裁剪，页签索引随之重算 |
| `pages/tools.dart` | 六个 nav 组改为能力驱动，空组不再渲染；删除永久性空入口 |
| `pages/settings.dart` | General 改为 Throne 分组树；Service 页换成 Throne 语义；删除 Core 内存/电源/弃用告警四项 |
| `pages/throne.dart` | 新增：Inbound / TUN / DNS / Routing / Core / Testing / Subscriptions / Updates 与四个通用控件，样式沿用现有 Sheet |
| `pages/dashboard.dart` | Intranet IP、Proxy groups、Memory 三张卡片按能力裁剪 |
| `pages/access.dart` | 移除受管应用列表、Rescan、Shizuku 查询入口 |
| `l10n.dart` | 新增 98 条中文译文（已检查无重复键，共 722 条） |

**自检与已知限制**

- Dart 侧：环境里的 Flutter 是 3.0.0（2022），而本项目用 Dart 3 语法，`flutter analyze` 无法在本机运行；
  已改为逐文件做词法级括号配平、跨语言 key 一致性、译文重复键三项检查，全部通过。真机集成时请先跑一次 `flutter analyze`。
- Kotlin 侧：本机没有 Android SDK/Gradle，未做编译验证。已按 Throne 源码逐个核对 API
  （`DataStore.*` 属性、`SettingsRegistry.*`、`ProfileManager`、`GroupRepo`、`SubscriptionQueue`、
  `SagerNet.start/stop/reloadService`、`SagerConnection.connect/disconnect/service`），
  并修掉了局部函数引用、劳而无功的 `android.provider.Settings` 与同名 `Settings` 对象冲突等问题。
- 删掉的 UI 选项在桥接层的对应 key 也一并删除（`autoRedirect`、`dynamicNotification`、
  `disableDeprecatedWarnings`、`oomKiller*`、`oomMemoryLimitMB`、`powerReportEnabled`），
  两端不再各留一份。

---

## 如何构建（本次补齐的部分）

上游 `ThroneForAndroid` 的 Gradle 只认识 Kotlin/XML，不含 Flutter。要让 CI 产出带新界面的 APK，补了四处：

| 位置 | 改动 |
|---|---|
| `repositories.gradle.kts` | 新增 Flutter 官方 maven（`storage.googleapis.com/download.flutter.io`）与 `flutter_ui/build/host/outputs/repo`（`flutter build aar` 的产物目录） |
| `app/build.gradle.kts` | `debugImplementation("io.ericlee.bbox_ui:flutter_debug:1.0")`、`releaseImplementation(...:flutter_release:1.0)`；补 `ndkVersion = "28.2.13676358"` 触发符号剥离（否则 libflutter.so 不打薄，APK 回到 ~272 MB） |
| `.github/actions/android-build/action.yml` | 在 Gradle 之前插入 Flutter 3.47.6、`flutter pub get --enforce-lockfile`、`flutter build aar --no-profile --target-platform android-arm,android-arm64,android-x64`（与 ThroneCore 的三个 ABI 一致） |
| `app/src/main/AndroidManifest.xml` | 注册 `io.throne.bbox.flutter.BboxFlutterActivity` |

另外 `app/proguard-rules.pro` 加了 `-keep class io.flutter.**` 与 `-keep class io.throne.bbox.**`：CI 的 debug 构建不 minify 看不出问题，release 构建会 shrink，引擎是走 JNI/反射调到这些类的。

### 原生界面与新界面的关系

新 Activity **没有**抢占 launcher：ADB 或 `throneui://open` 才能进原生 UI 之外的入口。原因是路由档、自动选择器、WARP 注册、WebDAV 备份这几块还依赖原生页面（见上文第 4 类），一旦把 launcher 换掉就没有回退路径。确认无误后，把 `BboxFlutterActivity` 的 intent-filter 里的 `VIEW` 换成 `MAIN` + `LAUNCHER`、并从 `MainActivity` 摘掉同名 filter 即可切换。

本地调试：

```bash
adb shell am start -n io.nekohasekai.sagernet.debug/io.throne.bbox.flutter.BboxFlutterActivity
adb shell am start -a android.intent.action.VIEW -d "throneui://open"
```

### ABI 与体积

`.github/actions/throne-core` 只为 android/arm64、android/arm、android/amd64 编译 `libthrone.so`，`buildSrc/Helpers.kt` 的 `splits.abi` 也是这三个。所以 `flutter build aar` 必须 `--target-platform android-arm,android-arm64,android-x64` —— 多构建出来的 ABI 会因缺 `libthrone.so` 而在分包阶段失败或装不上。
