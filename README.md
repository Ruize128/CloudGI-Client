# CloudGI Client

CloudGI 的 KMP / Compose 起始项目。Android 与桌面入口共用一个页面，
仅展示名称、版本和运行环境。桌面入口便于在 MacBook 上直接验证共享界面。

## 环境

- JDK 21；Android 编译需要 Android SDK Platform 36。
- Kotlin 2.4.0、Compose Multiplatform 1.10.3、AGP 9.1.0。
- 随仓库提供 Gradle 9.3.1 Wrapper，无需另装 Gradle。

Android SDK 路径通过 `ANDROID_HOME` 或本机 `local.properties` 指定。
例如 macOS 的 `local.properties` 内容为 `sdk.dir=/Users/你的用户名/Library/Android/sdk`。
该文件不纳入 Git。Windows 使用自己的 SDK 路径。

## 编译与运行

在仓库根目录运行桌面页面：

```sh
./gradlew :composeApp:run
```

编译 Android 调试安装包：

```sh
./gradlew :androidApp:assembleDebug
```

输出为 `androidApp/build/outputs/apk/debug/androidApp-debug.apk`。
连接 Android 设备或启动模拟器后，可运行 `./gradlew :androidApp:installDebug`，
再点击设备中的 CloudGI Client 图标；也可以在 Android Studio 中运行 `androidApp`。
Windows 将上述 `./gradlew` 换为 `gradlew.bat`。首次构建需联网下载依赖。

## 目录

- `composeApp/src/commonMain`：共享起始页面。
- `composeApp/src/desktopMain`：桌面窗口入口。
- `androidApp`：Android 应用入口。

当前未加入发现、配对、网络、Moonlight 或输入回传功能。
iOS 入口留待后续实现。本仓库仅在本地初始化，未配置远程仓库。
