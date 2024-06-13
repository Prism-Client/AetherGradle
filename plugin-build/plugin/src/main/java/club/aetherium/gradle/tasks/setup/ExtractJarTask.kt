package club.aetherium.gradle.tasks.setup

import club.aetherium.gradle.extension.MinecraftExtension
import club.aetherium.gradle.utils.extractJarFile
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files

abstract class ExtractJarTask : DefaultTask() {

    @Internal
    val startJavaCode = "import java.util.Arrays;\n" +
        "import net.minecraft.client.main.Main;\n" +
        "\n" +
        "public class Start {\n" +
        "     public static void main(String[] args) {\n" +
        "\t\tSystem.out.println(\"Change the assetIndex accordingly before starting the game!\")\n" +
        "\t\tSystem.exit(-1);\n" +
        "        Main.main((String[])concat(new String[]{\"--version\", \"mcp\", \"--accessToken\", \"0\", \"--assetsDir\", \"assets\", \"--assetIndex\", \"1.12\", \"--userProperties\", \"{}\"}, args));\n" +
        "     }\n" +
        "\n" +
        "     public static Object[] concat(Object[] first, Object[] second) {\n" +
        "          Object[] result = Arrays.copyOf(first, first.length + second.length);\n" +
        "          System.arraycopy(second, 0, result, first.length, second.length);\n" +
        "          return result;\n" +
        "     }\n" +
        "}";
    private val minecraftVersion: Property<String>
        get() = project.extensions.getByType(MinecraftExtension::class.java).minecraftVersion

    @Internal
    val pathToSrc = project.projectDir.resolve("src").resolve("minecraft").resolve("java")

    @Internal
    val pathToResources = project.projectDir.resolve("src").resolve("minecraft").resolve("resources")

    @Internal
    val remappedJar = project.projectDir.resolve("build")
        .resolve(".aether").resolve("client_${minecraftVersion.get()}_remapped.jar")

    @Internal
    val decompJar = project.projectDir.resolve("build")
        .resolve(".aether").resolve("client_${minecraftVersion.get()}_decomp.jar")

    @TaskAction
    fun extractDecompJar() {

        if (pathToSrc.resolve("Start.java").exists()) {
            throw IllegalStateException("Attempting to decompile jar when it was already decompiled.")
        }

        extractJarFile(decompJar.absolutePath, pathToSrc.absolutePath)
        extractJarFile(remappedJar.absolutePath, pathToResources.absolutePath, false)
        project.logger.lifecycle("[AetherGradle] Extracted minecraft jar.")

        Files.write(pathToSrc.resolve("Start.java").toPath(), startJavaCode.encodeToByteArray())
        project.logger.lifecycle("[AetherGradle] Finished setting up MCP environment")
    }
}
