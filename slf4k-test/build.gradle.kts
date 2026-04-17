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

/*
 * ---------------------------------------------------------------------------
 * slf4k Gradle 插件形式的等价配置（推荐给外部用户）
 * ---------------------------------------------------------------------------
 *
 * 当 top.wang3.slf4k:slf4k-gradle-plugin / slf4k-compiler-plugin / slf4k-annotations
 * 已发布到可解析的仓库（例如 Gradle Plugin Portal / mavenCentral / 企业内网 maven）后，
 * 用户工程只需：
 *
 *     plugins {
 *         kotlin("jvm") version "2.3.0"
 *         id("top.wang3.slf4k") version "0.0.1"
 *     }
 *
 *     // 可选：覆盖生成的 logger 属性名，默认为 "logger"
 *     slf4k {
 *         loggerName.set("log")
 *     }
 *
 *     dependencies {
 *         implementation("org.slf4j:slf4j-api:<slf4j-version>")
 *         implementation("org.slf4j:slf4j-simple:<slf4j-version>")
 *         // slf4k-annotations 由插件自动注入到 implementation 配置
 *     }
 *
 * 本模块之所以仍采用 -Xplugin= 直挂 jar 的 hack（上方 active 的配置），
 * 原因是同一个 Gradle build 内无法直接以 plugins {} 方式消费本 build 里的 gradle-plugin：
 *   - Gradle 9.x 禁止在 buildscript { dependencies { classpath(...) } } 中使用 project(":...")
 *     （报错 "Project dependencies cannot be declared here"）；
 *   - 使用 pluginManagement { includeBuild(...) } 需要把 slf4k-gradle-plugin 改造为独立的
 *     composite build（拆出自己的 settings.gradle.kts），同时会失去对 buildSrc 约定插件
 *     kotlin-convention / publish-convention 以及 project(":slf4k-compiler-plugin") 的便捷共享；
 *   - 使用 maven 仓库 + dependencySubstitution 需要在每次构建前先产出 build/test-maven-repo，
 *     而 buildscript classpath 在配置阶段就要被解析，任务执行顺序解决不了。
 *
 * 外部真实工程不存在上述约束，直接走上面注释里的 plugins {} 方式即可。
 * Gradle 插件的端到端行为由 :slf4k-gradle-plugin:functionalTest（GradleRunner）验证。
 */
