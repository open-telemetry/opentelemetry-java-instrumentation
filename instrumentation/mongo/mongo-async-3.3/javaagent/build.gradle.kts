import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.testcontainers-testing")
}

muzzle {
  pass {
    group.set("org.mongodb")
    module.set("mongodb-driver-async")
    versions.set("[3.3,)")
    assertInverse.set(true)
    extraDependency("org.mongodb:mongo-java-driver")
  }
}

dependencies {
  implementation(project(":instrumentation:mongo:mongo-3.1:library"))

  library("org.mongodb:mongodb-driver-async:3.3.0")

  testImplementation(project(":instrumentation:mongo:mongo-common:testing"))

  testInstrumentation(project(":instrumentation:mongo:mongo-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:mongo:mongo-4.0:javaagent"))
}

testing.suites {
  executions("test") {
    register("QueryText") {
      filter.includeTestsMatching("*MongoAsyncClientTest")
      telemetryConfig("otel.instrumentation.mongo.capture-query-text", "true")
    }
  }
}
