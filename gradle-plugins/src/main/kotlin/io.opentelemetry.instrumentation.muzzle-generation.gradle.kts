import io.opentelemetry.javaagent.muzzle.generation.ClasspathByteBuddyPlugin
import io.opentelemetry.javaagent.muzzle.generation.ClasspathTransformation
import net.bytebuddy.ClassFileVersion
import net.bytebuddy.build.gradle.ByteBuddySimpleTask
import net.bytebuddy.build.gradle.Transformation

plugins {
  `java-library`
}

/**
 * Starting from version 1.10.15, ByteBuddy gradle plugin transformation task autoconfiguration is
 * hardcoded to be applied to javaCompile task. This causes the dependencies to be resolved during
 * an afterEvaluate that runs before any afterEvaluate specified in the build script, which in turn
 * makes it impossible to add dependencies in afterEvaluate. Additionally the autoconfiguration will
 * attempt to scan the entire project for tasks which depend on the compile task, to make each task
 * that depends on compile also depend on the transformation task. This is an extremely inefficient
 * operation in this project to the point of causing a stack overflow in some environments.
 *
 * <p>To avoid all the issues with autoconfiguration, this plugin manually configures the ByteBuddy
 * transformation task. This also allows it to be applied to source languages other than Java. The
 * transformation task is configured to run between the compile and the classes tasks, assuming no
 * other task depends directly on the compile task, but instead other tasks depend on classes task.
 * Contrary to how the ByteBuddy plugin worked in versions up to 1.10.14, this changes the compile
 * task output directory, as starting from 1.10.15, the plugin does not allow the source and target
 * directories to be the same. The transformation task then writes to the original output directory
 * of the compile task.
 */

val LANGUAGES = listOf("java", "scala", "kotlin")
val pluginName = "io.opentelemetry.javaagent.tooling.muzzle.generation.MuzzleCodeGenerationPlugin"

val codegen by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = true
}

val sourceSet = sourceSets.main.get()
val inputClasspath = (sourceSet.output.resourcesDir?.let { codegen.plus(project.files(it)) }
  ?: codegen)
  .plus(sourceSet.output.dirs) // needed to support embedding shadowed modules into instrumentation
  .plus(configurations.runtimeClasspath.get())

val languageTasks = LANGUAGES.map { language ->
  if (fileTree("src/${sourceSet.name}/${language}").isEmpty) {
    return@map null
  }
  val compileTaskName = sourceSet.getCompileTaskName(language)
  if (!tasks.names.contains(compileTaskName)) {
    return@map null
  }
  val compileTask = tasks.named(compileTaskName)
  createLanguageTask(compileTask, language)
}.filterNotNull()

tasks {
  named(sourceSet.classesTaskName) {
    dependsOn(languageTasks)
  }
}

fun createLanguageTask(
  compileTaskProvider: TaskProvider<*>, language: String): TaskProvider<*> {
  val taskName = "byteBuddy${language.replaceFirstChar { it.uppercase() }}"
  val mainSourceSet = sourceSets.main.get()
  val projectName = project.name
  
  // Create the input classpath from the existing logic in the main part
  val inputClasspath = (mainSourceSet.output.resourcesDir?.let { codegen.plus(project.files(it)) }
    ?: codegen)
    .plus(mainSourceSet.output.dirs) // needed to support embedding shadowed modules into instrumentation
    .plus(configurations.runtimeClasspath.get())
  
  val byteBuddyTask = tasks.register(taskName, ByteBuddySimpleTask::class.java) {
    dependsOn(compileTaskProvider, mainSourceSet.processResourcesTaskName)
    
    transformations.add(createTransformation(inputClasspath, projectName))
    
    // Configure the ByteBuddy task properties directly during task creation
    val compileTask = compileTaskProvider.get()
    if (compileTask is AbstractCompile) {
      val classesDirectory = compileTask.destinationDirectory.asFile.get()
      val rawClassesDirectory = File(classesDirectory.parent, "${classesDirectory.name}raw").absoluteFile
      
      // Configure the compile task to write to rawClassesDirectory
      compileTask.destinationDirectory.set(rawClassesDirectory)
      
      // Configure ByteBuddy task properties
      source = rawClassesDirectory
      target = classesDirectory
      classPath = compileTask.classpath.plus(rawClassesDirectory)
      
      // Clear and set transformations with correct classpath
      transformations.clear()
      transformations.add(createTransformation(inputClasspath.plus(files(rawClassesDirectory)), pluginName))
    }
  }

  return byteBuddyTask
}

fun createTransformation(classPath: FileCollection, pluginClassName: String): Transformation {
  return ClasspathTransformation(classPath, pluginClassName).apply {
    plugin = ClasspathByteBuddyPlugin::class.java
  }
}
