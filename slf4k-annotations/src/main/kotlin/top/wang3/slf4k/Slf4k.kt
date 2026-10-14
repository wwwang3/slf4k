package top.wang3.slf4k

/**
 * 标注需要自动获得 slf4j [org.slf4j.Logger] 的 Kotlin 声明。
 *
 * 编译期（FIR + IR）会为被标注目标生成 `private val logger: Logger`（属性名由编译器插件参数
 * `logger-name` 控制，默认 `logger`，Gradle 插件可通过 `slf4k { loggerName.set("log") }`
 * 覆盖），并以被标注目标的 Kotlin FQN（如 `com.example.User.Nested`）作为 slf4j logger 的
 * category：
 *  - `class` 与 `enum class`：若缺失 companion object 则自动生成一个，logger 生成在该
 *    companion object 上；已有 companion object 时只追加属性；
 *  - `object` 与 `data object`：logger 直接生成在 object 自身的成员上。
 *
 * 冲突处理：若被标注目标（`class` 还包括其 companion object）已经存在同名属性，插件会以
 * ERROR 终止编译，避免与用户代码冲突。
 *
 * 仅对 `class`、`enum class` 与 `object` 生效；`interface`、`annotation class` 以及显式标注的
 * `companion object`（其 logger 由外部类承载）不支持。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Slf4k
