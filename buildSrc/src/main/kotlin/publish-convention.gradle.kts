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
    println("info: ${project.group}:${project.name}:${project.version}")
    coordinates(project.group.toString(), project.name, project.version.toString())

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