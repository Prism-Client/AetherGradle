package club.aetherium.gradle.tasks

import club.aetherium.gradle.extension.MinecraftExtension
import club.aetherium.gradle.utils.Downloader
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.net.URL

abstract class DownloadMappingTask : DefaultTask() {
    private val mappingType: Property<String>
        get() = project.extensions.getByType(MinecraftExtension::class.java).mappings
    private val mcVersion: Property<String>
        get() = project.extensions.getByType(MinecraftExtension::class.java).minecraftVersion

    @OutputFile
    val yarnJar = project.projectDir.resolve("build")
        .resolve(".aether").resolve("${mcVersion.get()}_yarn.jar")
    @get:Internal
    val legacyFabricUrl
        get() = "https://repo.legacyfabric.net/repository/legacyfabric"
    @get:Internal
    val fabricUrl
        get() = "https://maven.fabricmc.net"

    @TaskAction
    fun downloadMapping() {
        val url = pathToYarnMapping()
        Downloader.download(
            url, yarnJar,
            URL("${url}.sha1").openStream().reader().readText()
        ) {
            val totalBoxes = 30
            val neededBoxes = (it * totalBoxes).toInt()
            val characters = "=".repeat(neededBoxes)
            val whitespaces = " ".repeat(totalBoxes - neededBoxes)

            print(
                "[AetherGradle] Yarn tiny mapping: [$characters$whitespaces] (${it})\r"
            )
        }
    }


    private fun pathToYarnMapping(): String = if(mcVersion.get().split(".")[1].toInt() < 15)
        "${legacyFabricUrl}/net/legacyfabric/v2/yarn/${mappingType.get()}/yarn-${mappingType.get()}.jar"
    else
        "${fabricUrl}/net/fabricmc/yarn/${mappingType.get()}/yarn-${mappingType.get()}.jar"
}
