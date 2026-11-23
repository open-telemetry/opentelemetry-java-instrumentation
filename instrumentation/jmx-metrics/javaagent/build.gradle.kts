import io.opentelemetry.instrumentation.gradle.unitTests

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  implementation(project(":instrumentation:jmx-metrics:library"))

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
}

testing.suites {
  unitTests {
    dependencies {
      implementation(project(":javaagent-extension-api"))
      implementation(project(":instrumentation:jmx-metrics:library"))
      implementation(project(":declarative-config-bridge"))
      implementation(project(":testing:testing-common"))
      implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
      implementation("io.opentelemetry:opentelemetry-sdk-testing")
    }
  }
}
