# Throne v2.0.4 · Liquid Glass（基于 Liquid-Glass-Android）

给 [throneproj/ThroneForAndroid](https://github.com/throneproj/ThroneForAndroid) **v2.0.4** 换上
[QWEA0/Liquid-Glass-Android](https://github.com/QWEA0/Liquid-Glass-Android) 的液态玻璃。

![预览](preview/preview.png)

> **已构建好的 APK**：[Releases](https://github.com/yuedaochangmendian/throne-liquid-glass/releases)
> —— debug 签名，arm64-v8a / armeabi-v7a / x86_64 三包。构建过程见 [BUILD.md](BUILD.md)。

---

## 这次换的是渲染内核

第一版是我自己写的近似实现：整窗抓帧 → 三次盒式模糊 → 染色 + 镜面描边。观感对得上，但只有模糊，
没有折射。现在换成 **Liquid-Glass-Android** 的 `LiquidGlassView`，拿到的是：

| | 自研（v1） | Liquid-Glass-Android（当前） |
|---|---|---|
| 边缘 | 1dp 线性渐变描边 | **SDF 圆角透镜**：内容在边缘被压缩成环，随圆角实时变化 |
| 色散 | 无 | 沿法线逐通道折射，边缘出现真实的光谱分离 |
| 高光 | 固定左上渐变 | **重力感应光源**，倾斜设备时高光跟着走 |
| 材质 | 单一染色 | Regular / Clear 两档，外加背景亮度自适应 |
| 依赖 | 无 | 一个 AAR（MIT） |

为什么不用 Flutter 的 [`liquid_glass_widgets`](https://github.com/sdegenaar/liquid_glass_widgets)：
Throne 是纯原生 Kotlin + XML（369 个 `.kt`、62 个 layout、0 个 `.dart`、0 处 Compose），
没有 Flutter 运行时，那个包的 widget 一行也 import 不进来。`LiquidGlassView` 恰好是 `FrameLayout`
子类、API 24+、不需要 Compose 迁移，是同一套视觉语言里唯一能直接落地的实现。

## 怎么接的

库的模型是「玻璃是一个容器，内容放在它里面」。所以接入不是 `background = ...`，而是**把目标
View 换成一个玻璃容器，原 View 挪进去当唯一子级**——见 `GlassSurface.wrap()`：

```
原层级                     接入后
CoordinatorLayout          CoordinatorLayout
├── ServiceButton (fab)    └── LiquidGlassView (接管原 LayoutParams + anchor)
└── StatsBar                   └── ServiceButton
                               └── ...
```

三个关键点：

1. **`LayoutParams` 整体让给玻璃**，原 View 用原尺寸参数进去。`CoordinatorLayout` 的 anchor、
   `DrawerLayout` 的 `layout_gravity`、外边距、weight 全部原样保留，`findViewById` 照样能拿到原 View。
2. **采样源指向窗口根部的 `GlassHostLayout`**（`backdropSource`）。库默认只采直接父容器，
   而壁纸画在根节点，只采父容器会得到一片纯色，折射什么都看不见。
3. **内容必须在玻璃里面**。玻璃采样时会把自己连同子级一起设为 `INVISIBLE`，标题、FAB 图标
   因此不会在背景底片里留下重影——只有「内容放在玻璃里」这个结构才有这个效果。

## 覆盖范围

| 位置 | 做法 |
|---|---|
| 顶栏 | `layout_appbar.xml` 里 `AppBarLayout` → `LiquidGlassView` → `Toolbar` |
| 底部统计栏 | 运行时 `wrap()`，只圆上面两个角 |
| FAB | 运行时 `wrap()`，anchor 随 `LayoutParams` 一起转移 |
| 抽屉（左右两个面板） | 运行时 `wrap()`，Clear 材质 + 18dp bevel |
| 弹窗 | `LiquidGlassDialogBuilder` 替换 `MaterialAlertDialogBuilder`，跨 window 采样由库处理 |
| 列表卡片 / 设置项 | **半透明渐变 + 1dp 高光描边，不做实时折射** |

最后一条是刻意的：每块玻璃每帧都要把身后的内容重画进离屏位图，一屏十几张卡片就是十几倍开销。
玻璃只出现在控制和浮层上，也是 iOS 自己的做法。卡片走 `GlassDecor.card()`，观感统一、成本恒定。

## 构建

`./gradlew assembleDebug` 直接跑一定失败（缺 `ThroneCore.aar`，那个要先用 gomobile 打）。
完整步骤见 [BUILD.md](BUILD.md)。

```bash
git clone https://github.com/throneproj/ThroneForAndroid.git
cd ThroneForAndroid && git checkout v2.0.4
git am /path/to/0001-liquid-glass.patch
cp /path/to/liquidglass-v2.0.11.aar app/libs/     # 补丁里引用的是本地 AAR
./gradlew assembleOssDebug
```

`app/libs/liquidglass-v2.0.11.aar` 本仓库有现成的（`patches/app/libs/`），也可以从 JitPack
拿 `com.github.QWEA0:liquidglass:v2.0.11` 后改回 maven 坐标。

## 验证状态

- ✅ **编译通过**：Android SDK 35 · Build-Tools 35.0.1 · NDK 28.2.13676358 · Gradle 8.10.2 · AGP 8.8.1
  → `assembleOssDebug` BUILD SUCCESSFUL，三个 ABI 包 45.7–48.0 MB
- ✅ APK 验签通过，`dexdump` 确认 `com.example.liquidglass.LiquidGlassView` 与
  `libnativegauss.so`（arm64-v8a / armeabi-v7a）都在包里；x86_64 没有 `.so`，库会退回 Java 模糊
- ⚠️ **未在真机验证**。风险点：`wrap()` 改变了层级，`CoordinatorLayout` 的 FAB anchor 在
  嵌套后被解析成内层 View，理论上前者坐标与后者一致，但只有实机能确认；如果 FAB 位置跑偏，
  把 `GlassDecor.installMain` 里 FAB 那段注释掉即可单独排查
- ⚠️ 每块玻璃采样整窗内容，低端机上建议先只保留顶栏和统计栏

## 许可

- [Liquid-Glass-Android](https://github.com/QWEA0/Liquid-Glass-Android) — **MIT**，`libs/` 里的
  AAR 是 `v2.0.11` 原样副本，版权归 PandaDog
- [ThroneForAndroid](https://github.com/throneproj/ThroneForAndroid) — **GPL-3.0**，本补丁是它的
  衍生作品，同样以 GPL-3.0 授权，全文见 [LICENSE](LICENSE)；分发编译产物时须一并提供对应源码
