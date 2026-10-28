# ADR 0002：家族命名空间与可选桥

- 状态：开发中采用，尚未发布或冻结 ABI。
- 日期：2026-10-28。

基础包统一为 `io.github.jichuo1.meguviz.drawable`，构建 group 与其他 Meguviz 库保持一致。纯 Java core 保持独立，Android 实现只依赖 core。MeguvizAndroid、MeguvizUI、MeguvizRef 和 Lumen 的集成需要以真实可用的制品与版本化合同为前提，并分别作为可选桥；不能从本地设计或源码推定已发布 API。

在首个正式发布前完成包名调整。API、SPI 和发布坐标仍需分别冻结与验证；本 ADR 不代表任何发布或兼容性验收已通过。
