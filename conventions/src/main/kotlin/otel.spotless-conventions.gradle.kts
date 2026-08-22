import com.diffplug.spotless.LineEnding

plugins {
  id("com.diffplug.spotless")
}

val spotlessApplyRequested = gradle.startParameter.taskNames.any { requestedTask ->
  val taskName = requestedTask.substringAfterLast(':')
  taskName.startsWith("spotless") && taskName.endsWith("Apply")
}

if (project == rootProject && spotlessApplyRequested) {
  logger.lifecycle(
    "Spotless formatting requested. Use `mise run lint:fix` for Java, Kotlin, Markdown, and other " +
      "Flint-managed files; Spotless remains mainly for Scala, Groovy, and miscellaneous files."
  )
}

spotless {
  // Match .gitattributes without probing source files during configuration.
  lineEndings = LineEnding.UNIX

  // Kotlin has broad usage and ktlint support in Flint. Keep the much smaller Scala source set
  // in Spotless rather than adding a separate Flint integration for it.
  plugins.withId("scala") {
    scala {
      scalafmt()
      target("src/**/*.scala")
    }
  }
}

if (project == rootProject) {
  spotless {
    format("misc") {
      target(
        ".gitignore",
        ".gitattributes",
        ".gitconfig",
        ".editorconfig",
        "gradle.properties",
        ".github/**/*.sh",
        "examples/**/gradle.properties"
      )
      leadingTabsToSpaces()
      trimTrailingWhitespace()
      endWithNewline()
    }
  }
}
