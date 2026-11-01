# CloudGI Client

CloudGI 的 KMP / Compose 客户端，Android 集成固定版本 Moonlight Android v12.1。
主页点击“启动游戏”会自动发现 CloudGI Host、完成配对、请求 Host 启动原神，确认游戏进程后进入 Desktop 显示器串流。
游戏已运行时复用原进程；仍捕获整个显示器，保留电脑显示模式和游戏分辨率。
无需安装官方 Moonlight，也不需要输入 PIN、Sunshine 网址或管理账号密码。

## 架构

- `composeApp/src/commonMain`：共享主页、连接按钮和状态展示。
- `androidApp`：Android 入口、发现与自动配对控制，打开原生串流 Activity。
- `moonlightAndroid`：Android 库构建适配，提供 Moonlight 的 Java / JNI、解码、音频和输入能力。
- `third-party/moonlight-android`：固定的上游源码与原生子模块内容；具体提交和本地改动见 `CLOUDGI-NOTICE.md`。

Moonlight 原生串流页面打入同一 CloudGI APK。CloudGI 保持自己的应用 ID，和官方 Moonlight 分别保存配对身份。
桌面入口保留共享界面；目前串流实现仅提供 Android ARM64 版本，iOS / macOS 媒体桥接后续推进。

## 构建与安装

需要 JDK 21、Android SDK Platform 36、NDK `28.2.13676358`。
Android SDK 位置通过 `ANDROID_HOME` 或本机 `local.properties` 指定。Gradle Wrapper 随仓库提供。
首次构建需要联网取得 Maven 依赖；Moonlight 及原生依赖源码已经随仓库保存，不在运行时下载。

```sh
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:installDebug
```

APK 位于 `androidApp/build/outputs/apk/debug/androidApp-debug.apk`。Windows 使用 `gradlew.bat`。
普通运行只需安装此 APK。开发期间通过 USB 安装需要手机允许该电脑进行 USB 调试。

Mac 上查看共享界面：

```sh
./gradlew :composeApp:run
```

## 自动发现与配对

1. Host 运行其专属 Sunshine，并在基础端口加 2 处提供 CloudGI HTTPS 控制入口。
2. Android 复用 Moonlight 的系统 NSD 和 JmDNS 两条发现路径搜索 `_nvstream._tcp.local.`，核对 CloudGI `/cloudgi/info`，取得名称、UUID 及串流端口。
3. 首次连接按可信局域网模式取得并保存主机证书；再次连接核验已保存证书，证书改变时不自动替换。
4. Client 生成随机 PIN，并行执行 Moonlight 握手和 CloudGI `/cloudgi/pair` 请求。Host 按会话名称与来源地址匹配待配对请求，代为提交 PIN。
5. 握手成功后保存身份，以 Moonlight 已配对设备的私钥签名请求 Host 启动游戏。没有新密码或手动授权步骤；Sunshine 管理凭据留在 Host。
6. Host 使用电脑端保存的游戏路径和参数，手机不传可执行文件或参数；手机查询启动状态，确认实际游戏进程后打开 Desktop 原生串流页面。
7. 同一启动请求重试或连续点击复用已有启动操作；游戏已运行时直接复用。路径无效或启动失败会显示原因，不自动反复启动。

手机最多等待启动约两分钟；Windows 若显示安全确认或提权提示，仍需要在电脑正常处理。
实际串流页面退出、连接终止或切入后台并停止串流时，会通知 Host 自动退出会话对应的游戏。客户端进程被杀或网络断开时，Host 通过心跳超时执行退出。进程存在不代表登录加载完成。
控制请求使用时间戳和一次性随机数防止重放；手机与 Host 的系统时间需正常同步。设备被移除或禁用后不能发起启动请求。

自动配对采用可信家庭局域网模式：同局域网可达此接口的 CloudGI Client 能自动取得串流授权。
该版本不提供公网连接或互联网暴露配置。Host 的 Windows 局域网访问授权属于安装环境准备。

初始编码预设为 1280×720、60 FPS、10 Mbps，保留电脑显示器及游戏分辨率，暂不提供画质设置页。
当前捕获范围为整个显示器；触控沿用 Moonlight 现有输入模式，不代表游戏原生多点触控已经验证。
返回和断开 CloudGI 串流会退出本次会话的游戏。串流正常运行时每两秒发送一次签名心跳；Host 在连续八秒未收到心跳后开始清理，初次媒体连接宽限为 45 秒。
Host 先正常关闭游戏，五秒后仍未退出才结束绑定进程。网络中断后不保留游戏等待重连，再次点击会启动新的游戏进程。
该策略需要新版 Host（公布 `disconnectPolicy=close-game-lease-v1`），旧版会提示升级。
Android 通过 CloudGiGameActivity 继承上游 Game 页面添加生命周期处理，不改动 Moonlight 媒体内核。
已建立会话后正常关闭 Host 也会清理游戏；不要强制结束 Host 来代替正常关闭。

## 手机启动验收

1. 在 Host 设置中保存可访问的游戏路径，等待专属 Sunshine 可连接。
2. 手机点击一次“启动游戏”，观察发现、配对、启动和连接状态；仅停留在游戏登录界面，不点击“进入游戏”。
3. 返回手机主页后，Host 应自动退出游戏并恢复“未运行”；再次点击应启动新的游戏进程。
4. 配置无效时应显示错误，且不打开串流或自动重试启动；测试结束后关闭游戏。

## 发现调试模块

主页在“启动游戏”按钮下显示本轮发现的服务。打开页面自动搜索，也可点击“刷新搜索”。
列表包含 NSD / JmDNS 来源、原始服务名、解析出的 IP 和 Sunshine 端口、预期 CloudGI 控制端口及检查结果。
普通 Sunshine、尚未解析地址和 CloudGI HTTPS 探测失败的记录均会保留，便于区分“没有收到 mDNS 记录”和“服务已发现但控制接口不可用”。
本模块显示 Sunshine / GameStream 服务，不代表扫描了局域网中的全部设备。

## 验证状态

- Android APK 构建与安装通过；Mac 桌面共享界面编译通过。
- 已在连接的红米 Android 16 手机上安装测试包。
- 真机日志已确认同时发现原有 Sunshine 与 CloudGI 专属实例；CloudGI HTTPS 接口验证通过。
- CloudGI Client 使用自身新身份自动完成配对，成功查询 Desktop 并打开原生页面，未要求手动输入 PIN。
- Android 硬件解码器已实际接收并向显示 Surface 渲染 H.264 视频帧；当前虚拟机 Host 日志显示音频采集初始化失败，声音尚不可用；输入及重连尚待分别验收。
- 发现调试模块已编译安装，可观察原始服务、地址解析及 CloudGI 接口检查结果。
- 手机远程启动与状态轮询代码已实现；Android 构建和共享桌面界面编译通过。新版 APK 已更新安装到测试手机，主机接口探测通过。用户已确认手机启动后看到游戏登录界面；Windows 核对本次游戏确由 Host 创建。结束客户端进程后游戏 PID 保留，测试游戏随后已关闭。用户已确认可正常返回 Client 主页且游戏继续运行；此处为旧策略记录，当前改为断开退出游戏。

## 第三方许可

Moonlight Android 与其核心为 GPLv3 项目，相关源码、许可及本地改动说明保存在 `third-party` 中。
发布组合 APK 时需遵守这些许可及各依赖许可。源码仓库为 `Ruize128/CloudGI-Host` 和 `Ruize128/CloudGI-Client`。

### 断开退出验证

- 46 项 Host 核心测试和生产 HTTPS 接口替身集成检查通过，覆盖显式断开、心跳超时、旧会话隔离和重复通知。
- 新版 Android APK 已构建安装。正常返回已实机验证：用户确认游戏自动关闭，Host 恢复未运行；客户端日志确认 Host 完成清理，Windows 记录对应 PID 18584 退出。客户端被直接结束的实机补测也已通过：Host 自动关闭 PID 19696，观察到约 12 秒后进程消失。该时长包含心跳超时检测和游戏关闭等待，不是串流延迟。
- 前文 2026-11-01 游戏保留记录属于旧策略验收；当前策略已改为断开退出。
