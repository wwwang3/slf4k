package top.wang3.slf4k.compiler.fir

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import top.wang3.slf4k.compiler.Slf4kPluginMetadata

// 该类同时服务于两条装载通路：
//  - 编译器插件路径：Slf4kCompilerPluginRegistrar 直接以 (loggerName, messageCollector) 实例化并注册；
//  - KEFS (Kotlin External FIR Support) 路径：IDE 侧通过反射走无参构造器实例化，因此必须保留可用的
//    无参构造器，参数退化为默认值。KEFS 通路下暂不支持自定义 loggerName；如需 IDE 也感知自定义名，
//    参见 README 的 "IDE 支持" 小节。
class Slf4kFirExtensionRegistrar @JvmOverloads constructor(
    private val loggerName: String = Slf4kPluginMetadata.DEFAULT_LOGGER_NAME,
    private val messageCollector: MessageCollector = MessageCollector.NONE,
) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +{ session: FirSession ->
            Slf4kLoggerDeclarationGenerationExtension(
                session = session,
                loggerName = loggerName,
                messageCollector = messageCollector,
            )
        }
    }
}
