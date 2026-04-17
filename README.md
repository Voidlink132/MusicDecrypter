# 🎵 MusicDecrypter - 安卓音乐自动解密 & 歌词匹配工具

[![Android Build](https://github.com/qhccpa/MusicDecrypter/actions/workflows/android.yml/badge.svg)](https://github.com/qhccpa/MusicDecrypter/actions)
[![Version](https://img.shields.io/badge/Version-1.1-blue.svg)](https://github.com/qhccpa/MusicDecrypter/releases)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**MusicDecrypter** 是一款基于纯 Java 构建的安卓端音乐自动解密工具。它巧妙地集成了 `unlock-music` 的在线解密能力，实现了主流音乐平台加密格式的**一键自动化脱壳解密**。不仅如此，它还支持**歌词自动匹配下载**，为您提供更完整的音乐体验。

---

## ✨ 功能特性

- 🚀 **一键全自动解密**：原生文件选择界面，自动上传、解密、提取、保存，无需在 WebView 中手动点击，真正实现「选完即得」。
- 📝 **智能歌词匹配**：(New!) 支持从网易云音乐自动搜索并下载匹配的歌词文件（LRC/SRT），支持双语翻译、合并/交错显示。
- 📂 **智能文件管理**：解密后自动保存至 `Music/MusicDecrypter/` 目录，并自动触发媒体扫描，播放器立即识别。
- 🔍 **便捷文件查找**：内置专用视图，快速定位网易云音乐、QQ音乐、酷狗等平台的加密文件存储路径。
- 🛡️ **全面适配**：完美适配 Android 7.0 至 Android 15，支持分区存储及「所有文件访问权限」。
- 🤖 **GitHub Action 自动构建**：每次提交自动生成 APK，随时获取最新版本。

## 📥 下载安装

1. **GitHub Releases**: 前往 [Releases](https://github.com/qhccpa/MusicDecrypter/releases) 页面下载最新的 APK。
2. **Artifacts**: 您也可以在项目的 `Actions` 页面，从最新的成功构建记录中下载二进制文件。

## 🛠️ 使用说明

1. **授予权限**：首次启动请进入「设置」，授予「所有文件访问权限」，这是读写加密文件及保存结果所必须的。
2. **配置歌词 (可选)**：在设置中开启「自动下载歌词」，并根据喜好配置翻译格式（如：合并显示、交错显示）及编码（UTF-8/UTF-16）。
3. **开始解密**：
   - 在「查找」页面找到目标文件。
   - 或在「解密」页面点击按钮选择文件。
4. **结果查看**：解密完成后，文件将出现在系统的音乐库中，或在文件管理器的 `Music/MusicDecrypter/` 目录下找到。

## 🎼 支持格式

- **网易云音乐**: `.ncm`
- **QQ 音乐**: `.qmc`, `.mgg`, `.mflac`, `.tkm`
- **酷狗音乐**: `.kgm`, `.kgma`
- **酷我音乐**: `.kwm`
- （待开发）**其他平台**: `.m4a`, `.ogg`, `.wma` 等各种变体加密格式

## 🏗️ 开发者相关

### 技术栈
- **语言**: Java
- **UI**: Material Components, ViewPager2, BottomNavigationView
- **核心**: WebViewBridge + Javascript Injection

### 构建
```bash
# 克隆项目
git clone https://github.com/qhccpa/MusicDecrypter.git
# 使用 Gradle 构建
./gradlew assembleRelease
```

## ⚠️ 免责声明

1. 本项目仅供学习交流 Android 开发技术及加密原理使用。
2. 解密后的音频文件版权归原平台及唱片公司所有，请勿用于商业用途、二次传播或任何侵权行为。
3. 用户使用本工具所产生的一切法律后果由使用者本人承担。

## 📄 开源协议

基于 [MIT License](LICENSE) 开源。
