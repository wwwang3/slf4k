package top.wang3.slf4k.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import top.wang3.slf4k.compiler.fir.Slf4kFirExtensionRegistrar
import top.wang3.slf4k.compiler.ir.Slf4kIrGenerationExtension

@OptIn(ExperimentalCompilerApi::class)
class Slf4kCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = Slf4kPluginMetadata.COMPILER_PLUGIN_ID

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val loggerName = configuration.get(Slf4kConfigurationKeys.loggerName)
            ?: Slf4kPluginMetadata.DEFAULT_LOGGER_NAME
        val messageCollector = configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY)
            ?: MessageCollector.NONE
        messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, "register plugin")
        Slf4KNames.messageCollector = messageCollector
        FirExtensionRegistrarAdapter.registerExtension(
            Slf4kFirExtensionRegistrar(loggerName, messageCollector)
        )
        IrGenerationExtension.registerExtension(
            Slf4kIrGenerationExtension(loggerName)
        )
    }
}
