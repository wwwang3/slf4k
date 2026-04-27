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

val generatePluginCoordinates by tasks.registering {
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

val functionalTest: SourceSet by sourceSets.creating
configurations[functionalTest.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())
configurations[functionalTest.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    functionalTest.implementationConfigurationName(gradleTestKit())
    functionalTest.implementationConfigurationName(kotlin("test"))
    functionalTest.implementationConfigurationName(libs.junit.jupiter)
    functionalTest.runtimeOnlyConfigurationName(libs.junit.platform.launcher)
}

gradlePlugin.testSourceSets(functionalTest)

val testMavenRepo: Provider<Directory> = layout.buildDirectory.dir("test-maven-repo")

val annotationsJarTask = project(":slf4k-annotations").tasks.named<Jar>("jar")
val compilerPluginJarTask = project(":slf4k-compiler-plugin").tasks.named<Jar>("jar")

val installTestArtifacts by tasks.registering {
    description = "Installs slf4k jars into a local maven repo for functional tests"
    dependsOn(annotationsJarTask, compilerPluginJarTask, tasks.named("jar"))
    inputs.files(
        annotationsJarTask.map { it.archiveFile },
        compilerPluginJarTask.map { it.archiveFile },
        tasks.named<Jar>("jar").map { it.archiveFile },
    )
    outputs.dir(testMavenRepo)
    val groupValue = project.group.toString()

    val pluginArtifactVersion = project.version.toString()

    val repoDirProvider = testMavenRepo
    val annotationsJarProvider = annotationsJarTask.flatMap { it.archiveFile }
    val compilerJarProvider = compilerPluginJarTask.flatMap { it.archiveFile }
    val gradlePluginJarProvider = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    doLast {
        val repoDir = repoDirProvider.get().asFile
        installArtifact(
            repoDir = repoDir,
            groupId = groupValue,
            artifactId = "slf4k-annotations",
            version = slf4kVersion,
            jarFile = annotationsJarProvider.get().asFile,
        )
        installArtifact(
            repoDir = repoDir,
            groupId = groupValue,
            artifactId = "slf4k-compiler-plugin",
            version = compilerPluginVersionValue,
            jarFile = compilerJarProvider.get().asFile,
        )
        installArtifact(
            repoDir = repoDir,
            groupId = groupValue,
            artifactId = "slf4k-gradle-plugin",
            version = pluginArtifactVersion,
            jarFile = gradlePluginJarProvider.get().asFile,
            runtimeDependencies = listOf(Triple(groupValue, "slf4k-compiler-plugin", compilerPluginVersionValue)),
        )
        installPluginMarker(
            repoDir = repoDir,
            pluginId = "top.wang3.slf4k",
            implementationGroupId = groupValue,
            implementationArtifactId = "slf4k-gradle-plugin",
            version = pluginArtifactVersion,
        )
    }
}

fun installArtifact(
    repoDir: java.io.File,
    groupId: String,
    artifactId: String,
    version: String,
    jarFile: java.io.File,
    runtimeDependencies: List<Triple<String, String, String>> = emptyList(),
) {
    val artifactDir = repoDir.resolve(groupId.replace('.', '/'))
        .resolve(artifactId)
        .resolve(version)
    artifactDir.mkdirs()
    jarFile.copyTo(artifactDir.resolve("$artifactId-$version.jar"), overwrite = true)
    val depsXml = runtimeDependencies.joinToString("\n") { (g, a, v) ->
        """  <dependency>
    <groupId>$g</groupId>
    <artifactId>$a</artifactId>
    <version>$v</version>
    <scope>runtime</scope>
  </dependency>"""
    }
    val depsBlock = if (runtimeDependencies.isEmpty()) "" else "<dependencies>\n$depsXml\n</dependencies>"
    artifactDir.resolve("$artifactId-$version.pom").writeText(
        """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>$groupId</groupId>
  <artifactId>$artifactId</artifactId>
  <version>$version</version>
  <packaging>jar</packaging>
  $depsBlock
</project>
""",
    )
}

fun installPluginMarker(
    repoDir: java.io.File,
    pluginId: String,
    implementationGroupId: String,
    implementationArtifactId: String,
    version: String,
) {
    val markerArtifactId = "$pluginId.gradle.plugin"
    val markerGroupId = pluginId
    val markerDir = repoDir.resolve(markerGroupId.replace('.', '/'))
        .resolve(markerArtifactId)
        .resolve(version)
    markerDir.mkdirs()
    markerDir.resolve("$markerArtifactId-$version.pom").writeText(
        """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>$markerGroupId</groupId>
  <artifactId>$markerArtifactId</artifactId>
  <version>$version</version>
  <packaging>pom</packaging>
  <dependencies>
    <dependency>
      <groupId>$implementationGroupId</groupId>
      <artifactId>$implementationArtifactId</artifactId>
      <version>$version</version>
    </dependency>
  </dependencies>
</project>
""",
    )
}

val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs Gradle TestKit functional tests for the slf4k plugin"
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.named("test"))
    dependsOn(installTestArtifacts)
    systemProperty("slf4k.version", project.version.toString())
    systemProperty("slf4k.kotlinVersion", libs.versions.kotlin.get())
    systemProperty("slf4k.slf4jVersion", libs.versions.slf4j.get())
    val repoPath = testMavenRepo.map { it.asFile.absolutePath }
    doFirst {
        systemProperty("slf4k.testMavenRepo", repoPath.get())
    }
}

tasks.named("check") { dependsOn(functionalTestTask) }
