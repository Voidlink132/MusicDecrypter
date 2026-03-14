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
   构建完成后，在任务详情页的 `Artifacts` 中下载编译好的Release APK和Debug APK

### 方式二：本地手动编译
1. 克隆本仓库到本地
```
git clone https://github.com/Voidlink132/NCM2FLAC-Converter.git
cd NCM2FLAC-Converter
```
2. 连接Android SDK，或在Android Studio中打开本项目
3. 执行编译命令（Windows系统使用gradlew.bat替代./gradlew）
# 编译Release安装包
```
./gradlew assembleRelease
```
# 编译Debug安装包
```
./gradlew assembleDebug
```
4. 编译完成后，APK文件输出路径：

• Release APK：app/build/outputs/apk/release/

• Debug APK：app/build/outputs/apk/debug/

## 📱 APK安装与使用说明

### 1. 安装APK
1. 下载编译好的APK文件，传输到安卓手机中
2. 打开手机自带的文件管理器，找到APK文件并点击
3. 若系统提示“未知来源应用安装”，请在设置中允许对应文件管理器/浏览器的安装权限
4. 等待安装完成，点击「打开」启动APP

### 2. 首次启动配置（必做）

1. APP启动后，会弹出“需要所有文件访问权限”的提示框，点击「去设置」
2. 在系统设置页面，找到本应用（NCM2FLAC转换器），开启「所有文件访问权限」（或“管理所有文件”权限）
3. 返回APP，此时会自动扫描网易云音乐下载目录中的NCM文件
### 3. 转换文件使用方法
# 自动转换
1. 在「自动」页面，APP会列出所有扫描到的NCM文件
2. 点击任意一个文件名，APP会在后台自动开始转换
3. 转换完成后，会弹出“转换成功”的提示
# 手动转换
1. 切换到「手动」页面
2. 点击「选择文件」，从手机中选择任意NCM文件
3. 确认输出路径（默认是 /NCM2FLAC/ 目录），点击「开始转换」
4. 找到转换后的文件
转换完成的FLAC文件默认保存在手机根目录的 /NCM2FLAC/ 目录下，你可以在文件管理器中直接找到并播放，也可以在音乐APP中扫描本地音乐添加到播放列表。

## 📁 项目结构
```
NCM2FLAC-Converter/
├── .github/workflows/build-apk.yml  # GitHub Actions自动构建配置
├── app/                              # 应用主模块
│   └── src/main/
│       ├── AndroidManifest.xml       # 应用权限与核心配置清单
│       ├── java/com/ncmconverter/app/  # 核心功能源码
│       └── res/                      # 应用资源文件（图标、布局、字符串等）
│   └── build.gradle                  # 模块级构建配置
├── build.gradle                      # 项目级构建配置
├── gradle.properties                 # Gradle全局属性配置
├── settings.gradle                   # 项目模块设置
├── gradle/                           # Gradle包装器配置
├── gradlew / gradlew.bat             # Gradle跨平台执行脚本
└── README.md                         # 项目说明文档
```

## ❓ 常见问题排查

### 1. 转换后的FLAC文件无法播放
• 确保APP已获得「所有文件访问权限」
• 确保NCM文件完整，未在下载过程中损坏
• 尝试使用VLC、Poweramp等第三方播放器播放
• 检查转换后的文件大小是否为0字节，若为0则说明源文件读取失败，请重新授权权限

### 2. 扫描不到NCM文件
• 检查网易云音乐的下载目录是否为 /Download/netease/cloudmusic/Music/
• 确保已开启「所有文件访问权限」
• 尝试使用手动选择文件功能，验证文件是否存在

### 3. APK安装失败
• 在手机设置中开启对应来源的「未知应用安装权限」
• 确保APK文件完整，未在传输过程中损坏
• 确认手机系统版本满足Android 5.0+的最低要求

### 4. 项目编译失败
• 请联系项目维护者,邮箱：jiangzhe099222@163.com

## 📝 许可证

本项目仅供学习和个人使用，请勿用于商业用途。使用本项目产生的任何法律责任由使用者自行承担。

## 🤝 贡献

欢迎提交Issue和Pull Request来改进本项目。如果你有任何问题或建议，也可以通过GitHub Issues与我联系。

## 📌 注意事项

• 本项目仅支持NCM格式文件的转换，不支持其他加密音频格式
• 转换后的FLAC文件仅可用于个人学习和欣赏，请勿用于商业用途
• 请遵守相关法律法规，尊重音乐版权
