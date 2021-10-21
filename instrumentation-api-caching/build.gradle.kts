plugins {
  id("com.github.johnrengelman.shadow")

  id("otel.java-conventions")
}

sourceSets {
  main {
    val caffeine2ShadedDeps = project(":instrumentation-api-caching:caffeine2")
    output.dir(caffeine2ShadedDeps.file("build/extracted/shadow"), "builtBy" to ":instrumentation-api-caching:caffeine2:extractShadowJar")

    val caffeine3ShadedDeps = project(":instrumentation-api-caching:caffeine3")
    output.dir(caffeine3ShadedDeps.file("build/extracted/shadow"), "builtBy" to ":instrumentation-api-caching:caffeine3:extractShadowJar")
  }
}

val shadowInclude by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}

dependencies {
  compileOnly(project(":instrumentation-api-caching:caffeine2", configuration = "shadow"))
  compileOnly(project(":instrumentation-api-caching:caffeine3", configuration = "shadow"))

  compileOnly("org.checkerframework:checker-qual:3.14.0")
  compileOnly("com.blogspot.mydailyjava:weak-lock-free")
  shadowInclude("com.blogspot.mydailyjava:weak-lock-free")
}

tasks {
  shadowJar {
    configurations = listOf(shadowInclude)

    relocate("com.blogspot.mydailyjava.weaklockfree", "io.opentelemetry.instrumentation.api.internal.shaded.weaklockfree")
  }

  val extractShadowJar by registering(Copy::class) {
    dependsOn(shadowJar)
    from(zipTree(shadowJar.get().archiveFile)) {
      exclude("META-INF/**")
    }
    into("build/extracted/shadow")
  }
}
