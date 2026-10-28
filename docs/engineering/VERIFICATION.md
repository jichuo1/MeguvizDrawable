# 验证记录

2026-10-28，基线提交 `4c5dd54`，JDK 17、Gradle 9.8.2：`gradlew --offline --no-daemon check --rerun-tasks`，退出码 0，重新编译后执行 `CoreContractTest`，20,018 项检查通过。范围仅包括当时的状态匹配、输入复制和圆角约束；后续包名迁移及新增代码尚未验证。

初次 `check` 时合同测试通过，但默认 Gradle `test` 任务因没有测试框架用例而失败。构建随后改为禁用该默认任务，由 `check` 显式依赖可执行的合同测试；上述重跑包含修正后的接线。另一次在受限环境运行时 wrapper 缓存锁文件访问被拒绝，使用已有缓存权限重跑成功。

这些结果不验证 Android、设备、BIL 迁移、独立制品、ABI 或性能。机器相关原始日志保存在本地，不纳入仓库。

## 扩展验证，2026-10-28

- 宿主 JDK 17：`gradlew --offline --no-daemon check --rerun-tasks`，退出 0，20,058 项检查通过，涵盖状态、模型防御复制、参数隔离与绑定所有权。完整模型、缓存和 SPI 仍未完成，G2 不能判定通过。
- Android：使用已安装 SDK 执行 `gradlew --offline --no-daemon -Pmeguviz.profile=android check`，退出 0，含构建和 lint；首次未提供 SDK 位置的运行失败，保留本地日志。另在 API 37 单设备通过 29 项 instrumentation 检查，覆盖软件 Canvas 像素、状态数组别名、失效回调、实例隔离、固定 metrics、alpha 与 tint。其他 API、硬件 Canvas、完整 Drawable 协议和真实生产页面尚未验证。
- 实际生产源码基线的文本检索未发现旧 Builder 调用，但依赖声明仍存在；这不是运行依赖图、APK 或设备退出证明。

## 性能试验，2026-10-28

- 对重复传入相同状态的路径，当前实现直接比较状态，避免同值时克隆；状态输入和输出仍防御复制。在单台 API 37 设备、debug 构建中，以测试专用的简化旧克隆路径为基线，7 个不同 PID、每进程 12 组交替配对，每路径每组 50 万次。各进程中位数：基线 100.829–151.185 ns/次，当前路径 7.976–11.967 ns/次，对应下降 91.63%–92.10%。这仅是重复相同状态调用的局部测量，不代表整库、页面或 FPS 收益。
- 主机试验固定 8 状态、13 条规则，10 个新 JVM 进程各 7 轮配对；包含状态编码的实验查表相对直接匹配的进程级配对降幅中位数为 68.487%。表载荷 1,024 字节，未计表构建和完整对象内存；候选没有进入生产。实验语料、单机负载和 HotSpot 与实际 Android 工作负载不同，不能推断设备收益。

性能原始数据及设备身份信息仅保存在本地；协议和可复现工具位于 `benchmark/`。所有实验都不构成完整性能门禁通过。

## 设备验证复现

安装 Android SDK 并选择设备后，在 PowerShell 中执行 `./gradlew.bat --offline --no-daemon '-Pmeguviz.profile=android' :android:assembleDebugAndroidTest`。测试 APK 自包含，包名及 instrumentation 目标均为 `io.github.jichuo1.meguviz.drawable.contracttest`。安装前先检查设备是否已有同名包，避免覆盖已有应用。安装后运行：

```text
adb -s <device> shell am instrument -w -r io.github.jichuo1.meguviz.drawable.contracttest/io.github.jichuo1.meguviz.drawable.android.test.ContractInstrumentation
```

性能模式在组件名前添加 `-e mode benchmark`，应启动多个独立进程，并保留每次原始输出和 PID。设备差异、构建模式、基线实现及热身方式均应随结果记录。

## 发布候选与扩展设备验证，2026-10-28

- 同一测试 APK 在 API 34 模拟器和 API 37 真机上各通过 52 项软件 Canvas 合同，覆盖透明渐变及 alpha、非对称圆角变换、滤镜与 tint、实例隔离、错误恢复、状态数组原地复用及 checked/selected 区分。硬件 Canvas、API 21/22 和其他版本仍未验证。
- 本地隔离 Maven 仓库的 `0.1.0-dev.1` 候选由独立 Gradle 根目录 `fixtures/consumer` 消费，不使用 project 依赖或 composite 替换。Gradle metadata 和 Maven POM 两条解析路径都检查了解析字节与候选一致；releaseRuntimeClasspath 仅包括本库 Android 和 core，release 消费者完成 R8 构建。该未签名 APK 的独立调试签名副本在 API 37 真机安装并冷启动，确认安装字节与前台 Activity；未作页面像素验证，也不代表正式签名或公开发行。
- 初版候选元数据曾意外包含 Kotlin 标准库，已保留本地失败证据；仅有 Java 生产源码的当前配置已禁用自动注入，重新生成的 Android 候选只声明本库 core 依赖。项目已选用 Apache-2.0，候选 POM、二进制与源码归档均携带对应许可证，并以 `tools/verify_candidate_licenses.py` 核对。
- 从候选 JAR 和 AAR 生成首版 [ABI 清单](../abi/README.md)，分别覆盖 17 个和 1 个公开或受保护类。首版无历史版本，不声称旧二进制兼容。

候选 JAR SHA-256：`280635cf5e23b9c82be44195bdb8c2dcbb493b388e6071b63bd8f3de132f373e`；候选 AAR SHA-256：`bce48e151df9ac37f591080a2dc625d39d106fe553ffbcb40db6454411304272`。结果只绑定这些候选字节；若重新生成制品导致摘要变化，必须重新验证。完整构建与测试原始日志保留在本地。

## 扩展候选验证，2026-10-28

- `0.1.0-dev.2` 引入实验性闭合多边形 provider：冷路径显式注册、版本/依赖/顶点预算校验及独立 Android 绘制入口；不扩展 `RecipeDrawable`，也不宣称实现完整 SPI 或 tint 协议。独立 provider 的 22 项宿主合同、metadata/POM 两种解析和 R8 release 构建通过；API 37 真机上的 debug fixture 验证软件绘制与 owner 隔离，不能称为 R8 设备运行。
- 先前 `0.1.0-dev.1` 编译并冻结的有限 Java consumer 字节码，不重编译即可在 dev.2 core 上运行；旧 core 的 17 个公开/受保护类 API 文本均与新清单一致。该证据只覆盖此 fixture 和清单，不代表任意历史消费者或 Android ABI 已验证。
- API 21/22 云端模拟器在基线提交 `c4f7e77` 上各通过 52 项软件合同。首次运行因 shell 不兼容而在测试启动前失败，原始失败保留；对包含新测试类的最终候选需单独重跑。
- API 37 真机的最终测试 APK 在硬件加速窗口内通过单个静态渐变场景，与平台 shader 参考的 78×78 区域逐像素一致。初次全屏截图对照失败并已保留；改用测试窗口 PixelCopy 后不放宽容差。不能由此推断所有硬件、GPU 或绘制效果均通过。

dev.2 候选 core JAR SHA-256 `51b17b45e0397b5b322b1382bb73e42bb7e7a91199e538eb79e31031a352c9ba`；Android AAR SHA-256 `48ad5815a27ea0e2844a5bc50cdc869d3a6963ea17735a5db47e28353be6424f`。若这些字节改变，相关消费者和 ABI 证据需重新核验。
