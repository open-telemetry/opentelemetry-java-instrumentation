plugins {
  id("com.gradleup.shadow")
  id("otel.java-conventions")
}

dependencies {
  implementation("io.opentelemetry.proto:opentelemetry-proto")
  implementation("com.google.protobuf:protobuf-java-util:4.32.1")
}

tasks {
  shadowJar {
    relocate("io.opentelemetry.proto", "io.opentelemetry.testing.internal.proto")
    relocate("com.google.protobuf", "io.opentelemetry.testing.internal.protobuf")
    relocate("com.google.gson", "io.opentelemetry.testing.internal.gson")
    relocate("com.google.common", "io.opentelemetry.testing.internal.guava")

    enableAutoRelocation = true
    relocationPrefix = "io.opentelemetry.testing.internal"
  }

  val extractShadowJar by registering(Copy::class) {
    dependsOn(shadowJar)
    from(zipTree(shadowJar.get().archiveFile))
    into("build/extracted/shadow")
    includeEmptyDirs = false
  }
}
