import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.undertow")
    module.set("undertow-core")
    versions.set("[1.4.0.Final,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.undertow:undertow-core:2.0.0.Final")

  bootstrap(project(":instrumentation:executors:bootstrap"))
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))
  bootstrap(project(":instrumentation:undertow-1.4:bootstrap"))
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("ExceptionSignalLogs") {
          telemetryConfig("otel.semconv.exception.signal.preview", "logs")
        }
      }
    }
  }
}

// since 2.3.x, undertow is compiled by JDK 11
if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}
