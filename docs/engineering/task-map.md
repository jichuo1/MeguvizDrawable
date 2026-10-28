# 任务与命令映射

| 任务 | 状态 | 验证方式 |
|---|---|---|
| P0 基线与工具链 | IN_PROGRESS | 发现真实任务并记录环境、依赖和行为 |
| P1 生产依赖迁移 | IN_PROGRESS | 旧调用未在当前生产源码检索到；依赖声明及构建、依赖图和运行验证待处理 |
| P2 纯模型与核心 | IN_PROGRESS | `gradlew check`；扩展、缓存和完整模型待补充 |
| P3 Android、绑定 | IN_PROGRESS | 平台与设备行为测试尚未执行 |
| P4 独立消费者和 ABI | NOT_STARTED | 仅消费发布制品的独立构建 |
| P5 质量与发布 | NOT_STARTED | 制品、图像、设备和性能证据 |
| P6 高级效果 | NOT_STARTED | 专项能力与净收益验证 |

本表中的阶段名称是工程任务，并非 Gradle 任务名。`gradlew check` 当前执行的是纯 Java 合同测试；Android 和发布门禁需要独立证据。
