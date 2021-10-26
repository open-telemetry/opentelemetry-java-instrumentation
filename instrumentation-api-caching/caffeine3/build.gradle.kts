plugins {
  id("com.github.johnrengelman.shadow")

  id("otel.java-conventions")
}

val shadowInclude by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}

val caffeine3Version: String by project

dependencies {
  compileOnly("com.github.ben-manes.caffeine:caffeine:$caffeine3Version")
  shadowInclude("com.github.ben-manes.caffeine:caffeine:$caffeine3Version") {
    exclude("com.google.errorprone", "error_prone_annotations")
    exclude("org.checkerframework", "checker-qual")
  }
}

// patch inner class from Caffeine to avoid ForkJoinTask from being loaded too early in the javaagent
val patch by sourceSets.creating {
  java {}
}

tasks {
  shadowJar {
    configurations = listOf(shadowInclude)

    relocate("com.github.benmanes.caffeine", "io.opentelemetry.instrumentation.api.internal.shaded.caffeine3")

    minimize()
  }

  javadoc {
    enabled = false
  }
}
