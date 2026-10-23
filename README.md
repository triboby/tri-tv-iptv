# Tri TV

一个用于播放 IPTV 的 Android TV 应用（Kotlin + Jetpack Compose for TV + Media3 ExoPlayer）。

## 功能（v1）

- 从 URL 导入 M3U / M3U8 播放列表（支持 `tvg-id`、`tvg-name`、`tvg-logo`、`group-title`、`#EXTGRP`、`#EXTVLCOPT` 的 UA / Referer）
- 频道列表，记住上次的播放列表地址和频道
- 全屏播放，支持 HLS、DASH 和普通 HTTP 流；没有扩展名的地址播放失败时会自动按 HLS 重试
- 遥控器：上 / 下（或频道 +/-）换台，OK 显示 / 隐藏台标信息，返回键回到列表
- 允许明文 http，方便播放局域网内的源

## 构建

需要 JDK 17+ 和 Android SDK（compileSdk 35）。

```bash
./gradlew assembleDebug        # 输出 app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # M3U 解析单元测试
```

每次推送都会由 GitHub Actions 构建，Debug APK 可以在 Actions 运行页面的 Artifacts 里下载。

## 安装到电视

```bash
adb connect <电视IP>
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
