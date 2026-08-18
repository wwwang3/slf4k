import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import java.util.Base64

plugins {
    id("com.vanniktech.maven.publish")
    signing
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
//    signAllPublications()
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

fun String.normalizePgpKey(): String {
    val value = trim()
        // 兼容某些 CI 中把换行保存成字面量 \n 的情况
        .replace("\\n", "\n")

    // 原生 ascii-armored key
    if (value.contains("-----BEGIN PGP PRIVATE KEY BLOCK-----")) {
        return value
    }

    // Base64 编码后的 ascii-armored key
    val decoded = runCatching {
        String(Base64.getMimeDecoder().decode(value), Charsets.UTF_8)
    }.getOrElse {
        throw GradleException(
            "signingInMemoryKey 既不是原生 PGP private key，也不是合法的 Base64 编码"
        )
    }.trim().replace("\\n", "\n")

    if (!decoded.contains("-----BEGIN PGP PRIVATE KEY BLOCK-----")) {
        throw GradleException("Base64 解码后不是 PGP private key")
    }

    return decoded
}

val signingKey = providers
    .gradleProperty("signingInMemoryKey")
    .orElse(providers.gradleProperty("signingKey"))
    .orNull
    ?.normalizePgpKey()

val signingPassword = providers
    .gradleProperty("signingInMemoryKeyPassword")
    .orElse(providers.gradleProperty("signingPassword"))
    .orNull
    ?: ""

val signingKeyId = providers
    .gradleProperty("signingInMemoryKeyId")
    .orElse(providers.gradleProperty("signingKeyId"))
    .orNull

signing {
    val isRequired = !version.toString().endsWith("-SNAPSHOT")

    if (!signingKey.isNullOrBlank() && isRequired) {
        if (signingKeyId.isNullOrBlank()) {
            useInMemoryPgpKeys(signingKey, signingPassword)
        } else {
            useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
        }

        sign(publishing.publications)
    }
}
