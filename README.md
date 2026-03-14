# NCM2FLAC-Converter
安卓NCM文件转FLAC转换器，适配Android 5.0~Android 15+，修复转换后FLAC播放失败问题。

## ✨ 功能特性
- 自动扫描：自动扫描网易云音乐默认下载目录（`/Download/netease/cloudmusic/Music/`）中的NCM文件
- 手动转换：支持手动选择任意目录的NCM文件进行转换
- 权限适配：针对Android 13+系统，适配新的媒体权限规则，针对Android 15+强制要求并引导用户开启“所有文件访问权限”
- 核心修复：修复NCM解密头偏移问题，确保转换后的FLAC文件可正常播放
- 后台转换：支持后台前台服务转换，避免大文件转换过程中被系统杀死

## 📋 环境要求
### 本地编译环境
- JDK 17+（对应Android Gradle Plugin 8.2.0要求）
- Android Studio Hedgehog 及以上版本，或已配置Android SDK环境
- 项目已配置compileSdk 34、minSdk 21、targetSdk 35

### 运行环境
- Android 5.0 及以上版本的安卓手机/平板

## 🚀 快速开始
### 方式一：GitHub Actions 自动构建（推荐）
1. Fork 本仓库到你的GitHub账号
2. 提交代码到main分支，会自动触发`build-apk.yml`工作流
3. 进入仓库的 `Actions` 页面，找到对应的构建任务
4. 构建完成后，在任务详情页的 `Artifacts` 中下载编译好的Release APK和Debug APK

### 方式二：本地手动编译
1. 克隆本仓库到本地
```bash
git clone https://github.com/Voidlink132/NCM2FLAC-Converter.git
cd NCM2FLAC-Converter
