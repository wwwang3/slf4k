plugins {
    id("kotlin-convention")
    id("publish-convention")
}

description = "Kotlin compiler plugin for generating slf4j loggers from @Slf4K"


dependencies {
    compileOnly(project(":slf4k-annotations"))
    compileOnly(libs.kotlin.compiler)

    testImplementation(project(":slf4k-annotations"))
    testImplementation(kotlin("test"))
}
