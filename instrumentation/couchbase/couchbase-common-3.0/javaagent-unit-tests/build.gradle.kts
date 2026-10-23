import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:couchbase:couchbase-common-3.0:javaagent"))
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":javaagent-extension-api"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("ExceptionSignalLogs") {
          jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
        }
        register("ExceptionSignalLogsDup") {
          jvmArgs("-Dotel.semconv.exception.signal.preview=logs/dup")
        }
      }
    }
  }
}
