import org.gradle.jvm.tasks.Jar

plugins {
    id("kotlin-convention")
}

description = "Functional tests for the slf4k Gradle plugin"

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val slf4kVersion = libs.versions.slf4k.get()
val kotlinVersion = libs.versions.kotlin.get()
val slf4jVersion = libs.versions.slf4j.get()
val gradleDistributionUri = "https://mirrors.cloud.tencent.com/gradle/gradle-9.4.0-all.zip"

val groupValue = project.group.toString()
val compilerPluginVersionValue = project(":slf4k-compiler-plugin").version.toString()
val gradlePluginVersionValue = project(":slf4k-gradle-plugin").version.toString()

val testMavenRepo = layout.buildDirectory.dir("test-maven-repo")

val testExternalArtifacts by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    testExternalArtifacts("org.slf4j:slf4j-api:$slf4jVersion")
    testExternalArtifacts("org.slf4j:slf4j-simple:$slf4jVersion")
}

val annotationsJarTask = project(":slf4k-annotations").tasks.named<Jar>("jar")
val compilerPluginJarTask = project(":slf4k-compiler-plugin").tasks.named<Jar>("jar")
val gradlePluginJarTask = project(":slf4k-gradle-plugin").tasks.named<Jar>("jar")

val installTestArtifacts by tasks.registering {
    description = "Installs slf4k jars into a local maven repo for functional tests"
    dependsOn(annotationsJarTask, compilerPluginJarTask, gradlePluginJarTask)
    inputs.files(
        annotationsJarTask.flatMap { it.archiveFile },
        compilerPluginJarTask.flatMap { it.archiveFile },
        gradlePluginJarTask.flatMap { it.archiveFile },
        testExternalArtifacts,
    )
    outputs.dir(testMavenRepo)

    val repoDirProvider = testMavenRepo
    val annotationsJarProvider = annotationsJarTask.flatMap { it.archiveFile }
    val compilerJarProvider = compilerPluginJarTask.flatMap { it.archiveFile }
    val gradlePluginJarProvider = gradlePluginJarTask.flatMap { it.archiveFile }

    doLast {
        val repoDir = repoDirProvider.get().asFile
        repoDir.deleteRecursively()
        repoDir.mkdirs()
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
            version = gradlePluginVersionValue,
            jarFile = gradlePluginJarProvider.get().asFile,
            runtimeDependencies = listOf(Triple(groupValue, "slf4k-compiler-plugin", compilerPluginVersionValue)),
        )
        installArtifact(
            repoDir = repoDir,
            groupId = "org.slf4j",
            artifactId = "slf4j-api",
            version = slf4jVersion,
            jarFile = testExternalArtifacts.jarFile("slf4j-api", slf4jVersion),
        )
        installArtifact(
            repoDir = repoDir,
            groupId = "org.slf4j",
            artifactId = "slf4j-simple",
            version = slf4jVersion,
            jarFile = testExternalArtifacts.jarFile("slf4j-simple", slf4jVersion),
            runtimeDependencies = listOf(Triple("org.slf4j", "slf4j-api", slf4jVersion)),
        )
        installPluginMarker(
            repoDir = repoDir,
            pluginId = "top.wang3.slf4k",
            implementationGroupId = groupValue,
            implementationArtifactId = "slf4k-gradle-plugin",
            version = gradlePluginVersionValue,
        )
    }
}

fun Configuration.jarFile(artifactId: String, version: String): java.io.File =
    files.single { it.name == "$artifactId-$version.jar" }

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
    val markerDir = repoDir.resolve(pluginId.replace('.', '/'))
        .resolve(markerArtifactId)
        .resolve(version)
    markerDir.mkdirs()
    markerDir.resolve("$markerArtifactId-$version.pom").writeText(
        """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>$pluginId</groupId>
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

tasks.test {
    dependsOn(installTestArtifacts)
    systemProperty("slf4k.version", gradlePluginVersionValue)
    systemProperty("slf4k.kotlinVersion", kotlinVersion)
    systemProperty("slf4k.slf4jVersion", slf4jVersion)
    systemProperty("slf4k.gradleDistributionUri", gradleDistributionUri)
    systemProperty("slf4k.testKitDir", gradle.gradleUserHomeDir.absolutePath)
    val repoPath = testMavenRepo.map { it.asFile.absolutePath }
    doFirst {
        systemProperty("slf4k.testMavenRepo", repoPath.get())
    }
}
