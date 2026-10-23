# Tri TV

一个用于播放 IPTV 的 Android TV 应用（Kotlin + Jetpack Compose for TV + Media3 ExoPlayer）。

## 功能（v1）

- 从 URL 导入 M3U / M3U8 播放列表（支持 `tvg-id`、`tvg-name`、`tvg-logo`、`group-title`、`#EXTGRP`、`#EXTVLCOPT` 的 UA / Referer）
- 同一分组里同名（或同 `tvg-id`）的条目合并成一个频道，多个地址作为备用线路；自动隐藏聚合源里名字是时间戳的「更新时间」条目
- 按分组筛选频道列表，记住上次的播放列表地址和频道
- 全屏播放，支持 HLS、DASH 和普通 HTTP 流；.php、.ctv 或没有扩展名的地址先按 HLS 播放，失败再自动识别格式
- 当前线路播放失败时自动切换到下一条线路
- 遥控器：上 / 下（或频道 +/-）在当前分组内换台，左 / 右换线路，OK 显示 / 隐藏信息，返回键回到列表
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
