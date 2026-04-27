# slf4k 项目状态

## 项目简介

`slf4k` 是一个 Kotlin 编译器插件，通过 `@Slf4k` 注解为类自动生成 slf4j `Logger` 属性，消除手写 `private val logger = LoggerFactory.getLogger(...)` 的样板代码。提供两种接入形态：编译器插件直挂（`-Xplugin=<jar>`）和 Gradle 插件（`plugins { id("top.wang3.slf4k") version "<kotlin>-<slf4k>" }`）。

## 模块结构

| 模块 | 作用 | 状态 |
| --- | --- | --- |
| `slf4k-annotations` | `@Slf4k` 注解定义 | 完成；锁 `languageVersion=KOTLIN_2_0` baseline，跨 Kotlin 2.x 单一 artifact |
| `slf4k-compiler-plugin` | FIR + IR 编译期插件实现 | 可用（7 个集成测试通过） |
| `slf4k-gradle-plugin` | Gradle 插件胶水层（`KotlinCompilerPluginSupportPlugin`） | 可用；功能测试已提取到独立模块 |
| `slf4k-gradle-plugin-functional-test` | Gradle 插件端到端功能测试（GradleRunner） | 可用（3 个 GradleRunner 功能测试） |
| `slf4k-test` | 端到端测试样例 + 测试用例 | 7 个测试通过（`-Xplugin=` 直挂形态） |

## 最近一次迭代（Kotlin 版本与坐标规则重构）

### 背景
上一次迭代后的 `libs.versions.toml` 里把 Kotlin 版本拆成 `kotlin = 2.3.0`（KGP、宿主 kotlinc）与 `kotlinCompiler = 2.4.0-dev-2631`（`kotlin-compiler-embeddable` + KEFS 坐标）两个变量，实际迭代中造成三套版本（`plugin-kgp-version` / `plugin-kce-version` / `idea-kt-version`）本该一致却持续漂移：

- compiler-plugin 源码被 KGP 2.3.0 编译，却调用 2.4.0-dev embeddable API → 编译期 API drift
- `:slf4k-test` 宿主 kotlinc 与插件目标版本不一致 → 运行期 API mismatch
- KEFS 强制 `plugin-kce-version == idea-kt-version`，每次 IDE 升级要同时心算三个版本

### 修改
- `gradle/libs.versions.toml`：删除 `kotlinCompiler`，`kotlinCompilerEmbeddable` 指向 `kotlin`；更新 `kotlin` 头注释说明「必须 = IDE 捆绑 kotlinc 版本」。
- `settings.gradle.kts`：新增 `versionCatalogs.create("libs") { version("kotlin", override) }`，支持 `-Pkotlin.version=X` 命令行覆盖；注意 `libs` 已由 Gradle 从默认路径自动创建，此处只追加覆盖，不能再调 `from()`。
- `buildSrc/settings.gradle.kts`：同样的 `-Pkotlin.version` 覆盖逻辑；新增 JetBrains dev 仓库，支持 `-Pkotlin.version=2.4.0-dev-XXXX` 从 dev 仓库解析 KGP。
- `slf4k-annotations/build.gradle.kts`：`kotlin { compilerOptions { languageVersion/apiVersion = KOTLIN_2_0 } }` 锁 metadata baseline，annotations 作为跨 Kotlin 版本单一 artifact 不随 Kotlin 版本重发。
- `slf4k-compiler-plugin/build.gradle.kts`：version 公式 `${libs.versions.kotlinCompiler.get()}-${rootProject.version}` → `${libs.versions.kotlin.get()}-${rootProject.version}`；KEFS 注释更新。
- `slf4k-gradle-plugin/build.gradle.kts`：
  - 新增顶层 `version = "${libs.versions.kotlin.get()}-${rootProject.version}"`，Gradle 插件坐标改为 `<kotlin>-<slf4k>` 格式（用户在 `plugins {}` 里看到适配的 Kotlin 版本）。
  - `generatePluginCoordinates` 拆三元：`slf4kVersion`（= `rootProject.version`，给 annotations 注入）/ `pluginArtifactVersion`（= `project.version`）/ `compilerPluginVersionValue`（KEFS 规则）。
- `slf4k-gradle-plugin-functional-test/build.gradle.kts`：
  - 承接原 GradleRunner 测试和 `installTestArtifacts`，annotations 按 `slf4kVersion` 安装，compiler-plugin / gradle-plugin / plugin marker 按 `<kotlin>-<slf4k>` 安装。
  - `test` 的 `slf4k.version` system property 自动取到 `<kotlin>-<slf4k>`，测试生成的 `build.gradle.kts` 里 `id("top.wang3.slf4k") version "2.3.0-0.0.1"` 自动正确。
- `slf4k-test/build.gradle.kts`：注释里的示例版本更新到 `2.3.0-0.0.1`；源码零改动，但重构后宿主 kotlinc（KGP = `kotlin`）与插件 KCE（= `kotlin`）自动对齐，消除原先 API drift。
- `README.md`：接入示例改为 `id("top.wang3.slf4k") version "2.3.0-0.0.1"`；「坐标约定」段补 gradle-plugin 也走 `<kotlin>-<slf4k>`；「关键坐标」段重写；新增「多 Kotlin 版本支持」段说明 `-Pkotlin.version` 用法。

### 不动
- `Slf4kGradlePlugin.kt`、`Slf4kPluginMetadata.kt`、所有 compiler-plugin 源码。
- `Slf4kPluginCoordinates.VERSION` / `COMPILER_PLUGIN_VERSION` 双常量语义保留（前者 = slf4k semver 给 annotations 用，后者 = `<kotlin>-<slf4k>` 给 SubpluginArtifact 用），只是生成逻辑里 `VERSION` 的来源从 `project.version` 改为 `rootProject.version` 以适配 gradle-plugin 自身 `project.version` 的语义变化。

## 版本策略（当前状态）

| Artifact | GAV version | 说明 |
| --- | --- | --- |
| `slf4k-annotations` | `top.wang3.slf4k:slf4k-annotations:0.0.1` | 单一 artifact，languageVersion=2.0 baseline |
| `slf4k-compiler-plugin` | `top.wang3.slf4k:slf4k-compiler-plugin:2.3.0-0.0.1` | KEFS 规则 `<kotlin>-<slf4k>` |
| `slf4k-gradle-plugin` | `top.wang3.slf4k:slf4k-gradle-plugin:2.3.0-0.0.1` | 同规则，用户在 `plugins {}` 显式看到 Kotlin 版本 |
| plugin marker | `top.wang3.slf4k:top.wang3.slf4k.gradle.plugin:2.3.0-0.0.1` | 跟随 gradle-plugin |
| `rootProject.version` | `0.0.1` | slf4k semver |

用户接入（推荐）：

```kotlin
plugins {
    kotlin("jvm") version "2.3.0"
    id("top.wang3.slf4k") version "2.3.0-0.0.1"
}
```

多 Kotlin 版本发布：CI 矩阵用 `-Pkotlin.version=X` 并行产出多份 compiler-plugin / gradle-plugin artifact；annotations 只需发一次。

## 验证

- `./gradlew help` → 发布坐标全部正确。
- `./gradlew :slf4k-test:test` → 7/7 passed（4 PluginTest + 3 ExtendedPluginTest）。
- `./gradlew :slf4k-gradle-plugin-functional-test:test` → 3/3 passed；test maven repo 内含 slf4k artifacts、plugin marker 与 slf4j 测试依赖。
- `./gradlew :slf4k-annotations:publishToMavenLocal` 等 → `D:\Maven\maven-repository` 下发布四个 artifact 坐标正确；`slf4k-gradle-plugin-2.3.0-0.0.1.pom` 的运行时依赖指向 `slf4k-compiler-plugin:2.3.0-0.0.1`。
- `-Pkotlin.version=X` 覆盖机制验证通过（override 成功透传到 KGP 解析、jar 产物命名、artifact 坐标）。

## 下一步建议

1. **`./gradlew build` 整根 DAG 的 pre-existing race 修复（与本次重构无关）**
   - `slf4k-test/build.gradle.kts` 里 `dependsOn(":slf4k-compiler-plugin:clean")` 与 `:jar` 无执行顺序约束，`./gradlew build` 时 clean 可能在 jar 之后跑，导致 compileKotlin 读到不存在的插件 jar。
   - 建议改为：删除 `clean` 依赖（Gradle up-to-date 检查即可保证 jar 新鲜），或加 `mustRunAfter` 显式排序。

2. **多 Kotlin 版本发布流水线**
   - 在 CI 里配置矩阵 job：`matrix: [2.3.0, 2.4.0-...]`，每行跑 `-Pkotlin.version=$X ./gradlew publishAllPublicationsToWang3Repository`，产出对应 `<X>-0.0.1` 的 compiler-plugin + gradle-plugin。
   - annotations job 单独跑一次（不需要矩阵）。

3. **发布到仓库并完整回归**
   - `./gradlew publishAllPublicationsToWang3Repository`（需 `CNB_OI_TOKEN`）。
   - 发布后可把 `slf4k-test` 的 `-Xplugin=` hack 切到真正的 Gradle 插件方式作为 dogfood 终态（需要 `includeBuild` 结构调整，见 `slf4k-test/build.gradle.kts` 注释块里的分析）。

## 关键坐标

- group: `top.wang3.slf4k`
- slf4k semver: `0.0.1`（= annotations 坐标、`<kotlin>-<slf4k>` 中的 `<slf4k>`）
- compiler-plugin 坐标: `slf4k-compiler-plugin:2.3.0-0.0.1`
- gradle-plugin 坐标: `slf4k-gradle-plugin:2.3.0-0.0.1`
- Kotlin: `2.3.0`（= IDE 捆绑 kotlinc；可用 `-Pkotlin.version=X` 命令行覆盖）
- slf4j: `2.0.17`
- Gradle 插件 ID: `top.wang3.slf4k`
- 编译器插件 ID: `top.wang3.slf4k.compiler`
- CLI option: `logger-name`
