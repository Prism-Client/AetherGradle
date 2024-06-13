package club.aetherium.gradle.utils

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.io.*
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.outputStream

fun extractZipFile(zipFile: String, outputDirectory: String, exclusions: List<String> = emptyList()) {
    val buffer = ByteArray(1024)
    val folder = File(outputDirectory)

    if (!folder.exists()) {
        folder.mkdir()
    }

    ZipInputStream(FileInputStream(zipFile)).use { zis ->
        var zipEntry = zis.nextEntry

        while (zipEntry != null) {
            val newFile = File(outputDirectory, zipEntry.name)

            if (!exclusions.any { zipEntry?.name!!.contains(it) }) {
                if (zipEntry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    File(newFile.parent).mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }
                }
            } else {
                println("Excluding ${zipEntry.name}")
            }

            zipEntry = zis.nextEntry
        }
        zis.closeEntry()
    }
}

fun extractJarFile(zipFile: String, outputDirectory: String, javaFilesOnly: Boolean = true) {
    val buffer = ByteArray(1024)
    val folder = File(outputDirectory)

    if (!folder.exists()) {
        folder.mkdir()
    }

    ZipInputStream(FileInputStream(zipFile)).use { zis ->
        var zipEntry = zis.nextEntry

        while (zipEntry != null) {
            val newFile = File(outputDirectory, zipEntry.name)
            if(zipEntry.name.startsWith("META-INF") || zipEntry.name.startsWith("log4j2.xml")) {
                zipEntry = zis.nextEntry
                continue;
            }
            // Kappa :D
            println("Extracting ${zipEntry.name} ${javaFilesOnly}")
            if (zipEntry.name.startsWith("net") && javaFilesOnly || !zipEntry.name.startsWith("net") && !javaFilesOnly) {
                if (zipEntry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    File(newFile.parent).mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }
                }
            }
            zipEntry = zis.nextEntry
        }
        zis.closeEntry()
    }
}


fun <T> Optional<T>.orElseOptional(invoke: () -> Optional<T>): Optional<T> {
    return if (isPresent) {
        this
    } else {
        invoke()
    }
}

fun <T> Path.readZipInputStreamFor(path: String, throwIfMissing: Boolean = true, action: (InputStream) -> T): T {
    Files.newInputStream(this).use { fileInputStream ->
        ZipInputStream(fileInputStream).use { zipInputStream ->
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                if (entry.name == path.replace("\\", "/")) {
                    return action.invoke(zipInputStream)
                }
                entry = zipInputStream.nextEntry
            }
            if (throwIfMissing) {
                throw IllegalArgumentException("Missing file $path in $this")
            }
        }
    }
    return null as T
}

fun Path.forEachInZip(action: (String, InputStream) -> Unit) {
    Files.newInputStream(this).use { fileInputStream ->
        ZipInputStream(fileInputStream).use { zipInputStream ->
            var entry: ZipEntry? = zipInputStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    action(entry.name, zipInputStream)
                }
                entry = zipInputStream.nextEntry
            }
        }
    }
}

fun Path.openZipFileSystem(vararg args: Pair<String, Any>): FileSystem {
    return openZipFileSystem(args.associate { it })
}

fun Path.openZipFileSystem(args: Map<String, *> = mapOf<String, Any>()): FileSystem {
    if (!exists() && args["create"] == true) {
        ZipOutputStream(outputStream()).use { stream ->
            stream.closeEntry()
        }
    }
    return FileSystems.newFileSystem(URI.create("jar:${toUri()}"), args, null)
}

fun fixSnowman(file: File) {
    val jarFile = JarFile(file)

    val entries = jarFile.entries()
    val classPath = HashMap<String, ClassNode>()
    val resourcePath = HashMap<String, ByteArray>()
    while (entries.hasMoreElements()) {
        val element = entries.nextElement()
        if (element.name.endsWith(".class")) {
            val reader = ClassReader(jarFile.getInputStream(element))
            val classNode = ClassNode()
            reader.accept(classNode, 0)
            if (classNode.methods != null) {
                classNode.methods.forEach {
                    if (it.localVariables != null)
                        it.localVariables.clear()
                }
            }
            classPath[classNode.name] = classNode
        } else {
            val byteArrayOutputStream = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var length = 0
            length = jarFile.getInputStream(element).read(buffer)
            if (length > 0) {
                byteArrayOutputStream.write(buffer, 0, jarFile.getInputStream(element).read(buffer))
            }
            resourcePath[element.name] = byteArrayOutputStream.toByteArray()
        }
    }

    var outputStream = JarOutputStream(FileOutputStream(file.absolutePath))
    classPath.forEach { (k, v) ->
        val entry = ZipEntry("$k.class")
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        v.accept(writer)
        outputStream.putNextEntry(entry)
        outputStream.write(writer.toByteArray())
        outputStream.closeEntry()
    }
    resourcePath.forEach { (k, v) ->
        outputStream.putNextEntry(ZipEntry(k))
        outputStream.write(v)
        outputStream.closeEntry()
    }
    outputStream.close()
}
