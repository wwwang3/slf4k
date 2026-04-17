plugins {
    id("com.vanniktech.maven.publish")
}


publishing {
    repositories {
        maven {
            name = "wang3"
            url = uri("https://maven.cnb.cool/wwang3/maven-repository/-/packages/")

            val token = System.getenv("CNB_OI_TOKEN") ?: error("token required")
            credentials {
                username = "cnb"
                password = token
            }

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