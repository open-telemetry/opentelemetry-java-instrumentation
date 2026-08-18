import com.diffplug.spotless.LineEnding

plugins {
  id("com.diffplug.spotless")
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
