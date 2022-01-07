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
  createLanguageTask(compileTask, "byteBuddy${language.capitalize()}")
}.filterNotNull()

tasks {
  val byteBuddy by registering {
    dependsOn(languageTasks)
  }

  named(sourceSet.classesTaskName) {
    dependsOn(byteBuddy)
  }
}

fun createLanguageTask(
  compileTaskProvider: TaskProvider<*>, name: String): TaskProvider<*> {
  return tasks.register<ByteBuddySimpleTask>(name) {
    setGroup("Byte Buddy")
    outputs.cacheIf { true }
    classFileVersion = ClassFileVersion.JAVA_V8
    var transformationClassPath = inputClasspath
    val compileTask = compileTaskProvider.get()
    if (compileTask is AbstractCompile) {
      val classesDirectory = compileTask.destinationDirectory.asFile.get()
      val rawClassesDirectory: File = File(classesDirectory.parent, "${classesDirectory.name}raw")
        .absoluteFile
      dependsOn(compileTask)
      compileTask.destinationDirectory.set(rawClassesDirectory)
      source = rawClassesDirectory
      target = classesDirectory
      classPath = compileTask.classpath.plus(rawClassesDirectory)
      transformationClassPath = transformationClassPath.plus(files(rawClassesDirectory))
      dependsOn(compileTask, sourceSet.processResourcesTaskName)
    }

    transformations.add(createTransformation(transformationClassPath, pluginName))
  }
}

fun createTransformation(classPath: FileCollection, pluginClassName: String): Transformation {
  return ClasspathTransformation(classPath, pluginClassName).apply {
    plugin = ClasspathByteBuddyPlugin::class.java
  }
}
