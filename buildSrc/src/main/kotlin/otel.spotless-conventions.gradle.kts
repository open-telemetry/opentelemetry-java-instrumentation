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
    // ktfmt() // only supports 4 space indentation
    ktlint().userData(mapOf("indent_size" to "2", "continuation_indent_size" to "2"))
    licenseHeaderFile(rootProject.file("gradle/enforcement/spotless.license.java"), "(package|import|public)")
    targetExclude(
      "src/main/kotlin/io.opentelemetry.instrumentation.javaagent-codegen.gradle.kts",
      "build/**")
  }
  format("misc") {
    // not using "**/..." to help keep spotless fast
    target(".gitignore", "*.md", "src/**/*.md", "*.sh", "src/**/*.properties")
    indentWithSpaces()
    trimTrailingWhitespace()
    endWithNewline()
  }
}
