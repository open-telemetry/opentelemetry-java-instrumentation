import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
  java
  id("com.diffplug.spotless")
}

extensions.configure<SpotlessExtension>("spotless") {
  java {
    googleJavaFormat()
    licenseHeaderFile(rootProject.file("gradle/spotless.license.java"), "(package|import|public)")
    target("src/**/*.java")
    toggleOffOn()
  }
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
  }
}

val formatCode by tasks.registering {
  dependsOn(tasks.named("spotlessApply"))
}

tasks.named("check").configure {
  dependsOn(tasks.named("spotlessCheck"))
} 
