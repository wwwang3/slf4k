package top.wang3.slf4k.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.MessageCollectorAccess
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import top.wang3.slf4k.compiler.fir.Slf4kFirExtensionRegistrar
import top.wang3.slf4k.compiler.ir.Slf4kIrGenerationExtension

@OptIn(ExperimentalCompilerApi::class)
class Slf4kCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = Slf4kPluginMetadata.COMPILER_PLUGIN_ID

    override val supportsK2: Boolean = true

    // 插件在 FIR/IR 阶段延迟报告消息, 无法改用 CompilerConfiguration.report(需要
    // KtSourcelessDiagnosticFactory 且 diagnosticsCollector 在 KEFS/测试通路下可能未初始化),
    // 因此保留 MessageCollector 通道并显式 opt-in。
    @OptIn(MessageCollectorAccess::class)
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val loggerName = configuration[Slf4kConfigurationKeys.loggerName]
            ?: Slf4kPluginMetadata.DEFAULT_LOGGER_NAME
        val messageCollector = configuration.messageCollector
        FirExtensionRegistrarAdapter.registerExtension(
            Slf4kFirExtensionRegistrar(loggerName, messageCollector)
        )
        IrGenerationExtension.registerExtension(
            Slf4kIrGenerationExtension(loggerName, messageCollector)
        )
    }
}
