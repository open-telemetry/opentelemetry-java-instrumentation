plugins {
  id("com.github.johnrengelman.shadow")

  id("otel.java-conventions")
}

val shadowInclude by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}

dependencies {
  compileOnly("com.github.ben-manes.caffeine:caffeine")
  shadowInclude("com.github.ben-manes.caffeine:caffeine") {
    exclude("com.google.errorprone", "error_prone_annotations")
    exclude("org.checkerframework", "checker-qual")
  }

  compileOnly("com.blogspot.mydailyjava:weak-lock-free")
  shadowInclude("com.blogspot.mydailyjava:weak-lock-free")
}

tasks {
  shadowJar {
    configurations = listOf(shadowInclude)

    relocate("com.github.benmanes.caffeine", "io.opentelemetry.instrumentation.api.internal.shaded.caffeine")
    relocate("com.blogspot.mydailyjava.weaklockfree", "io.opentelemetry.instrumentation.api.internal.shaded.weaklockfree")

    minimize()
  }
}
