import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.unitTests

/*
 * Classes shared by the Couchbase 3.x tracing SPI adapters.
 */
plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

testing.suites {
  unitTests {
    dependencies {
      implementation(project(":instrumentation-api-incubator"))
      implementation(project(":javaagent-extension-api"))
      implementation("io.opentelemetry:opentelemetry-sdk-testing")
    }
  }

  executions("unitTests") {
    register("ExceptionSignalLogs") {
      jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
    }
    register("ExceptionSignalLogsDup") {
      jvmArgs("-Dotel.semconv.exception.signal.preview=logs/dup")
    }
  }
}
