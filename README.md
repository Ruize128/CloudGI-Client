# CloudGI Client

CloudGI 的 KMP / Compose 客户端，Android 集成固定版本 Moonlight Android v12.1。
主页点击“启动游戏”即可发现 CloudGI Host、自动配对、请求启动原神并进入显示器串流，无需安装官方 Moonlight 或手动输入 PIN。

## 当前行为

- 复用 Moonlight NSD 与 JmDNS 搜索 `_nvstream._tcp.local.`，通过 CloudGI HTTPS 接口核对主机身份与端口。
- 首次连接保存主机证书，后续核验；自动配对后使用设备私钥签名游戏控制请求。
- 游戏路径、参数、全屏与手机 UI 选项由 Host 保存，Client 不传可执行文件或命令。
- 等待 Host 确认实际游戏进程后打开原生 Desktop 串流；已有游戏直接复用，失败时不自动反复启动。
- 默认以 Moonlight 原生触点协议发送各 pointerId 的按下、移动、抬起及取消，不使用手指模拟鼠标。
- 三指不会触发旧的弹键盘手势；失焦、取消和断开时释放活动触点。不支持原生触控的 Host 会明确提示并停止连接。
- 退出串流、应用停止串流或连接丢失时通知 Host 关闭对应游戏；实际连接期间每两秒发送心跳，Host 超时后清理。

初始编码预设为 1280×720、60 FPS、10 Mbps，捕获整个显示器，保留主机的显示模式和游戏分辨率。
没有画质设置页。手机最多等待游戏启动约两分钟；Windows 安全提示按正常流程在电脑处理。

## 架构

- `composeApp/src/commonMain`：共享主页、连接状态和发现调试列表。
- `androidApp`：Android 入口、局域网发现、自动配对及签名控制。
- `moonlightAndroid`：固定 Moonlight 的 Android 库构建适配。
- `third-party/moonlight-android`：上游 Java/JNI、解码、音频、输入源码及本地改动说明。

原生串流 Activity 与 CloudGI 打包为同一 APK，使用 CloudGI 自己的应用 ID 和配对身份。
当前串流实现为 Android ARM64；桌面入口仅保留共享界面，iOS/macOS 媒体桥接尚待实现。

## 构建与安装

需要 JDK 21、Android SDK Platform 36、NDK `28.2.13676358`。
通过 `ANDROID_HOME` 或本机 `local.properties` 指定 Android SDK。首次构建需联网还原依赖。

```sh
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:installDebug

# 检查或运行桌面共享界面
./gradlew :composeApp:compileKotlinDesktop
./gradlew :composeApp:run
```

Windows 使用 `gradlew.bat`。APK 位于 `androidApp/build/outputs/apk/debug/androidApp-debug.apk`。
USB 安装需手机允许该电脑进行 USB 调试；普通使用只需安装 APK。

## 发现调试与会话

主页“启动游戏”下方显示本轮发现的服务，可手动刷新。
列表保留 NSD/JmDNS 来源、服务名、地址、Sunshine 端口和 CloudGI 探测结果，包括普通 Sunshine 和探测失败记录。
它用于区分发现、解析、接口连通性问题，不代表扫描了整个局域网的所有设备。

自动配对面向可信家庭局域网，不用于公网暴露；管理凭据保留在 Host。
两端时钟需同步，被禁用或移除的设备不能继续发起受保护的控制请求。
需要支持 `disconnectPolicy=close-game-lease-v1` 的 Host。正常关闭 Host 会清理活动游戏；强制结束 Host 无法保证清理。

## 阶段验收（2026-11-02）

用户已确认：手机自动连接后能进入游戏，使用手机 UI 并正常触控游玩。
已验证自动发现、无需手动 PIN 配对、H.264 硬件解码、原生多点触控、正常返回退出游戏及 Client 强退后的心跳超时清理。

音频、性能与连接恢复体验需分别验证。Parallels 旧测试曾出现音频采集失败，本阶段未单独重测音频。
新设备、游戏版本及其他主机环境仍需独立验收。

## 第三方许可

固定提交、源码改动和各项许可见 `third-party/moonlight-android/CLOUDGI-NOTICE.md`。
发布组合 Client 时须遵守 Moonlight GPLv3 及相关依赖许可。
