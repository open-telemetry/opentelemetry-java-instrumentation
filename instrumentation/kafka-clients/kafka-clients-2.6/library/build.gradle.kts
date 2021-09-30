plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:kafka-clients:kafka-clients-common:library"))
  library("org.apache.kafka:kafka-clients:2.6.0")

  testImplementation(project(":instrumentation:kafka-clients:kafka-clients-0.11:testing"))
  testImplementation("com.fasterxml.jackson.core:jackson-databind:2.10.2")

  testImplementation("org.testcontainers:kafka")
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

    // TODO run tests both with and without experimental span attributes
    jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=true")
  }
}
