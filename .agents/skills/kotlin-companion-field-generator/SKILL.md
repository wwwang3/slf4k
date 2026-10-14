---
name: kotlin-companion-field-generator
description: |
  Build a Kotlin (K2) compiler plugin that generates a `companion object` plus a
  field on it for every class matching a trigger annotation. Use this skill
  whenever the user asks to auto-inject a `companion object { val x: T = ... }`
  (logger, metric holder, cache, serializer, feature flag, etc.) via an
  annotation like `@Foo` — covers the annotation module, the FIR declaration
  generator, the IR initializer lowering, CLI options, the Gradle wiring, and
  the integration test harness. Skip for: runtime reflection, kapt/KSP
  approaches, or instance-level (non-companion) fields.
---

# Kotlin Companion Field Generator — 编译器插件 Skill

本 skill 把 `slf4k` 项目里“被 `@Slf4k` 标注的类自动在 companion object 上长出
`private val logger: Logger = LoggerFactory.getLogger(FQN)`”的做法抽成可复用的
配方。目标读者是需要做“标注 → 长 companion object + 长字段 + 初始化字段”的
Kotlin 2.x / K2 编译器插件作者。

适用场景判断：

-   ✅ 在任意被注解的 `class` / `enum class` 上追加 companion object 及其
    字段（logger、metric registry、cache、serializer、feature flag 等）。
-   ✅ 同时要处理「用户已写 companion 时只补字段」「字段名可配置」「冲突时
    编译报错」这类质量需求。
-   ❌ 仅想在类体上追加实例字段 → 用法不同（直接 `getCallableNamesForClass` +
    `generateProperties` 走 owner = 被标注类本身，但要小心 primary constructor
    初始化时机）。
-   ❌ 需要做语法展开而非声明生成 → 该走 FIR checker 或 plugin IR transform，
    不在此 skill 覆盖范围。
-   ❌ 只想用反射在运行时补 logger → 不需要编译器插件。

## 核心心智模型

插件分三个相互独立的阶段，任何一个出错都会让最终产物“看起来对但不可用”。
**必须三阶段都实现**，不能跳：

| 阶段  | 接口                                   | 做什么                                                                                    |
| ----- | -------------------------------------- | ----------------------------------------------------------------------------------------- |
| FIR   | `FirDeclarationGenerationExtension`    | 声明层面告诉前端：这个类多了一个 companion；companion 上多了一个属性 + 构造器。仅“签名”。 |
| IR    | `IrGenerationExtension`                | 在降级到 IR 后，给 FIR 生成的 backing field 补上 **初始化表达式**（真正的 `= ...`）。      |
| glue  | `CompilerPluginRegistrar` + CLI + SPI  | 把两个扩展挂到编译器；解析 `-P plugin:<id>:key=value` 参数。                              |

### 为什么要分两阶段？

-   FIR 只能描述“这里有个 val，类型是 T”——它不知道怎么跑一个任意表达式，
    也不负责写字节码。
-   IR 能插任意调用 / 字面量，但看不到“属性该不该被生成”的注解语义——它只认
    `IrDeclarationOrigin.GeneratedByPlugin` 标记。
-   所以约定：FIR 生成**空壳**，IR 找到这个空壳填**初始化**。中间的握手靠
    `GeneratedDeclarationKey`。

### 为什么不在 companion 的 `<init>` 里直接给字段赋值？

JVM 下 `private val` on a companion 的 backing field 实际上是外部类的 private
static 字段。从 companion 的 `<clinit>` 去写这个字段会触发 `IllegalAccessError`。
正确做法：**IR 阶段把初始化表达式设为 `IrField.initializer`**，Kotlin 后端
自动把它编到外部类的 `<clinit>` 里。这条是踩过坑的硬约束，不要自作主张挪到
构造器里写。

## 项目骨架

一个典型的仓库长这样（对标 slf4k）：

```
my-plugin/
├── settings.gradle.kts
├── buildSrc/                      # kotlin-convention / publish-convention
├── gradle/libs.versions.toml      # kotlin 版本单一真源
├── my-annotations/                # 纯注解，language/apiVersion 锁 2.0 baseline
│   └── src/main/kotlin/.../Foo.kt
├── my-compiler-plugin/            # 本 skill 的主体
│   └── src/main/
│       ├── kotlin/.../
│       │   ├── PluginMetadata.kt            # id/artifact/option 常量
│       │   ├── Names.kt                     # ClassId + DeclarationKey
│       │   ├── ConfigurationKeys.kt         # CompilerConfigurationKey<*>
│       │   ├── CommandLineProcessor.kt      # 解析 -P key=value
│       │   ├── CompilerPluginRegistrar.kt   # 注册 FIR + IR
│       │   ├── fir/
│       │   │   ├── FirExtensionRegistrar.kt
│       │   │   └── DeclarationGenerationExtension.kt
│       │   └── ir/
│       │       ├── IrGenerationExtension.kt
│       │       └── IrTransformer.kt
│       └── resources/META-INF/services/
│           ├── org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
│           └── org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
├── my-gradle-plugin/              # KotlinCompilerPluginSupportPlugin 胶水
└── my-test/                       # 端到端 JUnit 样例 + 运行时断言
```

**版本坐标规则**（强烈推荐沿用 slf4k 的 KEFS 友好格式）：

-   `my-annotations` → `group:my-annotations:<slf4kSemver>`（锁
    `languageVersion = KOTLIN_2_0` 后一份 artifact 跨所有 Kotlin 2.x 用）。
-   `my-compiler-plugin` → `group:my-compiler-plugin:<kotlinVersion>-<semver>`。
-   `my-gradle-plugin` → `group:my-gradle-plugin:<kotlinVersion>-<semver>`。
-   `kotlin` 变量同时决定 KGP、`kotlin-compiler-embeddable` 与发布坐标的
    `<kotlinVersion>` 部分，避免“编译期 KGP 2.3.0 + 运行期 embeddable
    2.4.0-dev”这种 API drift。

---

## 阶段 0：注解模块

```kotlin
// my-annotations/src/main/kotlin/com/example/Foo.kt
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Foo
```

要点：

-   `SOURCE` 保留级别足够——编译器插件在编译期就消耗掉它，运行时不需要读回。
-   `build.gradle.kts` 里锁 Kotlin 2.0 baseline：

    ```kotlin
    kotlin {
        compilerOptions {
            languageVersion.set(KotlinVersion.KOTLIN_2_0)
            apiVersion.set(KotlinVersion.KOTLIN_2_0)
        }
    }
    ```

    这样 annotations 发一份即可被任意 Kotlin 2.x 用户工程消费，不随主构建
    `kotlin` 变量重发版。

---

## 阶段 1：公共常量

```kotlin
// PluginMetadata.kt
object PluginMetadata {
    const val GRADLE_PLUGIN_ID     = "com.example.foo"
    const val COMPILER_PLUGIN_ID   = "com.example.foo.compiler"
    const val ANNOTATIONS_ARTIFACT = "my-annotations"
    const val COMPILER_ARTIFACT    = "my-compiler-plugin"
    const val FIELD_NAME_OPTION    = "field-name"
    const val DEFAULT_FIELD_NAME   = "logger"
}

// Names.kt
object PluginNames {
    val annotationClassId = ClassId.topLevel(FqName("com.example.Foo"))
    val targetTypeClassId = ClassId.topLevel(FqName("org.slf4j.Logger"))
    val factoryClassId    = ClassId.topLevel(FqName("org.slf4j.LoggerFactory"))
}

object PluginDeclarationKey : GeneratedDeclarationKey() {
    override fun toString() = "PluginDeclarationKey"
}
```

`GeneratedDeclarationKey` 是 FIR 与 IR 之间的**握手令牌**。同一个单例实例贯穿
两阶段；IR 侧通过 `IrDeclarationOrigin.GeneratedByPlugin(key)` 反向识别自己
应该填初始化的那批字段。

---

## 阶段 2：`CompilerConfigurationKey` + `CommandLineProcessor`

```kotlin
object PluginConfigurationKeys {
    val fieldName = CompilerConfigurationKey<String>("plugin field name")
}

@OptIn(ExperimentalCompilerApi::class)
class PluginCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = PluginMetadata.COMPILER_PLUGIN_ID
    override val pluginOptions = listOf(FIELD_NAME_OPTION)

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option.optionName) {
            PluginMetadata.FIELD_NAME_OPTION -> {
                if (!Name.isValidIdentifier(value)) {
                    throw CliOptionProcessingException("'$value' is not a valid Kotlin identifier")
                }
                configuration.put(PluginConfigurationKeys.fieldName, value)
            }
            else -> error("Unknown option: ${option.optionName}")
        }
    }

    private companion object {
        val FIELD_NAME_OPTION = CliOption(
            optionName = PluginMetadata.FIELD_NAME_OPTION,
            valueDescription = "<identifier>",
            description = "Name of the generated field.",
            required = false,
            allowMultipleOccurrences = false,
        )
    }
}
```

`Name.isValidIdentifier` 在入口校验，避免脏值穿透到 FIR 再炸——FIR 的报错位置
对用户非常难看懂。

---

## 阶段 3：`CompilerPluginRegistrar`（挂 FIR + IR）

```kotlin
@OptIn(ExperimentalCompilerApi::class)
class PluginCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = PluginMetadata.COMPILER_PLUGIN_ID
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val fieldName = configuration[PluginConfigurationKeys.fieldName]
            ?: PluginMetadata.DEFAULT_FIELD_NAME
        val messageCollector = configuration[CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY]
            ?: MessageCollector.NONE

        FirExtensionRegistrarAdapter.registerExtension(
            PluginFirExtensionRegistrar(fieldName, messageCollector)
        )
        IrGenerationExtension.registerExtension(
            PluginIrGenerationExtension(fieldName)
        )
    }
}
```

**注意：** `supportsK2 = true` 不是可选项。K1 前端已退出历史舞台，声明生成
扩展在 K1 上根本不会被调用。

### SPI 服务声明

```
src/main/resources/META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
├── com.example.foo.compiler.PluginCompilerPluginRegistrar

src/main/resources/META-INF/services/org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
├── com.example.foo.compiler.PluginCommandLineProcessor
```

忘记写这两个文件，插件 jar 可以编译、可以发布，但**在用户工程里完全不触发**，
症状是“一切看起来都对，就是没长字段”。

---

## 阶段 4：FIR `FirExtensionRegistrar`

```kotlin
class PluginFirExtensionRegistrar @JvmOverloads constructor(
    private val fieldName: String = PluginMetadata.DEFAULT_FIELD_NAME,
    private val messageCollector: MessageCollector = MessageCollector.NONE,
) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +{ session: FirSession ->
            PluginDeclarationGenerationExtension(session, fieldName, messageCollector)
        }
    }
}
```

**关键点：保留 `@JvmOverloads` 无参构造器。** 这样 KEFS（Kotlin External FIR
Support，IDE 侧反射加载编译器插件 jar 的社区方案）才能在 IDE 会话里实例化
扩展。KEFS 路径下自定义 `fieldName` 不会透传——如需 IDE 感知用户自定义名，
要走 `FirExtensionSessionComponent` 的方案，不在此 skill 范围。

---

## 阶段 5：`FirDeclarationGenerationExtension`（最复杂的一块）

这是整个插件的大脑。它要回答四个问题：

1. 哪些外部类需要新增 companion？
2. 被新增的 companion 应该叫什么 name？
3. 哪些 companion 上需要新增哪些可调用成员（属性 + 构造器）？
4. 属性的签名（名字、类型、可见性）是什么？

```kotlin
class PluginDeclarationGenerationExtension(
    session: FirSession,
    fieldName: String,
    private val messageCollector: MessageCollector,
) : FirDeclarationGenerationExtension(session) {

    private val fieldNameStr = fieldName
    private val fieldPropertyName = Name.identifier(fieldName)
    private val predicate = LookupPredicate.create {
        annotated(PluginNames.annotationClassId.asSingleFqName())
    }
    private val reportedConflicts = mutableSetOf<ClassId>()

    // 1. 注册谓词：告诉前端我们关心被 @Foo 标注的类
    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(predicate)
    }

    // 2. 问：这个类需要新增哪些嵌套类？
    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext,
    ): Set<Name> {
        if (!classSymbol.isTargetAnnotated()) return emptySet()
        if (hasFieldConflict(classSymbol)) return emptySet()
        if (classSymbol.companionObjectSymbol() != null) return emptySet() // 用户已有 → 不新增
        return setOf(SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT)
    }

    // 3. 生成嵌套类：即 companion object 的 FirClassSymbol
    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext,
    ): FirClassLikeSymbol<*>? {
        if (name != SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT) return null
        if (!owner.isTargetAnnotated()) return null
        if (hasFieldConflict(owner)) return null
        if (owner.companionObjectSymbol() != null) return null
        return createCompanionObject(owner, PluginDeclarationKey).symbol
    }

    // 4. 问：这个 companion 上需要生成哪些 callable 名字？
    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext,
    ): Set<Name> {
        if (!classSymbol.isTargetCompanion()) return emptySet()
        val outer = classSymbol.outerTargetClass() ?: return emptySet()
        if (hasFieldConflict(outer)) return emptySet()
        // 插件新建的 companion 需要默认构造器；用户已有 companion 仅补字段。
        return if (classSymbol.isGeneratedCompanion()) {
            setOf(fieldPropertyName, SpecialNames.INIT)
        } else {
            setOf(fieldPropertyName)
        }
    }

    // 5. 生成默认构造器（仅对插件新建的 companion；用户已有 companion 由其自己的构造器负责）
    override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
        if (!context.owner.isGeneratedCompanion()) return emptyList()
        return listOf(createDefaultPrivateConstructor(context.owner, PluginDeclarationKey).symbol)
    }

    // 6. 生成属性签名
    override fun generateProperties(
        callableId: CallableId,
        context: MemberGenerationContext?,
    ): List<FirPropertySymbol> {
        val owner = context?.owner ?: return emptyList()
        if (callableId.callableName != fieldPropertyName) return emptyList()
        if (!owner.isTargetCompanion()) return emptyList()
        val outer = owner.outerTargetClass() ?: return emptyList()
        if (hasFieldConflict(outer)) return emptyList()

        val property = createMemberProperty(
            owner = owner,
            key = PluginDeclarationKey,
            name = fieldPropertyName,
            returnType = PluginNames.targetTypeClassId.constructClassLikeType(emptyArray(), false),
            isVal = true,
            hasBackingField = true,        // 必须要 backing field，IR 才有东西可填
        ) {
            visibility = Visibilities.Private
        }
        return listOf(property.symbol)
    }

    // ---- 工具方法 ----
    private fun FirClassSymbol<*>.isTargetAnnotated(): Boolean {
        if (classKind != ClassKind.CLASS && classKind != ClassKind.ENUM_CLASS) return false
        return session.predicateBasedProvider.matches(predicate, this)
    }

    private fun FirClassSymbol<*>.isTargetCompanion(): Boolean =
        isCompanionObjectSymbol() && outerTargetClass() != null

    private fun FirClassSymbol<*>.outerTargetClass(): FirClassSymbol<*>? {
        val outerId = classId.outerClassId ?: return null
        val outer = session.symbolProvider.getClassLikeSymbolByClassId(outerId) as? FirClassSymbol<*>
            ?: return null
        return outer.takeIf { it.isTargetAnnotated() }
    }

    @OptIn(DirectDeclarationsAccess::class)
    private fun FirClassSymbol<*>.companionObjectSymbol(): FirClassSymbol<*>? =
        declarationSymbols.filterIsInstance<FirClassSymbol<*>>().firstOrNull { it.isCompanion }

    private fun FirClassSymbol<*>.isCompanionObjectSymbol(): Boolean =
        classKind == ClassKind.OBJECT && isCompanion

    private fun FirClassSymbol<*>.isGeneratedCompanion(): Boolean {
        val origin = origin as? FirDeclarationOrigin.Plugin ?: return false
        return origin.key == PluginDeclarationKey
    }

    // 冲突检测：外部类或其 companion 里已有同名成员
    private fun hasFieldConflict(classSymbol: FirClassSymbol<*>): Boolean {
        val id = classSymbol.classId
        if (id in reportedConflicts) return true
        val onClass = classSymbol.hasDeclaredProperty(fieldPropertyName)
        val onCompanion = classSymbol.companionObjectSymbol()?.hasDeclaredProperty(fieldPropertyName) ?: false
        if (!onClass && !onCompanion) return false
        reportedConflicts += id
        messageCollector.report(
            CompilerMessageSeverity.ERROR,
            "Class ${id.asSingleFqName().asString()} already declares '$fieldNameStr'; cannot generate."
        )
        return true
    }

    @OptIn(DirectDeclarationsAccess::class)
    private fun FirClassSymbol<*>.hasDeclaredProperty(name: Name): Boolean =
        declarationSymbols.filterIsInstance<FirPropertySymbol>().any { it.name == name }
}
```

### 这段代码里容易踩的坑

1.  **6 个 override 全部实现，缺一不可。** 省略 `getCallableNamesForClass`
    意味着虽然 companion 存在但上面没有任何属性；省略 `generateConstructors`
    会让新建的 companion 没有 `<init>`，前端报构造器缺失。
2.  **判定目标时必须限定 `classKind`。** 允许 `CLASS` 与 `ENUM_CLASS`；显式
    排除 `OBJECT`（object 无 companion 语义）和 `INTERFACE`（companion on
    interface 语义不同）。
3.  **区分「自己生成的 companion」与「用户已写的 companion」。** 前者靠
    `FirDeclarationOrigin.Plugin.key == PluginDeclarationKey` 识别。
    只给前者生成默认构造器，否则会和用户自己的构造器打架。
4.  **谓词与 `DirectDeclarationsAccess`。** `LookupPredicate.create { annotated(...) }`
    是唯一可靠的“找到所有被 X 标注的类”的入口。而 `declarationSymbols`
    要显式 `@OptIn(DirectDeclarationsAccess::class)`。
5.  **冲突检测去重。** 没去重每次 IDE 增量分析都会刷一条 ERROR，体验极差。
6.  **嵌套类正确支持。** 用 `classId.outerClassId` 反向找 owner；不要用
    `containingClassLookupTag` 之类私有 API。

---

## 阶段 6：IR `IrGenerationExtension` + `IrElementTransformerVoid`

此阶段的唯一任务：**把 FIR 留下的空 backing field 填上初始化表达式。**

```kotlin
class PluginIrGenerationExtension(private val fieldName: String) : IrGenerationExtension {

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val factoryClass = pluginContext.finderForBuiltins()
            .findClass(PluginNames.factoryClassId) ?: return

        // 挑一个稳定的 overload。避免挑依赖 KClass<?>.java 这类 stdlib inline 属性的 overload，
        // 解析路径在新 Kotlin 里容易断。优先挑 (String) -> Logger。
        val factoryFn = factoryClass.owner.declarations
            .filterIsInstance<IrSimpleFunction>()
            .firstOrNull { fn ->
                fn.name.asString() == "getLogger" &&
                    fn.parameters.singleOrNull { it.kind == IrParameterKind.Regular }
                        ?.type?.classOrNull?.owner?.kotlinFqName?.asString() == "kotlin.String"
            }?.symbol ?: return

        val propertyName = Name.identifier(fieldName)
        val transformer = PluginIrTransformer(pluginContext, factoryFn, propertyName)
        moduleFragment.files.forEach { it.transformChildrenVoid(transformer) }
    }
}

class PluginIrTransformer(
    private val pluginContext: IrPluginContext,
    private val factoryFn: IrSimpleFunctionSymbol,
    private val propertyName: Name,
) : IrElementTransformerVoid() {

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun visitClass(declaration: IrClass): IrStatement {
        declaration.transformChildrenVoid(this)

        // 目标是**外部类**（被标注那层）。companion 自己不是外部类。
        if (declaration.isCompanion) return declaration
        if (!declaration.hasAnnotation(PluginNames.annotationClassId.asSingleFqName())) return declaration

        val category = declaration.kotlinFqName.asString()
        declaration.collectGeneratedFields(propertyName).forEach { field ->
            if (field.initializer != null) return@forEach
            val builder = DeclarationIrBuilder(pluginContext, field.symbol)
            field.initializer = builder.irExprBody(builder.buildFactoryCall(category))
        }
        return declaration
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun IrClass.collectGeneratedFields(name: Name): List<IrField> {
        val onSelf = declarations.filterIsInstance<IrProperty>()
            .mapNotNull { it.backingField?.takeIf { f -> f.name == name } }

        // 关键兜底：对于插件新生成的 companion，其 FIR 声明在当前 IR 阶段尚未完全
        // 落到 declarations 列表；必须通过 referenceProperties（或
        // pluginContext.finderForBuiltins().findProperties）拿 backingField。
        val fromCompanion = declarations.filterIsInstance<IrClass>()
            .filter { it.isCompanion }
            .flatMap { companion ->
                val fromDecls = companion.declarations.filterIsInstance<IrProperty>()
                    .mapNotNull { it.backingField?.takeIf { f -> f.name == name } }
                val callableId = CallableId(companion.classIdOrFail, name)
                val fromRef = pluginContext.finderForBuiltins().findProperties(callableId)
                    .mapNotNull { it.owner.backingField }
                (fromDecls + fromRef).distinct()
            }

        // 严格过滤：只认自己 origin key 的字段，不误伤用户自写的 val logger。
        return (onSelf + fromCompanion).filter { field ->
            val origin = field.correspondingPropertySymbol?.owner?.origin ?: field.origin
            (origin as? IrDeclarationOrigin.GeneratedByPlugin)?.pluginKey == PluginDeclarationKey
        }
    }

    private fun IrBuilderWithScope.buildFactoryCall(arg: String) =
        irCall(factoryFn).apply { arguments[0] = irString(arg) }
}
```

### IR 阶段的硬约束

1.  **不要在 companion 的 `<clinit>` 里给 backing field 赋值。** JVM 下会炸
    `IllegalAccessError`（见前“心智模型”）。把表达式放进 `IrField.initializer`，
    让 Kotlin 后端决定往哪里生成初始化字节码。
2.  **`referenceProperties` 兜底是必须的。** 只扫 `declarations` 对“用户原有
    companion”有效，但“插件新建 companion”在当前 IR 阶段可能尚未把新属性
    落到 declarations 里，必须经 `pluginContext.finderForBuiltins().findProperties(
    CallableId(companion.classIdOrFail, name))` 兜底。**这是一条“代码能过但
    运行时 NPE”的隐形坑**。
3.  **严格按 origin key 过滤。** 否则用户自己写的同名 `val logger` 也会被覆盖，
    并且可能把用户的初始化表达式抹掉。
4.  **`UnsafeDuringIrConstructionAPI` 是必要 opt-in**，不是警告。新版 IR API
    会把 `.owner`、`declarations` 这类需要树稳定的访问标成 unsafe。

---

## 阶段 7：Gradle 插件胶水

```kotlin
class PluginGradlePlugin : KotlinCompilerPluginSupportPlugin {

    override fun apply(target: Project) {
        target.extensions.create("myPlugin", PluginExtension::class.java)
        // 自动注入 annotations 依赖，用户在 dependencies 里不必手写
        target.dependencies.add(
            "implementation",
            "${Coordinates.GROUP}:${PluginMetadata.ANNOTATIONS_ARTIFACT}:${Coordinates.VERSION}",
        )
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = PluginMetadata.COMPILER_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = Coordinates.GROUP,
        artifactId = PluginMetadata.COMPILER_ARTIFACT,
        version = Coordinates.COMPILER_PLUGIN_VERSION,  // <kotlinVersion>-<semver>
    )

    override fun applyToCompilation(kc: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val ext = kc.target.project.extensions.getByType(PluginExtension::class.java)
        return kc.target.project.provider {
            buildList {
                ext.fieldName.orNull?.takeIf { it.isNotBlank() }
                    ?.let { add(SubpluginOption(PluginMetadata.FIELD_NAME_OPTION, it)) }
            }
        }
    }
}

abstract class PluginExtension {
    abstract val fieldName: Property<String>
}
```

`Coordinates` 是 `build.gradle.kts` 里生成的 Kotlin 文件，把发布坐标作为
常量打进 jar（见 slf4k 的 `generatePluginCoordinates` 任务）：

```kotlin
val generatePluginCoordinates by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/sources/coordinates/kotlin")
    val groupValue = project.group.toString()
    val semver = rootProject.version.toString()
    val compilerPluginVersion = "${libs.versions.kotlin.get()}-$semver"
    doLast {
        val file = outDir.get().file("com/example/foo/gradle/Coordinates.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("""
            package com.example.foo.gradle
            internal object Coordinates {
                const val GROUP = "$groupValue"
                const val VERSION = "$semver"
                const val COMPILER_PLUGIN_VERSION = "$compilerPluginVersion"
            }
        """.trimIndent() + "\n")
    }
}
kotlin.sourceSets["main"].kotlin.srcDir(generatePluginCoordinates)
```

---

## 阶段 8：测试

### 单元 / 集成：`slf4k-test` 风格

建一个 `my-test` 子项目，它用 `-Xplugin=<jar>` **直接**挂编译器插件（不走
Gradle 插件），方便在未发布时跑回归：

```kotlin
// my-test/build.gradle.kts（关键片段）
dependencies {
    implementation(project(":my-annotations"))
    testImplementation(kotlin("test"))
    testImplementation(libs.slf4jSimple)
}

val pluginJarTask = project(":my-compiler-plugin").tasks.named<Jar>("jar")

tasks.withType<KotlinCompile>().configureEach {
    dependsOn(pluginJarTask)
    val pluginJar = pluginJarTask.flatMap { it.archiveFile }
    compilerOptions {
        freeCompilerArgs.add(pluginJar.map { "-Xplugin=${it.asFile.absolutePath}" })
    }
}
```

**必须覆盖的测试矩阵**（这些是 slf4k 线上回归过的真实坑点）：

| 场景                       | 断言                                                       |
| -------------------------- | ---------------------------------------------------------- |
| `class` 无 companion       | 反射 `::class.companionObject` 非空；属性存在且 non-null。 |
| `class` 已有 companion     | 用户成员保留；插件属性追加；不报错。                       |
| `enum class`               | 枚举常量上可调用 `logger.info(...)`。                      |
| 嵌套类 `Outer.Nested`      | category 用点号 FQN，对应 `kotlinFqName`。                 |
| 一文件多 `@Foo`            | 多个 logger 独立实例。                                     |
| 冲突（类或 companion 同名）| 编译失败，MessageCollector 报 ERROR。                      |

### Gradle 插件：`GradleRunner` 功能测试

```kotlin
@Test
fun `applies plugin and generates logger`() {
    val projectDir = File(tempDir, "demo").apply { mkdirs() }
    File(projectDir, "settings.gradle.kts").writeText(/* ... */)
    File(projectDir, "build.gradle.kts").writeText("""
        plugins {
            kotlin("jvm") version "${System.getProperty("slf4k.kotlinVersion")}"
            id("com.example.foo") version "${System.getProperty("slf4k.version")}"
        }
        repositories { maven(url = "${System.getProperty("slf4k.testMavenRepo")}") }
    """)
    File(projectDir, "src/main/kotlin/Main.kt").writeText("""
        @com.example.Foo
        class Demo { fun hi() = logger.info("hi") }
    """)
    GradleRunner.create().withProjectDir(projectDir).withArguments("build").build()
}
```

功能测试要先把 annotations / compiler-plugin / gradle-plugin 的 jar 写到一个
临时 maven 仓库（`installTestArtifacts` 任务），再让测试项目从那儿解析。slf4k
的 `slf4k-gradle-plugin/build.gradle.kts` 有完整样板可直接抄。

---

## 常见故障速查

| 症状                                                      | 根因                                           | 修复                                                                          |
| --------------------------------------------------------- | ---------------------------------------------- | ----------------------------------------------------------------------------- |
| 编译通过但运行期 companion 上无字段                       | IR 阶段 `collectGeneratedFields` 漏 reference 兜底 | 加 `pluginContext.finderForBuiltins().findProperties(CallableId(...))`。 |
| `IllegalAccessError` 访问 logger                          | 试图在 companion 的 `<init>` 里赋值字段        | 改为设 `IrField.initializer`，不写构造器里的 `irSetField`。                   |
| 用户写了同名 val 也被插件覆盖                             | IR 过滤忘判 `IrDeclarationOrigin.GeneratedByPlugin` | 按 `origin.pluginKey == OurKey` 严格过滤。                                |
| K2 IDE 里 `logger.info(...)` 红（但 Gradle build 绿）     | IDE 未加载 FIR 扩展                            | 装 KEFS；确保 `FirExtensionRegistrar` 有无参构造器；KEFS 坐标用 `<k>-<semver>`。 |
| 插件 jar 在用户工程里完全无反应                           | 忘了写 `META-INF/services/*` 文件              | 补上 SPI 服务文件。                                                           |
| CLI 传非法 `field-name` 值时 FIR 报难懂的错               | 没在 `CommandLineProcessor` 入口做 `Name.isValidIdentifier` | 入口处 `throw CliOptionProcessingException`。                       |
| `CompilerConfigurationKey` 拿不到 value                   | `CompilerPluginRegistrar` 没从 configuration 读 key | 在 `registerExtensions` 里读并传给扩展构造器。                            |
| 生成 companion 时 `FirConstructorSymbol` 缺失             | `getCallableNamesForClass` 没返回 `SpecialNames.INIT` 且 `generateConstructors` 返回空 | 给自己生成的 companion 同时登记 INIT + 生成默认构造器。 |
| `object` / `interface` 也被误判进来                       | `isTargetAnnotated` 没限定 classKind           | 显式 `classKind == CLASS \|\| classKind == ENUM_CLASS`。                    |

---

## 检查清单（实现完一次对照一遍）

-   [ ] 注解模块锁 `languageVersion = KOTLIN_2_0`，发布单一 artifact。
-   [ ] `PluginDeclarationKey : GeneratedDeclarationKey` 单例，FIR / IR 共享。
-   [ ] `CompilerPluginRegistrar.supportsK2 = true`。
-   [ ] `META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar` 存在。
-   [ ] `META-INF/services/org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor` 存在。
-   [ ] `CommandLineProcessor` 入口校验 `Name.isValidIdentifier`。
-   [ ] `FirExtensionRegistrar` 带无参构造器（KEFS 要求）。
-   [ ] `FirDeclarationGenerationExtension` 实现：`registerPredicates`、
        `getNestedClassifiersNames`、`generateNestedClassLikeDeclaration`、
        `getCallableNamesForClass`、`generateConstructors`、`generateProperties`。
-   [ ] 区分「插件新建 companion」vs「用户已有 companion」，仅前者发默认构造器。
-   [ ] classKind 限定 `CLASS` / `ENUM_CLASS`。
-   [ ] 冲突检测去重 + 报 ERROR。
-   [ ] IR 阶段只在**外部类**的 `visitClass` 里工作（`isCompanion` 早退）。
-   [ ] `IrField.initializer` 设值；**不在**构造器里用 `irSetField`。
-   [ ] `collectGeneratedFields` 同时扫 `declarations` 与 `referenceProperties` 兜底。
-   [ ] 只过滤 origin `pluginKey == OurKey` 的字段。
-   [ ] 发布坐标用 `<kotlinVersion>-<semver>`（KEFS 友好），annotations 用纯 semver。
-   [ ] 集成测试覆盖 6 种场景（参见上方矩阵）。
-   [ ] 功能测试通过 `GradleRunner` 从临时 maven 仓库消费插件 jar。

---

## 参考实现

整套脚手架在 `F:\wang3\hami\slf4k` 下是生产可用的实现，逐个文件对照：

-   注解 → `slf4k-annotations/src/main/kotlin/top/wang3/slf4k/Slf4k.kt`
-   FIR → `slf4k-compiler-plugin/src/main/kotlin/top/wang3/slf4k/compiler/fir/`
-   IR → `slf4k-compiler-plugin/src/main/kotlin/top/wang3/slf4k/compiler/ir/`
-   CLI + Registrar → `slf4k-compiler-plugin/src/main/kotlin/top/wang3/slf4k/compiler/`
-   SPI → `slf4k-compiler-plugin/src/main/resources/META-INF/services/`
-   Gradle → `slf4k-gradle-plugin/src/main/kotlin/top/wang3/slf4k/gradle/`
-   测试 → `slf4k-test/src/test/kotlin/top/wang3/test/`

写新插件时推荐：先把 slf4k 抄下来，把「`Slf4k` / `Slf4K` / `slf4k`」三个字符
串替换成新名，把 IR 阶段里 `LoggerFactory.getLogger(String)` 的那段换成你的
目标工厂/构造器调用，就完成了 80% 的工作。剩下 20% 是按目标字段语义微调
`returnType` 与 `visibility`。
