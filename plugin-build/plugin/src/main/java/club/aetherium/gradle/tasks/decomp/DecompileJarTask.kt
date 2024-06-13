package club.aetherium.gradle.tasks.decomp

import club.aetherium.gradle.decompiler.VineFlowerDecompiler
import club.aetherium.gradle.extension.MinecraftExtension
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class DecompileJarTask : DefaultTask() {

    private val minecraftVersion: Property<String>
        get() = project.extensions.getByType(MinecraftExtension::class.java).minecraftVersion
    @Internal
    val remappedJar = project.projectDir.resolve("build")
        .resolve(".aether").resolve("client_${minecraftVersion.get()}_remapped.jar")

    @Internal
    val decompJar = project.projectDir.resolve("build")
        .resolve(".aether").resolve("client_${minecraftVersion.get()}_decomp.jar")
    @TaskAction
    fun decompile() {
        if(decompJar.exists()) {
            project.logger.lifecycle("[AetherGradle] Wanting to re-decompile jar, delete it.")
            return
        }
        project.logger.lifecycle("[AetherGradle] Attempting to decompile jar")
        VineFlowerDecompiler.decompile(project, remappedJar, decompJar)
        project.logger.lifecycle("[AetherGradle] Decompiled jar")
    }
}
