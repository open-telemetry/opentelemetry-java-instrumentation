import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    coreJdk.set(true)
  }
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

dependencies {
  implementation(project(":instrumentation:java-http-client:library"))
  testImplementation(project(":instrumentation:java-http-client:testing"))
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("WithServerServiceMapping") {
          telemetryConfig(
            "otel.instrumentation.common.server-service-mapping",
            "127.0.0.1=test-server-service,localhost=test-server-service,192.0.2.1=test-server-service",
          )
        }
      }
    }
  }
}
