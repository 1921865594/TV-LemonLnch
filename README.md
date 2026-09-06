# LemonLnch Launcher / 柠檬桌面

<p align="center">
  <a href="#english">English</a> · <a href="#中文">中文</a>
</p>

---

<a id="english"></a>

## English

**LemonLnch Launcher** (package: `com.lemonlnch`) is an open-source **Android TV / Google TV / Fire TV style home launcher** designed for desktop-level use.  
It embeds **[lemon-TV](https://github.com/1921865594/Lemon-IPTV-v2)** live preview directly into the home screen for seamless channel switching, supports **remote number-key quick launch** of other apps, and allows **custom remote key mapping**.  

It is especially suitable for **elderly users** and **portable / living-room TV boxes**, offering a clean, large-focus, remote-friendly experience.

Inspired by ATK / Emotn-style layouts, written primarily in pure Java with Leanback-friendly navigation.

### Key Features

| Feature | Description |
|---------|-------------|
| **Embedded lemon-TV Live Preview** | Desktop-level card that binds to lemon-TV (`com.lemoniptv.lite`) via AIDL/Binder. Surface is rendered inside the launcher; no third-party Activity is launched into a freeform window. Click to open full-screen player, long-press to configure live URL. Auto-pause when launcher goes to background, auto-resume on foreground. Boot auto-start option available. |
| **Number-key App Shortcuts** | Map remote digits 0–9 to any installed app. Press the number key to instantly launch the bound app — ideal for elderly users who only need a few frequently used applications. |
| **Custom Remote Key Mapping** | Bind almost any remote key (except OK / D-pad) to an app. Capture mode lets you press a key to record it. |
| **True Home Launcher** | Declares `HOME` + `LEANBACK_LAUNCHER` categories. Can be set as default home. Optional Accessibility Service fallback for Google TV devices that block third-party default launchers. |
| **Boot & Keep-Alive** | BootReceiver + KeepAliveService ensure the launcher returns after power-on, package replace, or wake from sleep/screensaver. |
| **Rich Themes** | Built-in light (mountain wallpaper) / dark (AT4K-style gradient), custom local image, remote image URL, custom muted looping video, or custom gradient. |
| **Weather Hero Card** | Optional large weather card on the home screen (can be hidden). |
| **App Dock** | Bottom dock for favorite apps; long-press to replace or clear. |
| **Folders** | Create folders, move apps in/out, rename, reorder. |
| **Memory Auto-Clean** | When returning to the launcher, optionally kill background apps not on the whitelist (protects lemon-TV and other critical apps). |
| **Appearance Options** | Apps-per-row, 24h clock, minimal status bar, hide section titles, language (System / 中文 / English). |
| **File Transfer Helper** | Built-in simple web server + QR code for transferring files from phone to the TV box (assets/wap). |
| **Permission Center** | One-tap links to system settings for overlay, battery optimization, storage, notifications, etc. |

### Requirements

**Runtime (device)**

- Android **9.0 (API 28)** or higher (minSdk 28, targetSdk 34)
- Android TV / Google TV / Fire OS / most TV boxes with Leanback support recommended
- Landscape orientation preferred
- For live preview: install **[lemon-TV](https://github.com/1921865594/Lemon-IPTV-v2)** (package `com.lemoniptv.lite`) that exposes the PreviewService
- Optional root only for advanced freeform/hidden-API tweaks (see `tools/app_container_setup.sh`) — **not required** for normal use

**Recommended companion**

- [lemon-TV](https://github.com/1921865594/Lemon-IPTV-v2) — IPTV player that provides the embedded preview Surface via Binder

### Build & Development Environment

```bash
# Clone
git clone https://github.com/<your-username>/TV-LemonLnch.git
cd TV-LemonLnch

# Requirements
# - JDK 17 (recommended) or JDK 11+
# - Android SDK with compileSdk 34
# - Android Studio Hedgehog / Iguana or newer (or command-line only)

# Build debug APK
./gradlew assembleDebug

# Build release APK (uses debug signing if no release keystore is configured)
./gradlew assembleRelease
```

**Optional release signing** (in `gradle.properties` or command line):

```properties
LEMONLNCH_STORE_FILE=/path/to/keystore.jks
LEMONLNCH_STORE_PASSWORD=...
LEMONLNCH_KEY_ALIAS=...
LEMONLNCH_KEY_PASSWORD=...
```

**Project structure (simplified)**

```
TV-LemonLnch/
├── app/
│   ├── src/main/
│   │   ├── java/com/LemonLnch/
│   │   │   ├── ui/          # MainActivity, HomeUIBuilder, PreviewCardController, AppShortcutManager...
│   │   │   ├── service/     # BootReceiver, KeepAliveService, HomeAccessibilityService
│   │   │   └── system/      # RootShell helper
│   │   ├── java/com/tv/player/  # Hand-written AIDL-like Binder contracts (IPlayerService / IPlayerCallback)
│   │   ├── res/             # layouts (code-built), drawables, values + values-zh-rCN
│   │   └── assets/wap/      # simple web file-transfer UI
│   └── build.gradle
├── tools/app_container_setup.sh
└── README.md
```

### Technical Principle (Live Preview)

1. Launcher creates a `SurfaceView` inside the home preview card.
2. It binds to the remote service  
   `top.yogiczy.lemonlnch.preview.PreviewService` inside package `com.lemoniptv.lite`.
3. Communication uses a **hand-written Binder interface** (`com.tv.player.IPlayerService` / `IPlayerCallback`) kept identical in both apps.
4. Launcher calls `setSurface(surface)` → `startPreview(url)` / `resumePreview()` / `pausePreview()`.
5. lemon-TV renders the stream onto the shared Surface; the launcher never starts a foreign Activity into a freeform window.
6. Health checks + exponential retry + recovery debounce keep the preview stable across focus changes, background/foreground, and service death.

This design gives **true in-card seamless playback** and instant full-screen hand-off.

### Usage Tips (Elderly / Portable)

1. Install LemonLnch + lemon-TV.
2. Set LemonLnch as default Home (or enable the Accessibility fallback on restricted Google TV devices).
3. Long-press the live preview card → set your IPTV live URL.
4. Open Settings → **App Shortcuts** → bind digits 1–9 (and optionally a custom key) to the apps the user actually needs (e.g. 1 = Video, 2 = Music, 3 = Settings).
5. Enable “Start on boot” and “Memory Auto Clean” with lemon-TV on the whitelist.
6. Done — remote number keys become one-press app launchers, and the TV always returns to a familiar home with live TV preview.

### Disclaimer

- This project is provided **as-is** under the **Apache License 2.0**.
- It is an open-source community launcher. The authors and contributors are **not responsible** for any data loss, device instability, battery impact, or legal issues arising from the use of third-party IPTV streams.
- Live TV content is provided by the user-configured lemon-TV / IPTV sources. **You are solely responsible** for ensuring you have the legal right to access those streams in your jurisdiction.
- Accessibility Service is optional and only used as a fallback to return to the launcher; it does **not** read screen content, perform clicks, or collect personal data.
- Root / freeform scripts are optional advanced tools; normal operation does **not** require root.
- Use at your own risk. Always keep backups of important data.

### License

```
Copyright 2024-2026 LemonLnch contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

See the full [LICENSE](LICENSE) file.

### Related Projects

- [lemon-TV](https://github.com/1921865594/Lemon-IPTV-v2) — the companion IPTV player that supplies the embedded preview service.

---

<p align="center">
  <a href="#english">English</a> · <a href="#中文">中文</a>
</p>

---

<a id="中文"></a>

## 中文

**柠檬桌面（LemonLnch Launcher）**（包名：`com.lemonlnch`）是一款开源的 **Android TV / Google TV / 电视盒子桌面启动器**。  

配合 **[lemon-TV（柠檬 IPTV）](https://github.com/1921865594/Lemon-IPTV-v2)**，可在桌面卡片内 **直接嵌入直播预览**，实现桌面级无缝切换播放；支持 **遥控器数字键一键启动其他应用**，以及 **自定义遥控按键映射**。  

界面简洁、焦点大、操作简单，特别适合 **老年人群体** 和 **便携式 / 客厅电视盒子** 日常使用。

布局灵感来自 ATK / Emotn 风格，主要使用纯 Java 编写，完全适配遥控器导航。

### 主要功能

| 功能 | 说明 |
|------|------|
| **桌面内嵌 lemon-TV 直播预览** | 通过 AIDL/Binder 绑定 lemon-TV（`com.lemoniptv.lite`）的预览服务，Surface 直接渲染在启动器卡片内，**不会**把第三方 Activity 拉到自由窗口。点击进入全屏播放器，长按配置直播地址。退到后台自动暂停并释放 Surface，回到前台自动恢复。支持开机自动连接预览。 |
| **数字键应用直达** | 将遥控器 0–9 数字键绑定到任意已安装应用，按下即可一键启动。非常适合只需要几个常用软件的老年用户。 |
| **自定义遥控按键映射** | 除 OK / 方向键外，几乎任何按键都可绑定应用。进入捕获模式后直接按键即可完成绑定。 |
| **真正的 Home 启动器** | 声明 `HOME` + `LEANBACK_LAUNCHER`，可设为默认桌面。针对不允许第三方默认桌面的 Google TV 设备，提供可选的无障碍辅助服务作为后备方案。 |
| **开机自启与保活** | BootReceiver + KeepAliveService，开机、更新或从屏保/休眠唤醒后自动回到柠檬桌面。 |
| **丰富主题** | 内置浅色山景壁纸 / 深色 AT4K 风格渐变，支持本地图片、网络图片 URL、自定义静音循环视频、自定义渐变。 |
| **天气大卡片** | 首页可选天气英雄卡片（可隐藏）。 |
| **底部 Dock** | 收藏常用应用，长按可更换或清除。 |
| **文件夹** | 创建文件夹、移入移出应用、重命名、排序。 |
| **内存自动清理** | 回到启动器时可选杀死白名单外的后台应用（可保护 lemon-TV 等关键应用）。 |
| **外观与语言** | 每行应用数、24 小时制、极简状态栏、隐藏标题、语言切换（跟随系统 / 中文 / English）。 |
| **文件传输助手** | 内置简易 Web 服务 + 二维码，方便手机向电视盒子传文件（assets/wap）。 |
| **权限管理中心** | 一键跳转系统设置中的悬浮窗、电池优化、存储、通知等权限页面。 |

### 使用环境要求

**运行环境（设备）**

- Android **9.0（API 28）** 及以上（minSdk 28，targetSdk 34）
- 推荐 Android TV / Google TV / Fire OS 或支持 Leanback 的电视盒子
- 横屏使用体验最佳
- 直播预览功能需安装 **[lemon-TV](https://github.com/1921865594/Lemon-IPTV-v2)**（包名 `com.lemoniptv.lite`），并暴露 PreviewService
- 高级自由窗口 / 隐藏 API 调整才需要 root（见 `tools/app_container_setup.sh`），**普通使用不需要 root**

**推荐搭配**

- [lemon-TV](https://github.com/1921865594/Lemon-IPTV-v2) —— 提供嵌入式预览 Surface 的 IPTV 播放器

### 项目开发环境要求

```bash
# 克隆仓库
git clone https://github.com/<your-username>/TV-LemonLnch.git
cd TV-LemonLnch

# 环境要求
# - JDK 17（推荐）或 JDK 11+
# - Android SDK（compileSdk 34）
# - Android Studio Hedgehog / Iguana 或更新版本（也可纯命令行）

# 编译 Debug APK
./gradlew assembleDebug

# 编译 Release APK（未配置正式签名时自动使用 debug 签名）
./gradlew assembleRelease
```

**可选正式签名**（写入 `gradle.properties` 或命令行传入）：

```properties
LEMONLNCH_STORE_FILE=/path/to/keystore.jks
LEMONLNCH_STORE_PASSWORD=...
LEMONLNCH_KEY_ALIAS=...
LEMONLNCH_KEY_PASSWORD=...
```

**项目结构（简化）**

```
TV-LemonLnch/
├── app/
│   ├── src/main/
│   │   ├── java/com/LemonLnch/
│   │   │   ├── ui/          # MainActivity、HomeUIBuilder、PreviewCardController、AppShortcutManager...
│   │   │   ├── service/     # BootReceiver、KeepAliveService、HomeAccessibilityService
│   │   │   └── system/      # RootShell 辅助
│   │   ├── java/com/tv/player/  # 手写 Binder 契约（IPlayerService / IPlayerCallback）
│   │   ├── res/             # 代码构建 UI、drawable、values + values-zh-rCN
│   │   └── assets/wap/      # 简易网页文件传输界面
│   └── build.gradle
├── tools/app_container_setup.sh
└── README.md
```

### 技术原理（直播预览）

1. 启动器在首页预览卡片中创建 `SurfaceView`。
2. 绑定远程服务：  
   包名 `com.lemoniptv.lite` 中的 `top.yogiczy.lemonlnch.preview.PreviewService`。
3. 双方使用完全一致的 **手写 Binder 接口**（`com.tv.player.IPlayerService` / `IPlayerCallback`）通信。
4. 启动器调用 `setSurface(surface)` → `startPreview(url)` / `resumePreview()` / `pausePreview()`。
5. lemon-TV 将流直接渲染到共享 Surface 上，启动器**不会**把第三方 Activity 拉到自由窗口。
6. 健康检查 + 指数退避重试 + 恢复防抖，保证在焦点切换、前后台、服务死亡等场景下预览依然稳定。

该方案实现了真正的 **卡片内无缝播放** 与一键进入全屏的流畅体验。

### 使用建议（老年人 / 便携场景）

1. 安装柠檬桌面 + lemon-TV。
2. 将柠檬桌面设为默认桌面（受限 Google TV 设备可开启无障碍后备服务）。
3. 长按直播预览卡片 → 配置 IPTV 直播地址。
4. 进入设置 → **应用直达** → 把数字键 1–9（以及可选自定义键）绑定到用户真正需要的应用（例如 1=视频、2=音乐、3=设置）。
5. 开启「开机/更新后启动」和「内存自动清理」，并把 lemon-TV 加入白名单。
6. 完成。遥控器数字键变成一键启动，电视永远回到带直播预览的熟悉桌面。

### 赞赏
![赞赏](https://github.com/1921865594/TV-LemonLnch/blob/main/mmexport1733804547365.png)

### 免责声明

- 本项目基于 **Apache License 2.0** 开源，按「原样」提供。
- 这是社区开源启动器。作者与贡献者对因使用本软件或第三方 IPTV 流导致的任何数据丢失、设备不稳定、耗电增加或法律问题 **不承担任何责任**。
- 直播内容来自用户自行配置的 lemon-TV / IPTV 源。**请确保您在所在地区拥有合法收看权限**，相关法律责任由用户自行承担。
- 无障碍服务仅作为可选后备方案，用于返回启动器，**不会**读取屏幕内容、模拟点击或收集个人数据。
- Root / 自由窗口脚本仅为高级可选工具，正常使用 **不需要 root**。
- 使用本软件即表示您理解并接受上述风险，请自行备份重要数据。

### 许可证

```
Copyright 2024-2026 LemonLnch contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

完整许可证见 [LICENSE](LICENSE) 文件。

### 相关项目

- [lemon-TV](https://github.com/1921865594/Lemon-IPTV-v2) —— 配套的 IPTV 播放器，提供嵌入式预览服务。

---

<p align="center">
  <a href="#english">English</a> · <a href="#中文">中文</a>
</p>
