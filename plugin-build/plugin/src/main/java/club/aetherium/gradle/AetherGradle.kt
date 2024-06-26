package club.aetherium.gradle

import club.aetherium.gradle.api.GameExtension
import club.aetherium.gradle.extension.MinecraftExtension
import club.aetherium.gradle.tasks.DownloadAndRemapJarTask
import club.aetherium.gradle.tasks.DownloadMappingTask
import club.aetherium.gradle.tasks.GenerateSourcesTask
import club.aetherium.gradle.tasks.decomp.DecompileJarTask
import club.aetherium.gradle.tasks.remap.RemapJarTask
import club.aetherium.gradle.tasks.run.DownloadAssetsTask
import club.aetherium.gradle.tasks.run.RunClientTask
import club.aetherium.gradle.tasks.setup.ExtractJarTask
import club.aetherium.gradle.utils.NativesTask
import club.aetherium.gradle.utils.manifest.MinecraftManifest
import club.aetherium.gradle.utils.manifest.MinecraftManifest.gson
import club.aetherium.gradle.utils.manifest.data.VersionData
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.PluginAware
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.exclude
import java.io.File
import java.net.URL

abstract class AetherGradle : Plugin<PluginAware> {

    override fun apply(target: PluginAware) {
        if (target is Project) apply(target)
    }

    private fun apply(project: Project) {
        val extension = project.extensions.create("minecraft", MinecraftExtension::class.java, project)

        val sourceSets = project.extensions.getByName("sourceSets") as SourceSetContainer

        val sets = arrayOf("minecraft", "platform")
        var mainSourceset = sourceSets.getByName("main")
        sets.forEach { set ->
            sourceSets.maybeCreate(set)
            val sourceSet = sourceSets.getByName(set)
            sourceSet.compileClasspath = mainSourceset.compileClasspath
            sourceSet.java.srcDirs("src/$set")
            var dirs = arrayOf(File(project.projectDir, "src/$set/java"))
            if (set == "minecraft") {
                dirs += File(project.projectDir, "src/$set/resources")
            }
            dirs.forEach {
                if (!it.exists()) {
                    it.mkdirs()
                }
            }
        }

        val downloadMappingTask =
            project.tasks.register("downloadMapping", DownloadMappingTask::class.java) {
                it.group = "AetherGradle"
            }

        val downloadAndRemapJarTask =
            project.tasks.register("downloadAndRemapJar", DownloadAndRemapJarTask::class.java) {
                it.group = "AetherGradle"
                it.dependsOn(project.tasks.named("downloadMapping"))
            }
        val decompileJarTask =
            project.tasks.register("decompileJar", DecompileJarTask::class.java) {
                it.group = "AetherGradle"
                it.dependsOn(downloadAndRemapJarTask)
            }
        val setupMcpEnvTask =
            project.tasks.register("setupMcpEnv", ExtractJarTask::class.java) {
                it.group = "AetherGradle"
                it.dependsOn(decompileJarTask)
            }
        val generateSourcesTask = project.tasks.register("generateSources", GenerateSourcesTask::class.java) {
            it.group = "AetherGradle"
            it.dependsOn(downloadAndRemapJarTask)
        }

        val downloadAssetsTask = project.tasks.register("downloadAssets", DownloadAssetsTask::class.java) {
            it.group = "AetherGradle"
        }

        val runClientTask = project.tasks.register("runClient", RunClientTask::class.java) {
            it.group = "AetherGradle"
            it.dependsOn(project.tasks.named("processResources"))
            it.dependsOn(project.tasks.withType(AbstractCompile::class.java).matching { that ->
                !that.name.lowercase().contains("test")
            })
            it.mainClass.set(extension.runMode.get().mainClass)
        }
        val jarTask = project.tasks.named("jar", Jar::class.java).get()
        val remapJarTask = project.tasks.register("remapJar", RemapJarTask::class.java) {
            it.group = "AetherGradle"

            it.sourceNamespace.set("named")
            it.targetNamespace.set("official")

            it.inputJar.set(jarTask.archiveFile.get().asFile)
            it.outputJar.set(
                jarTask.archiveFile.get().asFile.parentFile.resolve(
                    jarTask.archiveFile.get().asFile.nameWithoutExtension + "-out.jar"
                )
            )
        }

        project.tasks.named("jar", Jar::class.java) {
            it.finalizedBy(remapJarTask)
        }.get()

        project.afterEvaluate {
            val mcManifest = MinecraftManifest.fromId(extension.minecraftVersion.get())
                ?: throw RuntimeException("Unknown version specified (${extension.minecraftVersion.get()})")

            val manifest = gson.fromJson(
                URL(mcManifest.url).openStream().reader().readText(),
                VersionData::class.java
            ) ?: throw RuntimeException("Failed to fetch version manifest")

            // Natives
            NativesTask.downloadAndExtractNatives(project, extension)

            // Deps
            project.repositories.add(
                project.repositories.maven {
                    it.url = project.uri("https://libraries.minecraft.net/")
                }
            )

            project.repositories.add(
                project.repositories.mavenLocal()
            )

            //  Libraries
            manifest.libraries.forEach {
                if (!it.name.contains("platform")) {
                    project.logger.info("Registering library ${it.name}")
                    project.dependencies.add("implementation", it.name)

                }
            }
//            project.dependencies.add(
//                "implementation",
//                "com.mojang:minecraft-deobf:${extension.minecraftVersion.get()}"
//            )
//
//            // RunMode
//            val mode = extension.runMode.get()
//
//            mode.additionalRepositories.forEach { dep ->
//                project.repositories.add(project.repositories.maven {
//                    it.url = project.uri(dep)
//                })
//            }
//
//            mode.additionalDependencies.forEach { dep ->
//                project.dependencies.add("implementation", dep)
//            }
//
//            // Extensions
//            val extensions = extension.gameExtensions
//
//            extensions.get().forEach { applyExtension(it, project) }
        }
    }

    private fun applyExtension(ext: GameExtension, project: Project) {
        ext.repositories.forEach { dep ->
            project.repositories.add(project.repositories.maven {
                it.url = project.uri(dep)
            })
        }

        project.dependencies {
            ext.dependencies.forEach {
                add("implementation", it) {
                    ext.excludes.forEach {
                        exclude(module = it)
                    }
                }
            }
        }

        ext.annotationProcessors.forEach {
            project.dependencies.add("annotationProcessor", it)
        }
    }
}
