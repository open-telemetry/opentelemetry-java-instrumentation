import io.opentelemetry.instrumentation.gradle.unitTests

plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("org.elasticsearch.client:transport:5.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}

testing.suites {
  unitTests {
    dependencies {
      implementation("org.elasticsearch.client:transport:5.0.0")
      implementation("org.apache.logging.log4j:log4j-api:2.11.0")
      implementation("org.apache.logging.log4j:log4j-core:2.11.0")
    }
  }
}
