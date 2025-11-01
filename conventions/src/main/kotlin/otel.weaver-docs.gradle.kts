import java.io.File

// Weaver documentation generation convention plugin
// Recursively finds all instrumentation modules with models/ directories
// and creates Gradle tasks to generate documentation using the OpenTelemetry Weaver tool

/**
 * Creates a Gradle task for generating weaver documentation from a models directory.
 *
 * @param instrumentationName The name to use in the task (derived from relative path)
 * @param modelsDir The directory containing weaver model files
 * @param docsDir The output directory for generated documentation
 */
fun createWeaverDocTask(instrumentationName: String, modelsDir: File, docsDir: File): TaskProvider<Exec> {
  return tasks.register<Exec>("generateWeaverDocs-${instrumentationName}") {
    group = "documentation"
    description = "Generate weaver documentation for $instrumentationName instrumentation"

    inputs.dir(modelsDir)
    outputs.dir(docsDir)

    standardOutput = System.out
    executable = "docker"

    // Run as root in container to avoid permission issues with cache directory
    val dockerArgs = listOf<String>()

    val cacheDir = File(project.layout.buildDirectory.get().asFile, ".weaver-cache")

    // Template hierarchy (in order of precedence):
    // 1. Module-specific templates: models/templates/ (highest priority)
    // 2. Global shared templates: weaver-templates/ (medium priority)
    // 3. Default semantic-conventions templates (fallback)
    val moduleTemplatesDir = File(modelsDir, "templates")
    val globalTemplatesDir = File(project.rootDir, "weaver-templates")

    val templatesSource = when {
      moduleTemplatesDir.exists() && moduleTemplatesDir.isDirectory -> {
        // Use module-specific templates (no mount needed, already in /source/templates)
        "/source/templates"
      }
      globalTemplatesDir.exists() && globalTemplatesDir.isDirectory -> {
        // Use global shared templates (needs separate mount)
        "/shared-templates"
      }
      else -> {
        // Fall back to default semantic-conventions templates
        "https://github.com/open-telemetry/semantic-conventions/archive/refs/tags/v1.34.0.zip[templates]"
      }
    }

    // Build mount arguments - add global templates mount if using them
    val mountArgs = mutableListOf(
      "--mount", "type=bind,source=${modelsDir.absolutePath},target=/source,readonly",
      "--mount", "type=bind,source=${docsDir.absolutePath},target=/target",
      "--mount", "type=bind,source=${cacheDir.absolutePath},target=/home/weaver/.weaver"
    )

    if (templatesSource == "/shared-templates") {
      mountArgs.addAll(listOf(
        "--mount", "type=bind,source=${globalTemplatesDir.absolutePath},target=/shared-templates,readonly"
      ))
    }

    val weaverArgs = listOf(
      "otel/weaver:v0.18.0@sha256:5425ade81dc22ddd840902b0638b4b6a9186fb654c5b50c1d1ccd31299437390",
      "registry", "generate",
      "--registry=/source",
      "--templates=${templatesSource}",
      "markdown", "/target"
    )

    args = listOf("run", "--rm", "--platform=linux/x86_64") + dockerArgs + mountArgs + weaverArgs

    doFirst {
      if (!modelsDir.exists()) {
        throw GradleException("Models directory does not exist: ${modelsDir.absolutePath}")
      }
      docsDir.mkdirs()
      cacheDir.mkdirs()
    }
  }
}

/**
 * Recursively searches for all models/ directories under a given directory.
 *
 * @param dir The directory to search
 * @param baseDir The base directory for calculating relative paths
 * @return List of pairs containing (task-suffix, module-directory)
 */
fun findModelsDirectories(dir: File, baseDir: File = dir): List<Pair<String, File>> {
  val results = mutableListOf<Pair<String, File>>()

  dir.listFiles()?.forEach { file ->
    if (file.isDirectory) {
      val modelsDir = File(file, "models")
      if (modelsDir.exists() && modelsDir.isDirectory) {
        val relativePath = file.relativeTo(baseDir).path.replace(File.separatorChar, '-')
        results.add(Pair(relativePath, file))
      }
      // Recursively search subdirectories
      results.addAll(findModelsDirectories(file, baseDir))
    }
  }

  return results
}

// Find all instrumentation modules with models directories and register tasks
val weaverDocTasks = mutableListOf<TaskProvider<Exec>>()
val instrumentationDir = file("instrumentation")
if (instrumentationDir.exists() && instrumentationDir.isDirectory) {
  findModelsDirectories(instrumentationDir).forEach { (taskSuffix, moduleDir) ->
    val modelsDir = File(moduleDir, "models")
    val docsDir = File(moduleDir, "docs")
    val taskProvider = createWeaverDocTask(taskSuffix, modelsDir, docsDir)
    weaverDocTasks.add(taskProvider)
  }
}

// Create an aggregate task to generate all weaver docs
tasks.register("generateAllWeaverDocs") {
  group = "documentation"
  description = "Generate weaver documentation for all instrumentation modules"
  dependsOn(weaverDocTasks)
}
