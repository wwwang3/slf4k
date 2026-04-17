package top.wang3.slf4k.compiler

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

object Slf4KNames {
    val annotationClassId: ClassId = ClassId.topLevel(FqName("top.wang3.slf4k.Slf4k"))
    val loggerClassId: ClassId = ClassId.topLevel(FqName("org.slf4j.Logger"))
    val loggerFactoryClassId: ClassId = ClassId.topLevel(FqName("org.slf4j.LoggerFactory"))
    val javaClassClassId: ClassId = ClassId.topLevel(FqName("java.lang.Class"))
    val getLoggerCallableId: CallableId = CallableId(
        loggerFactoryClassId.packageFqName,
        loggerFactoryClassId.relativeClassName,
        Name.identifier("getLogger")
    )
    val getJavaClassCallableId: CallableId = CallableId(
        FqName("kotlin.jvm"),
        Name.identifier("getJavaClass")
    )
    lateinit var messageCollector: MessageCollector
}

object Slf4KDeclarationKey : GeneratedDeclarationKey() {
    override fun toString(): String = "Slf4KDeclarationKey"
}


fun LOG(message: String) {
    Slf4KNames.messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, message)
}
