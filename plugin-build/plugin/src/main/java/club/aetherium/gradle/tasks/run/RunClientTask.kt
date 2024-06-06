package club.aetherium.gradle.tasks.run

import club.aetherium.gradle.extension.MinecraftExtension
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction

abstract class RunClientTask : JavaExec() {
    private val extension = project.extensions.getByType(MinecraftExtension::class.java)

    private val runDirectory = project.projectDir.resolve("run")
    private val assetsDirectory = project.projectDir.resolve("build")
        .resolve(".aether").resolve("assets")
    private val nativesDirectory = project.projectDir.resolve("build")
        .resolve(".aether").resolve("caches").resolve("natives")

    @TaskAction
    override fun exec() {
        val m = extension.runMode.get()

        jvmArgs(*m.vmArgs.toTypedArray())
        jvmArgs("-Djava.library.path=${nativesDirectory}")

        args(*m.runArgs.toTypedArray())

        args("--version=AetherGradle")
        args("--accessToken=0")

        workingDir(runDirectory)

        args("--gameDir=${runDirectory}")
        args("--assetsDir=${assetsDirectory}")

        val sourceSets = project.extensions.findByType(org.gradle.api.plugins.JavaPluginExtension::class.java)?.sourceSets
        val compiledJava = sourceSets?.flatMap { it.output.files }

        classpath(
            project.configurations.getByName("runtimeClasspath"),
            compiledJava
        )

        if(!runDirectory.exists()) runDirectory.mkdirs()

        super.exec()
    }
}
