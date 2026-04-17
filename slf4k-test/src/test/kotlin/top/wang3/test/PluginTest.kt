package top.wang3.test

import org.slf4j.Logger
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PluginTest {

    @Test
    fun `@Slf4k generates a companion object when missing`() {
        assertNotNull(TestA::class.companionObject, "expected generated companion on TestA")
        println("success")
    }

    @Test
    fun `@Slf4k preserves an existing companion object`() {
        val companion = assertNotNull(TestB::class.companionObject, "expected companion on TestB")
        val name = companion.declaredMemberProperties.firstOrNull { it.name == "NAME" }
        assertNotNull(name, "user-defined companion member NAME must still exist")
        println("success")
    }

    @Test
    fun `generated logger is a live slf4j Logger named after the class`() {
        val loggerA = readLogger(TestA::class.java)
        val loggerB = readLogger(TestB::class.java)

        assertEquals("top.wang3.test.TestA", loggerA.name)
        assertEquals("top.wang3.test.TestB", loggerB.name)
        println("success")
    }

    @Test
    fun `logger writes to slf4j output when used from an instance method`() {
        val output = captureStdErr {
            TestA().greet("world")
            TestB().doWork()
        }

        assertTrue(
            output.contains("hello world") && output.contains("top.wang3.test.TestA"),
            "TestA.greet should emit an info message: $output"
        )
        assertTrue(
            output.contains("doing work in TestB") && output.contains("top.wang3.test.TestB"),
            "TestB.doWork should emit a warn message: $output"
        )
        println("success")
    }

    private fun readLogger(ownerClass: Class<*>): Logger {
        val kClass = ownerClass.kotlin
        val companion = assertNotNull(kClass.companionObject, "no companion on ${kClass.qualifiedName}")
        val property = companion.declaredMemberProperties
            .firstOrNull { it.name == "logger" }
            ?: error("companion of ${kClass.qualifiedName} has no 'logger' property")
        property.isAccessible = true
        val value = property.getter.call(kClass.companionObjectInstance)
        return assertNotNull(value as? Logger, "logger was not initialized")
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
