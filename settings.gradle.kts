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
