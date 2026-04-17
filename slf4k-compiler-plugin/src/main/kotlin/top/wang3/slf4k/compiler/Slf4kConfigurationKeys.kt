package top.wang3.slf4k.compiler

import org.jetbrains.kotlin.config.CompilerConfigurationKey

object Slf4kConfigurationKeys {
    val loggerName = CompilerConfigurationKey<String>("slf4k logger name")
}
