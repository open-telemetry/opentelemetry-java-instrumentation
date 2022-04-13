plugins {
  war

  id("com.diffplug.spotless")
}

spotless {
  java {
    googleJavaFormat()
    licenseHeaderFile(rootProject.file("../../../buildscripts/spotless.license.java"), "(package|import|public)")
  }
  kotlinGradle {
    ktlint().userData(mapOf("indent_size" to "2", "continuation_indent_size" to "2", "disabled_rules" to "no-wildcard-imports"))
    target("**/*.gradle.kts")
  }
}

dependencies {
  compileOnly("jakarta.servlet:jakarta.servlet-api:5.0.0")
}

tasks {
  withType<JavaCompile>().configureEach {
    with(options) {
      release.set(8)
    }
  }
}
