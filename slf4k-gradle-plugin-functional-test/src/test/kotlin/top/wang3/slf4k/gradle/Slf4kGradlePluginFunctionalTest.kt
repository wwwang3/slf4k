package top.wang3.slf4k.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Slf4kGradlePluginFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private val slf4kVersion: String = requiredProperty("slf4k.version")
    private val kotlinVersion: String = requiredProperty("slf4k.kotlinVersion")
    private val slf4jVersion: String = requiredProperty("slf4k.slf4jVersion")
    private val gradleDistributionUri: String = requiredProperty("slf4k.gradleDistributionUri")
    private val testKitDir: String = requiredProperty("slf4k.testKitDir")
    private val testMavenRepo: String = requiredProperty("slf4k.testMavenRepo")

    @Test
    fun `default logger name wires @Slf4k companion and matches class FQN`() {
        writeSettings()
        writeBuild(loggerName = null)
        writeSample(loggerFieldName = "logger")
        writeSampleTest(expectedFieldName = "logger", expectedLoggerName = "sample.Sample")

        val result = runGradle("test", "--stacktrace")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome, result.output)
    }

    @Test
    fun `loggerName extension forwards custom option to compiler plugin`() {
        writeSettings()
        writeBuild(loggerName = "log")
        writeSample(loggerFieldName = "log")
        writeSampleTest(expectedFieldName = "log", expectedLoggerName = "sample.Sample")

        val result = runGradle("test", "--stacktrace")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome, result.output)
    }

    @Test
    fun `missing slf4j api fails with a clear diagnostic`() {
        writeSettings()
        writeBuild(loggerName = null, includeSlf4jApi = false)
        writeAnnotatedOnlySample()

        val result = runGradleAndFail("compileKotlin", "--stacktrace")

        assertEquals(TaskOutcome.FAILED, result.task(":compileKotlin")?.outcome, result.output)
        assertTrue(
            result.output.contains("slf4k cannot generate logger") &&
                result.output.contains("org.slf4j:slf4j-api"),
            result.output,
        )
    }

    private fun runGradle(vararg args: String): BuildResult =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withGradleDistribution(URI(gradleDistributionUri))
            .withTestKitDir(File(testKitDir))
            .withArguments(*testArguments(args))
            .forwardOutput()
            .build()

    private fun runGradleAndFail(vararg args: String): BuildResult =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withGradleDistribution(URI(gradleDistributionUri))
            .withTestKitDir(File(testKitDir))
            .withArguments(*testArguments(args))
            .forwardOutput()
            .buildAndFail()

    private fun testArguments(args: Array<out String>): Array<String> =
        (listOf("--offline", "--console=plain") + args.toList()).toTypedArray()

    private fun writeSettings() {
        val repoUri = File(testMavenRepo).toURI().toString()
        File(projectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    maven { url = uri("$repoUri") }
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            dependencyResolutionManagement {
                repositories {
                    maven { url = uri("$repoUri") }
                    mavenCentral()
                }
            }
            rootProject.name = "slf4k-functional-sample"
            """.trimIndent()
        )
    }

    private fun writeBuild(loggerName: String?, includeSlf4jApi: Boolean = true) {
        val loggerBlock = loggerName
            ?.let { "slf4k { loggerName.set(\"$it\") }" }
            .orEmpty()
        val slf4jDependencies = if (includeSlf4jApi) {
            """
                implementation("org.slf4j:slf4j-api:$slf4jVersion")
                implementation("org.slf4j:slf4j-simple:$slf4jVersion")
            """.trimIndent()
        } else {
            ""
        }
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "$kotlinVersion"
                id("top.wang3.slf4k") version "$slf4kVersion"
            }

            $loggerBlock

            dependencies {
                $slf4jDependencies
                testImplementation(kotlin("reflect"))
                testImplementation(kotlin("test"))
            }

            tasks.test { useJUnitPlatform() }
            """.trimIndent()
        )
    }

    private fun writeAnnotatedOnlySample() {
        val dir = File(projectDir, "src/main/kotlin/sample").apply { mkdirs() }
        File(dir, "Sample.kt").writeText(
            """
            package sample

            import top.wang3.slf4k.Slf4k

            @Slf4k
            class Sample
            """.trimIndent()
        )
    }

    private fun writeSample(loggerFieldName: String) {
        val dir = File(projectDir, "src/main/kotlin/sample").apply { mkdirs() }
        File(dir, "Sample.kt").writeText(
            """
            package sample

            import top.wang3.slf4k.Slf4k

            @Slf4k
            class Sample {
                fun ping() {
                    $loggerFieldName.info("ok")
                }
            }
            """.trimIndent()
        )
    }

    private fun writeSampleTest(expectedFieldName: String, expectedLoggerName: String) {
        val dir = File(projectDir, "src/test/kotlin/sample").apply { mkdirs() }
        File(dir, "SampleTest.kt").writeText(
            """
            package sample

            import org.slf4j.Logger
            import kotlin.reflect.full.companionObject
            import kotlin.reflect.full.companionObjectInstance
            import kotlin.reflect.full.declaredMemberProperties
            import kotlin.reflect.jvm.isAccessible
            import kotlin.test.Test
            import kotlin.test.assertEquals
            import kotlin.test.assertNotNull

            class SampleTest {
                @Test
                fun generated_logger_is_accessible_and_named_after_class() {
                    val companion = assertNotNull(Sample::class.companionObject)
                    val property = companion.declaredMemberProperties
                        .firstOrNull { it.name == "$expectedFieldName" }
                        ?: error("expected '$expectedFieldName' property on companion")
                    property.isAccessible = true
                    val value = property.getter.call(Sample::class.companionObjectInstance)
                    val logger = assertNotNull(value as? Logger)
                    assertEquals("$expectedLoggerName", logger.name)
                }

                @Test
                fun sample_ping_runs_without_error() {
                    Sample().ping()
                }
            }
            """.trimIndent()
        )
    }

    private companion object {
        fun requiredProperty(key: String): String = System.getProperty(key)
            ?: error("system property '$key' missing; run via :slf4k-gradle-plugin-functional-test:test")
    }
}
