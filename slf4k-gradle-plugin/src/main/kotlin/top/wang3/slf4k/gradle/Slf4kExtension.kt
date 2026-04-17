package top.wang3.slf4k.gradle

import org.gradle.api.provider.Property

abstract class Slf4kExtension {
    abstract val loggerName: Property<String>
}
