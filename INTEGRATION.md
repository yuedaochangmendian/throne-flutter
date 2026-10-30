# 接入清单（v2.0.4）

三种接入方式任选其一：`git am` 打补丁、整目录拷贝、按下面的 5 个改造点手工改。
改完执行 `./gradlew assembleDebug`；编译期不需要新增任何 Gradle 依赖。

---

## 0. 新增文件

把 `patches/app/src/main/java/io/nekohasekai/sagernet/glass/` 整个目录拷到
`app/src/main/java/io/nekohasekai/sagernet/glass/`（10 个 `.kt`，无外部依赖）。

---

## 1. `ThemedActivity.kt` — 挂载玻璃宿主（必改）

所有 Activity 的基类。`onContentChanged()` 在 `setContentView` 之后回调，
在这里把内容包进壁纸层，62 个 layout 一个都不用动。

```kotlin
import io.nekohasekai.sagernet.glass.GlassHost

override fun onContentChanged() {
    super.onContentChanged()
    findViewById<AppBarLayout>(R.id.appbar)?.applyTopInset()
    if (!isDialog) GlassHost.wrap(this)   // ← 新增
}
```

> 为什么放在 `onContentChanged` 而不是 `onCreate`：子类在 `onCreate` 里才 `setContentView`，
> 且 Dialog 主题的 Activity 走窗口级玻璃（`GlassWindows`），不套宿主。

## 2. `layout_appbar.xml` — 顶栏换成玻璃（必改）

```xml
<io.nekohasekai.sagernet.glass.GlassAppBarLayout
    android:id="@+id/appbar"
    android:layout_width="match_parent"
    android:layout_height="?attr/actionBarSize"
    app:elevation="0dp"
    app:liftOnScroll="false">

    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/toolbar"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@android:color/transparent"   ← 关键：去掉 colorPrimary
        android:theme="?actionBarTheme"
        android:touchscreenBlocksFocus="false"
        app:popupTheme="@style/ThemeOverlay.AppCompat.DayNight"
        app:title="@string/app_name" />
</io.nekohasekai.sagernet.glass.GlassAppBarLayout>
```

根标签由 `AppBarLayout` 改为 `GlassAppBarLayout`，并删掉根上的 `android:background="?attr/colorPrimary"`。
`?actionBarTheme` 保持 Dark，白色标题文字不需要改——玻璃是"烟熏"的，白字在 24 套主题下都成立。

## 3. `MainActivity.kt` — 统计栏 / FAB / 抽屉（必改）

```kotlin
setContentView(binding.root)
GlassDecor.installMain(this)   // ← 新增，紧跟 setContentView
```

`installMain` 做四件事：统计栏换玻璃（顺带丢掉 BottomAppBar 的 FAB 凹槽）、FAB 换玻璃、
两个 `NavigationView` 面板换玻璃、抽屉滑动时刷新底片。

> 第 93 行 `binding.fab.backgroundTintList = …`（White 主题专用）可以保留，会被覆盖。

## 4. `ids.xml` — 卡片标记（必改，供可选步骤用）

```xml
<item name="glass_card" type="id" />
```

## 5. `AlertDialogFragment.kt` — 弹窗玻璃（必改）

```kotlin
import io.nekohasekai.sagernet.glass.GlassWindows

override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog =
    MaterialAlertDialogBuilder(requireContext()).also { it.prepare(this) }.create().also {
        GlassWindows.apply(it)     // ← 新增
    }
```

API 31+ 走 `Window.setBackgroundBlurRadius()`，由 SurfaceFlinger 直接模糊窗口背后的内容，不抓帧、零成本；
API 30 及以下自动退化为高不透明度面板（`0xF2`），圆角与描边保持一致。

---

## 可选：列表卡片化（推荐，逐屏开）

```kotlin
// 例如 ProfileSelectActivity / AppListActivity / GroupFragment 里，recyclerView 初始化之后
GlassRecycler.glassItems(binding.recyclerView, radiusDp = 16f, gapDp = 6f)
```

它只挂在 `OnChildAttachStateChangeListener` 上，**不改 Adapter、不碰 ItemDecoration**；
已处理的项打 `R.id.glass_card` 标记，避免重复包裹。默认只补 `bottomMargin`（原本为 0 时），
横向留白交给 item 布局自己控制。

单个容器也可以用：

```kotlin
GlassDecor.card(binding.headerCard, radiusDp = 20f, clickable = true)
```

---

## 可选：按屏幕调参

| 场景 | 做法 |
|---|---|
| 低端机掉帧 | `GlassBackdrop.captureIntervalMs = 130`（默认 90） |
| 列表很长、卡片很多 | `GlassTokens.downscale` 0.22 → 0.18，模糊成本降 1/3 |
| 想要更"厚"的玻璃 | `GlassTokens.chromeTop/Bottom` 的 alpha 上调 10–15% |
| 完全关掉 | `GlassHost.enabled = false`（弹窗玻璃同样受控） |

---

## 排查

| 现象 | 原因 / 处理 |
|---|---|
| 顶栏是纯黑或纯透明 | 底片还没抓到：首帧 `frame()` 返回 null 会走纯染色，第二帧恢复。若一直如此，检查 `GlassHost.wrap` 是否被调用（`isDialog` 为 true 时会跳过） |
| 顶栏里有一层"鬼影" | 有玻璃面没注册到 `GlassBackdrop.registerSurface`，抓帧时把自己也画进去了 |
| 滚动时偶发掉帧 | 抓帧是同步的：上调 `captureIntervalMs`，或把 `downscale` 降到 0.18 |
| 弹窗没有模糊 | API < 31；或 OEM 关闭了窗口模糊（部分国产 ROM），此时高不透明度面板生效 |
| White 主题下顶栏文字看不清 | 不要改 `actionBarTheme`。玻璃令牌已按主色亮度自动压暗 52% |
| 抽屉里的内容像"截图" | 抽屉打开时没有触发刷新：确认 `GlassDecor.installMain` 里的 `DrawerListener` 生效 |

---

## 与上游同步

补丁基于 `v2.0.4` tag。若上游改动过 `ThemedActivity.onContentChanged`、`layout_appbar.xml`
或 `MainActivity.setContentView` 附近，`git am` 会冲突——这三处都是单行插入，手工合入即可，
其余文件（`glass/`、`ids.xml`）是纯新增，不受影响。
