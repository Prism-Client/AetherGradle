package club.aetherium.gradle.extension

import club.aetherium.gradle.api.GameExtension
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.getValue
import javax.inject.Inject

@Suppress("UnnecessaryAbstractClass")
abstract class MinecraftExtension @Inject constructor(project: Project) {
    private val objects = project.objects

    val minecraftVersion: Property<String> = objects.property(String::class.java)

    val mappings: Property<String> = objects.property(String::class.java)

    val runMode: Property<RunMode> = objects.property(RunMode::class.java)
    val gameExtensions: ListProperty<GameExtension> = objects.listProperty(GameExtension::class.java)

    init {
        runMode.set(RunMode.Vanilla)
    }
}
