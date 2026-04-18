// The settings file is the entry point of every Gradle build.
// Its primary purpose is to define the subprojects.
// It is also used for some aspects of project-wide configuration, like managing plugins, dependencies, etc.
// https://docs.gradle.org/current/userguide/settings_file_basics.html

dependencyResolutionManagement {
    versionCatalogs {
        // `libs` 已由 Gradle 从 gradle/libs.versions.toml 自动创建（已 from() 过一次），
        // 这里只补一次 kotlin 版本覆盖，不要再调 from()。
        // `-Pkotlin.version=2.4.0-dev-XXXX` 可在不改 libs.versions.toml 的前提下切版，
        // 用于 CI 矩阵并行产出多 Kotlin 版本的 artifact。
        create("libs") {
            providers.gradleProperty("kotlin.version").orNull?.let { version("kotlin", it) }
        }
    }
    @Suppress("UnstableApiUsage")
    repositories {

        val cnbToken = System.getenv("CNB_OI_TOKEN")
        if (cnbToken != null) {
            maven {
                setUrl("https://maven.cnb.cool/wwang3/maven-repository/-/packages/")
                credentials {
                    username = "cnb"
                    password = cnbToken
                }
            }
        }
        mavenCentral()
        // Kotlin dev 构建（如 2.4.0-dev-xxxx）不在 Maven Central，需要从 JetBrains dev 仓库解析
        maven {
            setUrl("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/")
            content {
                includeGroupByRegex("org\\.jetbrains\\.kotlin(\\..*)?")
            }
        }
    }
}

pluginManagement {
    repositories {
        mavenCentral()
        maven {
            setUrl("https://maven.aliyun.com/repository/gradle-plugin")
        }
        gradlePluginPortal()
    }
}

plugins {
    // Use the Foojay Toolchains plugin to automatically download JDKs required by subprojects.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":slf4k-annotations")
include(":slf4k-compiler-plugin")
include(":slf4k-gradle-plugin")
include(":slf4k-test")

rootProject.name = "slf4k"
