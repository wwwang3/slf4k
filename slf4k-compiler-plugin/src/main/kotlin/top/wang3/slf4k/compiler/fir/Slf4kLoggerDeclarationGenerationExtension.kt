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
import top.wang3.slf4k.compiler.Slf4kDiagnosticMessages

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
    private val reportedMissingSlf4jApi = mutableSetOf<ClassId>()

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(predicate)
    }

    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext,
    ): Set<Name> {
        // 只有 class / enum class 需要（也允许）生成承载 logger 的 companion；
        // object 把 logger 直接放进自身成员，interface 依旧不支持。
        if (!classSymbol.isSlf4KCompanionHost()) return emptySet()
        if (!canGenerateLogger(classSymbol)) return emptySet()
        if (classSymbol.companionObjectSymbol() != null) return emptySet()
        return setOf(SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT)
    }

    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext,
    ): FirClassLikeSymbol<*>? {
        if (name != SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT) return null
        if (!owner.isSlf4KCompanionHost()) return null
        if (!canGenerateLogger(owner)) return null
        if (owner.companionObjectSymbol() != null) return null
        return createCompanionObject(owner, Slf4KDeclarationKey).symbol
    }

    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext,
    ): Set<Name> {
        val hostClass = classSymbol.loggerHostClass() ?: return emptySet()
        if (!canGenerateLogger(hostClass)) return emptySet()
        // 插件新建的 companion 需要额外登记默认构造器；用户已有 companion 与 object 由自身构造器负责。
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
        val hostClass = owner.loggerHostClass() ?: return emptyList()
        if (!canGenerateLogger(hostClass)) return emptyList()

        // 属性在这里只声明签名，初始化表达式由 IR 阶段补写到 backing field 上：
        // companion 上的 logger 在 JVM 下是外部类的 private static 字段，从 companion 的构造器赋值
        // 会触发 IllegalAccessError；object 上的 logger 复用同一条路径，避免两套初始化逻辑。
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

    /**
     * 判断被标注目标是否可以承载 logger，不可以时顺带报告一次诊断（消息去重）。
     */
    private fun canGenerateLogger(hostClass: FirClassSymbol<*>): Boolean {
        return hasRequiredSlf4jApi(hostClass) && !hasLoggerConflict(hostClass)
    }

    /**
     * 返回由该符号承载 logger 时的目标类：companion 取其外部被标注类，object 取自身。
     *
     * 返回 null 表示该符号不是 @Slf4k 的生成目标，包括不受支持的 interface、annotation class
     * 以及显式标注的 companion object（其 logger 由外部类承载，重复生成会撞名）。
     */
    private fun FirClassSymbol<*>.loggerHostClass(): FirClassSymbol<*>? {
        if (isSlf4KTargetCompanion()) return outerSlf4KClass()
        if (isSlf4KTargetObject()) return this
        return null
    }

    private fun FirClassSymbol<*>.isSlf4KAnnotated(): Boolean {
        return session.predicateBasedProvider.matches(predicate, this)
    }

    /**
     * 需要生成 companion 来承载 logger 的目标：class 与 enum class。
     */
    private fun FirClassSymbol<*>.isSlf4KCompanionHost(): Boolean {
        return (classKind == ClassKind.CLASS || classKind == ClassKind.ENUM_CLASS) && isSlf4KAnnotated()
    }

    /**
     * 直接在自身成员上承载 logger 的目标：object（含 data object 与嵌套 object）。
     */
    private fun FirClassSymbol<*>.isSlf4KTargetObject(): Boolean {
        return classKind == ClassKind.OBJECT && !isCompanion && isSlf4KAnnotated()
    }

    private fun FirClassSymbol<*>.isSlf4KTargetCompanion(): Boolean {
        return isCompanionObjectSymbol() && outerSlf4KClass() != null
    }

    private fun FirClassSymbol<*>.outerSlf4KClass(): FirClassSymbol<*>? {
        val outerClassId = classId.outerClassId ?: return null
        val outerClassSymbol = session.symbolProvider.getClassLikeSymbolByClassId(outerClassId) as? FirClassSymbol<*>
            ?: return null
        return outerClassSymbol.takeIf { it.isSlf4KCompanionHost() }
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

    private fun hasRequiredSlf4jApi(classSymbol: FirClassSymbol<*>): Boolean {
        val missingSymbols = buildList {
            if (session.symbolProvider.getClassLikeSymbolByClassId(Slf4KNames.loggerClassId) == null) {
                add(Slf4KNames.loggerClassId.asSingleFqName().asString())
            }
            if (session.symbolProvider.getClassLikeSymbolByClassId(Slf4KNames.loggerFactoryClassId) == null) {
                add(Slf4KNames.loggerFactoryClassId.asSingleFqName().asString())
            }
        }
        if (missingSymbols.isEmpty()) return true

        val classId = classSymbol.classId
        if (reportedMissingSlf4jApi.add(classId)) {
            messageCollector.report(
                CompilerMessageSeverity.ERROR,
                Slf4kDiagnosticMessages.missingSlf4jApiForClass(
                    className = classId.asSingleFqName().asString(),
                    missingSymbols = missingSymbols,
                ),
            )
        }
        return false
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
