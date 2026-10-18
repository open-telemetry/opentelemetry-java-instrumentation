import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-testing")
}

dependencies {
  library("org.apache.wicket:wicket:10.0.0")

  testImplementation(project(":instrumentation:wicket-8.0:common-testing"))
  testImplementation("org.eclipse.jetty:jetty-server:11.0.0")
  testImplementation("org.eclipse.jetty:jetty-servlet:11.0.0")

  testInstrumentation(project(":instrumentation:wicket-8.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("ExceptionSignalLogs") {
          filter {
            includeTestsMatching("*WicketTest.testException")
          }
          telemetryConfig("otel.semconv.exception.signal.preview", "logs")
        }
        register("ExceptionSignalLogsDup") {
          filter {
            includeTestsMatching("*WicketTest.testException")
          }
          telemetryConfig("otel.semconv.exception.signal.preview", "logs/dup")
        }
      }
    }
  }
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}
