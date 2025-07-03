plugins {
  `kotlin-dsl`

  // When updating, update below in dependencies too
  id("com.diffplug.spotless") version "7.0.4"
}

spotless {
  kotlinGradle {
    ktlint().editorConfigOverride(
      mapOf(
        "indent_size" to "2",
        "continuation_indent_size" to "2",
        "max_line_length" to "160",
        "insert_final_newline" to "true",
        "ktlint_standard_no-wildcard-imports" to "disabled",
        // ktlint does not break up long lines, it just fails on them
        "ktlint_standard_max-line-length" to "disabled",
        // ktlint makes it *very* hard to locate where this actually happened
        "ktlint_standard_trailing-comma-on-call-site" to "disabled",
        // depends on ktlint_standard_wrapping
        "ktlint_standard_trailing-comma-on-declaration-site" to "disabled",
        // also very hard to find out where this happens
        "ktlint_standard_wrapping" to "disabled"
      )
    )
    target("**/*.gradle.kts")
  }
}

repositories {
  gradlePluginPortal()

  // for otel upstream snapshots
  maven {
    url = uri("https://central.sonatype.com/repository/maven-snapshots/")
  }
}

dependencies {
  implementation(gradleApi())

  implementation("com.diffplug.spotless:spotless-plugin-gradle:7.0.4")
  implementation("io.opentelemetry.instrumentation:gradle-plugins:2.9.0-alpha")

  // keep these versions in sync with settings.gradle.kts
  implementation("com.gradleup.shadow:shadow-gradle-plugin:8.3.7")
}
