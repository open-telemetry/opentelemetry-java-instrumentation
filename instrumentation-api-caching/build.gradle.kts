import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("com.github.johnrengelman.shadow")

  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.instrumentation"

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
  named<ShadowJar>("shadowJar") {
    configurations = listOf(shadowInclude)

    archiveClassifier.set("")

    relocate("com.github.benmanes.caffeine", "io.opentelemetry.instrumentation.api.internal.shaded.caffeine")
    relocate("com.blogspot.mydailyjava.weaklockfree", "io.opentelemetry.instrumentation.api.internal.shaded.weaklockfree")

    minimize()
  }

  named("jar") {
    enabled = false

    dependsOn(shadowJar)
  }
}

// Because shadow does not use default configurations
publishing {
  publications {
    named<MavenPublication>("maven") {
      project.shadow.component(this)
    }
  }
}
