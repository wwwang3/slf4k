package top.wang3.slf4k.compiler.fir

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class Slf4kFirExtensionRegistrar(
    private val loggerName: String,
    private val messageCollector: MessageCollector,
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
