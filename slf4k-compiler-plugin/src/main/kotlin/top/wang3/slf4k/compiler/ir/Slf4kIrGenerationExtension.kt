package top.wang3.slf4k.compiler.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name
import top.wang3.slf4k.compiler.Slf4KNames
import top.wang3.slf4k.compiler.Slf4kDiagnosticMessages

class Slf4kIrGenerationExtension(
    private val loggerName: String,
    private val messageCollector: MessageCollector,
) : IrGenerationExtension {

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        if (!moduleFragment.hasSlf4kAnnotatedClass()) return

        val loggerFactoryClass = pluginContext.finderForBuiltins().findClass(Slf4KNames.loggerFactoryClassId)
            ?: return reportMissingDependency(Slf4KNames.loggerFactoryClassId.asSingleFqName().asString())
        // 选用 LoggerFactory.getLogger(String) 重载并以类 FQN 作为 category；
        // KClass.java 在新版 Kotlin stdlib 里是 inline 属性，无法通过 CallableId 稳定解析，
        // 因此不走 getLogger(Class<?>) 路径。
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
            ?: return reportMissingDependency(
                "${Slf4KNames.loggerFactoryClassId.asSingleFqName().asString()}.getLogger(kotlin.String)"
            )
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

    private fun reportMissingDependency(missingSymbol: String) {
        messageCollector.report(
            CompilerMessageSeverity.ERROR,
            Slf4kDiagnosticMessages.missingSlf4jApiInIr(missingSymbol),
        )
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun IrModuleFragment.hasSlf4kAnnotatedClass(): Boolean =
        files.any { file ->
            file.declarations.any { declaration -> declaration.isOrContainsSlf4kAnnotatedClass() }
        }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun IrDeclaration.isOrContainsSlf4kAnnotatedClass(): Boolean {
        if (this !is IrClass) return false
        if (!isCompanion && hasAnnotation(Slf4KNames.annotationClassId.asSingleFqName())) return true
        return declarations.any { declaration -> declaration.isOrContainsSlf4kAnnotatedClass() }
    }
}
