import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway

plugins {
  id("net.ltgt.errorprone")
  id("net.ltgt.nullaway")
}

dependencies {
  errorprone("com.google.errorprone:error_prone_core:2.50.0")
  errorprone("com.uber.nullaway:nullaway:0.14.1")
}

nullaway {
  annotatedPackages.add("com.example")
}

tasks {
  withType<JavaCompile>().configureEach {
    options.errorprone.nullaway {
      if (name.contains("test", ignoreCase = true)) {
        disable()
      } else {
        error()
      }
    }
  }
}
