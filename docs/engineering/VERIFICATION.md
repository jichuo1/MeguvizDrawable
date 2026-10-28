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
