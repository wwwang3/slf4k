package top.wang3.slf4k.compiler.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.classIdOrFail
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import top.wang3.slf4k.compiler.Slf4KDeclarationKey

class Slf4kIrTransformer(
    private val pluginContext: IrPluginContext,
    private val getLoggerFunction: IrSimpleFunctionSymbol,
    private val generatedLoggerName: Name,
) : IrElementTransformerVoid() {

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun visitClass(declaration: IrClass): IrStatement {
        declaration.transformChildrenVoid(this)

        if (declaration.isCompanion) return declaration

        val loggerCategory = declaration.kotlinFqName.asString()
        declaration.collectGeneratedLoggerFields(generatedLoggerName).forEach { field ->
            if (field.initializer != null) return@forEach
            val builder = DeclarationIrBuilder(pluginContext, field.symbol)
            field.initializer = builder.irExprBody(builder.buildGetLoggerCall(loggerCategory))
        }
        return declaration
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun IrClass.collectGeneratedLoggerFields(loggerName: Name): List<IrField> {
        val ownPropertyBackings = declarations
            .filterIsInstance<IrProperty>()
            .mapNotNull { property -> property.backingField?.takeIf { it.name == loggerName } }
        val companionFields = declarations
            .filterIsInstance<IrClass>()
            .filter { it.isCompanion }
            .flatMap { companion ->
                val fromDeclarations = companion.declarations
                    .filterIsInstance<IrProperty>()
                    .mapNotNull { property -> property.backingField?.takeIf { it.name == loggerName } }
                val fromReference = pluginContext
                    .referenceProperties(CallableId(companion.classIdOrFail, loggerName))
                    .mapNotNull { it.owner.backingField }
                (fromDeclarations + fromReference).distinct()
            }
        return (ownPropertyBackings + companionFields)
            .filter { field ->
                val origin = field.correspondingPropertySymbol?.owner?.origin ?: field.origin
                val plugin = origin as? IrDeclarationOrigin.GeneratedByPlugin ?: return@filter false
                plugin.pluginKey == Slf4KDeclarationKey
            }
    }

    private fun IrBuilderWithScope.buildGetLoggerCall(category: String) =
        irCall(getLoggerFunction).apply {
            arguments[0] = irString(category)
        }
}
