plugins {
  id("com.diffplug.spotless")
}

spotless {
  java {
    googleJavaFormat("1.10.0")
    licenseHeaderFile(rootProject.file("gradle/enforcement/spotless.license.java"), "(package|import|public|// Includes work from:)")
    target("src/**/*.java")
  }
  groovy {
    licenseHeaderFile(rootProject.file("gradle/enforcement/spotless.license.java"), "(package|import|class)")
  }
  scala {
    scalafmt()
    licenseHeaderFile(rootProject.file("gradle/enforcement/spotless.license.java"), "(package|import|public)")
    target("src/**/*.scala")
  }
  kotlin {
    ktlint().userData(mapOf("indent_size" to "2", "continuation_indent_size" to "2"))
    licenseHeaderFile(rootProject.file("gradle/enforcement/spotless.license.java"), "(package|import|public)")
  }
  kotlinGradle {
    ktlint().userData(mapOf("indent_size" to "2", "continuation_indent_size" to "2"))
  }
  format("misc") {
    // not using "**/..." to help keep spotless fast
    target(".gitignore", "*.md", "src/**/*.md", "*.sh", "src/**/*.properties")
    indentWithSpaces()
    trimTrailingWhitespace()
    endWithNewline()
  }
}
