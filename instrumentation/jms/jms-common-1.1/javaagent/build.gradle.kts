import io.opentelemetry.instrumentation.gradle.unitTests

plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  bootstrap(project(":instrumentation:jms:jms-common-1.1:bootstrap"))
}

testing.suites {
  unitTests {
    dependencies {
      implementation(project(":instrumentation:jms:jms-common-1.1:bootstrap"))
      implementation("io.opentelemetry:opentelemetry-sdk")
      implementation("io.opentelemetry:opentelemetry-sdk-testing")
    }
  }
}
