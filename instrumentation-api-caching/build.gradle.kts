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

// patch inner class from Caffeine to avoid ForkJoinTask from being loaded too early in the javaagent
val patch by sourceSets.creating {
  java {}
}

tasks {
  shadowJar {
    configurations = listOf(shadowInclude)

    relocate("com.github.benmanes.caffeine", "io.opentelemetry.instrumentation.api.internal.shaded.caffeine")
    relocate("com.blogspot.mydailyjava.weaklockfree", "io.opentelemetry.instrumentation.api.internal.shaded.weaklockfree")

    minimize()
  }

  val extractShadowJar by registering(Copy::class) {
    dependsOn(shadowJar)

    // replace caffeine class with our patched version
    from(zipTree(shadowJar.get().archiveFile)) {
      exclude("io/opentelemetry/instrumentation/api/internal/shaded/caffeine/cache/BoundedLocalCache\$PerformCleanupTask.class")
    }
    from(patch.output) {
      include("io/opentelemetry/instrumentation/api/internal/shaded/caffeine/cache/BoundedLocalCache\$PerformCleanupTask.class")
    }

    into("build/extracted/shadow")
    // prevents empty com/github/benmanes/caffeine/cache path from ending up in instrumentation-api
    includeEmptyDirs = false
  }
}
