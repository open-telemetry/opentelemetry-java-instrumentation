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
  unitTests().executions {
    register("ExceptionSignalLogs") {
      jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
    }
    register("ExceptionSignalLogsDup") {
      jvmArgs("-Dotel.semconv.exception.signal.preview=logs/dup")
    }
  }
}
