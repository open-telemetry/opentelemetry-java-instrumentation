import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
  id("otel.testcontainers-testing")
}

dependencies {
  library("org.mongodb:mongo-java-driver:3.1.0")

  implementation(project(":instrumentation-api-incubator"))

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation(project(":instrumentation:mongo:mongo-3.1:testing"))
  testImplementation("com.github.jnr:jnr-unixsocket:0.18")
}

testing.suites {
  executions("test") {
    register("QueryText") {
      filter.includeTestsMatching("*MongoClientTest")
      systemProperty("otel.instrumentation.mongo.capture-query-text", "true")
      systemProperty("metadataConfig", "MongoTelemetryBuilder.setCaptureQueryText(true)")
    }
  }
}
