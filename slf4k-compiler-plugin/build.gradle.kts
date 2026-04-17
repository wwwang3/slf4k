plugins {
    id("kotlin-convention")
    id("publish-convention")
}

description = "Kotlin compiler plugin for generating slf4j loggers from @Slf4K"

dependencies {
    compileOnly(project(":slf4k-annotations"))
    compileOnly(libs.kotlinCompilerEmbeddable)

    testImplementation(project(":slf4k-annotations"))
    testImplementation(libs.kotlinCompilerEmbeddable)
    testImplementation(kotlin("test"))
}
