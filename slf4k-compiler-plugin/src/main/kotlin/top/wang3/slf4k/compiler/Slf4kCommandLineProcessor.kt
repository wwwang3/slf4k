package top.wang3.slf4k.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.name.Name

@OptIn(ExperimentalCompilerApi::class)
class Slf4kCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = Slf4kPluginMetadata.COMPILER_PLUGIN_ID

    override val pluginOptions: Collection<CliOption> = listOf(LOGGER_NAME_OPTION)

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option.optionName) {
            Slf4kPluginMetadata.LOGGER_NAME_OPTION_NAME -> {
                if (!Name.isValidIdentifier(value)) {
                    throw CliOptionProcessingException(
                        "The logger name '$value' is not a valid Kotlin identifier."
                    )
                }
                configuration.put(Slf4kConfigurationKeys.loggerName, value)
            }

            else -> error("Unknown option: ${option.optionName}")
        }
    }

    private companion object {
        val LOGGER_NAME_OPTION = CliOption(
            optionName = Slf4kPluginMetadata.LOGGER_NAME_OPTION_NAME,
            valueDescription = "<identifier>",
            description = "Name of the generated logger property.",
            required = false,
            allowMultipleOccurrences = false
        )
    }
}
