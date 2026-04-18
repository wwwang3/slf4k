plugins {
    id("kotlin-convention")
    id("publish-convention")
}

description = "Kotlin compiler plugin for generating slf4j loggers from @Slf4K"

// KEFS (Kotlin External FIR Support) 坐标规则：<idea-kotlinc-version>-<slf4kVersion>
// 这样 KEFS 可以按当前 IDE 捆绑的 Kotlin 版本拉到匹配的编译器插件 jar。
// 注意用 kotlinCompiler（= IDE 捆绑 kotlinc 版本），不是 kotlin（= 本构建使用的 KGP 版本）。
version = "${libs.versions.kotlinCompiler.get()}-${rootProject.version}"

dependencies {
    compileOnly(project(":slf4k-annotations"))
    compileOnly(libs.kotlinCompilerEmbeddable)

    testImplementation(project(":slf4k-annotations"))
    testImplementation(libs.kotlinCompilerEmbeddable)
    testImplementation(kotlin("test"))
}
