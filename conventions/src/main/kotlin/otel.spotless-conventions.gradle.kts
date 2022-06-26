import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
  id("com.diffplug.spotless")
}

spotless {
  java {
    googleJavaFormat()
    licenseHeaderFile(rootProject.file("buildscripts/spotless.license.java"), "(package|import|public|// Includes work from:)")
    toggleOffOn()
    target("src/**/*.java")
  }
  plugins.withId("groovy") {
    groovy {
      licenseHeaderFile(rootProject.file("buildscripts/spotless.license.java"), "(package|import|class)")
    }
  }
  plugins.withId("scala") {
    scala {
      scalafmt()
      licenseHeaderFile(rootProject.file("buildscripts/spotless.license.java"), "(package|import|public)")
      target("src/**/*.scala")
    }
  }
  plugins.withId("org.jetbrains.kotlin.jvm") {
    kotlin {
      ktlint().userData(mapOf("continuation_indent_size" to "2", "disabled_rules" to "no-wildcard-imports"))
        .editorConfigOverride(mapOf("indent_size" to "2")) // not sure why it's not using the setting from .editorconfig
      licenseHeaderFile(rootProject.file("buildscripts/spotless.license.java"), "(package|import|class|// Includes work from:)")
    }
  }
  kotlinGradle {
    ktlint().userData(mapOf("continuation_indent_size" to "2", "disabled_rules" to "no-wildcard-imports"))
      .editorConfigOverride(mapOf("indent_size" to "2")) // not sure why it's not using the setting from .editorconfig
  }
  format("misc") {
    // not using "**/..." to help keep spotless fast
    target(
      ".gitignore",
      ".gitattributes",
      ".gitconfig",
      ".editorconfig",
      "*.md",
      "src/**/*.md",
      "docs/**/*.md",
      "*.sh",
      "src/**/*.properties"
    )
    indentWithSpaces()
    trimTrailingWhitespace()
    endWithNewline()
  }
}

// Use root declared tool deps to avoid issues with high concurrency.
if (project == rootProject) {
  spotless {
    predeclareDeps()
  }

  with(extensions["spotlessPredeclare"] as SpotlessExtension) {
    java {
      googleJavaFormat()
    }
    scala {
      scalafmt()
    }
    kotlin {
      ktlint()
    }
    kotlinGradle {
      ktlint()
    }
  }
}
