import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("org.springframework")
    module.set("spring-web")
    versions.set("[6.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.springframework:spring-web:6.0.0")

  testInstrumentation(project(":instrumentation:http-url-connection:javaagent"))
}

// spring 6 requires java 17
otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

testing.suites.named<JvmTestSuite>("test") {
  executions {
    register("Experimental") {
      telemetryConfig("otel.instrumentation.http.client.emit-experimental-telemetry", "true")
    }
  }
}
