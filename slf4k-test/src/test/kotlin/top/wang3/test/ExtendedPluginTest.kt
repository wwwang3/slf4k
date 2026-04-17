package top.wang3.test

import org.slf4j.Logger
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class ExtendedPluginTest {

    @Test
    fun `enum class gets a logger named after the enum FQN`() {
        val logger = readLogger(EnumSample::class)
        assertEquals("top.wang3.test.EnumSample", logger.name)
    }

    @Test
    fun `nested class gets a logger named after its dotted FQN`() {
        val logger = readLogger(OuterSample.Nested::class)
        assertEquals("top.wang3.test.OuterSample.Nested", logger.name)
    }

    @Test
    fun `multiple Slf4k classes in one file stay independent`() {
        val a = readLogger(MultiA::class)
        val b = readLogger(MultiB::class)
        assertEquals("top.wang3.test.MultiA", a.name)
        assertEquals("top.wang3.test.MultiB", b.name)
        assertNotSame(a, b)
    }

    private fun readLogger(kClass: KClass<*>): Logger {
        val companion = assertNotNull(kClass.companionObject, "no companion on ${kClass.qualifiedName}")
        val property = companion.declaredMemberProperties
            .firstOrNull { it.name == "logger" }
            ?: error("companion of ${kClass.qualifiedName} has no 'logger' property")
        property.isAccessible = true
        val value = property.getter.call(kClass.companionObjectInstance)
        return assertNotNull(value as? Logger, "logger was not initialized on ${kClass.qualifiedName}")
    }
}
