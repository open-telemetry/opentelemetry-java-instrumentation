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
  implementation("javax.servlet:javax.servlet-api:3.0.1")
}

tasks {
  withType<JavaCompile>().configureEach {
    with(options) {
      release.set(8)
    }
  }
}
