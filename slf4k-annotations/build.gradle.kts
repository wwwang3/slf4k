import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("kotlin-convention")
    id("publish-convention")
}

description = "Annotations for the slf4k compiler plugin"

// 锁到 Kotlin 2.0 metadata baseline：此 artifact 只发一份却被任意 Kotlin 2.x
// 用户工程消费，不随主构建的 `kotlin` 版本重发。
kotlin {
    compilerOptions {
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
    }
}
