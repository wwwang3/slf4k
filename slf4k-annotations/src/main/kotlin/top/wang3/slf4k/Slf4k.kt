package top.wang3.slf4k

/**
 * 标注需要自动获得 slf4j [org.slf4j.Logger] 的 Kotlin 类。
 *
 * 编译期（FIR + IR）会为被标注的类：
 *  - 若缺失 companion object，则自动生成一个；
 *  - 在 companion object 上生成 `private val logger: Logger`（属性名由编译器插件参数
 *    `logger-name` 控制，默认 `logger`，Gradle 插件可通过 `slf4k { loggerName.set("log") }`
 *    覆盖）；
 *  - 以被标注类的 Kotlin FQN（如 `com.example.User.Nested`）作为 slf4j logger 的 category。
 *
 * 冲突处理：若被标注类或其 companion 已经存在同名属性，插件会以 ERROR 终止编译，
 * 避免与用户代码冲突。
 *
 * 仅对 `class` 和 `enum class` 生效；`object`、`interface`、`companion object` 自身
 * 不支持（无意义或由外层承载）。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Slf4k
