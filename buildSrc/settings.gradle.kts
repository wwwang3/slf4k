dependencyResolutionManagement {

    // Use Maven Central and the Gradle Plugin Portal for resolving dependencies in the shared build logic (`buildSrc`) project.
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        // 与主构建一致：允许 -Pkotlin.version=2.4.0-dev-XXXX 时 buildSrc 也能从 JetBrains dev 仓库解析 KGP
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/")
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    }

    // Reuse the version catalog from the main build.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
            // 与主构建保持一致：`-Pkotlin.version=X` 同时覆盖 buildSrc 里的 KGP 版本，
            // 否则 buildSrc 与主构建两侧 Kotlin 可能不同版。
            providers.gradleProperty("kotlin.version").orNull?.let { version("kotlin", it) }
        }
    }
}

rootProject.name = "buildSrc"