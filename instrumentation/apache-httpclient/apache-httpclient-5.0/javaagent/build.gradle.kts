import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.httpcomponents.client5")
    module.set("httpclient5")
    versions.set("[5.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.httpcomponents.client5:httpclient5:5.0")
  // https://issues.apache.org/jira/browse/HTTPCORE-653
  testImplementation("org.apache.httpcomponents.core5:httpcore5:5.0.3")

  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-4.0:javaagent"))
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        configureEach {
          systemProperty("testLatestDeps", otelProps.testLatestDeps)
        }
        register("PreviewSemconv") {
          telemetryConfig("otel.semconv-stability.preview", "service.peer")
        }
      }
    }
  }
}
