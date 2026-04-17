# slf4k

`slf4k` 是一个 Kotlin 编译器插件，通过 `@Slf4k` 注解自动为类生成 slf4j `Logger`，消除手写
`private val logger = LoggerFactory.getLogger(Foo::class.java)` 的样板代码。

```kotlin
import top.wang3.slf4k.Slf4k

@Slf4k
class UserService {
    fun greet(name: String) {
        logger.info("hello {}", name)   // logger 由插件在 companion object 上生成
    }
}
```

生成效果（概念等价）：

```kotlin
class UserService {
    fun greet(name: String) { logger.info("hello {}", name) }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger("com.example.UserService")
    }
}
```

## 模块

| 模块 | 作用 |
| --- | --- |
| `slf4k-annotations` | `@Slf4k` 注解定义 |
| `slf4k-compiler-plugin` | FIR + IR 编译期插件实现 |
| `slf4k-gradle-plugin` | Gradle 插件胶水层（`KotlinCompilerPluginSupportPlugin`） |

## 接入方式

### 1. Gradle 插件（推荐）

```kotlin
plugins {
    kotlin("jvm") version "2.3.0"
    id("top.wang3.slf4k") version "0.0.1"
}

// 可选：覆盖生成的 logger 属性名，默认 "logger"
slf4k {
    loggerName.set("log")
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.17")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")
    // slf4k-annotations 由 Gradle 插件自动注入到 implementation 配置，无需手写
}
```

### 2. 编译器插件直挂（仅在无法使用 Gradle 插件时使用）

```kotlin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

dependencies {
    implementation("top.wang3.slf4k:slf4k-annotations:0.0.1")
    implementation("org.slf4j:slf4j-api:2.0.17")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xplugin=/path/to/slf4k-compiler-plugin-0.0.1.jar")
        // 可选：自定义 logger 属性名
        freeCompilerArgs.addAll(
            "-P", "plugin:top.wang3.slf4k.compiler:logger-name=log",
        )
    }
}
```

## 行为细节

- `@Slf4k` 仅对 `class` 与 `enum class` 生效；不支持 `object`、`interface`。
- 若类本身或其 companion object 已经存在与 `loggerName` 同名的属性，插件会以 ERROR 终止
  编译，避免静默覆盖。
- 生成的 logger category 为被标注类的 Kotlin FQN（嵌套类使用点号分隔）。
- 当用户已定义 companion object 时，插件只在已有 companion 上追加 logger 属性，不会替换它。

## 构建与测试

```bash
./gradlew build                              # 全量构建
./gradlew :slf4k-test:test                   # 编译器插件集成测试
./gradlew :slf4k-gradle-plugin:check         # Gradle 插件 functionalTest（GradleRunner）
```

## 关键坐标

- group: `top.wang3.slf4k`
- version: `0.0.1`
- Kotlin: `2.3.0`
- slf4j: `2.0.17`
- Gradle 插件 ID: `top.wang3.slf4k`
- 编译器插件 ID: `top.wang3.slf4k.compiler`
- CLI option: `logger-name`（必须是合法 Kotlin 标识符）
