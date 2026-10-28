# 发布到 Maven Central

坐标：`io.github.jichuo1.meguviz:meguviz-drawable-core` 与 `meguviz-drawable-android`，版本取自 `core/build.gradle.kts` 和 `android/build.gradle.kts`（须一致）。

## 生成上传包

前提：JDK 17、Android SDK、系统 `gpg` 中有签名密钥，且公钥已发布到 keyserver。签名口令由 gpg-agent 弹窗询问，不进入构建参数。

```text
gradlew -Pmeguviz.profile=android -Pmeguviz.sign=true centralBundle
python tools/verify_central_bundle.py build/central/meguviz-drawable-<版本>-central-bundle.zip
```

- 产物为 Maven 仓库布局的 zip；每个文件带 `.asc` 与 `.md5/.sha1/.sha256/.sha512`，包含 sources 与 javadoc jar。`maven-metadata.xml` 被有意排除，由 Portal 生成。
- 不带 `-Pmeguviz.sign=true` 时 `centralBundle` 会拒绝运行，避免产出无签名的包。
- 校验脚本检查签名、校验和、POM 必填字段与文件齐全；不检查命名空间是否已验证，也不检查公钥是否在 keyserver 上。

## 上传

在 Central Portal 的 Publish 页面上传该 zip，先选 User Managed 部署，确认校验通过后再手动 Publish。发布不可撤销：同一版本号不能覆盖或重发，改字节必须升版本。

## 上传前自查

- 构建内容变化后须重新生成并重验：`docs/engineering/VERIFICATION.md` 中的 SHA-256 只绑定当时的字节。
- `docs/abi/<版本>/` 与独立 consumer/provider fixture 用同一份 staging 候选生成。
- 版本号不等于成熟度：对外声明的稳定范围以 `STATUS.md` 与 README 为准。
