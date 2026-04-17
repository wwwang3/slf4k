package top.wang3.slf4k.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class Slf4KGradlePluginFunctionalTest {
    @TempDir
    lateinit var projectDir: Path

    @Test
    fun `generates logger inside a new companion object`() {
        writeBuild(
            source = """
                package demo

                import top.wang3.slf4k.Slf4K

                @Slf4K
                class DemoService {
                    fun loggerName(): String = logger.name
                }
            """.trimIndent(),
            testSource = """
                package demo

                import org.junit.jupiter.api.Assertions.assertEquals
                import org.junit.jupiter.api.Test

                class DemoServiceTest {
                    @Test
                    fun loggerIsGeneratedAndInitialized() {
                        assertEquals("demo.DemoService", DemoService().loggerName())
                    }
                }
            """.trimIndent(),
        )

        val result = gradleRunner("test").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
    }

    @Test
    fun `adds logger to an existing companion object and honors custom names`() {
        writeBuild(
            loggerName = "log",
            source = """
                package demo

                import top.wang3.slf4k.Slf4K

                @Slf4K
                class DemoService {
                    companion object {
                        fun marker(): String = "marker"
                    }

                    fun loggerName(): String = log.name
                }
            """.trimIndent(),
            testSource = """
                package demo

                import org.junit.jupiter.api.Assertions.assertEquals
                import org.junit.jupiter.api.Test

                class DemoServiceTest {
                    @Test
                    fun loggerIsAvailableOnExistingCompanion() {
                        assertEquals("demo.DemoService", DemoService().loggerName())
                        assertEquals("marker", DemoService.Companion.marker())
                    }
                }
            """.trimIndent(),
        )

        val result = gradleRunner("test").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
    }

    @Test
    fun `fails compilation when the class already has a conflicting property`() {
        writeBuild(
            source = """
                package demo

                import top.wang3.slf4k.Slf4K

                @Slf4K
                class DemoService {
                    val logger = "boom"
                }
            """.trimIndent(),
        )

        val result = gradleRunner("compileKotlin").buildAndFail()

        assertTrue(
            result.output.contains("already declares a member named 'logger'"),
            result.output
        )
    }

    @Test
    fun `fails compilation when the companion already has a conflicting property`() {
        writeBuild(
            source = """
                package demo

                import top.wang3.slf4k.Slf4K

                @Slf4K
                class DemoService {
                    companion object {
                        val logger = "boom"
                    }
                }
            """.trimIndent(),
        )

        val result = gradleRunner("compileKotlin").buildAndFail()

        assertTrue(
            result.output.contains("already declares a member named 'logger'"),
            result.output
        )
    }

    @Test
    fun `plugin contributes the annotation dependency automatically`() {
        writeBuild(
            source = """
                package demo

                import top.wang3.slf4k.Slf4K

                @Slf4K
                class DemoService {
                    fun loggerName(): String = logger.name
                }
            """.trimIndent(),
        )

        val result = gradleRunner("compileKotlin").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlin")?.outcome)
    }

    @Test
    fun `missing slf4j api still fails explicitly`() {
        writeBuild(
            includeSlf4jApi = false,
            source = """
                package demo

                import top.wang3.slf4k.Slf4K

                @Slf4K
                class DemoService {
                    fun loggerName(): String = logger.name
                }
            """.trimIndent(),
        )

        val result = gradleRunner("compileKotlin").buildAndFail()

        assertTrue(
            result.output.contains("org.slf4j.Logger") || result.output.contains("org.slf4j.LoggerFactory"),
            result.output
        )
    }

    private fun writeBuild(
        loggerName: String = "logger",
        includeSlf4jApi: Boolean = true,
        source: String,
        testSource: String? = null,
    ) {
        projectDir.resolve("src/main/kotlin/demo").createDirectories()
        projectDir.resolve("src/main/kotlin/demo/DemoService.kt").writeText(source)

        if (testSource != null) {
            projectDir.resolve("src/test/kotlin/demo").createDirectories()
            projectDir.resolve("src/test/kotlin/demo/DemoServiceTest.kt").writeText(testSource)
        }

        projectDir.resolve("settings.gradle.kts").writeText(
            """
                pluginManagement {
                    includeBuild("${repoRoot().toString().replace("\\", "/")}")
                    repositories {
                        mavenCentral()
                        maven("https://maven.aliyun.com/repository/gradle-plugin")
                        gradlePluginPortal()
                    }
                }

                includeBuild("${repoRoot().toString().replace("\\", "/")}")

                dependencyResolutionManagement {
                    repositories {
                        mavenCentral()
                    }
                }

                rootProject.name = "functional-test"
            """.trimIndent()
        )

        projectDir.resolve("build.gradle.kts").writeText(
            """
                plugins {
                    kotlin("jvm") version "${systemProperty("slf4k.kotlinVersion")}"
                    id("top.wang3.slf4k")
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    ${if (includeSlf4jApi) """implementation("org.slf4j:slf4j-api:${systemProperty("slf4k.slf4jVersion")}")""" else ""}
                    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
                    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
                    testRuntimeOnly("org.slf4j:slf4j-simple:${systemProperty("slf4k.slf4jVersion")}")
                }

                slf4k {
                    loggerName.set("$loggerName")
                }

                tasks.test {
                    useJUnitPlatform()
                }
            """.trimIndent()
        )
    }

    private fun gradleRunner(vararg arguments: String): GradleRunner {
        val gradleUserHome = repoRoot()
            .resolve("build")
            .resolve("test-gradle-user-home")
            .resolve(UUID.randomUUID().toString())
        gradleUserHome.createDirectories()

        return GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments(*arguments, "-g", gradleUserHome.toString(), "--stacktrace")
            .forwardOutput()
    }

    private fun repoRoot(): Path = Path.of(systemProperty("slf4k.repoRoot"))

    private fun systemProperty(name: String): String = System.getProperty(name)
        ?: error("Missing system property: $name")
}
