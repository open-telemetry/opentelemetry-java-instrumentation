plugins {
  id("com.gradleup.shadow")
  id("otel.java-conventions")
}

dependencies {
  implementation("io.opentelemetry.proto:opentelemetry-proto")
}

tasks {
  shadowJar {
    relocate("com.google.protobuf", "io.opentelemetry.proto.internal.protobuf")
  }

  val extractShadowJar by registering(Copy::class) {
    dependsOn(shadowJar)
    from(zipTree(shadowJar.get().archiveFile))
    into("build/extracted/shadow")
    includeEmptyDirs = false
  }
}
