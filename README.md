NCM2FLAC-Converter
安卓NCM文件转FLAC转换器，纯Termux编译，适配Android15+，修复转换后FLAC播放失败问题。

---

功能特性
- 自动扫描：自动扫描网易云音乐默认下载目录（/Download/netease/cloudmusic/Music/）中的NCM文件
- 手动转换：支持手动选择任意目录的NCM文件进行转换
- 权限适配：针对Android 15+系统，强制要求并引导用户开启“所有文件访问权限”
- 核心修复：修复NCM解密头偏移问题，确保转换后的FLAC文件可正常播放
- 纯手机操作：全程在Termux中完成编译、打包和使用，无需电脑

---

环境要求
- 一部已安装Termux的Android手机
- Termux已安装以下工具：
  pkg update && pkg upgrade -y
  pkg install -y wget unzip zip openjdk-17 git coreutils aapt2 ecj dx apksigner
- 已执行 termux-setup-storage 并授权存储权限

---

快速开始

1. 克隆项目（或使用你已有的本地项目）
   cd ~
   git clone https://github.com/Voidlink132/NCM2FLAC-Converter.git
   cd NCM2FLAC-Converter

2. 下载Android核心库（编译必备）
   wget -O android.jar https://github.com/AndroidIDEOfficial/android-jar/raw/master/android-35.jar

3. 一键编译打包APK
   # 清空旧编译缓存
   rm -rf build compiled_res classes.dex app-unsigned.apk app-signed.apk

   # 1. 编译资源文件
   aapt2 compile --dir res -o compiled_res

   # 2. 链接资源和清单，生成未签名APK
   aapt2 link -o app-unsigned.apk -I android.jar \
       --manifest AndroidManifest.xml \
       compiled_res/*.flat

   # 3. 编译Java代码（强制Java1.7兼容）
   ecj -source 1.7 -target 1.7 -cp android.jar -d build src/com/ncmconverter/app/MainActivity.java

   # 4. 转换class为dex文件
   dx --dex --output=classes.dex build/

   # 5. 将dex文件加入APK
   zip -u app-unsigned.apk classes.dex

   # 6. 签名APK（使用调试密钥）
   apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android --key-pass pass:android --out app-signed.apk app-unsigned.apk

   # 7. 复制签名后的APK到手机Download目录
   cp app-signed.apk ~/storage/downloads/NCM2FLAC转换器.apk

---

APK运行说明（手机端操作）

1. 从GitHub Release下载APK
   1. 打开手机浏览器，访问项目GitHub主页：https://github.com/Voidlink132/NCM2FLAC-Converter
   2. 点击页面右侧的 Releases 标签，进入发布页面。
   3. 在最新的Release版本中，找到并下载 NCM2FLAC转换器.apk 文件。
   4. 下载完成后，APK文件会保存在手机的 Download（下载）目录中。

2. 安装APK
   1. 打开手机自带的文件管理器，进入 Download（下载）目录。
   2. 找到刚下载的 NCM2FLAC转换器.apk 文件，点击它。
   3. 如果系统提示“未知来源应用”，请选择允许或“继续安装”（不同手机路径：设置 → 安全 → 更多安全设置 → 安装未知应用，允许文件管理器安装）。
   4. 等待安装完成，点击“打开”启动APP。

3. 首次启动配置（必做）
   1. APP启动后，会弹出“需要所有文件访问权限”的提示框，点击去设置。
   2. 在系统设置页面，找到本应用（NCM2FLAC转换器），开启所有文件访问权限（或“管理所有文件”）。
   3. 返回APP，此时会自动扫描网易云音乐的NCM文件。

4. 使用APP转换文件
   - 自动转换：
     1. 在“自动”页面，APP会列出所有扫描到的NCM文件。
     2. 点击任意一个文件名，APP会在后台自动开始转换。
     3. 转换完成后，会弹出“转换成功”的提示。
   - 手动转换：
     1. 切换到“手动”页面。
     2. 点击“选择文件”，从手机中选择任意NCM文件。
     3. 确认输出路径（默认是 /NCM2FLAC/），点击“开始转换”。

5. 找到转换后的文件
   - 转换后的FLAC文件默认保存在手机的 /NCM2FLAC/ 目录下。
   - 你可以在文件管理器中直接找到并播放，也可以在音乐APP中扫描本地音乐添加到播放列表。

---

项目结构
NCM2FLAC-Converter/
├── AndroidManifest.xml  # APP权限和配置清单
├── android.jar          # Android 35 SDK核心库
├── src/                 # Java源码目录
│   └── com/ncmconverter/app/
│       └── MainActivity.java  # 核心功能代码
├── res/                 # 安卓资源目录
│   └── layout/          # 布局文件
│   └── values/          # 字符串资源
├── compiled_res/        # 编译后的资源缓存（自动生成）
├── build/               # 编译后的class文件（自动生成）
├── app-unsigned.apk     # 未签名APK（打包中间文件）
├── app-signed.apk       # 签名后的可安装APK（最终文件）
├── classes.dex          # 安卓可执行dex文件（自动生成）
└── README.md            # 项目说明文档

---

常见问题排查

1. 转换后的FLAC文件无法播放
   - 确保APP已获得“所有文件访问权限”
   - 确保NCM文件完整，未在下载过程中损坏
   - 尝试使用VLC、Poweramp等第三方播放器播放
   - 检查文件大小是否为0字节，若为0则说明转换失败

2. 找不到NCM文件
   - 检查网易云音乐的下载目录是否为 /Download/netease/cloudmusic/Music/
   - 确保已开启“所有文件访问权限”
   - 尝试手动选择文件进行转换，验证文件是否存在

3. APK安装失败
   - 在手机设置中开启“允许来自此来源的应用”
   - 确保APK文件完整，未在传输过程中损坏
   - 尝试重新编译打包APK

4. Termux编译报错
   - 确保已安装所有必要的工具包
   - 检查项目目录是否正确，是否存在 AndroidManifest.xml 和 android.jar
   - 尝试清空编译缓存后重新编译

---

许可证
本项目仅供学习和个人使用，请勿用于商业用途。使用本项目产生的任何法律责任由使用者自行承担。

---

贡献
欢迎提交Issue和Pull Request来改进本项目。如果你有任何问题或建议，也可以通过GitHub Issues与我联系。

---

注意事项
- 本项目仅支持NCM格式文件的转换，不支持其他加密音频格式
- 转换后的FLAC文件仅可用于个人学习和欣赏，请勿用于商业用途
- 请遵守相关法律法规，尊重音乐版权

