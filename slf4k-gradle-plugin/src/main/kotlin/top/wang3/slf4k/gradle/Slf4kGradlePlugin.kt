package top.wang3.slf4k.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import top.wang3.slf4k.compiler.Slf4kPluginMetadata

class Slf4kGradlePlugin : KotlinCompilerPluginSupportPlugin {

    override fun apply(target: Project) {
        target.extensions.create("slf4k", Slf4kExtension::class.java)
        target.dependencies.add(
            "implementation",
            "${Slf4kPluginCoordinates.GROUP}:${Slf4kPluginMetadata.ANNOTATIONS_ARTIFACT_ID}:${Slf4kPluginCoordinates.COMPILER_PLUGIN_VERSION}",
        )
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = Slf4kPluginMetadata.COMPILER_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = Slf4kPluginCoordinates.GROUP,
        artifactId = Slf4kPluginMetadata.COMPILER_PLUGIN_ARTIFACT_ID,
        version = Slf4kPluginCoordinates.COMPILER_PLUGIN_VERSION,
    )

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>,
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val ext = project.extensions.getByType(Slf4kExtension::class.java)
        return project.provider {
            buildList {
                ext.loggerName.orNull
                    ?.takeIf { it.isNotBlank() }
                    ?.let { add(SubpluginOption(Slf4kPluginMetadata.LOGGER_NAME_OPTION_NAME, it)) }
            }
        }
    }
}
