import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

plugins {
    id("com.vanniktech.maven.publish")
}

publishing {
    repositories {
        maven {
            name = "cnb"
            url = uri("https://maven.cnb.cool/wwang3/maven-repository/-/packages/")

            credentials {
                username = "cnb"
                password = providers.environmentVariable("CNB_OI_TOKEN").orNull
            }

        }
    }
}

tasks.withType<PublishToMavenRepository>().configureEach {
    doFirst {
        if (repository.name == "cnb" && providers.environmentVariable("CNB_OI_TOKEN").orNull.isNullOrBlank()) {
            error("CNB_OI_TOKEN is required to publish to the CNB Maven repository.")
        }
    }
}

mavenPublishing {
    signAllPublications()
    // 默认坐标即 project.group:project.name:project.version；不在此处急切捕获，
    // 以便子项目（例如 slf4k-compiler-plugin）在 plugins 块之后覆盖 version。
    afterEvaluate {
        println("info: ${project.group}:${project.name}:${project.version}")
    }

    pom {
        name = project.name
        description = project.description ?: "slf4j in kotlin"

        developers {
            developer {
                id.set("wwwang3")
                name.set("wang3")
                url.set("https://github.com/wwwang3/")
            }
        }
    }
}
