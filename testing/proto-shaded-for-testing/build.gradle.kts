plugins {
  id("com.gradleup.shadow")
  id("otel.java-conventions")
}

dependencies {
  implementation("io.opentelemetry.proto:opentelemetry-proto")
}

tasks {
  shadowJar {
    relocate("io.opentelemetry.proto", "io.opentelemetry.testing.internal.proto")
    relocate("com.google.protobuf", "io.opentelemetry.testing.internal.protobuf")
  }

  val extractShadowJar by registering(Copy::class) {
    dependsOn(shadowJar)
    from(zipTree(shadowJar.get().archiveFile))
    into("build/extracted/shadow")
    includeEmptyDirs = false
  }
}
