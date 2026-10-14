dependencyResolutionManagement {

    // CI 机器 IP 被官方 maven 仓库拦截, 统一走阿里云公共仓库(代理 Maven Central), 不要再加回 mavenCentral()。
    @Suppress("UnstableApiUsage")
    repositories {
        maven("https://maven.aliyun.com/repository/public/")
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

// buildSrc 没有 pluginManagement 时, `kotlin-dsl` 插件及其传递依赖(如 abi-tools-api)只能走
// Gradle Plugin Portal, Portal 对非自托管构件会 302 到 repo.maven.apache.org(CI 上被 429),
// 因此这里同样接阿里云镜像; JetBrains 仓库供 IJ 捆绑版 kotlinc 兜底, Portal 仅作最后兜底。
pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/public/")
        maven {
            setUrl("https://maven.aliyun.com/repository/gradle-plugin")
        }
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/")
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
        gradlePluginPortal()
    }
}

rootProject.name = "buildSrc"