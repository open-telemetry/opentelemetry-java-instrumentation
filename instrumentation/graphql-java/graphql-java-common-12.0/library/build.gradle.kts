import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("com.graphql-java:graphql-java:12.0")
}

if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
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
