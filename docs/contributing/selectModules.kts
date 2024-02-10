#!/usr/bin/env kotlin

//install kotlin compiler: https://kotlinlang.org/docs/tutorials/command-line.html
import java.io.File

val includeRegex = Regex("include\\(\"(.*?)\"\\)")
val projectRegex = "project\\(\"([^\"]+)\"(, configuration = \".*\")?\\)".toRegex()
val keepModules = mutableSetOf<Module>()
var root = ""

main(args)

fun main(args: Array<String>) {
  if (args.isEmpty()) {
    println("Usage: ./docs/contributing/selectModules.kts instrumentation/spring/spring-boot-autoconfigure/ <module to include2> ...")
    return
  }

  (args.map {
    moduleOfArg(
      File(File(it).absolutePath),
      "/" + it.trimStart('.', '/').trimEnd('/')
    )
  } + listOf(":javaagent"))
    .map { Module(it) }
    .forEach(Module::addSelfAndChildren)

  File("$root/conventions/src/main/kotlin").listFiles()!!
    .filter { it.name.endsWith(".kts") }
    .forEach {
      children(it).forEach(Module::addSelfAndChildren)
    }

  println("removing modules except:\n${keepModules.map { it.name }.sorted().joinToString("\n")}")

  val target = File("$root/settings.gradle.kts")
  val text = target.readText().lines().flatMap { line ->
    includeRegex.matchEntire(line)?.let { it.groupValues[1] }?.let { module ->
      if (Module(module) in keepModules) {
        listOf(line)
      } else {
        emptyList()
      }
    } ?: listOf(line)


  }.joinToString("\n")
  target.writeText(text)
}

data class Module(val name: String) {
  fun children(): List<Module> {
    val file = moduleFile()
    return children(file)
  }

  private fun moduleFile(): File = File("$root/${name.replace(":", "/")}/build.gradle.kts")

  fun addSelfAndChildren() {
    if (!keepModules.add(this)) {
      return
    }

    children().forEach(Module::addSelfAndChildren)
  }
}

fun moduleOfArg(file: File, name: String): String {
  val settings = File(file, "settings.gradle.kts")
  return if (settings.exists()) {
    root = file.absolutePath
    name.substringAfter(root).replace("/", ":")
  } else {
    moduleOfArg(file.parentFile, name)
  }
}

fun children(file: File) = file.readText().lines().flatMap { line ->
  projectRegex.find(line)?.let { it.groupValues[1] }?.let { module ->
    listOf(Module(module))
  } ?: emptyList()
}

