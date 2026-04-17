package top.wang3.slf4k.compiler.fir

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.utils.isCompanion
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createCompanionObject
import org.jetbrains.kotlin.fir.plugin.createDefaultPrivateConstructor
import org.jetbrains.kotlin.fir.plugin.createMemberProperty
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import top.wang3.slf4k.compiler.Slf4KDeclarationKey
import top.wang3.slf4k.compiler.Slf4KNames

class Slf4kLoggerDeclarationGenerationExtension(
    session: FirSession,
    loggerName: String,
    private val messageCollector: MessageCollector,
) : FirDeclarationGenerationExtension(session) {
    private val loggerNameAsString = loggerName
    private val loggerPropertyName = Name.identifier(loggerName)
    private val predicate = LookupPredicate.create {
        annotated(Slf4KNames.annotationClassId.asSingleFqName())
    }
    private val reportedConflicts = mutableSetOf<ClassId>()

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(predicate)
    }

    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext,
    ): Set<Name> {
        if (!classSymbol.isSlf4KAnnotatedClass()) return emptySet()
        if (hasLoggerConflict(classSymbol)) return emptySet()
        if (classSymbol.companionObjectSymbol() != null) return emptySet()
        return setOf(SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT)
    }

    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext,
    ): FirClassLikeSymbol<*>? {
        if (name != SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT) return null
        if (!owner.isSlf4KAnnotatedClass()) return null
        if (hasLoggerConflict(owner)) return null
        if (owner.companionObjectSymbol() != null) return null
        return createCompanionObject(owner, Slf4KDeclarationKey).symbol
    }

    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext,
    ): Set<Name> {
        if (!classSymbol.isSlf4KTargetCompanion()) return emptySet()
        val outerClass = classSymbol.outerSlf4KClass() ?: return emptySet()
        if (hasLoggerConflict(outerClass)) return emptySet()
        return if (classSymbol.isGeneratedSlf4KCompanion()) {
            setOf(loggerPropertyName, SpecialNames.INIT)
        } else {
            setOf(loggerPropertyName)
        }
    }

    override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
        // 仅给插件新建的 companion 生成默认构造器；用户已有 companion 由其原有构造器负责，
        // 注意不要在构造器里给 logger 赋值——JVM 下 companion 的 logger 背后是外部类的 private static
        // 字段，从 companion 访问会触发 IllegalAccessError，赋值逻辑交给 IR 后端编到外部类 <clinit>。
        if (!context.owner.isGeneratedSlf4KCompanion()) return emptyList()
        return listOf(createDefaultPrivateConstructor(context.owner, Slf4KDeclarationKey).symbol)
    }

    override fun generateProperties(
        callableId: CallableId,
        context: MemberGenerationContext?,
    ): List<FirPropertySymbol> {
        val owner = context?.owner ?: return emptyList()
        if (callableId.callableName != loggerPropertyName) return emptyList()
        if (!owner.isSlf4KTargetCompanion()) return emptyList()
        val outerClass = owner.outerSlf4KClass() ?: return emptyList()
        if (hasLoggerConflict(outerClass)) return emptyList()

        val property = createMemberProperty(
            owner = owner,
            key = Slf4KDeclarationKey,
            name = loggerPropertyName,
            returnType = Slf4KNames.loggerClassId.constructClassLikeType(emptyArray(), false),
            isVal = true,
            hasBackingField = true,
        ) {
            visibility = Visibilities.Private
        }

        return listOf(property.symbol)
    }

    private fun FirClassSymbol<*>.isSlf4KAnnotatedClass(): Boolean {
        if (classKind != ClassKind.CLASS && classKind != ClassKind.ENUM_CLASS) return false
        return session.predicateBasedProvider.matches(predicate, this)
    }

    private fun FirClassSymbol<*>.isSlf4KTargetCompanion(): Boolean {
        if (!isCompanionObjectSymbol()) return false
        return outerSlf4KClass() != null
    }

    private fun FirClassSymbol<*>.outerSlf4KClass(): FirClassSymbol<*>? {
        val outerClassId = classId.outerClassId ?: return null
        val outerClassSymbol = session.symbolProvider.getClassLikeSymbolByClassId(outerClassId) as? FirClassSymbol<*>
            ?: return null
        return outerClassSymbol.takeIf { it.isSlf4KAnnotatedClass() }
    }

    @OptIn(DirectDeclarationsAccess::class)
    private fun FirClassSymbol<*>.companionObjectSymbol(): FirClassSymbol<*>? =
        declarationSymbols
            .filterIsInstance<FirClassSymbol<*>>()
            .firstOrNull { it.isCompanion }

    private fun FirClassSymbol<*>.isCompanionObjectSymbol(): Boolean {
        return classKind == ClassKind.OBJECT && isCompanion
    }

    private fun FirClassSymbol<*>.isGeneratedSlf4KCompanion(): Boolean {
        val origin = origin as? FirDeclarationOrigin.Plugin ?: return false
        return origin.key == Slf4KDeclarationKey
    }

    private fun hasLoggerConflict(classSymbol: FirClassSymbol<*>): Boolean {
        val classId = classSymbol.classId
        if (classId in reportedConflicts) return true

        val hasClassPropertyConflict = classSymbol.hasDeclaredProperty(loggerPropertyName)
        val hasCompanionPropertyConflict = classSymbol.companionObjectSymbol()
            ?.hasDeclaredProperty(loggerPropertyName)
            ?: false

        if (!hasClassPropertyConflict && !hasCompanionPropertyConflict) {
            return false
        }

        reportedConflicts += classId
        messageCollector.report(
            CompilerMessageSeverity.ERROR,
            buildString {
                append("Class ")
                append(classId.asSingleFqName().asString())
                append(" already declares a member named '")
                append(loggerNameAsString)
                append("', so @Slf4k cannot generate the logger.")
            }
        )
        return true
    }

    @OptIn(DirectDeclarationsAccess::class)
    private fun FirClassSymbol<*>.hasDeclaredProperty(name: Name): Boolean {
        return declarationSymbols
            .filterIsInstance<FirPropertySymbol>()
            .any { it.name == name }
    }
}
