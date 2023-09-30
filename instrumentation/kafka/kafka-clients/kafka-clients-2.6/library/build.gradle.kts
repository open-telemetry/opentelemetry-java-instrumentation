plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common:library"))
  library("org.apache.kafka:kafka-clients:2.6.0")

  testImplementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:testing"))
  testImplementation("com.fasterxml.jackson.core:jackson-databind:2.10.2")

  testImplementation("org.testcontainers:kafka")
  testImplementation("org.testcontainers:junit-jupiter")

  testCompileOnly("com.google.auto.value:auto-value-annotations")
  testAnnotationProcessor("com.google.auto.value:auto-value")
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
}
