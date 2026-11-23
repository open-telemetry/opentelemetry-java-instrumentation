import io.opentelemetry.instrumentation.gradle.unitTests

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}

testing.suites {
  unitTests {
    dependencies {
      implementation(project(":javaagent-extension-api"))
      implementation(project(":instrumentation:couchbase:couchbase-2.0:javaagent"))
      implementation("com.couchbase.client:java-client:2.5.0")
    }
  }
}
