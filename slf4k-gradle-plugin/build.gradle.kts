plugins {
    id("kotlin-convention")
    id("publish-convention")
    `java-gradle-plugin`
}

description = "Gradle plugin that wires the slf4k Kotlin compiler plugin into user builds"

dependencies {
    compileOnly(libs.kotlin.gradle.plugin.api)
    implementation(project(":slf4k-compiler-plugin")) {
        isTransitive = false
    }
}

val slf4kVersion = libs.versions.slf4k.get()
val compilerPluginVersionValue = project.version.toString()
val groupValue = project.group.toString()

val generatePluginCoordinates = tasks.register("generatePluginCoordinates") {
    description = "generate slf4k gradle plugin coordinates"
    val outDir = layout.buildDirectory.dir("generated/sources/slf4k-coordinates/kotlin")
    inputs.property("group", groupValue)
    inputs.property("slf4kVersion", slf4kVersion)
    inputs.property("compilerPluginVersion", compilerPluginVersionValue)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("top/wang3/slf4k/gradle/Slf4kPluginCoordinates.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package top.wang3.slf4k.gradle

            internal object Slf4kPluginCoordinates {
                const val GROUP = "$groupValue"
                // slf4k semver，用于 slf4k-annotations 坐标（单一 artifact、跨 Kotlin 版本通用）。
                const val VERSION = "$slf4kVersion"
                const val COMPILER_PLUGIN_VERSION = "$compilerPluginVersionValue"
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin.sourceSets["main"].kotlin.srcDir(generatePluginCoordinates)

gradlePlugin {
    plugins {
        register("slf4k") {
            id = "top.wang3.slf4k"
            displayName = "slf4k"
            description = "Auto-generates slf4j Logger fields from @Slf4k"
            implementationClass = "top.wang3.slf4k.gradle.Slf4kGradlePlugin"
        }
    }
}
