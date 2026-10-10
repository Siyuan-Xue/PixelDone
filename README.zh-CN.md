<!-- 术语表（修改中文版前请先对照）：todo/task=待办；checklist=清单；priority=优先级（Low/Mid/High/XHigh 保持英文）；DDL=截止时间（DDL）；reminder=提醒；Trash=回收站；widget=桌面小组件；dock=底部 Dock；local-first=本地优先；sync=同步；Realtime 保持英文；three-way merge=三方合并；conflict review=冲突审阅；tombstone=删除标记（tombstone）；release=发布版；APK、Supabase、Room、WorkManager、Jetpack Compose 等名称不翻译。人称统一用“你”，引号统一用「」。 -->

<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" alt="PixelDone 应用图标" width="128" height="128">

# PixelDone

### 像素风格的 Android 待办清单：离线可用，登录后多端实时同步

<a href="./README.md">English</a> · <b>简体中文</b>

[![Latest release](https://img.shields.io/github/v/release/Siyuan-Xue/PixelDone?label=release&logo=github)](https://github.com/Siyuan-Xue/PixelDone/releases/latest)
[![CI](https://github.com/Siyuan-Xue/PixelDone/actions/workflows/ci.yml/badge.svg)](https://github.com/Siyuan-Xue/PixelDone/actions/workflows/ci.yml)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B%20%28API%2026%29-3DDC84?logo=android&logoColor=white)
![Kotlin · Jetpack Compose](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

> 本文档与英文版同步更新；如有出入，以 [English](./README.md) 版本为准。

<div align="center">

<img src="app/src/main/res/drawable-nodpi/pixeldone_widget_preview_image.png" alt="桌面小组件预览" width="24%">
<img src="docs/screenshots/android-settings-arabic.png" alt="阿拉伯语从右到左布局的设置页" width="24%">
<!-- 截图位：docs/screenshots/android-main-list.png（有几条任务的主清单，竖屏 PNG，约 1080 × 2400）。添加方式：<img src="docs/screenshots/android-main-list.png" alt="主清单" width="24%"> -->
<!-- 截图位：docs/screenshots/android-task-editor.png（打开底部任务编辑器，竖屏 PNG，约 1080 × 2400）。添加方式：<img src="docs/screenshots/android-task-editor.png" alt="任务编辑器" width="24%"> -->

</div>

## ✨ 亮点

- 📋 **清单轻快**：多清单、四档优先级（Low / Mid / High / XHigh）、截止时间（DDL），可按优先级或时间排序，也能切换成 DDL 倒计时视图。
- ⏰ **提醒可靠**：获得授权时使用精确闹钟，否则回退到兼容 Doze 的方案；支持每日、每周重复，补回错过的周期，XHigh 任务还有全屏闹钟。
- 📴 **本地优先**：基于 Room 离线可用；删除的任务在回收站保留 30 天，到期后才彻底清除。
- 🔄 **多端实时同步**：可选 Supabase 登录，事务化同步加 Realtime 推送；字段级三方合并，冲突一律进入审阅，不会悄悄丢数据。
- 🧩 **小组件与自定义 Dock**：在桌面直接勾选完成；`+` 按钮两侧最多放四个快捷功能，自由排序。
- 🌍 **七种语言**：跟随系统，或选择联合国六种官方语言（阿拉伯语完整支持从右到左布局），并支持浅色与深色主题。
- 🔐 **应用内更新有校验**：优先从 GitHub Releases 获取，Gitee 镜像兜底，安装前核对 SHA-256 和签名证书。

<details>
<summary><b>完整功能列表</b></summary>

**待办与清单**

- 新建待办时可设名称、四档优先级（Low、Mid、High、XHigh）和日期时间；优先级颜色依次为绿、蓝、黄、红。
- 支持多个本地清单，默认从 `MAIN` 开始；另有固定的 `TRASH`（回收站）和 `SETTINGS`（设置）。
- 展开顶栏即可切换清单、修改清单名；系统返回手势会沿本次会话的清单浏览记录逐级返回。
- 长按 `+` 新建清单，短按新建任务。
- 点按任意一行，在底部编辑器中修改，也可以直接删除。
- 按优先级（XHigh → Low，再按截止时间）或按时间排序；副标题可切换为 DDL 倒计时（天、时、分）。
- 已过期的日期标红；恰好到期的待办也算作过期。
- 点复选框即可完成或重新激活；连续快速勾选时排序会稍作延迟，列表不会乱跳。
- 新建、编辑、重新激活或更新图片的待办会自动滚动到可见处并短暂高亮；已完成的待办可以隐藏。

**回收站**

- 删除的任务、批量清理的已完成任务以及被删清单中的任务，都会移入 `TRASH`。
- 恢复时保留完成状态，原清单不存在时会自动重建。
- 可恢复的条目恰好保留 30 天，之后只留下最小化的删除标记（tombstone），确保删除操作仍能同步到其他设备。

**提醒**

- 每个未完成的待办对应一条本地闹钟通知；有授权时用精确闹钟，否则回退到兼容 Doze 的非精确闹钟。
- 每个待办可设为不重复、每日或每周重复；只补回最近一次错过的提醒，通知送达成功后才推进下一次 DDL 并同步。
- XHigh 闹钟：锁屏上直接显示全屏控制（不降低锁屏安全性），STOP/SNOOZE 响应及时，返回键即贪睡 10 分钟，最长响铃 10 分钟，权限不足时安全降级。

**图片**

- 每个待办可通过系统选择器附加、预览、替换或移除一张 JPEG/PNG/WebP 图片（≤ 10 MiB），无需申请完整相册权限。
- 大尺寸相机照片也能安全生成预览，原图保持不变，并通过私有 Supabase Storage 同步。

**云同步**

- 在设置的 `CLOUD` 区域注册、登录、退出、修改密码（先验证当前密码，成功后全局注销所有会话），也可手动同步。
- 同步由事件触发，经防抖后执行一次性 WorkManager 任务，不注册周期任务；同步元数据存放在独立、可重建的 Room 数据库中。

**Dock、设置与更新**

- Dock：设置 `+` 的位置并实时预览，最多选择四个功能（`SORT`、`DDL`、`HIDE DONE`、`CLEAN DONE`、`QUICK DELETE`）并调整顺序；默认是 `SORT`、`+`、`DDL`。
- 设置：语言、浅色/深色模式、Dock、云同步（含待同步数和冲突数）、更新提示、权限和版本信息。
- 更新：每次启动静默检查；只下载与当前渠道匹配的最新 APK，显示校验进度，并通过 PackageInstaller 会话安装。

</details>

## 📱 截图

| 桌面小组件 | 设置页（阿拉伯语，从右到左） | Windows 客户端 |
|:---:|:---:|:---:|
| <img src="app/src/main/res/drawable-nodpi/pixeldone_widget_preview_image.png" alt="桌面小组件预览" width="240"> | <img src="docs/screenshots/android-settings-arabic.png" alt="阿拉伯语设置页" width="200"> | <img src="docs/screenshots/windows-main.png" alt="PixelDone Windows 版主窗口" width="320"> |

<!-- 截图位：docs/screenshots/android-main-light.png 与 docs/screenshots/android-main-dark.png（同一清单的浅色、深色主题，竖屏 PNG，约 1080 × 2400，显示宽度 200） -->
<!-- GIF 位：docs/media/demo.gif（新建任务、提醒响起、在小组件上勾选完成；宽约 360 px，10～20 秒，小于 10 MB） -->

## 📥 下载

| 平台 | 最新版本 | 说明 |
|---|---|---|
| **Android 8.0+** | [PixelDone-3.3.6-release.apk](https://github.com/Siyuan-Xue/PixelDone/releases/download/v3.3.6/PixelDone-3.3.6-release.apk)（[SHA-256](https://github.com/Siyuan-Xue/PixelDone/releases/download/v3.3.6/PixelDone-3.3.6-release.apk.sha256)） | [全部版本](https://github.com/Siyuan-Xue/PixelDone/releases) · [Gitee 镜像](https://gitee.com/milesxue/PixelDone/releases)（适合国内网络） |
| **Windows 11（x64）** | [v3.3.1 安装包](https://github.com/Siyuan-Xue/PixelDone-windows/releases/tag/v3.3.1) | [PixelDone-windows](https://github.com/Siyuan-Xue/PixelDone-windows)：Tauri 2 + Rust + Svelte |
| **Ubuntu 26.04（amd64），测试版** | [v4.0.0-beta.1 .deb](https://github.com/Siyuan-Xue/PixelDone-Linux/releases/tag/v4.0.0-beta.1) | [PixelDone-Linux](https://github.com/Siyuan-Xue/PixelDone-Linux)：Rust + GTK 4 / libadwaita |

PixelDone 以单个已签名 APK 发布，没有上架 Google Play，安装时 Android 会请你允许浏览器或文件管理器安装应用。三个客户端共用同一套同步后端，一个账号即可多端使用。

## 🔄 同步机制

```mermaid
flowchart LR
  A["手机<br/>Room（本地优先）"] -- "事务化推送 / 拉取" --> S[("Supabase<br/>Auth · PostgREST RPC · Storage")]
  S -- "Realtime 变更事件" --> B["其他设备<br/>（Android / Windows / Linux）"]
  B -- "事务化推送 / 拉取" --> S
  S -- "Realtime 变更事件" --> A
  A --> M{"两端改了<br/>同一字段？"}
  M -- "否" --> AM["三方合并<br/>两边修改都保留"]
  M -- "是" --> CR["冲突审阅<br/>（解决前一直保留）"]
```

- 每次修改都先写入本地。同步由事件驱动：本地修改或 Realtime 事件会触发一次防抖后的事务化拉取/推送，走带版本号的 3.2 RPC。
- 不同字段的修改自动合并；同一字段的修改记为冲突，在你处理之前一直保留在列表里。删除以删除标记（tombstone）的形式同步，优先于过时的编辑。
- 图片通过私有 Storage 单独同步，打开时才下载，上传再慢也不会拖住文字内容的同步。

<details>
<summary><b>代码结构</b></summary>

- `domain/todo/`：待办、清单、排序和提醒规则，纯 Kotlin 实现，不依赖 Android 或 Compose。
- `data/local/`：本地优先的 Room 数据库，以及按代际管理、可独立重建的同步元数据库。
- `data/sync/`、`domain/sync/`：Supabase Auth、3.2 事务 RPC、Storage 附件、Realtime、字段合并、变更 UUID、游标和删除标记。
- `data/image/`：私有图片复制、签名与哈希校验、按需缓存和预览采样。
- `data/update/`：版本检查、APK 校验和与签名验证、DownloadManager 和 PackageInstaller。
- `reminder/`：AlarmManager、通知、开机广播、前台服务和 XHigh 全屏闹钟。
- `ui/todo/`、`ui/theme/`：Compose 界面、Dock、像素风组件和主题；`di/`：手动依赖注入。

</details>

## 🧰 技术栈

| 层级 | 技术 |
|---|---|
| 界面 | Kotlin · Jetpack Compose · Material 3 · Glance（小组件） |
| 本地数据 | Room · DataStore · Android Keystore（会话存储） |
| 后台任务 | WorkManager（事件触发的一次性任务） · AlarmManager · 前台服务 |
| 云端 | Supabase Auth · PostgREST RPC · Realtime 3.6.0（Ktor CIO WebSockets） · Storage |
| 交付 | GitHub Actions CI 与发布流程 · GitHub Releases + Gitee 镜像 |

包名 `com.milesxue.pixeldone` · minSdk 26 · target SDK 37 · 当前版本 3.3.6（versionCode 96）。

## 🛠️ 从源码构建

准备好 JDK 和 Android SDK。

```sh
git clone https://github.com/Siyuan-Xue/PixelDone.git
cd PixelDone
./gradlew testDebugUnitTest lintDebug assembleDebug   # Windows: .\gradlew.bat …
```

云同步是可选的。如需开启，把公开的客户端配置写进不纳入 Git 的 `local.properties`，或通过 Gradle 属性、环境变量传入：

```properties
pixeldone.supabaseUrl=YOUR_SUPABASE_URL
pixeldone.supabasePublishableKey=YOUR_SUPABASE_PUBLISHABLE_OR_ANON_KEY
pixeldone.requireCloudConfig=true
```

客户端里只能放 publishable（anon）key，千万不要放 service-role 凭据。后端配置见 [`docs/pixeldone-supabase-sync-setup.md`](docs/pixeldone-supabase-sync-setup.md)。构建 release 版需要在不纳入 Git 的 `signing/release-signing.properties` 中配置签名。推送 `vX.Y.Z` 标签会触发 [`release-android.yml`](.github/workflows/release-android.yml)：只构建并签名一次，同时发布到 GitHub 和 Gitee 镜像。参与开发前请先阅读 [`AGENTS.md`](AGENTS.md) 和 [`PROJECT_SPEC.md`](PROJECT_SPEC.md)。

## 🗺️ 路线图

- [x] Android 3.3.x：周期提醒更可靠，同步协议 3.2
- [x] Windows 客户端（3.3.x）与 Linux 测试版（4.0.0-beta.1）
- [ ] TODO(Miles)：后续计划，仅在你愿意公开时填写（例如上架 Play 商店）

## 📄 许可证

以 [MIT 许可证](LICENSE) 发布。内置字体仍遵循各自的 SIL Open Font License 1.1（见下方致谢）。

### 致谢

由 Miles Xue（「CODEX & XUE」）设计和开发。内置字体（Source Sans 3、Source Serif 4、Noto Sans SC、Noto Serif SC、Noto Sans Arabic、Noto Naskh Arabic）遵循 SIL Open Font License 1.1，详见 [`FONT_ATTRIBUTIONS.md`](FONT_ATTRIBUTIONS.md) 和 [`licenses/fonts/OFL-1.1.txt`](licenses/fonts/OFL-1.1.txt)。
