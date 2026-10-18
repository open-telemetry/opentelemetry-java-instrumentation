import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

dependencies {
  testImplementation(project(":instrumentation:java-http-client:testing"))
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("PreviewSemconv") {
          jvmArgs("-Dotel.semconv-stability.preview=service.peer")
        }
      }
    }
  }
}
