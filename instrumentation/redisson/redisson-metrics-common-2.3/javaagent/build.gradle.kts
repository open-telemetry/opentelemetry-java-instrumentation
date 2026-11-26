import io.opentelemetry.instrumentation.gradle.unitTests

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  compileOnly("org.redisson:redisson:2.3.0")
}

testing.suites {
  unitTests {
    dependencies {
      implementation("io.opentelemetry:opentelemetry-api")
    }
  }
}
