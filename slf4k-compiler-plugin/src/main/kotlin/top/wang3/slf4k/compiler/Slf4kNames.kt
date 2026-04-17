package top.wang3.slf4k.compiler

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

object Slf4KNames {
    val annotationClassId: ClassId = ClassId.topLevel(FqName("top.wang3.slf4k.Slf4k"))
    val loggerClassId: ClassId = ClassId.topLevel(FqName("org.slf4j.Logger"))
    val loggerFactoryClassId: ClassId = ClassId.topLevel(FqName("org.slf4j.LoggerFactory"))
}

object Slf4KDeclarationKey : GeneratedDeclarationKey() {
    override fun toString(): String = "Slf4KDeclarationKey"
}
