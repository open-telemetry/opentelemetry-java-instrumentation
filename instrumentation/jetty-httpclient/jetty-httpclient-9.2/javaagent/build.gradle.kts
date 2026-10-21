import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("org.eclipse.jetty")
    module.set("jetty-client")
    versions.set("[9.2,10)")
    assertInverse.set(true)
  }
}

// Jetty client 9.2 is the best starting point, HttpClient.send() is stable there
val jettyVersBase9 = "9.2.0.v20140526"

dependencies {
  implementation(project(":instrumentation:jetty-httpclient:jetty-httpclient-9.2:library"))

  library("org.eclipse.jetty:jetty-client:$jettyVersBase9")

  testInstrumentation(project(":instrumentation:jetty-httpclient:jetty-httpclient-12.0:javaagent"))

  testImplementation(project(":instrumentation:jetty-httpclient:jetty-httpclient-9.2:testing"))

  latestDepTestLibrary("org.eclipse.jetty:jetty-client:9.+") // documented limitation
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("PreviewSemconv") {
          telemetryConfig("otel.semconv-stability.preview", "service.peer")
        }
      }
    }
  }
}
