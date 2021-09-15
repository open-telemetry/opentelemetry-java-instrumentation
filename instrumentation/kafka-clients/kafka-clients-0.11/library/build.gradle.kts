plugins {
  id("otel.library-instrumentation")
}

val versions: Map<String, String> by project

dependencies {
  implementation(project(":instrumentation:kafka-clients:kafka-clients-common:library"))
  library("org.apache.kafka:kafka-clients:0.11.0.0")

  testImplementation(project(":instrumentation:kafka-clients:kafka-clients-0.11:testing"))

  testImplementation("org.testcontainers:kafka:${versions["org.testcontainers"]}")
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

    // TODO run tests both with and without experimental span attributes
    jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=true")
  }
}
