package top.wang3.test

import org.slf4j.Logger
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `object gets a logger on itself named after the object FQN`() {
        assertNull(ObjectDemo::class.companionObject, "an object must not gain a companion object")
        val logger = readObjectLogger(ObjectDemo::class, ObjectDemo)
        assertEquals("top.wang3.test.ObjectDemo", logger.name)
    }

    @Test
    fun `data object gets a logger on itself named after the data object FQN`() {
        assertNull(DataObjectSample::class.companionObject, "a data object must not gain a companion object")
        val logger = readObjectLogger(DataObjectSample::class, DataObjectSample)
        assertEquals("top.wang3.test.DataObjectSample", logger.name)
    }

    @Test
    fun `object nested in a class gets a logger named after its dotted FQN`() {
        val logger = readObjectLogger(ObjectHolder.Nested::class, ObjectHolder.Nested)
        assertEquals("top.wang3.test.ObjectHolder.Nested", logger.name)
    }

    @Test
    fun `object nested in an object gets a logger named after its dotted FQN`() {
        val logger = readObjectLogger(OuterObject.Inner::class, OuterObject.Inner)
        assertEquals("top.wang3.test.OuterObject.Inner", logger.name)
    }

    @Test
    fun `object logger writes to slf4j output when used from an object method`() {
        val output = captureStdErr { ObjectDemo.greet("object") }

        assertTrue(
            output.contains("hello object") && output.contains("top.wang3.test.ObjectDemo"),
            "ObjectDemo.greet should emit an info message: $output"
        )
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

    private fun readObjectLogger(kClass: KClass<*>, instance: Any): Logger {
        // object 上的 logger 是插件生成的私有成员，反射时直接取 backing field：
        // object 的成员 getter 在 kotlin-reflect 下不接受 receiver 参数，属性访问路径不适用。
        val field = kClass.java.getDeclaredField("logger")
        field.isAccessible = true
        return assertNotNull(field.get(instance) as? Logger, "logger was not initialized on ${kClass.qualifiedName}")
    }

    private fun captureStdErr(block: () -> Unit): String {
        val original = System.err
        val buffer = ByteArrayOutputStream()
        val stream = PrintStream(buffer, true, Charsets.UTF_8)
        System.setErr(stream)
        try {
            block()
        } finally {
            System.setErr(original)
        }
        return buffer.toString(Charsets.UTF_8)
    }
}
