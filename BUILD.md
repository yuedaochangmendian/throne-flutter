# 从零构建这个 APK

记录本仓库产物（`Throne-2.0.4-*-debug.apk`）的**实际**构建路径。踩过的坑都写在末尾。

## 结论先说

`./gradlew assembleDebug` 直接跑一定失败 —— 报 `Unresolved reference 'throneproj'`、`'go'`、`'Seq'` 共 465 条。
不是代码问题：**`ThroneCore.aar` 不在仓库里**。从 v2.0.0 起 Throne 把核心从 `libcore/` 移到了
`throneproj/Throne` 的 Go 源码里，由 CI 用 gomobile 现打成 AAR、生成 `sing-box.json` schema，
再喂给 Android 侧。所以顺序是：**先打核心，再打 App**。

## 环境

| 组件 | 版本 | 备注 |
|---|---|---|
| JDK | 17 或 20 | 本次用 Zulu 20 |
| Gradle | 8.10.2 | 项目 wrapper 指定，别用 9.x |
| AGP | 8.8.1 | buildSrc 里锁死 |
| compileSdk / Build-Tools | 35 / **35.0.1** | 脚本里写死了 buildToolsVersion |
| NDK | **28.2.13676358** | gomobile 交叉编译用 |
| Go | ≥ 1.26 | `go.mod` 要求 1.26.0，`GOTOOLCHAIN=auto` 会自动拉 |

镜像（国内直连官方源会慢到不可用）：SDK 与 NDK 走
`https://mirrors.cloud.tencent.com/AndroidSDK/`，Maven 走 `maven.aliyun.com/repository/google`。

## 步骤

### 1. 装 SDK（三个 zip，解压后要改目录名）

```bash
mkdir -p $ANDROID_HOME && cd $ANDROID_HOME
curl -O https://mirrors.cloud.tencent.com/AndroidSDK/platform-35_r02.zip
curl -O https://mirrors.cloud.tencent.com/AndroidSDK/build-tools_r35.0.1_linux.zip
curl -O https://mirrors.cloud.tencent.com/AndroidSDK/platform-tools_r37.0.1-linux.zip
unzip platform-35_r02.zip && mv android-35 platforms/            # 平台包自带 android-35
unzip build-tools_r35.0.1_linux.zip && mv android-15 build-tools/35.0.1   # 注意：Build-Tools 包叫 android-15
mkdir -p licenses && printf '24333f8a63b6825ea9c5514f83c2829b004d1fee\n' > licenses/android-sdk-license
```

Build-Tools 的 zip 内部目录名是 API 代号（`android-15`），必须手动改成版本号 `35.0.1`，否则 AGP 找不到。

### 2. 打 ThroneCore.aar

```bash
curl -L -o throne.tgz "https://codeload.github.com/throneproj/Throne/tar.gz/$(sed -n 's/^THRONE_CORE_REF=//p' nb4a.properties)"
tar xzf throne.tgz && cd Throne-*/

go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.13
go install github.com/sagernet/gomobile/cmd/gobind@v0.1.13

export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/28.2.13676358
DEST=/path/to/ThroneForAndroid/app/libs bash script/build_android.sh
```

产出 `app/libs/ThroneCore.aar`（约 92 MB，内含 `arm64-v8a` / `armeabi-v7a` / `x86_64` 三套 `libthrone.so`）。
32 核机器约 4 分钟，主要耗时在 Go 依赖拉取和 `gomobile bind`。

### 3. 生成 schema（App 运行时要读）

```bash
cd core
TAGS=$(sed -n 's/^TAGS="\(.*\)"$/\1/p' ../script/build_android.sh)
mkdir -p ../../../ThroneForAndroid/app/src/main/assets/schema
CGO_ENABLED=0 go run -trimpath -ldflags=-checklinkname=0 \
  -tags "$TAGS,with_purego" ./cmd/schemagen \
  -o ../../../ThroneForAndroid/app/src/main/assets/schema/sing-box.json
```

### 4. 打 App

```bash
cd ThroneForAndroid
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
./gradlew assembleOssDebug      # 或 assembleFdroidDebug
```

`assembleDebug` 会同时打 oss 与 fdroid 两套，耗时翻倍；只要一套就指定 flavor。
产物在 `app/build/outputs/apk/oss/debug/`，按 ABI 分三个 APK。

## 踩坑记录

1. **`Unresolved reference 'throneproj'`（465 条）** —— 缺 `ThroneCore.aar`，不是代码错误。见步骤 2。
2. **Gradle 报 `initialization script ... Unexpected input: '{'`** —— `~/.gradle/init.gradle` 里
   `maven { url https://... }` 缺引号、`mavelCentral()` 拼错。改掉或删掉。
3. **守护进程无故消失 / `Gradle build daemon disappeared unexpectedly`** —— 容器 cgroup 内存 8 GB，
   而 `gradle.properties` 给的是 `-Xmx4g` + Kotlin daemon 默认值 + `workers.max` 自动，KSP 阶段直接 OOM。
   压到 `-Xmx3g` / `kotlin.daemon.jvmargs=-Xmx2g` / `workers.max=3` / `parallel=false` 即过。
4. **`Canvas.drawRoundRect` 没有四角独立圆角重载** —— 只有 `Path.addRoundRect(RectF, FloatArray, Direction)` 有。
5. **`Drawable.bounds` 是 `Rect`（整型）** —— 传给 `LinearGradient` 需要先 `toFloat()`。
6. **debug 包名带 `.debug` 后缀** —— `applicationIdSuffix` 所致，可与正式版共存安装，但**不能**覆盖升级。

## 产物

| ABI | 文件 | 大小 | 适用 |
|---|---|---|---|
| arm64-v8a | `Throne-2.0.4-arm64-v8a-debug.apk` | 46 MB | 绝大多数手机（首选） |
| armeabi-v7a | `Throne-2.0.4-armeabi-v7a-debug.apk` | 46 MB | 32 位老机型 |
| x86_64 | `Throne-2.0.4-x86_64-debug.apk` | 48 MB | 模拟器 / 部分平板 |

debug 签名（Android Debug 证书），仅用于自测；对外分发请按 Throne 的 release 流程配置正式签名。
