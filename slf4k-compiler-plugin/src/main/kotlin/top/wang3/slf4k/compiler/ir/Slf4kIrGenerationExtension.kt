package top.wang3.slf4k.compiler.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name
import top.wang3.slf4k.compiler.Slf4KNames

class Slf4kIrGenerationExtension(
    private val loggerName: String,
) : IrGenerationExtension {

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val loggerFactoryClass = pluginContext.referenceClass(Slf4KNames.loggerFactoryClassId) ?: return
        val getLoggerFunction = loggerFactoryClass.owner.declarations
            .filterIsInstance<IrSimpleFunction>()
            .firstOrNull { fn ->
                fn.name.asString() == "getLogger" &&
                    fn.parameters.singleOrNull { it.kind == IrParameterKind.Regular }
                        ?.type
                        ?.classOrNull
                        ?.owner
                        ?.kotlinFqName
                        ?.asString() == "kotlin.String"
            }
            ?.symbol
            ?: return
        val generatedLoggerName = Name.identifier(loggerName)

        val transformer = Slf4kIrTransformer(
            pluginContext = pluginContext,
            getLoggerFunction = getLoggerFunction,
            generatedLoggerName = generatedLoggerName,
        )

        moduleFragment.files.forEach { file ->
            file.transformChildrenVoid(transformer)
        }
    }
}
