# BIL 旧依赖退出候选

`remove-drawabletoolbox-74d78e.patch` 基于 BIL 提交
`74d78e19095baa006b1315001190241952f1b84a`。该提交的生产源码已不含
`DrawableBuilder` 调用，但依赖目录和应用构建仍声明 DrawableToolbox 1.0.7；补丁仅移除
这三处声明，不改变开关构造、皮肤、绘制或业务行为，也不加入新依赖。

移除候选只在按该提交导出的隔离快照中验证，不代表已修改用户 BIL 工作树。实际应用前应
重新检查目标 HEAD、源码与未提交改动，在 BIL 仓库根目录运行 `git apply --check`；不要覆盖
其他改动。移除验证必须区分 release 依赖组件图、混淆产物、APK、设备和实际界面证据。

对应远端候选为草稿 [BIL PR #39](https://github.com/jichuo1/Bilibili_Innocent_Lab/pull/39)，
其两个文件内容与隔离补丁一致。`Advanced` 已非强制快进到基线并设为 PR 目标；
PR 尚未合并，BIL `main` 和原工作树未修改。

隔离基线与候选的 `releaseRuntimeClasspath` 完整组件图分别为 101 与 100 项，均无未解析项；
两图唯一差异为移除旧库 1.0.7。隔离候选的 `minifyReleaseWithR8` 已完成，R8 映射与生成的
DEX 字节串检索未发现该库包名及 Builder 名称。此结果不证明 release APK 或运行时行为，构建
日志和原始路径仅保存在本机忽略的工程证据目录。

同一提交在隔离基线和候选下均完成 `:app:assembleDebug`。两份 APK 均包含 34 个 DEX；基线
DEX 中旧包名字节串出现 89 次、`DrawableBuilder` 出现 62 次，候选均为 0。基线 APK 的
SHA-256 为 `1291ae58db44444d536b1867b02e38116586d714d7a250f3db733b3968ff8675`，候选为
`7a0cb0204f0444bfbe8c8ad770df99aa5c6c2c312de8b0f0b207cf98035b754d`。数字是字符串出现
次数而非类数量；两个 debug APK 均未安装，不能替代 release 签名、APK、Hook 或 UI 验收。
