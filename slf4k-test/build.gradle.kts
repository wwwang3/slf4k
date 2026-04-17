import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("kotlin-convention")
}


dependencies {
    implementation(kotlin("reflect"))
    implementation(kotlin("stdlib"))
    implementation(project(":slf4k-annotations"))
    implementation(libs.slf4jApi)
    implementation(libs.slf4jSimple)
    implementation(kotlin("test"))
}

tasks.withType<KotlinCompile> {
    dependsOn(":slf4k-compiler-plugin:clean")
    dependsOn(":slf4k-compiler-plugin:jar")

    val jarTask = project(":slf4k-compiler-plugin").tasks.named<Jar>("jar").get()
    val jarPath = jarTask.archiveFile.get().asFile.absolutePath

    doFirst {
        println("compiler-path: $jarPath")
    }

    compilerOptions {
        freeCompilerArgs.add("-Xplugin=${jarPath}")
    }
}
