// The settings file is the entry point of every Gradle build.
// Its primary purpose is to define the subprojects.
// It is also used for some aspects of project-wide configuration, like managing plugins, dependencies, etc.
// https://docs.gradle.org/current/userguide/settings_file_basics.html

dependencyResolutionManagement {
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
