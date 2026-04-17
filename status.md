# slf4k 项目状态

## 项目简介

`slf4k` 是一个 Kotlin 编译器插件，通过 `@Slf4k` 注解为类自动生成 slf4j `Logger` 属性，消除手写 `private val log = LoggerFactory.getLogger(...)` 的样板代码。提供两种接入形态：编译器插件直挂（`-Xplugin=<jar>`）和 Gradle 插件（`plugins { id("top.wang3.slf4k") }`）。

## 模块结构

| 模块 | 作用 | 状态 |
| --- | --- | --- |
| `slf4k-annotations` | `@Slf4k` 注解定义 | 完成 |
| `slf4k-compiler-plugin` | FIR + IR 编译期插件实现 | 可用（4 个集成测试通过） |
| `slf4k-gradle-plugin` | Gradle 插件胶水层（`KotlinCompilerPluginSupportPlugin`） | 可用（2 个 GradleRunner 功能测试通过） |
| `slf4k-test` | 端到端测试样例 + 测试用例 | 4 个测试通过 |

## 最近一次迭代（Gradle 插件落地）

### 新增
- `slf4k-gradle-plugin/src/main/kotlin/top/wang3/slf4k/gradle/Slf4kExtension.kt`：DSL 扩展，暴露 `loggerName: Property<String>`。
- `slf4k-gradle-plugin/src/main/kotlin/top/wang3/slf4k/gradle/Slf4kGradlePlugin.kt`：实现 `KotlinCompilerPluginSupportPlugin`：
  - `apply(target)` 自动向用户工程的 `implementation` 配置注入 `top.wang3.slf4k:slf4k-annotations:<version>`。
  - `getCompilerPluginId()` 与 `getPluginArtifact()` 从 `Slf4kPluginMetadata` 常量拉取，版本号通过构建期生成的 `Slf4kPluginCoordinates` 对象注入以避免与 `project.version` 漂移。
  - `applyToCompilation()` 仅在 `slf4k.loggerName` 被设置时追加 `SubpluginOption("logger-name", ...)`。
- `slf4k-gradle-plugin/src/functionalTest/kotlin/top/wang3/slf4k/gradle/Slf4kGradlePluginFunctionalTest.kt`：两个 GradleRunner 用例：
  - 默认 `logger` 属性生成并命名为 `sample.Sample`。
  - 自定义 `slf4k { loggerName.set("log") }` 生效。

### 修改
- `slf4k-gradle-plugin/build.gradle.kts`：
  - 应用 `kotlin-convention`、`publish-convention`、`java-gradle-plugin`。
  - `compileOnly(libs.kotlinGradlePluginApi)` + `implementation(project(":slf4k-compiler-plugin")) { isTransitive = false }`（复用 `Slf4kPluginMetadata` 常量但不泄漏 `kotlin-compiler-embeddable`）。
  - `generatePluginCoordinates` 任务在构建期生成 `Slf4kPluginCoordinates.kt`（group/version）。
  - `gradlePlugin { plugins { register("slf4k") { id = "top.wang3.slf4k" } } }`。
  - `installTestArtifacts` 任务把 annotations、compiler-plugin、gradle-plugin 的 jar + POM、以及 Gradle 插件 marker POM (`top.wang3.slf4k:top.wang3.slf4k.gradle.plugin`) 装到 `build/test-maven-repo/`，作为 functionalTest 的解析仓库。
  - 新增 `functionalTest` source set 与任务，依赖 `installTestArtifacts`，`check` 任务聚合。
- `slf4k-test/build.gradle.kts`：保留 `-Xplugin=<jar>` active 以维持 4 个回归测试通过；文件末尾以注释块形式记录 Gradle 插件方式的推荐使用写法，并说明为何本仓库内无法在同一个 build 里直接 dogfood（Gradle 9 禁止 `buildscript { classpath(project(...)) }`；`includeBuild` 需要结构重构；buildscript classpath 在配置阶段解析，早于任务执行）。

### 不动
- `slf4k-annotations/**`、`slf4k-compiler-plugin/**`、根 `build.gradle.kts`、`settings.gradle.kts`、`gradle.properties`。

## 已完成

### 编译器插件（上一轮）
- 将 `Slf4kIrGenerationExtension.generate` 中内联的匿名 `IrElementTransformerVoid` 抽离到独立的 `Slf4kIrTransformer`，职责单一。
- 修复的缺陷：
  1. `KClass.java` 在新版 Kotlin 是 inline 属性无法通过 `CallableId(kotlin.jvm, getJavaClass)` 解析 —— 改用 `LoggerFactory.getLogger(String)` + 类 FQN 作为 category。
  2. 字段来源过滤改为检查 `field.correspondingPropertySymbol?.owner?.origin`（IR 给 backing field 的 origin 是 `PROPERTY_BACKING_FIELD`）。
  3. 用户自定义 companion 里 FIR 生成成员的兜底查找通过 `pluginContext.referenceProperties(CallableId(...))` 补齐。
  4. 移除 companion 构造器里的 `logger = ...` 赋值（JVM `private static` 字段归外部类，companion 访问会 `IllegalAccessError`）；只留 `field.initializer`，由后端编译到外部类 `<clinit>`。

### Gradle 插件（本轮）
- `KotlinCompilerPluginSupportPlugin` 实现 + `slf4k { loggerName }` DSL + 自动注入 annotations 依赖。
- `publish-convention` 接入，产物坐标：`top.wang3.slf4k:slf4k-gradle-plugin:0.0.1` 及自动生成的插件 marker。
- GradleRunner 端到端测试链路（`installTestArtifacts` → `functionalTest`）在本仓库内运行无需外部仓库或手动发布。

### 测试覆盖
- `slf4k-test/src/test/kotlin/top/wang3/test/PluginTest.kt`：4 个集成测试（生成 companion、保留用户 companion 成员、logger 命名、运行时输出捕获）。
- `slf4k-gradle-plugin/src/functionalTest/...`：2 个 GradleRunner 功能测试（默认 logger 名、自定义 loggerName option）。

## 下一步建议

按优先级排列：

1. **发布到仓库并完整回归**
   - 跑一次 `./gradlew publishAllPublicationsToWang3Repository`（需要 `CNB_OI_TOKEN`），验证三个 artifact + gradle 插件 marker 正确上传到 `maven.cnb.cool`。
   - 发布后可以把 `slf4k-test` 切到真正的 Gradle 插件形式 active（从注释里取出），作为 dogfood 终态。

2. **移除/整理遗留代码**
   - `Slf4KNames.getJavaClassCallableId` 与 `javaClassClassId` 已不再使用，可删。
   - `Slf4KNames.getLoggerCallableId` 也已不再使用（改为直接从 class declarations 查找），可删。
   - `Slf4KNames.messageCollector` 全局 lateinit 仅服务于 `LOG()` 便利函数，建议收敛或改为依赖注入。

3. **扩展测试覆盖**
   - 嵌套类、内部类、object、enum class 的 `@Slf4k` 行为。
   - 外部类或 companion 已存在同名 `logger` 的冲突检测路径（`hasLoggerConflict` 分支）。
   - 多个 `@Slf4k` 类在同一文件时的处理。
   - functionalTest 补充：非法 `loggerName`（非 Kotlin 标识符）应在编译期报错路径。

4. **文档**
   - `README.md` 补使用说明（Gradle 插件方式 + 编译器插件方式）。
   - 在 `@Slf4k` 注解上补充 KDoc。

5. **可选优化**
   - `Slf4kIrTransformer.collectGeneratedLoggerFields` 当前对每个被 `@Slf4k` 标注的类都触发 `referenceProperties`，对大型项目可先判断类是否带注解再调用。
   - 考虑根据 `@Slf4k` 注解剪枝 `visitClass`，避免遍历全部类。
   - 把 `Slf4kPluginMetadata` 抽到独立的 `slf4k-common` 模块，避免 gradle-plugin 依赖整个 compiler-plugin（尽管已 `isTransitive = false`）。

## 关键坐标

- group: `top.wang3.slf4k`
- version: `0.0.1`
- Kotlin: `2.3.0`
- slf4j: `2.0.17`
- Gradle 插件 ID: `top.wang3.slf4k`
- 编译器插件 ID: `top.wang3.slf4k.compiler`
- `logger-name` CLI option（Kotlin 标识符校验）
