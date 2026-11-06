// Packages a provider module into a .fmkp plugin: a zip with manifest.json and classes.dex, which
// is the same layout Cloudstream uses for its .cs3 extensions and what the app's PluginLoader
// reads at runtime. The dex is produced with d8 from the Android build-tools.
//
// Applied from a provider module:
//   apply(from = rootProject.file("gradle/provider-plugin.gradle.kts"))
// and the module keeps its manifest in plugin/manifest.json.

val pluginBaseName: String = project.name.removePrefix("provider-")
val pluginManifestFile = layout.projectDirectory.file("plugin/manifest.json")

val buildPlugin by tasks.registering {
    group = "build"
    description = "Builds ${pluginBaseName}.fmkp (manifest.json + classes.dex) from this module."

    dependsOn(tasks.named("jar"))

    val jarFile = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    // The ABI classes stay in the app; d8 only needs them on its classpath to resolve types.
    val apiJar = project(":provider-api").tasks.named<Jar>("jar").flatMap { it.archiveFile }
    dependsOn(project(":provider-api").path + ":jar")
    val manifestFile = pluginManifestFile
    val outputDirectory = layout.buildDirectory.dir("plugin")
    val moduleDir = layout.projectDirectory.asFile

    doLast {
        if (!manifestFile.asFile.exists()) {
            throw GradleException("Missing plugin manifest: ${manifestFile.asFile}")
        }
        val manifestText = manifestFile.asFile.readText()
        val pluginId = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
            .find(manifestText)?.groupValues?.get(1)
            ?: pluginBaseName

        val sdkDir = androidSdkDir(moduleDir)
        val d8 = sdkDir?.let { findTool(it, "build-tools", "d8") }
            ?: throw GradleException(
                "d8 was not found. Set ANDROID_HOME/ANDROID_SDK_ROOT (or sdk.dir in " +
                    "local.properties) to an Android SDK with build-tools installed.",
            )
        val androidJar = sdkDir?.let { findAndroidJar(it) }

        val dexDir = File(temporaryDir, "dex")
        dexDir.deleteRecursively()
        dexDir.mkdirs()

        val command = mutableListOf(
            d8.absolutePath,
            "--release",
            "--min-api", "26",
            "--output", dexDir.absolutePath,
            jarFile.get().asFile.absolutePath,
        )
        if (androidJar != null) {
            command += listOf("--lib", androidJar.absolutePath)
        }
        runCatching { apiJar.get().asFile }.getOrNull()?.takeIf { it.isFile }?.let { jar ->
            command += listOf("--classpath", jar.absolutePath)
        }

        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().use { it.readText() }
        val exit = process.waitFor()
        if (exit != 0) {
            throw GradleException("d8 failed with exit code $exit:\n$log")
        }

        val dexFile = File(dexDir, "classes.dex")
        if (!dexFile.exists()) {
            throw GradleException("d8 produced no classes.dex:\n$log")
        }

        val outDir = outputDirectory.get().asFile
        outDir.mkdirs()
        val pluginFile = File(outDir, "$pluginId.fmkp")
        java.util.zip.ZipOutputStream(pluginFile.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
            zip.write(manifestText.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("classes.dex"))
            zip.write(dexFile.readBytes())
            zip.closeEntry()
        }
        logger.lifecycle("Built provider plugin: ${pluginFile.absolutePath}")
    }
}

fun androidSdkDir(projectDir: File): File? {
    val fromLocalProperties = File(projectDir.parentFile ?: projectDir, "local.properties")
        .takeIf { it.exists() }
        ?.let { file ->
            runCatching {
                java.util.Properties().apply { file.inputStream().use { load(it) } }
                    .getProperty("sdk.dir")
            }.getOrNull()
        }
    val candidates = listOfNotNull(
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
        fromLocalProperties,
    )
    return candidates.map(::File).firstOrNull { it.isDirectory }
}

fun findTool(sdkDir: File, parent: String, tool: String): File? {
    val directories = File(sdkDir, parent).listFiles()?.filter { it.isDirectory } ?: return null
    return directories
        .sortedByDescending { it.name }
        .firstNotNullOfOrNull { directory ->
            listOf(tool, "$tool.bat").map { File(directory, it) }.firstOrNull { it.isFile }
        }
}

fun findAndroidJar(sdkDir: File): File? {
    val platforms = File(sdkDir, "platforms").listFiles()?.filter { it.isDirectory } ?: return null
    return platforms
        .sortedByDescending { it.name }
        .firstNotNullOfOrNull { directory -> File(directory, "android.jar").takeIf { it.isFile } }
}
