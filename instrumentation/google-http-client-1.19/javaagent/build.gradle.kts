import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.google.http-client")
    module.set("google-http-client")

    // 1.19.0 is the first release.  The versions before are betas and RCs
    versions.set("[1.19.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.google.http-client:google-http-client:1.19.0")
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("WithServiceMapping") {
          telemetryConfig(
            "otel.instrumentation.common.server-service-mapping",
            "127.0.0.1=test-server-service,localhost=test-server-service,192.0.2.1=test-server-service",
          )
        }
      }
    }
  }
}
