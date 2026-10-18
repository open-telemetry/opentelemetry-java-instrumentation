import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("com.ning")
    module.set("async-http-client")
    versions.set("[1.9.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:async-http-client:async-http-client-common-1.8:javaagent"))

  library("com.ning:async-http-client:1.9.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testInstrumentation(project(":instrumentation:netty:netty-3.8:javaagent"))
  testInstrumentation(project(":instrumentation:async-http-client:async-http-client-1.8:javaagent"))
  testInstrumentation(project(":instrumentation:async-http-client:async-http-client-2.0:javaagent"))
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        configureEach {
          // required on jdk17
          jvmArgs("--add-exports=java.base/sun.security.util=ALL-UNNAMED")
          jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

          systemProperty("testLatestDeps", otelProps.testLatestDeps)
        }
        register("PreviewSemconv") {
          telemetryConfig("otel.semconv-stability.preview", "service.peer")
        }
      }
    }
  }
}
