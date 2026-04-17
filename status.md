# slf4k 项目状态

## 项目简介

`slf4k` 是一个 Kotlin 编译器插件，通过 `@Slf4k` 注解为类自动生成 slf4j `Logger` 属性，消除手写 `private val log = LoggerFactory.getLogger(...)` 的样板代码。

## 模块结构

| 模块 | 作用 | 状态 |
| --- | --- | --- |
| `slf4k-annotations` | `@Slf4k` 注解定义 | 完成 |
| `slf4k-compiler-plugin` | FIR + IR 编译期插件实现 | 可用（见下方"已完成"） |
| `slf4k-gradle-plugin` | Gradle 插件胶水层 | **未实现**（`src/main` 为空，只剩一个功能测试骨架） |
| `slf4k-test` | 端到端测试样例 + 测试用例 | 4 个测试全部通过 |

## 已完成

### 代码重构
- 将 `Slf4kIrGenerationExtension.generate` 中内联的 `object : IrElementTransformerVoid() {}` 抽离到独立的 `Slf4kIrTransformer` 类。
- 重构后 `Slf4kIrGenerationExtension` 只负责解析 slf4j 符号并调用 transformer，职责单一。

### 修复的缺陷（测试补全过程中发现）
1. **符号解析**：原实现通过 `CallableId(kotlin.jvm, getJavaClass)` 查找 `KClass.java` 的 getter，新版 Kotlin 中 `java` 是一个 inline 属性，无法通过该 CallableId 解析 → 返回 0 候选。改为直接使用 `LoggerFactory.getLogger(String)` 重载，并用类 FQN 作为 logger category，彻底规避 `KClass → Class` 的转换。
2. **字段来源过滤**：原过滤器检查 `field.origin`，但 IR 给 backing field 的 origin 是 `PROPERTY_BACKING_FIELD`，插件 key 实际挂在对应的 `IrProperty` 上。已改为优先检查 `field.correspondingPropertySymbol?.owner?.origin`。
3. **用户自定义 companion 中的 FIR 生成成员**：当被 `@Slf4k` 标注的类已有 companion（如 `TestB`）时，FIR 生成的 logger 属性在 IR 变换阶段尚未出现在 `companion.declarations` 中。已补充通过 `pluginContext.referenceProperties(CallableId(...))` 作为兜底查找。
4. **静态字段跨类赋值**：原本向 companion 构造器注入 `logger = ...` 赋值语句，但该字段在 JVM 层是 `private static` 属于外部类，companion 访问外部类的 private 字段会抛 `IllegalAccessError`。移除该注入后仅保留 `field.initializer`，JVM 后端会将其编译到外部类的 `<clinit>`，合法且足够。

### 测试覆盖（`slf4k-test/src/test/kotlin/top/wang3/test/PluginTest.kt`）
- `@Slf4k generates a companion object when missing` —— 为无 companion 的类生成 companion（TestA）
- `@Slf4k preserves an existing companion object` —— 保留用户 companion 中原有成员（TestB 的 `NAME`）
- `generated logger is a live slf4j Logger named after the class` —— 反射读取 logger，验证其 name 与类全限定名一致
- `logger writes to slf4j output when used from an instance method` —— 捕获 stderr，验证 `greet()` / `doWork()` 调用 slf4j 成功输出

## 下一步建议

按优先级排列：

1. **实现 Gradle 插件** (`slf4k-gradle-plugin`)
   - 当前 `src/main` 为空，用户无法通过 `plugins { id("top.wang3.slf4k") }` 使用。
   - 需要 `KotlinCompilerPluginSupportPlugin`：注册 artifact、拼 CLI 参数（`logger-name` 等）、添加 `slf4k-annotations` 依赖。
   - 完成后补上 `Slf4KGradlePluginFunctionalTest` 对应的测试实现。

2. **移除/整理遗留代码**
   - `Slf4KNames.getJavaClassCallableId` 和 `javaClassClassId` 已不再使用，可删除。
   - `Slf4KNames.getLoggerCallableId` 也已不再使用（改为直接从 class 的 declarations 查找），可删除。
   - `Slf4KNames.messageCollector` 全局 lateinit 仅用于 `LOG()` 便利函数，建议收敛或改为依赖注入。

3. **扩展测试覆盖**
   - 嵌套类、内部类、object、enum class 的 `@Slf4k` 行为
   - `-Plogger-name=xxx` 自定义 logger 属性名
   - 外部类或 companion 已存在同名 `logger` 的冲突检测路径（`hasLoggerConflict` 分支）
   - 多个 `@Slf4k` 类在同一文件时的处理

4. **文档**
   - `README.md` 需要补使用说明（至少 Gradle 插件可用之后）
   - 在 `@Slf4k` 注解上补充 KDoc

5. **可选优化**
   - `Slf4kIrTransformer.collectGeneratedLoggerFields` 现在对每个被 `@Slf4k` 标注的类都会触发 `referenceProperties`，对大型项目可考虑只在必要时调用（例如先判断类是否带注解）。
   - 考虑根据 `@Slf4k` 注解的存在来剪枝 `visitClass`，避免遍历全部类。
