plugins {
  id("com.diffplug.spotless")
}

spotless {
  java {
    googleJavaFormat()
    licenseHeaderFile(rootProject.file("buildscripts/spotless.license.java"), "(package|import|public|// Includes work from:)")
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
      ktlint().userData(mapOf("indent_size" to "2", "continuation_indent_size" to "2", "disabled_rules" to "no-wildcard-imports"))
      licenseHeaderFile(rootProject.file("buildscripts/spotless.license.java"), "(package|import|class|// Includes work from:)")
    }
  }
  kotlinGradle {
    ktlint().userData(mapOf("indent_size" to "2", "continuation_indent_size" to "2", "disabled_rules" to "no-wildcard-imports"))
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
      "src/**/*.properties")
    indentWithSpaces()
    trimTrailingWhitespace()
    endWithNewline()
  }
}
