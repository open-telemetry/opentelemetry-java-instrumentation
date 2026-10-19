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
        register("WithPeerServiceMapping") {
          telemetryConfig("otel.instrumentation.common.peer-service-mapping", "127.0.0.1=test-peer-service,localhost=test-peer-service,192.0.2.1=test-peer-service")
        }
        register("PreviewSemconvWithPeerServiceMapping") {
          telemetryConfig("otel.semconv-stability.preview", "service.peer")
          telemetryConfig("otel.instrumentation.common.peer-service-mapping", "127.0.0.1=test-peer-service,localhost=test-peer-service,192.0.2.1=test-peer-service")
        }
      }
    }
  }
}
